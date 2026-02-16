import {
    uploadFile,
    startUploadSession,
    validateDirectory, endUploadSession,
} from "/static/js/upload/upload-file.js";
import {apiRequest} from "/static/js/common.js";

const view = document.getElementById("file-view-container");

const gridBtn = document.getElementById("grid-view-btn");
const listBtn = document.getElementById("list-view-btn");

gridBtn.addEventListener("click", function () {
    // Add grid-view
    view.classList.add("grid-view");
    view.classList.remove("list-view");

    gridBtn.classList.add('hidden');
    listBtn.classList.remove('hidden');
});

listBtn.addEventListener("click", function () {
    // Add list-view
    view.classList.add("list-view");
    view.classList.remove("grid-view");

    gridBtn.classList.remove('hidden');
    listBtn.classList.add('hidden');
});

async function getRootDir() {
    const rootDir = await apiRequest('/api/file/root');
    if (!rootDir.ok) {
        alert('Failed to get root directory');
        return null;
    }
    return rootDir.json();
    // return [
    //     {
    //         id: 1,
    //         path: "media",
    //         name: "vid",
    //         fileType: "DIR",
    //     },
    //     {
    //         id: 2,
    //         path: "media",
    //         name: "file.txt",
    //         fileType: "FILE",
    //     }
    // ]
}

async function getFilesInDir(dirId) {
    const files = await apiRequest('/api/file/dir', {
        method: 'POST',
        body: dirId
    });
    if (!files.ok) {
        alert('Failed to get files in directory');
        return null;
    }
    return files.json();
    // return [
    //     {
    //         id: 3,
    //         path: "media/vid",
    //         name: "vid.mp4",
    //         fileType: "VIDEO",
    //     },
    //     {
    //         id: 4,
    //         path: "media/vid",
    //         name: "vid.jpg",
    //         fileType: "IMAGE",
    //     },
    //     {
    //         id: 5,
    //         path: "media/vid",
    //         name: "file.txt",
    //         fileType: "FILE",
    //     },
    //     {
    //         id: 6,
    //         path: "media/vid",
    //         name: "album1",
    //         fileType: "ALBUM",
    //     }
    // ]
}

const fileViewContainer = document.getElementById('file-view-container');
const fileNodeTem = fileViewContainer.querySelector('.file-node-wrapper');

const iconContainer = document.getElementById('icon-container');

function getIconNode(fileType) {
    let iconNode = null;
    if (fileType === 'DIR') iconNode = iconContainer.querySelector('.directory-icon');
    else if (fileType === 'IMAGE') iconNode =  iconContainer.querySelector('.photo-icon');
    else if (fileType === 'VIDEO') iconNode = iconContainer.querySelector('.video-icon');
    else if (fileType === 'ALBUM') iconNode = iconContainer.querySelector('.album-icon');
    else if (fileType === 'FILE') iconNode = iconContainer.querySelector('.file-icon');
    if (iconNode) return helperCloneAndUnHideNode(iconNode);
    return null;
}

let isProcessing = false;
let currentFileItems = null;

function displayFileItem(fileItems) {
    const first = fileViewContainer.firstElementChild;
    if (first) fileViewContainer.replaceChildren(first);
    currentFileItems = fileItems;

    if (fileItems.length === 0) {
        const emptyNode = document.createElement('div');
        emptyNode.classList.add('flex', 'w-full', 'h-full', 'justify-center', 'items-center', 'absolute', 'text-lg')
        emptyNode.innerText = 'No files found';
        fileViewContainer.appendChild(emptyNode);
        return;
    }

    fileItems.forEach(item => {
        const fileNode = helperCloneAndUnHideNode(fileNodeTem);
        const fileType = item.fileType;
        if (item.thumbnail) {
            const imgNode = document.createElement('img');
            imgNode.src = item.thumbnail;
            fileNode.querySelector('.icon').appendChild(imgNode);
        }
        else {
            const dirIconNode = getIconNode(fileType);
            fileNode.querySelector('.icon').appendChild(dirIconNode);
        }
        fileNode.querySelector('.name').innerText = item.name;
        fileNode.querySelector('.name').title = item.name;
        fileNode.dataset.id = item.id;
        if (item.mid)
            fileNode.dataset.mid = item.mid;
        if (fileType === 'DIR' || fileType === 'ALBUM') {
            fileNode.addEventListener('click', async function () {
                if (isProcessing) return;
                isProcessing = true;
                const subFiles = await getFilesInDir(item.id);
                isProcessing = false;
                if (!subFiles) return;
                displayFileItem(subFiles);
                addToCurrentPath(item.id, item.name, fileType);
            });
        }
        fileViewContainer.appendChild(fileNode);
    });
}

