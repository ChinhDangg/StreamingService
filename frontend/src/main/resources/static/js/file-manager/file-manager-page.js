import {
    uploadFile,
    startUploadSession,
    endUploadSession,
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
    const subFiles = await rootDir.json();
    if (subFiles.children.hasNext)
        nextPage = subFiles.children.pageable.pageNumber + 1;
    else
        nextPage = -1;
    return subFiles;
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
    const files = await apiRequest('/api/file/dir?id=' + dirId);
    if (!files.ok) {
        alert('Failed to get files in directory');
        return null;
    }
    const subFiles = await files.json();
    if (subFiles.hasNext)
        nextPage = subFiles.pageable.pageNumber + 1;
    else
        nextPage = -1;
    return subFiles;
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
const currentFileItems = [];

function displayFileItem(fileItems, clearNode = true, clearFileList = true) {
    if (clearNode) {
        const first = fileViewContainer.firstElementChild;
        if (first) fileViewContainer.replaceChildren(first);
        if (observer)
            observer.observe(sentinel);
    }
    if (clearFileList)
        currentFileItems.length = 0;

    fileViewWrapper.querySelector('.end-of-file-text').classList.add('hidden');

    if (fileItems.length === 0) {
        const emptyNode = document.createElement('div');
        emptyNode.classList.add('flex', 'w-full', 'h-full', 'justify-center', 'items-center', 'absolute', 'text-lg')
        emptyNode.innerText = 'No files found';
        fileViewContainer.appendChild(emptyNode);
        return;
    }

    fileItems.forEach(item => {
        if (clearFileList)
            currentFileItems.push(item);

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
        if (item.mId) {
            fileNode.dataset.mid = item.mId;
            fileNode.style.backgroundColor = '#4f46e5';
        }
        if (item.statusCode) {
            fileNode.dataset.status = item.statusCode;
        }
        if (fileType === 'DIR' || fileType === 'ALBUM') {
            fileNode.addEventListener('click', async function () {
                if (isProcessing) return;
                isProcessing = true;
                const subFiles = (await getFilesInDir(item.id)).content;
                isProcessing = false;
                if (!subFiles) return;
                displayFileItem(subFiles);
                addToCurrentPath(item.id, item.name, fileType);
            });
        }
        fileViewContainer.appendChild(fileNode);
    });

    if (nextPage === -1) {
        fileViewWrapper.querySelector('.end-of-file-text').classList.remove('hidden');
    }
}

const fileViewWrapper = document.getElementById('file-view-wrapper');
const sortSelect = document.getElementById('file-sort-by-select');
const sentinel = document.createElement("div");
let observer;
let nextPage = -1;
async function fetchMoreFiles(subId, page = 0) {
    if (isProcessing) return false;
    isProcessing = true;
    const params = new URLSearchParams();
    if (subId) params.append('id', subId);
    params.append('p', page);
    params.append('by', sortSelect.value.substring(0, sortSelect.value.indexOf('-')));
    params.append('order', sortSelect.value.includes('DESC') ? 'DESC' : 'ASC');

    const url = subId ? '/api/file/dir' : '/api/file/root';
    const response = await apiRequest(url + '?' + params.toString());
    if (!response.ok) {
        alert('Failed to fetch more files');
        isProcessing = false;
        return null;
    }
    const subFiles = await response.json();
    if (subId) {
        if (subFiles.hasNext)
            nextPage = subFiles.pageable.pageNumber + 1;
        else
            nextPage = -1;
        isProcessing = false;
        return subFiles.content;
    }
    if (subFiles.children.hasNext)
        nextPage = subFiles.children.pageable.pageNumber + 1;
    else
        nextPage = -1;
    isProcessing = false;
    return subFiles.children.content;
}

sortSelect.addEventListener('change', async function () {
    if (nextPage === -1) {
        // reached the end - should have all files with all info to sort locally
        const value = sortSelect.value;
        let key; let order;
        if (value.includes('NAME'))
            key = 'name';
        else if (value.includes('SIZE'))
            key = 'size';
        else if (value.includes('LENGTH'))
            key = 'length';
        else if (value.includes('UPLOAD'))
            key = 'uploadDate';
        if (value.includes('DESC'))
            order = 'DESC';
        else
            order = 'ASC';
        currentFileItems.sort(dynamicSortByField(key, order));
        displayFileItem(currentFileItems, true, false);
        return;
    }
    const currentPathStack = getCurrentPath();
    if (!currentPathStack) {
        return;
    }
    const subId = currentPathStack.id;
    if (!subId) return;
    const subFiles = await fetchMoreFiles(subId);
    if (!subFiles) return
    displayFileItem(subFiles);
    if (observer)
        observer.observe(sentinel);
});

function dynamicSortByField(key, order = 'ASC') {
    return function innerSort(a, b) {
        if (!a.hasOwnProperty(key) || !b.hasOwnProperty(key)) {
            return 0;
        }
        const varA = (typeof a[key] === 'string') ? a[key].toUpperCase() : a[key];
        const varB = (typeof b[key] === 'string') ? b[key].toUpperCase() : b[key];
        let comparison = 0;
        if (varA > varB) {
            comparison = 1;
        } else if (varA < varB) {
            comparison = -1;
        }
        return (order === 'DESC') ? (comparison * -1) : comparison;
    }
}

function initializeObserveFileViewContainer() {
    fileViewWrapper.appendChild(sentinel);
    observer = new IntersectionObserver(async (entries) => {
        if (entries[0].isIntersecting) {
            console.log('Intersecting');
            const currentPathStack = getCurrentPath();
            if (currentPathStack == null) {
                observer.unobserve(sentinel);
                return;
            }
            const subId = currentPathStack.id;
            if (nextPage === -1) {
                observer.unobserve(sentinel);
                return;
            }
            const subFiles = await fetchMoreFiles(subId, nextPage);
            if (subFiles === false)
                return;
            if (subFiles == null) {
                observer.unobserve(sentinel);
                return;
            }
            displayFileItem(subFiles, false);
        }
    }, { rootMargin: '500px' });
    observer.observe(sentinel);
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
        const subFiles = isRoot
            ? (await getRootDir()).children.content
            : (await getFilesInDir(currentPathStack[thisIndex].id)).content;
        isProcessing = false;
        if (!subFiles) return;
        displayFileItem(subFiles);
        console.log(thisIndex, currentPathStack[thisIndex]);
    });
}

