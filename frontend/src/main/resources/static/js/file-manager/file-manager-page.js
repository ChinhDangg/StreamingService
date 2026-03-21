import {endFileSession, endVideoUploadSession, uploadFile,} from "/static/js/upload/upload-file.js";
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
    if (subFiles.hasNext)
        nextPage = subFiles.pageable.pageNumber + 1;
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

const fileViewContainer = document.getElementById('file-view-container');
const fileNodeTem = fileViewContainer.querySelector('.file-node-wrapper');

const iconContainer = document.getElementById('icon-container');

function getIconNode(fileType) {
    let iconNode = null;
    if (fileType === 'DIR') iconNode = iconContainer.querySelector('.directory-icon');
    else if (fileType === 'IMAGE') iconNode =  iconContainer.querySelector('.photo-icon');
    else if (fileType === 'VIDEO') iconNode = iconContainer.querySelector('.video-icon');
    else if (fileType === 'AUDIO') iconNode = iconContainer.querySelector('.audio-icon');
    else if (fileType === 'ALBUM') iconNode = iconContainer.querySelector('.album-icon');
    else if (fileType === 'FILE') iconNode = iconContainer.querySelector('.file-icon');
    if (iconNode) return helperCloneAndUnHideNode(iconNode);
    return null;
}

let isProcessing = false;
const currentFileItems = [];

function displayFileItem(fileItems, clearNode = true, clearFileList = true, pushFileList = true) {
    if (clearNode) {
        const first = fileViewContainer.firstElementChild;
        if (first) fileViewContainer.replaceChildren(first);
        if (observer)
            observer.observe(sentinel);
    }
    if (clearFileList)
        currentFileItems.length = 0;

    fileViewWrapper.querySelector('.end-of-file-text').classList.add('hidden');

    fileItems.forEach(item => {
        if (pushFileList)
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
        fileNode.dataset.type = fileType;
        if (item.mId) {
            fileNode.dataset.mid = item.mId;
            setMediaBgColor(fileNode, fileType);
        }
        if (fileType === 'DIR' || fileType === 'ALBUM' || fileType === 'GROUPER') {
            fileNode.addEventListener('click', async function () {
                if (isProcessing) return;
                setCurrentUri(item.id);
                const subFiles = await fetchMoreFiles(item.id);
                if (!subFiles) return;
                displayFileItem(subFiles);
                addToCurrentPath(item.id, item.name);
            });
        }
        const sortingInfo = fileNode.querySelector('.sorting-info');
        const sortInfo = getItemKeyFromSortSelectValue(getSortSelectValue().by);
        if (sortInfo && item[sortInfo] && sortInfo !== 'name') {
            if (sortInfo.startsWith('resolution')) {
                sortingInfo.innerText = item.resolution.width + 'x' + item.resolution.height;
            } else if (sortInfo === 'length') {
                sortingInfo.innerText = fileType === 'VIDEO' ? formatDuration(item.length) : item[sortInfo];
            } else if (sortInfo === 'size') {
                sortingInfo.innerText = formatSize(item.size);
            } else if (sortInfo === 'uploadDate') {
                sortingInfo.innerText = formatDate(item[sortInfo]);
            } else
                sortingInfo.innerText = item[sortInfo];
            sortingInfo.classList.remove('hidden');
        } else {
            sortingInfo.classList.add('hidden');
        }
        fileViewContainer.appendChild(fileNode);
    });

    if (nextPage === -1) {
        const endOfFileText = fileViewWrapper.querySelector('.end-of-file-text');
        if (fileItems.length === 0)
            endOfFileText.innerText = 'No files found';
        else
            endOfFileText.innerText = 'End of file list';
        endOfFileText.classList.remove('hidden');
    }
}

function setMediaBgColor(fileNode, type) {
    if (type === 'VIDEO') {
        fileNode.style.backgroundColor = '#2b7fff';
    } else if (type === 'AUDIO') {
        fileNode.style.backgroundColor = '#162456';
    } else if (type === 'ALBUM') {
        fileNode.style.backgroundColor = '#4f39f6';
    } else if (type === 'GROUPER') {
        fileNode.style.backgroundColor = '#ad46ff';
    }
}