const currentPathStack = [];

const pathBar = document.getElementById('path-bar');
const pathNodeTem = pathBar.querySelector('.path-node');
const currentPathText = document.getElementById('current-path');
function addToCurrentPath(id, name, fileType, isRoot = false) {
    const pathNode = helperCloneAndUnHideNode(pathNodeTem);
    pathNode.innerText = name;
    pathBar.appendChild(pathNode);
    const span = document.createElement('span');
    span.innerText = '/';
    pathBar.appendChild(span);
    currentPathText.innerText = name;
    currentPathStack.push({id: id, name: name, fileType: fileType});
    const thisIndex = currentPathStack.length - 1;
    console.log(thisIndex, currentPathStack[thisIndex]);
    pathNode.addEventListener('click', async function () {
        if (isProcessing) return;
        isProcessing = true;
        for (let i = thisIndex + 1; i < currentPathStack.length; i++) {
            removeLastPathStack();
        }
        currentPathText.innerText = currentPathStack[thisIndex].name;
        const subFiles = isRoot ? (await getRootDir()).children : await getFilesInDir(currentPathStack[thisIndex].id);
        isProcessing = false;
        if (!subFiles) return;
        displayFileItem(subFiles);
        console.log(thisIndex, currentPathStack[thisIndex]);
    });
}

function getDirPath(filePath, fileName) {
    const firstSlashIndex = filePath.indexOf('/');
    if (firstSlashIndex === -1) return fileName;
    const rootOmitted = filePath.substring(firstSlashIndex + 1);
    if (rootOmitted.lastIndexOf('/') === -1)
        return rootOmitted + '/' + fileName;
    if (fileName.length === 0)
        return rootOmitted.substring(0, rootOmitted.length - 1);
    return rootOmitted + fileName;
}

function removeLastPathStack() {
    currentPathStack.pop();
    pathBar.removeChild(pathBar.lastElementChild);
    pathBar.removeChild(pathBar.lastElementChild);
}

const pathBackBtn = document.getElementById('path-back-btn');
pathBackBtn.addEventListener('click', async function () {
    if (isProcessing) return;
    if (currentPathStack.length <= 1) return;
    isProcessing = true;
    removeLastPathStack();
    const lastPath = getCurrentPath();
    currentPathText.innerText = lastPath.name;
    const subFiles = currentPathStack.length === 1
        ? (await getRootDir()).children
        : await getFilesInDir(lastPath.id);
    console.log(subFiles);
    isProcessing = false;
    if (!subFiles) return;
    displayFileItem(subFiles);
});

async function initialize() {
    const rootInfo = await getRootDir();
    if (!rootInfo) return;
    addToCurrentPath(rootInfo.rootId, rootInfo.rootName, 'DIR', true);
    displayFileItem(rootInfo.children);
}

window.addEventListener('DOMContentLoaded', async function () {
    await initialize();
})


const uploadToggleButton = document.getElementById('upload-toggle-btn');
const uploadContainer = document.getElementById('upload-container');
uploadToggleButton.addEventListener('click', function () {
    uploadContainer.classList.toggle('hidden');
});

