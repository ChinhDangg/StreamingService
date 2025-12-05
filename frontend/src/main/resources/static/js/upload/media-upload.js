

const singleFileInput = document.getElementById('single-file-input');
const folderInput = document.getElementById('folder-input');
const savingPathInput = document.getElementById('saving-path-input');
const grouperInput = document.getElementById('grouper-input');

const titleInput = document.getElementById('title-input');
const yearInput = document.getElementById('year-input');

const submitBtn = document.getElementById('submit-btn');

let currentSavingPath = null;
let currentFiles = null;
const uploadingFiles = new Map();
let currentSessionId = null;
let currentFailTexts = [];

const MediaTypes = Object.freeze({
    VIDEO: 'VIDEO',
    ALBUM: 'ALBUM',
    GROUPER: 'GROUPER',
});

let currentMediaType = null;

const uploadTypeSelect = document.getElementById('upload-type-select');
const singleFileContainer = document.getElementById('single-file-container');
const folderContainer = document.getElementById('folder-container');
const grouperContainer = document.getElementById('grouper-container');
const savingPathContainer = document.getElementById('saving-path-container');

uploadTypeSelect.addEventListener('change', () => {
    const selected = uploadTypeSelect.value;
    if (selected === MediaTypes.VIDEO) {
        currentMediaType = MediaTypes.VIDEO;
        singleFileContainer.classList.remove('hidden');
        savingPathContainer.classList.remove('hidden');
        folderContainer.classList.add('hidden');
        grouperContainer.classList.add('hidden');

    } else if (selected === MediaTypes.ALBUM) {
        currentMediaType = MediaTypes.ALBUM;
        folderContainer.classList.remove('hidden');
        savingPathContainer.classList.remove('hidden');
        singleFileContainer.classList.add('hidden');
        grouperContainer.classList.add('hidden');

    } else if (selected === MediaTypes.GROUPER) {
        currentMediaType = MediaTypes.GROUPER;
        grouperContainer.classList.remove('hidden');
        singleFileContainer.classList.add('hidden');
        folderContainer.classList.add('hidden');
        savingPathContainer.classList.add('hidden');
    }
})

singleFileInput.addEventListener('change', () => {
    clearUploadedFile();
    if (!singleFileInput.files.length) {
        updateSavingPath('');
        return;
    }
    const file = singleFileInput.files[0];
    currentFiles = file;
    folderInput.value = '';

    updateSavingPath(file.name);
    updateTitle(file.name);
    currentMediaType = MediaTypes.VIDEO;
});

const ALLOWED = ["video/", "image/png", "image/jpeg", "image/gif"];

folderInput.addEventListener('change', () => {
    clearUploadedFile();
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
    singleFileInput.value = '';

    const relativePath = validFiles[0].webkitRelativePath;
    const folderPath = relativePath.substring(0, relativePath.lastIndexOf('/'));

    updateSavingPath(folderPath);
    updateTitle(folderPath.substring(folderPath.lastIndexOf('/') + 1));
    currentMediaType = MediaTypes.ALBUM;
});

grouperInput.addEventListener('change', () => {
    clearUploadedFile();
    if (!grouperInput.files.length) {
        return;
    }
    currentFiles = grouperInput.files[0];
    updateTitle(grouperInput.files[0].name);
    currentMediaType = MediaTypes.GROUPER;
})

function updateSavingPath(path) {
    savingPathInput.value = path;
}

function updateTitle(title) {
    titleInput.value = title.replace(/[_-]+/g, ' ').replace(/\s+/g, ' ').trim();
}

function clearUploadedFile() {
    uploadingFiles.clear();
    currentSessionId = null;
}

const errorMessageContainer = document.getElementById('error-message-container');
function displayFailTexts() {
    errorMessageContainer.innerHTML = '';
    currentFailTexts.forEach(t => {
        const span = document.createElement('span');
        span.textContent = t;
        errorMessageContainer.appendChild(span);
    });
    currentFailTexts = [];
    errorMessageContainer.classList.remove('hidden');
}