function formatSize(bytes) {
    if (bytes >= 1073741824) { return (bytes / 1073741824).toFixed(2) + " GB"; }
    if (bytes >= 1048576)    { return (bytes / 1048576).toFixed(2) + " MB"; }
    if (bytes >= 1024)       { return (bytes / 1024).toFixed(2) + " KB"; }
    return bytes + " Bytes";
}

function formatDuration(totalSeconds) {
    if (totalSeconds < 60) {
        return totalSeconds + "s";
    }
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    if (seconds === 0)
        return minutes + "m";
    return minutes + "m " + seconds + "s";
}

function formatDate(isoString) {
    // isoString: "2026-03-18T19:47:37.151Z"
    // Positions:  012345678901234567890123

    const year = isoString.slice(0, 4);
    const month = isoString.slice(5, 7);
    const day = isoString.slice(8, 10);
    const hours = isoString.slice(11, 13);
    const minutes = isoString.slice(14, 16);

    return `${day}/${month}/${year} ${hours}:${minutes}`;
}

const fileViewWrapper = document.getElementById('file-view-wrapper');
const sortSelect = document.getElementById('file-sort-by-select');
const sentinel = document.createElement("div");
let observer;
let nextPage = -1;
async function fetchMoreFiles(subId, page = 0, getParentInfo = false) {
    if (isProcessing) return false;
    isProcessing = true;
    const params = new URLSearchParams();
    if (subId) params.append('id', subId);
    const sortSelectValue = getSortSelectValue();
    params.append('p', page.toString());
    params.append('by', sortSelectValue.by);
    params.append('order', sortSelectValue.order);
    if (getParentInfo) params.append('full', 'true');

    const url = subId ? '/api/file/dir' : '/api/file/root';
    const response = await apiRequest(url + '?' + params.toString());
    if (!response.ok) {
        alert('Failed to fetch more files');
        isProcessing = false;
        return null;
    }
    const subFiles = await response.json();
    if (subFiles.hasNext)
        nextPage = subFiles.pageable.pageNumber + 1;
    else
        nextPage = -1;
    isProcessing = false;
    if (getParentInfo)
        return subFiles;
    return subFiles.content;
}

function getSortSelectValue() {
    return {
        by: sortSelect.value.substring(0, sortSelect.value.indexOf('-')),
        order: sortSelect.value.includes('DESC') ? 'DESC' : 'ASC'
    }
}

sortSelect.addEventListener('change', async function () {
    const currentPathStack = getCurrentPath();
    const subId = currentPathStack.id;

    setCurrentUri(subId);

    if (nextPage === -1) {
        // reached the end - should have all files with all info to sort locally
        const sortSelectValue = getSortSelectValue();
        let key = getItemKeyFromSortSelectValue(sortSelectValue.by);
        if (key === 'resolution')
            key = 'resolution.area';
        const order = sortSelectValue.order;
        currentFileItems.sort(dynamicSortByField(key, order));
        displayFileItem(currentFileItems, true, false, false);
        return;
    }

    const subFiles = await fetchMoreFiles(subId);
    if (!subFiles) return
    displayFileItem(subFiles);
    if (observer)
        observer.observe(sentinel);
});

function getItemKeyFromSortSelectValue(value) {
    if (value === 'NAME')
        return 'name';
    else if (value === 'SIZE')
        return 'size';
    else if (value === 'LENGTH')
        return 'length';
    else if (value === 'RESOLUTION')
        return 'resolution';
    else if (value === 'UPLOAD')
        return 'uploadDate';
    return null;
}

function dynamicSortByField(key, order = 'ASC') {
    const getValue = (obj, path) => {
        // Splitting by dot and reducing through the object
        return path.split('.').reduce((acc, part) => acc?.[part], obj);
    };

    return function innerSort(a, b) {
        let varA = getValue(a, key);
        let varB = getValue(b, key);
        if (!varA || !varB) {
            return 0;
        }
        const valA = (typeof varA === 'string') ? varA.toUpperCase() : varA;
        const valB = (typeof varB === 'string') ? varB.toUpperCase() : varB;
        let comparison = 0;
        if (valA > valB) {
            comparison = 1;
        } else if (valA < valB) {
            comparison = -1;
        }
        return (order === 'DESC') ? (comparison * -1) : comparison;
    }
}