const fileDropZone = document.getElementById('file-zone');
const fileInput = document.getElementById('file-input');
const folderInput = document.getElementById('folder-input');
const fileList = [];

fileInput.addEventListener('change', async (e) => {
    await handleFiles(e.target.files);
});

async function handleFiles(files) {
    clearUploadingList();
    for (let i = 0; i < files.length; i++) {
        if (!validateAllowFile(files[i]))
            continue;
        const file = files[i];
        fileList.push({
            name: file.name,
            file: file
        });
    }
    sortFileAndDisplayFileName(fileList);
}

folderInput.addEventListener('change', async (e) => {
    await handleFolderFiles(e.target.files);
});

async function handleFolderFiles(files) {
    clearUploadingList();
    for (let i = 0; i < files.length; i++) {
        if (!validateAllowFile(files[i]))
            continue;
        const file = files[i];
        fileList.push({
            name: file.webkitRelativePath,
            file: file
        });
    }
    sortFileAndDisplayFileName(fileList);
}

// prevent default drag behavior
["dragenter", "dragover", "dragleave", "drop"].forEach(event => {
    fileDropZone.addEventListener(event, (e) => {
        e.preventDefault();
        e.stopPropagation();
    });
});

// highlight drop zone when dragging over it
["dragenter", "dragover"].forEach(event => {
    fileDropZone.addEventListener(event, () => {
        fileDropZone.classList.add("bg-blue-50", "border", "border-blue-600");
    });
});

["dragleave", "drop"].forEach(event => {
    fileDropZone.addEventListener(event, () => {
        fileDropZone.classList.remove("bg-blue-50", "border", "border-blue-600");
    });
});

fileDropZone.addEventListener("drop", async (e) => {
    const fileArray = [];

    const items = [...e.dataTransfer.items];
    const promise = items.map(item => {
        const entry = item.webkitGetAsEntry();
        return entry ? traverseEntry(entry, fileArray) : null;
    });
    await Promise.all(promise);
    await handleFileArray(fileArray);
});

async function handleFileArray(fileArray) {
    clearUploadingList();
    for (const file of fileArray) {
        if (!validateAllowFile(file.file))
            continue;
        fileList.push({
            name: file.path,
            file: file.file
        });
    }
    sortFileAndDisplayFileName(fileList);
}

async function traverseEntry(entry, filesArray, path = "") {
    if (entry.isFile) {
        await new Promise((resolve) => {
            entry.file(file => {
                filesArray.push({
                    path: path + file.name,
                    file: file
                });
                resolve();
            })
        });
    }

    if (entry.isDirectory) {
        const dirReader = entry.createReader();
        const entries = await new Promise((resolve) => {
            dirReader.readEntries(resolve);
        });
        const promises = entries.map(child =>
            traverseEntry(child, filesArray, path + entry.name + '/')
        );
        await Promise.all(promises);
    }
}

const sortSelection = document.getElementById('sort-file-upload-select');
sortSelection.addEventListener('change', function () {
    sortFileAndDisplayFileName(fileList);
})

function sortFileAndDisplayFileName(fileList) {
    if (fileList.length === 0) return;
    else if (fileList.length === 1) {
        addUploadingFile(fileList[0].name);
        return;
    }
    const sortSelectionValue = sortSelection.value;
    switch (sortSelectionValue) {
        case 'name-asc':
            fileList.sort((a, b) => a.name.localeCompare(b.name));
            break;
        case 'name-desc':
            fileList.sort((a, b) => b.name.localeCompare(a.name));
            break;
        case 'modified-asc':
            fileList.sort((a, b) => a.file.lastModified - b.file.lastModified);
            break;
        case 'modified-desc':
            fileList.sort((a, b) => b.file.lastModified - a.file.lastModified);
            break;
        default: fileList.sort((a, b) => a.name.localeCompare(b.name));
    }
    clearUploadingListNameText();
    fileList.forEach(file => {
        addUploadingFile(file.name);
    });
}

