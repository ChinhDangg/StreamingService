

const singleFileInput = document.getElementById('single-file-input');
const folderInput = document.getElementById('folder-input');
const savingPathInput = document.getElementById('saving-path-input');

const submitBtn = document.getElementById('submit-btn');

let currentFiles = null;

const MediaTypes = Object.freeze({
    VIDEO: 'VIDEO',
    ALBUM: 'ALBUM',
    GROUPER: 'GROUPER',
});

let currentMediaType = null;

singleFileInput.addEventListener('change', () => {
    if (!singleFileInput.files.length) {
        updateSavingPath('');
        return;
    }
    const file = singleFileInput.files[0];
    currentFiles = file;
    updateSavingPath(file.name);
    currentMediaType = MediaTypes.VIDEO;
});

const ALLOWED = ["video/", "image/png", "image/jpeg", "image/gif"];

folderInput.addEventListener('change', () => {
    if (!folderInput.files.length) {
        updateSavingPath('');
        return;
    }

    const files = Array.from(folderInput.files);
    const validFiles = files.filter((f) => {
        return (
            ALLOWED.some(a => f.type.startsWith(a)) ||
            // fallback: extension based
            /\.(mp4|mp3|mov|png|jpg|jpeg|gif|webp)$/i.test(f.name)
        );
    });
    if (!validFiles.length) {
        alert("No valid files found " + ALLOWED.join(", "));
        return;
    }
    currentFiles = validFiles;

    const relativePath = validFiles[0].webkitRelativePath;
    const folderPath = relativePath.substring(0, relativePath.lastIndexOf('/'));

    updateSavingPath(folderPath);
    currentMediaType = MediaTypes.ALBUM;
});

function updateSavingPath(path) {
    savingPathInput.value = path;
}

submitBtn.addEventListener('click',async () => {
    console.log('submitting');
    if (currentMediaType === null) {
        alert('No file selected');
        return;
    }
    if (currentFiles === null) {
        alert('No file selected');
        return;
    }
    const savingPath = savingPathInput.value.trim();
    if (savingPath.length === 0) {
        alert('No saving path entered');
        return;
    }
    if (currentMediaType === MediaTypes.VIDEO) {
        if (!validateVideo(savingPath)) return;
    } else if (currentMediaType === MediaTypes.ALBUM) {
        if (!validateDirectory(savingPath)) return;
    }
    const sessionResponse = await fetch('/api/upload/media/create-session', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            objectKey: savingPath,
            mediaType: currentMediaType
        })
    });
    if (!sessionResponse.ok) {
        alert('Failed to create session: ' + await sessionResponse.text());
        return;
    }
    const sessionId = await sessionResponse.text();
    console.log('sessionId: ' + sessionId);
    if (currentMediaType === MediaTypes.VIDEO) {
        await uploadFile(sessionId, currentFiles, savingPath, MediaTypes.VIDEO);
    } else if (currentMediaType === MediaTypes.ALBUM) {
        for (const f of currentFiles) {
            await uploadFile(sessionId, f, savingPath + '/' + f.name, MediaTypes.ALBUM);
        }
    }
});

async function uploadFile(sessionId, file, fileName, mediaType) {

    console.log('fileName: ' + fileName);

    const response = await fetch('/api/upload/media/initiate', {
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
        alert('Failed to init ' + await response.text());
        return;
    }

    const uploadId = await response.text();
    console.log('uploadId: ' + uploadId);

    const chunks = chunkFile(file);
    const etags = [];

    console.log('chunks:');
    console.log(chunks);

    for (const c of chunks) {
        const urlResponse = await fetch('/api/upload/media/presign-part-url', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                uploadId: uploadId,
                objectKey: fileName,
                partNumber: c.partNumber
            })
        });
        if (!urlResponse.ok) {
            alert('Failed to get presign url for chunks ' + await urlResponse.text());
            return;
        }
        const urlRes = await urlResponse.text();
        console.log('Presigned url: ' + urlRes);

        const res = await fetch(urlRes, {
            method: 'PUT',
            body: c.blob
        });
        console.log('blob upload result: ' + res);

        etags.push({
            partNumber: c.partNumber,
            etag: res.headers.get('ETag').replace(/"/g, '')
        });
    }

    const completeResponse = await fetch('/api/upload/media/complete', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            uploadId: uploadId,
            objectKey: fileName,
            uploadedParts: etags
        })
    });
    if (!completeResponse.ok) {
        alert('Failed to complete ' + await completeResponse.text());
    }
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
        });
        partNumber++;
        start = end;
    }

    return chunks;
}

function validateVideo(path) {
    const INVALID = /[<>:"|?*\x00-\x1F]/;
    const VIDEO_EXTS = ['mp4','mkv','mov','avi','wmv','flv','webm','m4v'];
    const trimmed = path.trim();
    let errors = [];

    if (!trimmed.length) {
        errors.push("Video path cannot be empty.");
    }

    // Contains invalid characters
    if (INVALID.test(trimmed)) {
        errors.push(`Path contains invalid characters: "${path}"`);
    }

    // Extract file name
    const file = trimmed.split(/[/\\]/).at(-1);

    // Must include extension
    if (!file.includes(".")) {
        errors.push("Video file must have an extension.");
    } else {
        const ext = file.split(".").at(-1).toLowerCase();
        if (!VIDEO_EXTS.includes(ext)) {
            errors.push(`Unsupported video format: .${ext}`);
        }
    }

    if (errors.length) {
        alert("Invalid video:\n\n" + errors.join("\n"));
        return false;
    }
    return true;
}


function validateDirectory(path) {
    const INVALID = /[<>:"|?*\x00-\x1F]/; // Windows + control chars
    const trimmed = path.trim();
    let errors = [];

    // Empty
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

    if (errors.length) {
        alert("Invalid directory:\n\n" + errors.join("\n"));
        return false;
    }
    return true;
}