let previousSubId = null;
function setCurrentUri(subId) {
    const sortSelectValue = getSortSelectValue();
    const params = new URLSearchParams();
    if (subId) params.append('id', subId);
    params.append('by', sortSelectValue.by);
    params.append('order', sortSelectValue.order);

    if (params.toString().length === 0) return;

    const state = { pathId: subId ? subId : "root" };
    const path = window.location.pathname + '?' + params.toString();

    if (previousSubId !== subId) {
        previousSubId = subId;
        window.history.pushState(state, '', path);
    } else {
        window.history.replaceState(state, '', path);
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
function addToCurrentPath(id, name, isRoot = false) {
    const pathNode = helperCloneAndUnHideNode(pathNodeTem);
    pathNode.innerText = name;
    pathBar.appendChild(pathNode);
    const span = document.createElement('span');
    span.innerText = '/';
    pathBar.appendChild(span);
    currentPathText.innerText = name;
    currentPathStack.push({id: id, name: name});
    const thisIndex = currentPathStack.length - 1;
    pathNode.addEventListener('click', async function () {
        if (isProcessing) return;
        const end = currentPathStack.length;
        for (let i = thisIndex + 1; i < end; i++) {
            console.log('removing : ' + i);
            removeLastPathStack();
        }
        currentPathText.innerText = currentPathStack[thisIndex].name;
        setCurrentUri(isRoot ? null : currentPathStack[thisIndex].id);
        const subFiles = isRoot
            ? await fetchMoreFiles(null)
            : await fetchMoreFiles(currentPathStack[thisIndex].id);
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
    removeLastPathStack();
    const lastPath = getCurrentPath();
    currentPathText.innerText = lastPath.name;
    setCurrentUri(currentPathStack.length === 1 ? null : lastPath.id);
    const subFiles = currentPathStack.length === 1
        ? await fetchMoreFiles(null)
        : await fetchMoreFiles(lastPath.id);
    if (!subFiles) return;
    displayFileItem(subFiles);
});

async function initialize() {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    const subId = urlParams.get('id');
    const sortBy = urlParams.get('by');
    const sortOrder = urlParams.get('order');
    if (subId || sortBy || sortOrder) {
        const newValue = sortBy + '-' + sortOrder;
        const exists = Array.from(sortSelect.options).some(opt => opt.value === newValue);
        if (exists)
            sortSelect.value = newValue;
        else
            sortSelect.value = 'NAME-ASC';
        const subFiles = await fetchMoreFiles(subId, 0, true);
        if (subFiles) {
            if (subFiles.parentId && subFiles.parentName) {
                const parentIds = subFiles.parentId.split('/').filter(Boolean);
                const parentNames = subFiles.parentName.split('/').filter(Boolean);
                for (let i = 0; i < parentIds.length; i++) {
                    addToCurrentPath(parentIds[i], parentNames[i]);
                }
                displayFileItem(subFiles.content);
                return;
            }
        }
    }

    homeButton.click();
}

window.addEventListener('DOMContentLoaded', async function () {
    await initialize();
    initializeAddNameEntity();
    initializeEditArea();
    initializeObserveFileViewContainer();
});

const homeButton = document.getElementById('home-btn');
homeButton.addEventListener('click', async function () {
    if (isProcessing) return;
    const end = currentPathStack.length;
    for (let i = 0; i < end; i++) {
        removeLastPathStack();
    }

    setCurrentUri(null);

    const rootInfo = await getRootDir();
    if (!rootInfo) return;
    addToCurrentPath(rootInfo.parentId, rootInfo.parentName, true);
    displayFileItem(rootInfo.content);
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
    sortUploadFileAndDisplayFileName(fileList);
    fileInput.value = '';
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
    sortUploadFileAndDisplayFileName(fileList);
    folderInput.value = '';
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
    uploadContainer.classList.remove('hidden');

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
    sortUploadFileAndDisplayFileName(fileList);
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

const sortUploadSelection = document.getElementById('sort-file-upload-select');
sortUploadSelection.addEventListener('change', function () {
    sortUploadFileAndDisplayFileName(fileList);
})

function sortUploadFileAndDisplayFileName(fileList) {
    if (fileList.length === 0) return;
    else if (fileList.length === 1) {
        addUploadingFile(fileList[0].name);
        return;
    }
    const sortSelectionValue = sortUploadSelection.value;
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
uploadAsVideoCheckbox.addEventListener('change', function () {
    if (uploadAsVideoCheckbox.checked)
        editNameSection.classList.remove('hidden');
    else
        editNameSection.classList.add('hidden');
});

let allVideo = true;
function validateAllowFile(file) {
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

function getFullCurrentPathInIds() {
    let fullPath = '';
    currentPathStack.forEach(item => {
        fullPath += item.id + '/';
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

function validateAllowUpload(fileList) {
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
    if (uploadingFiles.size === 0) { // no reuploading file - recalculate total progress
        for (const file of fileList) {
            totalProgress += file.file.size;
        }
    }
    clearFailTexts();
    await uploadFiles(fileList);
    isSubmitting = false;
});

async function uploadFiles(fileList) {
    allVideo = allVideo && uploadAsVideoCheckbox.checked;
    const currentFullPath = getFullCurrentPath();

    const mediaNameEntities = getNameEntityForMediaUpload();

    const endVideoMediaSession = async (uploadId, uploadedParts, filename, isLast) => {
        let base = filename.substring(filename.lastIndexOf('/') + 1);
        const basicInfo = {
            title: base.substring(0, base.lastIndexOf('.') >>> 0 || base.length),
            year: new Date().getFullYear()
        }
        return await endVideoUploadSession(uploadId, uploadedParts, basicInfo, mediaNameEntities, isLast); // media id or error message
    }

    const endSession = async (fileInfo, filename, isLast = false) => {
        if (allVideo) {
            const mess = await endVideoMediaSession(fileInfo.uploadId, fileInfo.eTags, filename, isLast);
            if (mess.startsWith('Error:')) {
                displayFailTexts([mess]);
                return null;
            }
        } else {
            const mess = await endFileSession(fileInfo.uploadId, fileInfo.eTags, isLast);
            if (mess.startsWith('Error:')) {
                displayFailTexts([mess]);
                return null;
            }
        }
        return true;
    }

    if (uploadingFiles.size) {
        let i = -1;
        const total = uploadingFiles.size;
        for (const f of uploadingFiles.keys()) {
            i++;
            const uploadingFile = uploadingFiles.get(f);
            uploadingFile.chunks.partNumber = uploadingFile.partNumber;
            const passed = await uploadFile(
                uploadingFile.sessionId, uploadingFile.file, uploadingFile.fileName, uploadingFiles, currentFailTexts,
                uploadingFile.chunks, uploadingFile.eTags, uploadingFile.uploadId,
                showProgress
            );
            if (!passed) {
                displayFailTexts(currentFailTexts);
            } else {
                const noError = await endSession(uploadingFiles.get(f), uploadingFiles.get(f).fileName, i >= total);
                if (!noError) {
                    continue;
                }
                uploadingFiles.delete(f);
            }
        }
    } else {
        for (let i = 0; i < fileList.length; i++) {
            const file = fileList[i];
            const fileName = getDirPath(currentFullPath, file.name);
            const passed = await uploadFile(
                null, file.file, fileName, uploadingFiles, currentFailTexts,
                null, null, null,
                showProgress
            );
            if (!passed) {
                displayFailTexts(currentFailTexts);
            } else {
                const fileInfo = uploadingFiles.get(fileName);
                const noError = await endSession(fileInfo, fileName, i >= fileList.length - 1);
                if (!noError) {
                    continue;
                }
                uploadingFiles.delete(fileName);
            }
        }
    }

    if (uploadingFiles.size) {
        submitBtn.textContent = 'Retry';
        console.log(uploadingFiles);
        currentFailTexts.length = 0;
        return;
    }

    displayInfoMessage('Upload completed', true, 30000);
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

function clearFailTexts() {
    errorMessageContainer.innerHTML = '';
    errorMessageContainer.classList.add('hidden');
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
    fileList.length = 0;
    uploadAsVideoCheckbox.disabled = false;
    allVideo = true;
    uploadingFiles.clear();
    clearProgress();
    totalProgress = 0;
    currentFailTexts.length = 0;
    clearFailTexts();
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
const nameEntityNodeList = [];
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
        nameEntityNodeList.push(nameEntity);
        nameEntityEditMap.set(key, new Map());
        addNameEntityContainer.appendChild(nameEntity);
    });
}

function loadCurrentNameEntityToEditArea() {
    clearNameEntityDisplayNode(currentNameEntityNode);
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

function getNameEntityForMediaUpload() {
    const body = [];
    nameEntityEditMap.forEach((value, key) => {
        const adding = [];
        value.forEach((name, id) => adding.push({name: name, id: id}));
        if (adding.length > 0)
            body.push({nameEntity: key.toUpperCase(), adding: adding});
    });
    if (body.length === 0) return null;
    return body;
}

function clearNameEntityMap() {
    for (const nameEntityNode of nameEntityNodeList)
        clearNameEntityDisplayNode(nameEntityNode);
    nameEntityEditMap.forEach((value, _) => value.clear());
}

function clearNameEntityDisplayNode(nameEntityNode) {
    if (nameEntityNode === null) return;
    const infoContainer = nameEntityNode.querySelector('.info-container');
    const first = infoContainer.firstElementChild;
    if (first) infoContainer.replaceChildren(first);
    editAreaContainer.querySelector('#currentArea').textContent = '';
}


const customRightMenu = document.getElementById('custom-right-menu');
const newFolderButton = customRightMenu.querySelector('.new-folder-btn');
const renameButton = customRightMenu.querySelector('.rename-btn');
const addAsVideoButton = customRightMenu.querySelector('.add-as-video-btn');
const addAsAlbumButton = customRightMenu.querySelector('.add-as-album-btn');
const addAsGrouperButton = customRightMenu.querySelector('.add-as-grouper-btn');
const openMediaButton = customRightMenu.querySelector('.open-media-btn');
const moveButton = customRightMenu.querySelector('.move-btn');
const deleteFileButton = customRightMenu.querySelector('.delete-file-btn');

const currentTargetNode = {
    id: null,
    type: null,
    mId: null,
    node: null
}

function clearTargetNode() {
    currentTargetNode.id = null;
    currentTargetNode.type = null;
    currentTargetNode.mId = null;
    currentTargetNode.node = null;
}

fileDropZone.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    clearTargetNode();
    const targetNode = event.target.closest('.file-node-wrapper');

    addAsVideoButton.disabled = true;
    addAsAlbumButton.disabled = true;
    addAsGrouperButton.disabled = true;
    openMediaButton.disabled = true;
    renameButton.disabled = true;
    addAsVideoButton.classList.add('invisible');
    addAsAlbumButton.classList.add('invisible');
    addAsGrouperButton.classList.add('invisible');
    openMediaButton.classList.add('invisible');
    renameButton.classList.add('invisible');

    if (!targetNode) {
        showCustomRightMenu(event.clientX, event.clientY);
        return;
    }

    currentTargetNode.id = targetNode.getAttribute('data-id');
    currentTargetNode.type = targetNode.getAttribute('data-type');
    currentTargetNode.mId = targetNode.getAttribute('data-mId');
    currentTargetNode.node = targetNode;

    if (currentTargetNode.mId) {
        openMediaButton.disabled = false;
        openMediaButton.classList.remove('invisible');
    } else {
        if (currentTargetNode.type === 'VIDEO') {
            addAsVideoButton.disabled = false;
            addAsVideoButton.classList.remove('invisible');
        } else if (currentTargetNode.type === 'DIR') {
            addAsAlbumButton.disabled = false;
            addAsAlbumButton.classList.remove('invisible');
            addAsGrouperButton.disabled = false;
            addAsGrouperButton.classList.remove('invisible');
        }
    }
    renameButton.disabled = false;
    renameButton.classList.remove('invisible');

    showCustomRightMenu(event.clientX, event.clientY);
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
    if (currentTargetNode.id === null) {
        console.log('No target selected');
        return;
    }
    const response = await apiRequest(`/api/file/vid/${currentTargetNode.id}`, {
        method: 'POST'
    });
    if (!response.ok) {
        alert('Failed to add as video: ' + await response.text());
        return;
    }
    displayInfoMessage(await response.text());
});

addAsAlbumButton.addEventListener('click', async function () {
    if (currentTargetNode.id === null) {
        console.log('No target selected');
        return;
    }
    const response = await apiRequest(`/api/file/album/${currentTargetNode.id}`, {
        method: 'POST'
    });
    if (!response.ok) {
        alert('Failed to add as album: ' + await response.text());
        return
    }
    displayInfoMessage(await response.text());
});

addAsGrouperButton.addEventListener('click', async function () {
    if (currentTargetNode.id === null) {
        console.log('No target selected');
        return;
    }
    const response = await apiRequest(`/api/file/grouper/${currentTargetNode.id}`, {
        method: 'POST'
    });
    if (!response.ok) {
        alert('Failed to add as grouper: ' + await response.text());
        return;
    }
    displayInfoMessage(await response.text());
})

openMediaButton.addEventListener('click', async function () {
    if (currentTargetNode.mId === null) {
        console.log('No target selected');
        return;
    }
    let url;
    if (currentTargetNode.type === 'VIDEO')
        url = `/page/video?mediaId=${currentTargetNode.mId}`;
    else if (currentTargetNode.type === 'ALBUM')
        url = `/page/album?mediaId=${currentTargetNode.mId}`;
    if (url)
        window.open(url);
});

deleteFileButton.addEventListener('click', async function () {
    if (currentTargetNode.id === null && currentTargetNode.mId === null) {
        console.log('No target selected');
        return;
    }
    const confirmDelete = confirm('Are you sure to delete this file?');
    if (!confirmDelete) return;
    if (currentTargetNode.mId) {
        const response = await apiRequest(`/api/file/media/${currentTargetNode.mId}`, {
            method: 'DELETE'
        });
        if (!response.ok) {
            alert('Failed to delete media: ' + await response.text());
            return;
        }
        displayInfoMessage("Processing to delete media");
    } else {
        const response = await apiRequest(`/api/file/${currentTargetNode.id}`, {
            method: 'DELETE'
        });
        if (!response.ok) {
            alert('Failed to delete file: ' + await response.text());
            return;
        }
        displayInfoMessage("Processing to delete file");
    }
    currentTargetNode.node.remove();
    const index = currentFileItems.findIndex(item => item.id === currentTargetNode.id);
    if (index !== -1) currentFileItems.splice(index, 1);
});

newFolderButton.addEventListener('click', async function () {
    const sendCreateNewFolderRequest = async (name) => {
        const currentPath = getCurrentPath();
        if (currentPath == null) {
            alert('Failed to get current path');
            return;
        }
        const currentFolderId = currentPath.id;
        if (currentFolderId === null) {
            alert('Failed to get current folder id');
            return;
        }
        if (name.length === 0) {
            alert('Folder name cannot be empty');
            return;
        }
        const newFolderName = name.trim();
        if (newFolderName.length === 0) {
            alert('Folder name cannot be empty');
            return;
        }
        const sameNameItem = currentFileItems.find(item => item.name === name);
        if (sameNameItem) {
            alert('Folder name already exists');
            return;
        }
        const response = await apiRequest(`/api/file/folder`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                parentId: currentFolderId,
                name: newFolderName
            })
        });
        if (!response.ok) {
            alert('Failed to create folder: ' + await response.text());
            return;
        }
        const fileInfo = await response.json();
        displayFileItem([fileInfo], false, false, true);
        displayInfoMessage(`Created folder: ${name}`, true, 30000);
    }
    openOverlayTextPrompt('New Folder', 'Untitled Folder', sendCreateNewFolderRequest);
});

renameButton.addEventListener('click', async function () {
    const currentFileItem = currentFileItems.find(item => item.id === currentTargetNode.id);
    if (currentFileItem === undefined) {
        console.log('No current file item');
        return;
    }
    const sendRenameRequest = async (newName) => {
        if (currentFileItem.name === newName) {
            displayInfoMessage('New name is the same as current name');
            return;
        }
        const sameNameItem = currentFileItems.find(item => item.name === newName);
        if (sameNameItem) {
            alert('An item the same name already exists');
            return;
        }
        const response = await apiRequest(`/api/file/rename`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                parentId: currentFileItem.id,
                name: newName
            })
        });
        if (!response.ok) {
            alert('Failed to rename file: ' + await response.text());
            return;
        }
        currentFileItem.name = newName;
        currentTargetNode.node.querySelector('.name').textContent = await response.text();
        displayInfoMessage(`Renamed: ${newName}`, true, 30000);
    }
    openOverlayTextPrompt('Rename', currentFileItem.name, sendRenameRequest);
});

const movingFileBanner = document.getElementById('moving-file-banner');
moveButton.addEventListener('click', async function () {
    const currentFileItem = currentFileItems.find(item => item.id === currentTargetNode.id);
    const currentId = currentFileItem?.id;
    const currentParentId = currentFileItem?.parentId;
    if (currentId === null) {
        alert('No target selected');
        return;
    }
    const moveFile = async () => {
        const currentPath = getCurrentPath();
        if (currentPath == null) {
            alert('Failed to get current path');
            return;
        }
        const currentFolderId = currentPath.id;
        if (currentFolderId === null) {
            alert('Failed to get current folder id');
            return;
        }
        console.log('current id: ' + currentId);
        console.log('current parent id: ' + currentParentId);
        console.log('current folder id: ' + currentFolderId);
        if (currentParentId === currentFolderId) {
            alert('Item is already in the same folder');
            return;
        }
        if (currentId === currentFolderId) {
            alert('Cannot move item to itself');
            return;
        }
        if (getFullCurrentPathInIds().includes(currentId)) {
            alert('Cannot move item to its subfolder');
            return;
        }
        const sameNameItem = currentFileItems.find(item => item.name === currentFileItem.name);
        if (sameNameItem) {
            alert('Cannot move item to a folder with item that has the same name');
            return;
        }
        const response = await apiRequest(`/api/file/move`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                fileId: currentId,
                parentId: currentFolderId
            })
        });
        if (!response.ok) {
            alert('Failed to move file: ' + await response.text());
            return;
        }
        movingFileBanner.querySelector('.cancel-btn').click();
        const fileInfo = await response.json();
        displayFileItem([fileInfo], false, false, true);
        displayInfoMessage(`Moved: ${fileInfo.name}`, true, 30000);
    }
    const currentFullPath = currentFileItem.name;
    openMovingFileBanner(currentFullPath, moveFile);
});