const uploadAsVideoCheckbox = document.getElementById('upload-video-checkbox');
let allVideo = true;
function validateAllowFile(file) {
    const currentPath = getCurrentPath();
    if (currentPath && currentPath.fileType === 'ALBUM') {
        uploadAsVideoCheckbox.disabled = true;
        allVideo = false;
    }
    if (validateAllowImage(file)) {
        uploadAsVideoCheckbox.disabled = true;
        allVideo = false;
        return true;
    } else if (validateAllowVideo(file)) {
        return true;
    }
    return null;
}

const ALLOWED_IMAGE = ["image/png", "image/jpg", "image/jpeg", "image/gif", "image/webp"];
function validateAllowImage(file) {
    return ALLOWED_IMAGE.some(a => file.type.startsWith(a)) ||
        (/\.(png|jpg|jpeg|gif|webp)$/i.test(file.name));
}

const ALLOWED_VIDEO = ["video/mp4", "video/mov", "video/mp3"];
function validateAllowVideo(file) {
    return ALLOWED_VIDEO.some(a => file.type.startsWith(a)) ||
        (/\.(mp4|mov|mp3)$/i.test(file.name));
}

function getCurrentPath() {
    if (currentPathStack.length === 0) return null;
    return currentPathStack[currentPathStack.length - 1];
}

function getFullCurrentPath() {
    let fullPath = '';
    currentPathStack.forEach(item => {
        fullPath += item.name + '/';
    });
    return fullPath.substring(0, fullPath.length - 1);
}

const uploadingList = document.getElementById('upload-list');
const listItemTem = uploadingList.querySelector('li');
function addUploadingFile(name) {
    name = name.replace(/\\/g, '/');
    const listItem = helperCloneAndUnHideNode(listItemTem);
    listItem.innerText = name;
    uploadingList.appendChild(listItem);
}

let savingPath = null;
function validateAllowUpload(fileList) {
    for (const file of fileList) {
        if (savingPath === null && file.name.indexOf('/') !== -1) {
            savingPath = file.name.substring(0, file.name.indexOf('/'));
        }
        totalProgress += file.file.size;
    }

    if (fileList.length === 0) {
        alert('No file selected');
        return false;
    }
    if (currentPathStack.length <= 0) {
        alert('No target folder selected');
        return false;
    }
    const currentPath = getCurrentPath();
    if (currentPath.fileType !== 'ALBUM' && !allVideo && !savingPath) {
        alert('Cannot upload non-video files to non-album folder - images should be saved in separated album folder first');
        return false;
    }
    return true;
}

let isSubmitting = false;
const uploadingFiles = new Map(); // keep track of failed files, if any for re-upload
const submitBtn = document.getElementById('submit-btn');
submitBtn.addEventListener('click', async function () {
    if (isSubmitting) return;
    isSubmitting = true;
    submitBtn.textContent = 'Submit';
    if (!validateAllowUpload(fileList)) return;
    await uploadFiles(fileList);
    isSubmitting = false;
});

