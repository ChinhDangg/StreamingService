import {
    uploadFile,
    startUploadSession,
    validateDirectory,
    initCsrfToken,
    getCsrfToken
} from "/static/js/upload/upload-file.js";
import {addNewAlbumItem} from "/static/js/album-grouper/album-grouper-page.js";

let grouperId = null;

function initialize() {
    const urlParams = new URLSearchParams(window.location.search);
    grouperId = urlParams.get('grouperId');
    if (!grouperId) {
        alert('No grouper id found');
        window.location.href = '/';
    }
    initCsrfToken();
}

window.addEventListener('DOMContentLoaded', initialize);

const editAlbumGrouperBtn = document.getElementById('edit-album-grouper-btn');
const grouperUploadContainer = document.getElementById('grouper-upload-container');

editAlbumGrouperBtn.addEventListener('click', () => {
    grouperUploadContainer.classList.toggle('hidden');
});

const folderInput = grouperUploadContainer.querySelector('#folder-input');
const savingPathInput = grouperUploadContainer.querySelector('#saving-path-input');
const fileListContainer = grouperUploadContainer.querySelector('#file-list-container');
const submitBtn = grouperUploadContainer.querySelector('#submit-btn');

const parentPathMap = new Map();
const uploadingAlbumFiles = new Map();

let currentFailTexts = [];
const errorMessageContainer = document.getElementById('error-message-container');

const ALLOWED = ["video/", "image/png", "image/jpeg", "image/gif", "image/webp"];

folderInput.addEventListener('change', () => {
    const first = fileListContainer.firstElementChild;
    if (first) fileListContainer.replaceChildren(first);
    parentPathMap.clear();
    uploadingAlbumFiles.clear();
    currentFailTexts = [];
    errorMessageContainer.innerHTML = '';
    errorMessageContainer.classList.add('hidden');

    if (!folderInput.files.length) return;
    for (const f of folderInput.files) {
        if (ALLOWED.some(a => f.type.startsWith(a))) {
            const parentPath = f.webkitRelativePath.substring(0, f.webkitRelativePath.lastIndexOf('/'));
            if (parentPathMap.has(parentPath)) {
                parentPathMap.get(parentPath).fileList.push(f);
            } else {
                const fileList = [];
                fileList.push(f);
                parentPathMap.set(parentPath, { fileList: fileList });
            }
        }
    }
    const fileListItemTem = fileListContainer.querySelector('.file-item');

    const dirs = Array.from(parentPathMap.keys()).sort();

    dirs.forEach(dir => {
        const fileListItem = helperCloneAndUnHideNode(fileListItemTem);
        fileListItem.querySelector('.file-path').textContent = dir;
        fileListItem.querySelector('.remove-btn').addEventListener('click', () => {
            parentPathMap.delete(dir);
            uploadingAlbumFiles.delete(dir);
            fileListItem.remove();
        });
        parentPathMap.get(dir).fileListItem = fileListItem;
        fileListContainer.appendChild(fileListItem);
    });
});

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

async function uploadAlbum(files, savingPath, parentPath, sessionId = null, uploadingFiles = new Map()) {
    const objectName = savingPath + '/' + parentPath;
    if (!sessionId) {
        sessionId = await startUploadSession(objectName, 'ALBUM');
    }
    if (!sessionId)
        return null;

    if (uploadingFiles.size) {
        for (const f of uploadingFiles.keys()) {
            const fileName = objectName + '/' + f.name;
            uploadingFiles.get(f).chunks.partNumber = uploadingFiles.get(f).partNumber;
            await uploadFile(sessionId, f, fileName, 'ALBUM', uploadingFiles, currentFailTexts,
                uploadingFiles.get(f).chunks, uploadingFiles.get(f).eTags);
        }
    } else {
        for (const f of files) {
            const fileName = objectName + '/' + f.name;
            await uploadFile(sessionId, f, fileName, 'ALBUM', uploadingFiles, currentFailTexts);
        }
    }
    if (uploadingFiles.size) {
        uploadingAlbumFiles.set(parentPath, { sessionId: sessionId, uploadingFiles: uploadingFiles });
        return null;
    } else {
        uploadingAlbumFiles.delete(parentPath);
    }
    const basicInfo = {
        sessionId: sessionId,
        grouperMediaId: grouperId,
        title: parentPath.substring(parentPath.lastIndexOf('/') + 1)
    };
    const response = await fetch('/api/upload/media/end-session-grouper', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': getCsrfToken()
        },
        body: JSON.stringify(basicInfo)
    });
    if (!response.ok) {
        alert('Failed to create album: ' + await response.text());
        return null;
    }
    return await response.text();
}

submitBtn.addEventListener('click', async () => {
    errorMessageContainer.innerHTML = '';
    if (!parentPathMap.size) {
        alert('No files selected');
        return;
    }
    if (!savingPathInput.value) {
        alert('Saving path is empty');
        return;
    }
    const savingPath = validateDirectory(savingPathInput.value);
    if (!savingPath) return;
    for (const [parentPath, object] of parentPathMap) {
        if (uploadingAlbumFiles.has(parentPath)) {
            const uploadingFiles = uploadingAlbumFiles.get(parentPath).uploadingFiles;
            const sessionId = uploadingAlbumFiles.get(parentPath).sessionId;
            await uploadAlbum(object.fileList, savingPath, parentPath, sessionId, uploadingFiles);
        } else {
            const passed = await uploadAlbum(object.fileList, savingPath, parentPath);
            if (passed) {
                parentPathMap.get(parentPath).fileListItem.remove();
                parentPathMap.delete(parentPath);
                addNewAlbumItem(passed);
            }
        }
    }
    if (currentFailTexts.length)
        displayFailTexts();
});

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}