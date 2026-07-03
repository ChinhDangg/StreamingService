import {displayInfoMessage, getCurrentPath, getFullCurrentPath} from "/static/js/file-manager/file-manager-page.js";
import {endFileSession, endVideoUploadSession, uploadFile} from "/static/js/file-manager/upload-file.js";
import {apiRequest} from "/static/js/common.js";

window.addEventListener('DOMContentLoaded', async function () {
    initializeAddNameEntity();
    initializeEditArea();
});

const uploadToggleButton = document.getElementById('upload-toggle-btn');
const uploadContainer = document.getElementById('upload-container');
uploadToggleButton.addEventListener('click', function () {
    uploadContainer.classList.toggle('hidden');
});

const fileDropZone = document.getElementById('file-zone');
// prevent default drag behavior
["dragenter", "dragover", "dragleave", "drop"].forEach(event => {
    fileDropZone.addEventListener(event, (e) => {
        e.preventDefault();
        e.stopPropagation();
    });
});

// highlight the drop zone when dragging over it
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
    if (fileArray.length === 0) return;
    await handleFileArray(fileArray);
});

const fileList = [];
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
    displayInfoMessage(`Uploading ${fileList.length} files`, false);
    sortUploadFileAndDisplayFileName(fileList);
}

async function traverseEntry(entry, fileArray) {
    if (entry.isFile) {
        // Handle individual files
        return new Promise((resolve) => {
            entry.file(file => {
                // Adjust the object structure to match what your handleFileArray expects
                fileArray.push({
                    path: entry.fullPath.startsWith('/') ? entry.fullPath.substring(1) : entry.fullPath,
                    file: file
                });
                resolve();
            });
        });
    } else if (entry.isDirectory) {
        // Handle directories
        const dirReader = entry.createReader();
        const allEntries = [];

        // Recursive function to keep reading batches of 100 until empty
        const readDirectory = () => {
            return new Promise((resolve, reject) => {
                dirReader.readEntries(async (entriesBatch) => {
                    if (entriesBatch.length > 0) {
                        allEntries.push(...entriesBatch);
                        // Call itself again to get the next 100 files
                        resolve(await readDirectory());
                    } else {
                        // No more files left to read
                        resolve();
                    }
                }, reject);
            });
        };

        // Wait for all batches to be read
        await readDirectory();

        // Now process all the gathered entries (including subfolders)
        const promises = allEntries.map(childEntry => traverseEntry(childEntry, fileArray));
        return Promise.all(promises);
    }
}


const fileInput = document.getElementById('file-input');
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
    displayInfoMessage(`Uploading ${fileList.length} files`, false);
    sortUploadFileAndDisplayFileName(fileList);
    fileInput.value = '';
}


const folderInput = document.getElementById('folder-input');
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
    displayInfoMessage(`Uploading ${fileList.length} files`, false);
    sortUploadFileAndDisplayFileName(fileList);
    folderInput.value = '';
}


const sortUploadSelection = document.getElementById('sort-file-upload-select');
sortUploadSelection.addEventListener('change', function () {
    sortUploadFileAndDisplayFileName(fileList);
});

function sortUploadFileAndDisplayFileName(fileList) {
    if (fileList.length === 0) return;
    else if (fileList.length === 1) {
        addUploadingFileText(fileList[0].name);
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
        addUploadingFileText(file.name);
    });
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
    uploadContainer.classList.remove('hidden');
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


const uploadingList = document.getElementById('upload-list');
const listItemTem = uploadingList.querySelector('li');
const uploadingFileNameNodeMap = new Map();

function addUploadingFileText(name) {
    name = name.replace(/\\/g, '/');
    const listItem = helperCloneAndUnHideNode(listItemTem);
    listItem.innerText = name;
    uploadingList.appendChild(listItem);
    uploadingFileNameNodeMap.set(name, listItem);
}

function validateAllowUpload(fileList) {
    if (fileList.length === 0) {
        alert('No file selected');
        return false;
    }
    if (!getCurrentPath()) {
        alert('No target folder selected');
        return false;
    }
    return true;
}

let isSubmitting = false;
const uploadingFiles = new Map(); // keep track of failed files, if any, for re-upload
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
    await manageUploadFile(fileList);
    isSubmitting = false;
});

async function manageUploadFile(fileList) {
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
            displayInfoMessage(`Re-uploading ${total - i} files`);
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
                uploadingFileNameNodeMap.get(uploadingFile.fileName).remove();
                uploadingFileNameNodeMap.delete(uploadingFile.fileName);
            }
        }
    } else {
        displayInfoMessage(`Uploading ${fileList.length} files`);
        for (let i = 0; i < fileList.length; i++) {
            displayInfoMessage(`Uploading ${fileList.length - i} files`);
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
                uploadingFileNameNodeMap.get(file.name).remove();
                uploadingFileNameNodeMap.delete(file.name);
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

function getDirPath(filePath, fileName) {
    const firstSlashIndex = filePath.indexOf('/');
    const rootOmitted = filePath.substring(firstSlashIndex + 1);
    fileName = fileName == null ? '' : fileName;
    let path =  rootOmitted + (rootOmitted.endsWith('/') ? '' : '/') + fileName;
    if (path.startsWith('/')) path = path.substring(1);
    if (path.endsWith('/')) path = path.substring(0, path.length - 1);
    return path;
}

function clearUploadingListNameText() {
    const first = uploadingList.firstElementChild;
    if (first) uploadingList.replaceChildren(first);
    uploadingFileNameNodeMap.clear();
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
    submitBtn.textContent = 'Submit';
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


function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('!hidden', 'hidden');
    return clone;
}