async function uploadFiles(fileList) {
    const mediaType = allVideo ? 'VIDEO' : 'ALBUM';
    const currentFullPath = getFullCurrentPath();
    let sessionId = allVideo ? null : await startUploadSession(getDirPath(currentFullPath, savingPath), mediaType);

    if (sessionId === null && !allVideo) {
        alert('Failed to start upload session');
        return;
    }

    const endSession = async (sessionId, title) => {
        const basicInfo = {
            sessionId: sessionId,
            basicInfo: {
                title: title,
                year: new Date().getFullYear()
            }
        }
        const responseText = await endUploadSession(basicInfo);
        if (responseText.startsWith('Error:')) {
            currentFailTexts.push(responseText);
        }
    }

    if (uploadingFiles.size) {
        for (const f of uploadingFiles.key()) {
            const uploadingFile = uploadingFiles.get(f);
            const fileName = getDirPath(currentFullPath, f.name);
            uploadingFile.chunks.partNumber = uploadingFile.partNumber;
            const passed = await uploadFile(
                uploadingFile.sessionId, f, fileName, uploadingFile.mediaType, uploadingFiles, currentFailTexts,
                uploadingFile.chunks, uploadingFile.eTags, uploadingFile.uploadId,
                showProgress
            );
            if (!passed) {
                displayFailTexts(currentFailTexts);
            } else if (allVideo) {
                await endSession(uploadingFile.sessionId, f.name.substring(f.name.lastIndexOf('/') + 1));
            } else {
                sessionId = uploadingFile.sessionId;
            }
        }
    } else {
        for (const file of fileList) {
            const fileName = getDirPath(currentFullPath, file.name);
            const sId = allVideo ? await startUploadSession(fileName, mediaType) : sessionId;
            const passed = await uploadFile(
                sId, file.file, fileName, mediaType, uploadingFiles, currentFailTexts,
                null, null, null,
                showProgress
            );
            if (!passed) {
                uploadingFiles.get(file.file).sessionId = sId;
                displayFailTexts(currentFailTexts);
            } else if (allVideo) {
                await endSession(sId, file.name.substring(file.name.lastIndexOf('/') + 1));
            }
        }
    }

    if (uploadingFiles.size) {
        submitBtn.textContent = 'Retry';
        return;
    }

    if (!allVideo) {
        const response = await apiRequest('/api/upload/media/end-session-unfinished', {
            method: 'POST',
            body: sessionId
        });
        if (!response.ok) {
            displayFailTexts(['Failed to end upload session']);
            return;
        }
    }

    alert('Upload completed');
    clearUploadingList();
}

const currentFailTexts = [];
const errorMessageContainer = document.getElementById('error-message-container');
function displayFailTexts(failTexts) {
    failTexts.forEach(t => {
        const span = document.createElement('span');
        span.textContent = t;
        errorMessageContainer.appendChild(span);
    });
    failTexts.length = 0;
    errorMessageContainer.classList.remove('hidden');
}

const progressContainer = document.getElementById("upload-progress");
const progressFill = document.getElementById("progress-bar-fill");
const progressPercent = document.getElementById("progress-percent");
let progress = 0;
let totalProgress = 0;

function showProgress(value) {
    if (totalProgress === 0) {
        console.log('No progress to show');
        return;
    }
    progressContainer.classList.remove("hidden");

    progress += value;
    const percent = (progress / totalProgress) * 100;
    console.log(`Progress: ${percent.toFixed(2)}%`);

    requestAnimationFrame(() => {
        progressFill.style.width = `${percent}%`;
        progressPercent.textContent = `${percent.toFixed(1)}%`;
    });
}

function clearProgress() {
    progress = 0;
    progressFill.style.width = `${progress}%`;
    progressPercent.textContent = `${progress.toFixed(1)}%`;
}

function clearUploadingListNameText() {
    const first = uploadingList.firstElementChild;
    if (first) uploadingList.replaceChildren(first);
}

function clearUploadingList() {
    clearUploadingListNameText();
    savingPath = null;
    fileList.length = 0;
    uploadAsVideoCheckbox.disabled = false;
    allVideo = true;
    uploadingFiles.clear();
    clearProgress();
    totalProgress = 0;
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('!hidden');
    return clone;
}


const customRightMenu = document.getElementById('custom-right-menu');
const newFolderButton = customRightMenu.querySelector('.new-folder-btn');
const addAsVideoButton = customRightMenu.querySelector('.add-as-video-btn');
const addAsAlbumButton = customRightMenu.querySelector('.add-as-album-btn');
const deleteFileButton = customRightMenu.querySelector('.delete-file-btn');

let currentRightMenuTargetId = null;
let currentRightMenuTargetMId = null;