function openMovingFileBanner(name, moveFunc) {
    const movingPathText = movingFileBanner.querySelector('.moving-path-text');
    movingPathText.textContent = getFullCurrentPath() + name;
    movingPathText.title = name;

    movingFileBanner.querySelector('.move-btn').onclick = () => moveFunc();
    movingFileBanner.classList.remove('hidden');
}

movingFileBanner.querySelector('.cancel-btn').onclick = () => {
    movingFileBanner.querySelector('.moving-path-text').textContent = '';
    movingFileBanner.querySelector('.move-btn').onclick = null;
    movingFileBanner.classList.add('hidden');
}

document.addEventListener('click', () => {
    customRightMenu.style.display = 'none';
});

const infoMessageContainer = document.getElementById('info-message-container');
let infoMessageTimer = null;
function displayInfoMessage(message, hasTimeout = true, timeoutTime = 5000) {
    infoMessageContainer.querySelector('.info-message').textContent = message;
    infoMessageContainer.classList.remove('hidden');
    if (hasTimeout) {
        clearTimeout(infoMessageTimer);
        infoMessageTimer = setTimeout(() => {
            infoMessageContainer.classList.add('hidden');
        }, timeoutTime);
    }
}


const overlayTextPrompt = document.getElementById('overlay-text-prompt');
overlayTextPrompt.querySelector('.cancel-btn').addEventListener('click', () => {
    overlayTextPrompt.querySelector('.ok-btn').onclick = null;
    overlayTextPrompt.classList.add('hidden');
});

function openOverlayTextPrompt(title, text, okFunc) {
    overlayTextPrompt.querySelector('.title').textContent = title;
    const inputText = overlayTextPrompt.querySelector('.input-text');
    inputText.value = text;
    overlayTextPrompt.querySelector('.ok-btn').onclick = () => okFunc(inputText.value);
    overlayTextPrompt.classList.remove('hidden');
    const dotIndex = text.lastIndexOf('.');
    inputText.focus();
    if (dotIndex === -1 || dotIndex === 0)
        inputText.select();
    else
        inputText.setSelectionRange(0, dotIndex);
}




