submitBtn.addEventListener('click',async () => {
    submitBtn.textContent = 'Submit';
    const submitFileUpload = async () => {
        console.log('submitting');
        if (currentMediaType === null) {
            alert('No file selected');
            return;
        }
        if (currentFiles === null) {
            alert('No file selected');
            return;
        }
        const title = titleInput.value.trim();
        if (title.length === 0) {
            alert('No title entered');
            return;
        }
        const year = yearInput.value;
        if (!year || year.length === 0) {
            alert('No year entered');
            return;
        }

        if (currentMediaType === MediaTypes.GROUPER) {
            const formData = new FormData();
            formData.append('thumbnail', currentFiles);
            formData.append('title', title);
            formData.append('year', year);

            const response = await fetch('/api/upload/media/create-grouper', {
                method: 'POST',
                body: formData
            });
            if (!response.ok) {
                alert('Failed to create grouper: ' + await response.text());
                return;
            }
            alert('Grouper created successfully! ' + await response.text());
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

        if (currentSavingPath !== savingPath) {
            // if the current saving path is different when resubmitting, start a new session
            currentSessionId = null;
        }
        currentSavingPath = savingPath;

        let sessionId = currentSessionId;
        if (!sessionId) {
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
                alert('Failed to start upload: ' + await sessionResponse.text());
                return;
            }
            sessionId = await sessionResponse.text();
        }
        console.log('sessionId: ' + sessionId);

        let fileNotUploaded = 0;
        if (currentMediaType === MediaTypes.VIDEO) {
            if (uploadingFiles.has(currentFiles)) {
                uploadingFiles.get(currentFiles).chunks.partNumber = uploadingFiles.get(currentFiles).partNumber;
                const passed = await uploadFile(sessionId, currentFiles, savingPath, MediaTypes.VIDEO, uploadingFiles.get(currentFiles).chunks, uploadingFiles.get(currentFiles).eTags);
                if (!passed) fileNotUploaded++;
            } else {
                const passed = await uploadFile(sessionId, currentFiles, savingPath, MediaTypes.VIDEO);
                if (!passed) fileNotUploaded++;
            }
        } else if (currentMediaType === MediaTypes.ALBUM) {
            if (uploadingFiles.size > 0) {
                for (const f of uploadingFiles.keys()) {
                    uploadingFiles.get(f).chunks.partNumber = uploadingFiles.get(f).partNumber;
                    const passed = await uploadFile(sessionId, f, savingPath + '/' + f.name, MediaTypes.ALBUM, uploadingFiles.get(f).chunks, uploadingFiles.get(f).eTags);
                    if (!passed) fileNotUploaded++;
                }
            } else {
                for (const f of currentFiles) {
                    const passed = await uploadFile(sessionId, f, savingPath + '/' + f.name, MediaTypes.ALBUM);
                    if (!passed) fileNotUploaded++;
                }
            }
        }

        if (fileNotUploaded) {
            submitBtn.textContent = 'Resubmit';
            console.log(uploadingFiles);
            displayFailTexts();
            return;
        }

        const endResponse = await fetch('/api/upload/media/end-session', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                sessionId: sessionId,
                basicInfo: {
                    title: title,
                    year: year
                }
            })
        });
        if (!endResponse.ok) {
            alert('Failed to finalize upload: ' + await endResponse.text());
            return;
        }
        singleFileInput.value = '';
        folderInput.value = '';
        uploadingFiles.clear();
        updateTitle('');
        updateSavingPath('');
        alert('Upload successful! ' + await endResponse.text());
    }

    submitBtn.disabled = true;
    submitFileUpload().then(() => {
        submitBtn.disabled = false
    });
});

async function uploadFile(sessionId, file, fileName, mediaType, chunks = null, eTags = null) {

    console.log('fileName: ' + fileName);

    chunks = chunks == null ? chunkFile(file) : chunks;
    eTags = eTags == null ? [] : eTags;

    console.log('chunks:');
    console.log(chunks);

    uploadingFiles.set(file, { chunks: chunks, eTags: eTags, partNumber: chunks[0].partNumber });

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
        currentFailTexts.push('Init: ' + await response.text());
        return false;
    }

    const uploadId = await response.text();
    console.log('uploadId: ' + uploadId);

    for (const c of chunks) {
        const urlResponse = await fetch('/api/upload/media/presign-part-url', {
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
            currentFailTexts.push('Presign: ' + await urlResponse.text());
            return false;
        }
        const urlRes = await urlResponse.text();
        console.log('Presigned url: ' + urlRes);

        const res = await fetch(urlRes, {
            method: 'PUT',
            body: c.blob
        });
        console.log('blob upload result: ' + res);

        eTags.push({
            partNumber: c.partNumber,
            etag: res.headers.get('ETag').replace(/"/g, '')
        });

        uploadingFiles.get(file).partNumber++;
    }

    const completeResponse = await fetch('/api/upload/media/complete', {
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
        currentFailTexts.push('Complete: ' + await completeResponse.text());
        return false;
    }

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