fileViewContainer.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    const targetNode = event.target.closest('.file-node-wrapper');

    if (!targetNode) {
        currentRightMenuTargetId = null;
        currentRightMenuTargetMId = null;
        addAsVideoButton.disabled = true;
        addAsAlbumButton.disabled = true;
        addAsVideoButton.classList.add('invisible');
        addAsAlbumButton.classList.add('invisible');
        showCustomRightMenu(event.clientX, event.clientY);
        return;
    }

    addAsVideoButton.disabled = false;
    addAsAlbumButton.disabled = false;
    addAsVideoButton.classList.remove('invisible');
    addAsAlbumButton.classList.remove('invisible');
    showCustomRightMenu(event.clientX, event.clientY);

    currentRightMenuTargetId = targetNode.getAttribute('data-id');
    currentRightMenuTargetMId = targetNode.getAttribute('data-mid');
});

function showCustomRightMenu(posX, posY) {
    customRightMenu.style.display = 'block';

    const menuWidth = customRightMenu.offsetWidth;
    const menuHeight = customRightMenu.offsetHeight;
    const windowWidth = window.innerWidth;
    const windowHeight = window.innerHeight;
    // Horizontal Check
    if (posX + menuWidth > windowWidth) {
        posX = posX - menuWidth;
    }
    // Vertical Check
    if (posY + menuHeight > windowHeight) {
        posY = posY - menuHeight;
    }
    customRightMenu.style.left = `${posX}px`;
    customRightMenu.style.top = `${posY}px`;
}

addAsVideoButton.addEventListener('click', async function () {
    if (currentRightMenuTargetId === null) {
        console.log('No target selected');
        return;
    }
    const response = await apiRequest(`/api/file/vid/${currentRightMenuTargetId}`, {
        method: 'POST'
    });
    if (!response.ok) {
        alert('Failed to add as video: ' + await response.text());
        return;
    }
    displayInfoMessage(await response.text());
});

addAsAlbumButton.addEventListener('click', async function () {
    if (currentRightMenuTargetId === null) {
        console.log('No target selected');
        return;
    }
    const response = await apiRequest(`/api/file/album/${currentRightMenuTargetId}`, {
        method: 'POST'
    });
    if (!response.ok) {
        alert('Failed to add as album: ' + await response.text());
        return
    }
    displayInfoMessage(await response.text());
});

deleteFileButton.addEventListener('click', async function () {
    if (currentRightMenuTargetId === null && currentRightMenuTargetMId === null) {
        console.log('No target selected');
        return;
    }
    const confirmDelete = confirm('Are you sure to delete this file?');
    if (!confirmDelete) return;
    if (currentRightMenuTargetMId) {
        if (Number.parseInt(currentRightMenuTargetMId) < 0) {
            displayInfoMessage("Item is still processing: " + currentRightMenuTargetId, false);
            return;
        }
        const response = await apiRequest(`/api/modify/media/${currentRightMenuTargetMId}`, {
            method: 'DELETE'
        });
        if (!response.ok) {
            alert('Failed to delete media: ' + await response.text());
            return
        }
        displayInfoMessage("Processing to delete media");
        return;
    }
    const response = await apiRequest(`/api/file/${currentRightMenuTargetId}`, {
        method: 'DELETE'
    });
    if (!response.ok) {
        alert('Failed to delete file: ' + await response.text());
        return
    }
    displayInfoMessage("Processing to delete file");
})

document.addEventListener('click', () => {
    customRightMenu.style.display = 'none';
    currentRightMenuTargetId = null;
});

const infoMessageContainer = document.getElementById('info-message-container');
function displayInfoMessage(message, hasTimeout = true) {
    console.log(message);
    infoMessageContainer.querySelector('.info-message').textContent = message;
    infoMessageContainer.classList.remove('hidden');
    if (hasTimeout)
        setTimeout(() => infoMessageContainer.classList.add('hidden'), 5000);
}