function getDirPath(filePath, fileName) {
    const firstSlashIndex = filePath.indexOf('/');
    const rootOmitted = filePath.substring(firstSlashIndex + 1);
    fileName = fileName == null ? '' : fileName;
    let path =  rootOmitted + (rootOmitted.endsWith('/') ? '' : '/') + fileName;
    if (path.startsWith('/')) path = path.substring(1);
    if (path.endsWith('/')) path = path.substring(0, path.length - 1);
    return path;
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
        ? (await getRootDir()).children.content
        : (await getFilesInDir(lastPath.id)).content;
    console.log(subFiles);
    isProcessing = false;
    if (!subFiles) return;
    displayFileItem(subFiles);
});

async function initialize() {
    const rootInfo = await getRootDir();
    if (!rootInfo) return;
    addToCurrentPath(rootInfo.rootId, rootInfo.rootName, 'DIR', true);
    displayFileItem(rootInfo.children.content);
}

window.addEventListener('DOMContentLoaded', async function () {
    await initialize();
    initializeAddNameEntity();
    initializeEditArea();
    initializeObserveFileViewContainer();
});


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
    return fullPath;
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
    return true;
}

let isSubmitting = false;
const uploadingFiles = new Map(); // keep track of failed files, if any for re-upload
const submitBtn = document.getElementById('submit-btn');
submitBtn.addEventListener('click', async function () {
    if (isSubmitting) return;
    isSubmitting = true;
    submitBtn.textContent = 'Submit';
    if (!validateAllowUpload(fileList)) {
        isSubmitting = false;
        return;
    }
    await uploadFiles(fileList);
    isSubmitting = false;
});

