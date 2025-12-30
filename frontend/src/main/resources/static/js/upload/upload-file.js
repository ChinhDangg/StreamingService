import {apiRequest} from "/static/js/common.js";

export async function startUploadSession(objectKey, mediaType) {
    const sessionResponse = await apiRequest('/api/upload/media/create-session', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            objectKey: objectKey,
            mediaType: mediaType
        })
    });
    if (!sessionResponse.ok) {
        alert('Failed to start upload: ' + await sessionResponse.text());
        return null;
    }
    return await sessionResponse.text();
}

export async function endUploadSession(body) {
    if (body.sessionId == null) {
        alert('No sessionId found');
        return null;
    }
    const endResponse = await apiRequest('/api/upload/media/end-session', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
    });
    if (!endResponse.ok) {
        alert('Failed to finalize upload: ' + await endResponse.text());
        return null;
    }
    return await endResponse.text();
}

export async function uploadFile(sessionId, file, fileName, mediaType,
                                 uploadingFiles = null, currentFailTexts = null,
                                 chunks = null, eTags = null, uploadId = null,
                                 showProgressFn = null) {

    console.log('fileName: ' + fileName);

    chunks = chunks == null ? chunkFile(file) : chunks;
    eTags = eTags == null ? [] : eTags;

    console.log('chunks:');
    console.log(chunks);

    if (uploadingFiles)
        uploadingFiles.set(file, { uploadId: uploadId, chunks: chunks, eTags: eTags, partNumber: chunks.partNumber ? chunks.partNumber : 0 });

    const initiateUpload = async () => {
        const response = await apiRequest('/api/upload/media/initiate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: sessionId,
                objectKey: fileName,
                mediaType: mediaType
            })
        });
        if (!response.ok) {
            if (currentFailTexts)
                currentFailTexts.push('Init: ' + await response.text());
            return false;
        }
        return await response.text();
    }

    uploadId = uploadId == null ? await initiateUpload() : uploadId;
    if (!uploadId)
        return false;
    console.log('uploadId: ' + uploadId);

    if (uploadingFiles)
        uploadingFiles.get(file).uploadId = uploadId;

    const start = chunks.partNumber ? chunks.partNumber : 0;

    for (let i = start; i < chunks.length; i++) {
        const c = chunks[i];
        const urlResponse = await apiRequest('/api/upload/media/presign-part-url', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: sessionId,
                uploadId: uploadId,
                objectKey: fileName,
                partNumber: c.partNumber
            })
        });
        if (!urlResponse.ok) {
            if (currentFailTexts)
                currentFailTexts.push('Presign: ' + await urlResponse.text());
            return false;
        }
        const urlRes = await urlResponse.text();
        console.log('Presigned url: ' + urlRes);

        const res = await apiRequest(urlRes, {
            method: 'PUT',
            body: c.blob
        });
        console.log('blob upload result: ' + res);

        eTags.push({
            partNumber: c.partNumber,
            etag: res.headers.get('ETag').replace(/"/g, '')
        });

        if (uploadingFiles) {
            uploadingFiles.get(file).partNumber++;
        }

        if (showProgressFn)
            showProgressFn(c.size);
    }

    const completeResponse = await apiRequest('/api/upload/media/complete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            sessionId: sessionId,
            uploadId: uploadId,
            objectKey: fileName,
            uploadedParts: eTags
        })
    });
    if (!completeResponse.ok) {
        if (currentFailTexts)
            currentFailTexts.push('Complete: ' + await completeResponse.text());
        return false;
    }

    if (uploadingFiles)
        uploadingFiles.delete(file);

    return true;
}

function chunkFile(file) {
    const MAX_PARTS = 1000;
    const MIN_CHUNK = 5 * 1024 * 1024;       // 5 MB
    const DEFAULT_CHUNK = 8 * 1024 * 1024;   // 8 MB
    const MAX_CHUNK = 64 * 1024 * 1024;      // 64 MB

    let chunkSize = DEFAULT_CHUNK;

    // Dynamically resize chunk to avoid more than 1000 parts
    const estimatedParts = Math.ceil(file.size / chunkSize);
    if (estimatedParts > MAX_PARTS) {
        chunkSize = Math.ceil(file.size / MAX_PARTS);
    }

    // Hard clamp between safe range
    chunkSize = Math.max(chunkSize, MIN_CHUNK);
    chunkSize = Math.min(chunkSize, MAX_CHUNK);

    const chunks = [];
    let start = 0;
    let partNumber = 1;

    while (start < file.size) {
        const end = Math.min(start + chunkSize, file.size);
        chunks.push({
            partNumber,
            blob: file.slice(start, end),
            size: end - start
        });
        partNumber++;
        start = end;
    }

    return chunks;
}

export function validateDirectory(path) {
    const INVALID = /[<>:"|?*\x00-\x1F]/; // Windows + control chars
    const trimmed = path.trim();
    let errors = [];

    // Must not be empty
    if (!trimmed.length) {
        errors.push("Path cannot be empty.");
    }

    // Contains invalid characters
    if (INVALID.test(trimmed)) {
        errors.push(`Path contains invalid characters: "${path}"`);
    }

    // Cannot end with slash or backslash
    if (/[/\\]$/.test(trimmed)) {
        errors.push("Directory path must not end with a slash.");
    }

    // Cannot look like a file (should have no extension)
    const parts = trimmed.split(/[/\\]/).filter(Boolean);
    const last = parts.at(-1);
    if (/\.[^./\\]+$/i.test(last)) {
        errors.push("This looks like a file, not a directory.");
    }

    // Must contain at least ONE letter (any language)
    // \p{L} covers all alphabets: Chinese, Vietnamese, Arabic, etc.
    if (!/\p{L}/u.test(trimmed)) {
        errors.push("Path must contain at least one letter (any language).");
    }

    if (errors.length) {
        alert("Invalid directory:\n\n" + errors.join("\n"));
        return null;
    }

    return trimmed;
}
