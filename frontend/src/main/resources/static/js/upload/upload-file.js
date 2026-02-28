import {apiRequest} from "/static/js/common.js";

export async function endVideoUploadSession(uploadId, uploadedParts, basicInfo) {
    const endResponse = await apiRequest('/api/upload/media/end-session-video', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            uploadId: uploadId,
            uploadedParts: uploadedParts,
            basicInfo: basicInfo
        })
    });
    if (!endResponse.ok) {
        return 'Error: Failed to finalize video upload: ' + await endResponse.text();
    }
    return endResponse.text();
}

export async function endFileSession(uploadId, uploadedParts){
    const response = await apiRequest('/api/upload/media/end-session-file', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            uploadId: uploadId,
            uploadedParts: uploadedParts
        })
    });
    if (!response.ok) {
        return 'Error: Failed to finalize file upload: ' + await response.text();
    }
    return "Success";
}

export async function uploadFile(sessionId, file, fileName,
                                 uploadingFiles = null, currentFailTexts = null,
                                 chunks = null, eTags = null, uploadId = null,
                                 showProgressFn = null) {

    console.log('fileName: ' + fileName);

    chunks = chunks == null ? chunkFile(file) : chunks;
    eTags = eTags == null ? [] : eTags;

    console.log('chunks:');
    console.log(chunks);

    if (uploadingFiles)
        uploadingFiles.set(file, { sessionId: sessionId, uploadId: uploadId, chunks: chunks, eTags: eTags, partNumber: chunks.partNumber ? chunks.partNumber : 0 });

    const startUploadSession = async(filePath) => {
        const sessionResponse = await apiRequest('/api/file/upload/create-session', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                filePath: filePath
            })
        });
        if (!sessionResponse.ok) {
            return 'Error: Failed to start upload: ' + await sessionResponse.text();
        }
        return await sessionResponse.text();
    }

    sessionId = sessionId == null ? await startUploadSession(fileName) : sessionId;
    if (!sessionId || sessionId.startsWith('Error:')) {
        if (currentFailTexts)
            currentFailTexts.push(sessionId);
        return false;
    }
    console.log('sessionId: ' + sessionId);

    if (uploadingFiles)
        uploadingFiles.get(file).sessionId = sessionId;

    const initiateUpload = async () => {
        const response = await apiRequest('/api/upload/media/initiate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: sessionId
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
                uploadId: uploadId,
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
        if (!res.ok) {
            if (currentFailTexts)
                currentFailTexts.push('Upload: ' + await res.text());
            return false;
        }
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