async function uploadFiles(fileList) {
    allVideo = allVideo && uploadAsVideoCheckbox.checked;
    const mediaType = allVideo ? 'VIDEO' : savingPath ? 'ALBUM' : 'OTHER';
    const currentFullPath = getFullCurrentPath();
    let sessionId = mediaType === 'ALBUM'
        ? await startUploadSession(getDirPath(currentFullPath, savingPath), mediaType)
        : null;

    if (sessionId === null && mediaType === 'ALBUM') {
        alert('Failed to start upload session');
        return;
    } else if (sessionId !== null && sessionId.startsWith('Error:') && !allVideo) {
        displayFailTexts([sessionId]);
        return;
    }

    const endMediaSession = async (sessionId, title) => {
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
            return null;
        }
        return responseText; // media id
    }

    const endFileSession = async (sessionId) => {
        const response = await apiRequest('/api/upload/media/end-session-file', {
            method: 'POST',
            body: sessionId
        });
        if (!response.ok) {
            displayFailTexts(['Failed to end upload session']);
            return null;
        }
        return response.text();
    }

    if (uploadingFiles.size) {
        for (const f of uploadingFiles.keys()) {
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
            } else if (mediaType !== 'ALBUM') {
                const mediaId = allVideo
                    ? await endMediaSession(uploadingFile.sessionId, f.name.substring(f.name.lastIndexOf('/') + 1))
                    : await endFileSession(uploadingFile.sessionId);
                const errorMess = await uploadNameEntityForMediaInBatch(mediaId);
                if (errorMess) {
                    displayFailTexts([errorMess]);
                    currentFailTexts.push(errorMess);
                }
            } else {
                sessionId = uploadingFile.sessionId;
            }
        }
    } else {
        for (const file of fileList) {
            const fileName = getDirPath(currentFullPath, file.name);
            const sId = sessionId === null ? await startUploadSession(fileName, mediaType) : sessionId;
            if (!sId) {
                displayFailTexts(['Failed to start upload session']);
                return;
            }
            const passed = await uploadFile(
                sId, file.file, fileName, mediaType, uploadingFiles, currentFailTexts,
                null, null, null,
                showProgress
            );
            if (!passed) {
                uploadingFiles.get(file.file).sessionId = sId;
                displayFailTexts(currentFailTexts);
            } else if (mediaType !== 'ALBUM') {
                const mediaId = allVideo
                    ? await endMediaSession(sId, file.name.substring(file.name.lastIndexOf('/') + 1))
                    : await endFileSession(sId);
                const errorMess = await uploadNameEntityForMediaInBatch(mediaId);
                if (errorMess) {
                    displayFailTexts([errorMess]);
                    currentFailTexts.push(errorMess);
                }
            }
        }
    }

    if (uploadingFiles.size || currentFailTexts.length > 0) {
        submitBtn.textContent = 'Retry';
        currentFailTexts.length = 0;
        return;
    }

    if (mediaType === 'ALBUM') {
        const response = endFileSession(sessionId);
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
    currentFailTexts.length = 0;
    errorMessageContainer.classList.add('hidden');
    clearNameEntityMap();
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('!hidden', 'hidden');
    return clone;
}


const editNameSection = document.getElementById('edit-name-section');
const editAreaContainer = editNameSection.querySelector('#editAreaContainer');
const addNameEntityContainer = document.getElementById('add-name-entity-container');
uploadAsVideoCheckbox.addEventListener('change', function () {
    if (uploadAsVideoCheckbox.checked) {
        editNameSection.classList.remove('hidden');
    } else {
        editNameSection.classList.add('hidden');
    }
});

const NameEntities = Object.freeze({
    Universes: 'universes',
    Characters: 'characters',
    Authors: 'authors',
    Tags: 'tags'
});
let currentNameEntity = null;
let currentNameEntityNode = null;
const nameEntityEditMap = new Map();

function initializeAddNameEntity() {
    const nameEntityTem = addNameEntityContainer.querySelector('.name-entity-item');
    Object.entries(NameEntities).forEach(([key, value]) => {
        const nameEntity = helperCloneAndUnHideNode(nameEntityTem);
        nameEntity.querySelector('.name-text').textContent = key + ':';
        const nameEditButton = nameEntity.querySelector('button');
        nameEditButton.id = 'edit-' + key + '-btn';
        nameEditButton.addEventListener('click', function () {
            currentNameEntity = key;
            currentNameEntityNode = nameEntity;
            loadCurrentNameEntityToEditArea();
            editAreaContainer.querySelector('.current-edit-name-title').textContent = value;
            editAreaContainer.classList.remove('hidden');
        });
        nameEntityEditMap.set(key, new Map());
        addNameEntityContainer.appendChild(nameEntity);
    });
}

function loadCurrentNameEntityToEditArea() {
    clearNameEntityDisplayNode();
    nameEntityEditMap.get(currentNameEntity).forEach((value, key) => {
        addNameEntity(currentNameEntityNode, currentNameEntity, value, key);
    });
}

