import {
    uploadFile,
    startUploadSession,
    endUploadSession,
    validateDirectory,
} from "/static/js/upload/upload-file.js";
import {apiRequest} from "/static/js/common.js";

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

const ALLOWED = ["video/", "image/png", "image/jpeg", "image/gif", "image/webp"];

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

            const response = await apiRequest('/api/upload/media/create-grouper', {
                method: 'POST',
                body: formData
            });
            if (!response.ok) {
                alert('Failed to create grouper: ' + await response.text());
                return;
            }
            alert('Grouper created successfully! ' + await response.text());
            grouperInput.value = '';
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
            // if the current saving path is different when resubmitting, start a new session with new uploading files
            currentSessionId = null;
            uploadingFiles.clear();
            currentFailTexts = [];
        }
        currentSavingPath = savingPath;

        let sessionId = currentSessionId;
        if (!sessionId) {
            sessionId = await startUploadSession(savingPath, currentMediaType);
            if (!sessionId) {
                return;
            }
            currentSessionId = sessionId;
        }
        console.log('sessionId: ' + sessionId);

        let fileNotUploaded = 0;
        if (currentMediaType === MediaTypes.VIDEO) {
            if (uploadingFiles.has(currentFiles)) {
                uploadingFiles.get(currentFiles).chunks.partNumber = uploadingFiles.get(currentFiles).partNumber;
                const passed = await uploadFile(sessionId, currentFiles, savingPath, MediaTypes.VIDEO, uploadingFiles, currentFailTexts,
                    uploadingFiles.get(currentFiles).chunks, uploadingFiles.get(currentFiles).eTags, uploadingFiles.get(currentFiles).uploadId);
                if (!passed) fileNotUploaded++;
            } else {
                const passed = await uploadFile(sessionId, currentFiles, savingPath, MediaTypes.VIDEO, uploadingFiles, currentFailTexts);
                if (!passed) fileNotUploaded++;
            }
        } else if (currentMediaType === MediaTypes.ALBUM) {
            if (uploadingFiles.size > 0) {
                for (const f of uploadingFiles.keys()) {
                    uploadingFiles.get(f).chunks.partNumber = uploadingFiles.get(f).partNumber;
                    const passed = await uploadFile(sessionId, f, savingPath + '/' + f.name, MediaTypes.ALBUM, uploadingFiles, currentFailTexts,
                        uploadingFiles.get(f).chunks, uploadingFiles.get(f).eTags, uploadingFiles.get(f).uploadId);
                    if (!passed) fileNotUploaded++;
                }
            } else {
                for (const f of currentFiles) {
                    const passed = await uploadFile(sessionId, f, savingPath + '/' + f.name, MediaTypes.ALBUM, uploadingFiles, currentFailTexts);
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

        const basicInfo = {
            sessionId: sessionId,
            basicInfo: {
                title: title,
                year: year
            }
        };
        endUploadSession(basicInfo).then(responseText => {
            if (responseText) {
                singleFileInput.value = '';
                folderInput.value = '';
                grouperInput.value = '';
                uploadingFiles.clear();
                currentFailTexts = [];
                updateTitle('');
                updateSavingPath('');
                errorMessageContainer.classList.add('hidden');
                alert('Upload successful! ' + responseText);
            }
        });
    }

    submitBtn.disabled = true;
    submitFileUpload().then(() => {
        submitBtn.disabled = false
    });
});

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

