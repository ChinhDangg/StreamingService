import {apiRequest} from "/static/js/common.js";

export async function endVideoUploadSession(uploadId, uploadedParts, basicInfo, nameUpdateList, isLast = false) {
    const endResponse = await apiRequest('/api/upload/media/end-session-video', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            uploadId: uploadId,
            uploadedParts: uploadedParts,
            basicInfo: basicInfo,
            isLast: isLast,
            nameUpdateList: nameUpdateList
        })
    });
    if (!endResponse.ok) {
        return 'Error: Failed to finalize video upload: ' + await endResponse.text();
    }
    return endResponse.text();
}

export async function endFileSession(uploadId, uploadedParts, isLast = false){
    const response = await apiRequest('/api/upload/media/end-session-file', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            uploadId: uploadId,
            uploadedParts: uploadedParts,
            isLast: isLast
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
        uploadingFiles.set(fileName, { file: file, sessionId: sessionId, uploadId: uploadId, fileName: fileName, chunks: chunks, eTags: eTags, partNumber: chunks.partNumber ? chunks.partNumber : 0 });

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
        uploadingFiles.get(fileName).sessionId = sessionId;

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
        uploadingFiles.get(fileName).uploadId = uploadId;


    const startIdx = chunks.partNumber ? chunks.partNumber : 0;
    const CONCURRENCY_LIMIT = 4; // 4 parallel uploads
    const tasks = chunks.slice(startIdx); // The remaining chunks to upload
    let hasError = false; // Flag to stop other workers if one fails

    const worker = async () => {
        // Keep looping as long as there are tasks and no errors have occurred
        while (tasks.length > 0 && !hasError) {
            const c = tasks.shift();

            try {
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
                if (!urlResponse.ok) throw new Error('Presign: ' + await urlResponse.text());
                const urlRes = await urlResponse.text();


                const res = await apiRequest(urlRes, {
                    method: 'PUT',
                    body: c.blob
                });
                if (!res.ok) throw new Error('Upload: ' + await res.text());

                eTags.push({
                    partNumber: c.partNumber,
                    etag: res.headers.get('ETag').replace(/"/g, '')
                });

                if (uploadingFiles) uploadingFiles.get(fileName).partNumber++;
                if (showProgressFn) showProgressFn(c.size);

            } catch (error) {
                hasError = true;
                if (currentFailTexts) currentFailTexts.push(error.message);
            }
        }
    }

    // Start 4 workers at the same time
    const workers = Array(Math.min(CONCURRENCY_LIMIT, tasks.length))
        .fill(null)
        .map(() => worker());

    await Promise.all(workers);

    if (hasError) return false;

    // S3 requires part numbers to be in strictly ascending order
    eTags.sort((a, b) => a.partNumber - b.partNumber);

    return true;
}

function chunkFile(file) {
    const MAX_PARTS = 10000;
    const MIN_CHUNK = 5 * 1024 * 1024;       // 5 MB
    const DEFAULT_CHUNK = 15 * 1024 * 1024;  // 15 MB
    const MAX_CHUNK = 64 * 1024 * 1024;      // 64 MB

    let chunkSize = DEFAULT_CHUNK;

    // Dynamically resize chunk to avoid more than 10,000 parts
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