function initializeEditArea() {
    editNameSection.querySelector('.save-edit-name-btn').classList.add('hidden');
    editNameSection.querySelector('.close-edit-name-btn').addEventListener('click', function () {
        editAreaContainer.classList.add('hidden');
    });

    let searchTimeOut = null;
    const searchInput = editAreaContainer.querySelector('.adding-search-input');
    const searchEntryList = editAreaContainer.querySelector('.search-dropdown-ul');

    const searchEntryTem = searchEntryList.querySelector('li');
    const addSearchEntry = (nameEntity) => {
        const searchEntry = helperCloneAndUnHideNode(searchEntryTem);
        searchEntry.textContent = nameEntity.name;
        searchEntry.addEventListener('click', () => {
            if (currentNameEntity === null) return;
            if (nameEntityEditMap.get(currentNameEntity).has(nameEntity.id)) return;
            nameEntityEditMap.get(currentNameEntity).set(nameEntity.id, nameEntity.name);
            addNameEntity(currentNameEntityNode, currentNameEntity, nameEntity.name, nameEntity.id);
        });
        searchEntryList.appendChild(searchEntry);
        searchEntryList.classList.remove('hidden');
    }

    const searchName = async (nameString) => {
        const response = await apiRequest(`/api/search/name/${currentNameEntity}?s=${nameString}`);
        if (!response.ok) {
            alert('Failed to fetch name info: ' + await response.text());
            return;
        }
        const nameEntityInfo = await response.json();
        // const nameEntityInfo = [
        //     {id: 1, name: 'name1', thumbnail: 'thumbnail1'},
        //     {id: 2, name: 'name2', thumbnail: 'thumbnail1'},
        // ];
        const first = searchEntryList.firstElementChild;
        if (first) searchEntryList.replaceChildren(first);
        if (nameEntityInfo.length === 0) {
            const searchEntry = helperCloneAndUnHideNode(searchEntryTem);
            searchEntry.textContent = 'No matching name found.'
            searchEntryList.appendChild(searchEntry);
            searchEntryList.classList.remove('hidden');
            return;
        }
        nameEntityInfo.forEach(nameEntity => addSearchEntry(nameEntity));
    }

    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimeOut);
        const searchInputValue = searchInput.value.trim();
        if (searchInputValue.length < 2) {
            const first = searchEntryList.firstElementChild;
            if (first) searchEntryList.replaceChildren(first);
            return;
        }
        searchTimeOut = setTimeout(async () => {
            await searchName(searchInputValue)
        }, 500);
    });

    searchInput.addEventListener('blur', () => {
        setTimeout(() => {
            if (document.activeElement === searchEntryList)
                return
            const first = searchEntryList.firstElementChild;
            if (first) searchEntryList.replaceChildren(first);
            searchEntryList.classList.add('hidden');
        }, 100)
    });
    searchEntryList.addEventListener('blur', () => {
        setTimeout(() => {
            const first = searchEntryList.firstElementChild;
            if (first) searchEntryList.replaceChildren(first);
            searchEntryList.classList.add('hidden');
        }, 100);
    });
}

function addNameEntity(nameEntityNode, nameEntity, name, nameId) {
    const infoNodeTem = nameEntityNode.querySelector('.info-node');
    const infoNode = helperCloneAndUnHideNode(infoNodeTem);
    infoNode.textContent = name;
    nameEntityNode.querySelector('.info-container').appendChild(infoNode);

    const tempEditNodeLi = editAreaContainer.querySelector('.temp-edit-node-li');
    const tempEditNode = helperCloneAndUnHideNode(tempEditNodeLi);
    tempEditNode.querySelector('.text-name').textContent = name;
    tempEditNode.addEventListener('click', () => {
        infoNode.remove();
        tempEditNode.remove();
        nameEntityEditMap.get(nameEntity).delete(nameId);
    });
    editAreaContainer.querySelector('#currentArea').appendChild(tempEditNode);
}

async function uploadNameEntityForMediaInBatch(mediaId) {
    const body = [];
    nameEntityEditMap.forEach((value, key) => {
        const adding = [];
        value.forEach((name, id) => adding.push({name: name, id: id}));
        if (adding.length > 0)
            body.push({nameEntity: key.toUpperCase(), adding: adding});
    });
    if (body.length === 0) return null;
    const response = await apiRequest(`/api/modify/media/update-batch/${mediaId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
    });
    if (!response.ok) {
        return `Failed to add name entity: ${await response.text()}`;
    }
    return null;
}

function clearNameEntityMap() {
    clearNameEntityDisplayNode();
    nameEntityEditMap.forEach((value, key) => value.clear());
}

function clearNameEntityDisplayNode() {
    if (currentNameEntityNode === null) return;
    const infoContainer = currentNameEntityNode.querySelector('.info-container');
    const first = infoContainer.firstElementChild;
    if (first) infoContainer.replaceChildren(first);
    editAreaContainer.querySelector('#currentArea').textContent = '';
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

    if (!targetNode || targetNode.getAttribute('data-statusCode')) {
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