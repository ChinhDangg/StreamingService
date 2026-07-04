import {apiRequest} from "/static/js/common.js";
import {FileManager} from "/static/js/file-manager/FileManager.js";
import {
    currentSearchFileItems,
    clearSearch,
    getIsSearching,
    setIsSearching, getIsInDeepSearch, setIsInDeepSearch
} from "/static/js/file-manager/search-file.js";

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
        currentMainFileItems.setCurrentFilePage(subFiles.pageable.pageNumber + 1);
    else
        currentMainFileItems.setCurrentFilePage(-1);
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
    else if (fileType === 'GROUPER') iconNode = iconContainer.querySelector('.grouper-icon');
    else if (fileType === 'FILE') iconNode = iconContainer.querySelector('.file-icon');
    if (iconNode) return helperCloneAndUnHideNode(iconNode);
    return null;
}

let isProcessing = false;
export const currentMainFileItems = new FileManager();

/*
    if useGlobalMapItems is true, then the fileItems will be a list of fileItemIds
    and the displayFileItem will use the global map to get the fileItem objects
    and display them. This is useful when we want to display the fileItems in a
    different order than the order they are in the fileItems array.
 */
export function displayFileItem(fileItems, fileItemManager = null, clearNode = true, clearFileList = true, pushFileList = true, useGlobalMapItems = false) {
    if (clearNode) {
        const first = fileViewContainer.firstElementChild;
        if (first) fileViewContainer.replaceChildren(first);
        if (observer)
            observer.observe(sentinel);
    }
    if (clearFileList && fileItemManager !== null) {
        fileItemManager.removeAll();
    }

    fileViewWrapper.querySelector('.end-of-file-text').classList.add('hidden');

    const selectedViews = getSelectedViews();

    const renderFileItem = (item) => {
        if (pushFileList && fileItemManager !== null) {
            fileItemManager.addFileItem(item);
        }

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
        fileNode.dataset.name = item.name;
        showSelectedViews(fileNode, item, selectedViews);
        fileViewContainer.appendChild(fileNode);
    }

    if (useGlobalMapItems && fileItemManager !== null) {
        if (fileItems && fileItems.length > 0)
            fileItemManager.loopAndDoWithGivenFileItemIds(fileItems, renderFileItem);
        else if (fileItems && fileItems.length === 0)
            fileItemManager.loopAllAndDo(renderFileItem);
    } else {
        fileItems.forEach(item => {
            renderFileItem(item);
        });
    }

    if (fileItemManager && fileItemManager.getCurrentFilePage() === -1) {
        const endOfFileText = fileViewWrapper.querySelector('.end-of-file-text');
        if (!fileItems || fileItems.length === 0)
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

function getSelectedViews() {
    return new Map(Array.from(viewCheckboxes).map(cb => [cb.value, cb.checked]));
}

function showSelectedViews(fileNode, item, selectedViews) {
    for (const [key, value] of selectedViews) {
        const keyString = key.toString().toLowerCase();
        const selectedNode = fileNode.querySelector('.' + keyString + '-info');
        if (selectedNode === null) continue;
        selectedNode.classList.toggle('hidden', !value);
        if (!value)
            continue;
        if (keyString === 'resolution' && item.resolution) {
            selectedNode.innerText = item.resolution.width + 'x' + item.resolution.height;
        } else if (keyString === 'length') {
            selectedNode.innerText = item.fileType === 'VIDEO' ? formatDuration(item.length) : item.length;
        } else if (keyString === 'size') {
            selectedNode.innerText = formatSize(item.size);
        } else if (keyString === 'upload') {
            selectedNode.innerText = formatDate(item.uploadDate);
        }
    }
}

const viewCheckboxes = document.querySelectorAll('#dropdown-menu input[type="checkbox"]');
const button = document.getElementById('dropdown-button');
const menu = document.getElementById('dropdown-menu');

let selectedViewVisible = false;
let previousSelectedViewSnapshot = '';
function getSelectedViewSnapshot() {
    return Array.from(viewCheckboxes)
        .filter(cb => cb.checked)
        .map(cb => cb.value)
        .sort()              // ensure consistent order
        .join('|');          // inexpensive comparison
}

button.addEventListener('click', () => {
    selectedViewVisible = !selectedViewVisible;
    if (selectedViewVisible) {
        menu.classList.remove('hidden');
    } else {
        const newSnapshot = getSelectedViewSnapshot();
        menu.classList.add('hidden');
        if (previousSelectedViewSnapshot === newSnapshot) {
            console.log('No change');
            return;
        }
        previousSelectedViewSnapshot = newSnapshot;
        console.log('re-displaying');
        displayFileItem([], currentMainFileItems, true, false, false, true);
    }
});

const fileViewWrapper = document.getElementById('file-view-wrapper');
const sortSelect = document.getElementById('file-sort-by-select');
const sentinel = document.createElement("div");
let observer;
export function unobserveSentinel() {
    if (observer)
        observer.unobserve(sentinel);
}
export function observeSentinel() {
    if (observer)
        observer.observe(sentinel);
}
export function disconnectObserver() {
    if (observer)
        observer.disconnect();
}
export function setObserver(newObserver) {
    observer = newObserver;
}

async function fetchMoreFiles(subId, page = 0, getParentInfo = false) {
    if (isProcessing) return false;
    isProcessing = true;
    setIsSearching(false);
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
        currentMainFileItems.setCurrentFilePage(subFiles.pageable.pageNumber + 1);
    else
        currentMainFileItems.setCurrentFilePage(-1);
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

    if (currentMainFileItems.getCurrentFilePage() === -1) {
        // reached the end - should have all files with all info to sort locally
        const sortSelectValue = getSortSelectValue();
        let key = getItemKeyFromSortSelectValue(sortSelectValue.by);
        if (key === 'resolution')
            key = 'resolution.area';
        const order = sortSelectValue.order;
        console.log('sorting locally');
        currentMainFileItems.sortFileItems(key, order);
        displayFileItem([], currentMainFileItems, true, false, false, true);
        return;
    }

    const subFiles = await fetchMoreFiles(subId);
    if (!subFiles) return;
    displayFileItem(subFiles, currentMainFileItems);
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
    setObserverToFetchMore();
    observer.observe(sentinel);
}

export function setObserverToFetchMore() {
    if (observer)
        observer.disconnect();
    observer = new IntersectionObserver(async (entries) => {
        if (entries[0].isIntersecting) {
            console.log('Intersecting');
            const currentPathStack = getCurrentPath();
            if (currentPathStack == null) {
                observer.unobserve(sentinel);
                return;
            }
            const subId = currentPathStack.id;
            if (currentMainFileItems.getCurrentFilePage() === -1) {
                observer.unobserve(sentinel);
                return;
            }
            const subFiles = await fetchMoreFiles(subId, currentMainFileItems.getCurrentFilePage());
            if (subFiles === false)
                return;
            if (subFiles == null) {
                observer.unobserve(sentinel);
                return;
            }
            displayFileItem(subFiles, currentMainFileItems,false, false, true);
        }
    }, { rootMargin: '500px' });
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
        displayFileItem(subFiles, currentMainFileItems);
        console.log(thisIndex, currentPathStack[thisIndex]);
        if (!isMovingFile)
            selectFileBannerCancelBtn.click();
    });
}

function removeLastPathStack() {
    currentPathStack.pop();
    pathBar.removeChild(pathBar.lastElementChild);
    pathBar.removeChild(pathBar.lastElementChild);
}

function clearPathStack() {
    const end = currentPathStack.length;
    for (let i = 0; i < end; i++) {
        removeLastPathStack();
    }
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
    displayFileItem(subFiles, currentMainFileItems);
    if (!isMovingFile)
        selectFileBannerCancelBtn.click();
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
        const fetchedAndMoved = await fetchMoreFilesAndMove(subId, 0);
        if (fetchedAndMoved) return;
    }

    homeButton.click();
}

window.addEventListener('popstate', async function () {
    await initialize();
});

async function fetchMoreFilesAndMove(subId, page) {
    const subFiles = await fetchMoreFiles(subId, page, true);
    if (subFiles) {
        if (subFiles.parentId && subFiles.parentName) {
            clearPathStack();
            const parentIds = subFiles.parentId.split('/').filter(Boolean);
            const parentNames = subFiles.parentName.split('/').filter(Boolean);
            for (let i = 0; i < parentIds.length; i++) {
                addToCurrentPath(parentIds[i], parentNames[i]);
            }
            displayFileItem(subFiles.content, currentMainFileItems);
            return true;
        }
    }
    return false;
}

window.addEventListener('DOMContentLoaded', async function () {
    await initialize();
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
    setIsSearching(false);
    addToCurrentPath(rootInfo.parentId, rootInfo.parentName, true);
    displayFileItem(rootInfo.content, currentMainFileItems);
    if (!isMovingFile)
        selectFileBannerCancelBtn.click();
});


export function getCurrentPath() {
    if (currentPathStack.length === 0) return null;
    return currentPathStack[currentPathStack.length - 1];
}

export function getFullCurrentPath() {
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

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('!hidden', 'hidden');
    return clone;
}



const selectedFiles = new Map();
const selectFileBanner = document.getElementById('select-file-banner');
const selectFileBannerCancelBtn = selectFileBanner.querySelector('.cancel-btn');

function addSelectedFile(fileId, mediaId, fileType, fileName, fileNode) {
    if (selectedFiles.has(fileId))
        return false;
    selectedFiles.set(fileId, {mediaId: mediaId, fileType: fileType, fileName: fileName, fileNode: fileNode});
    fileNode.classList.add('border-[3px]', 'border-white')
    selectFileBanner.querySelector('.selected-count-text').textContent = selectedFiles.size.toString();
    selectFileBanner.classList.remove('hidden');
    return true;
}

function removeSelectedFile(fileId) {
    if (!selectedFiles.has(fileId))
        return false;
    selectedFiles.get(fileId).fileNode.classList.remove('border-[3px]', 'border-white');
    selectedFiles.delete(fileId);
    selectFileBanner.querySelector('.selected-count-text').textContent = selectedFiles.size.toString();
    if (selectedFiles.size === 0) {
        selectFileBanner.classList.add('hidden');
    }
    return true;
}

selectFileBannerCancelBtn.addEventListener('click', function () {
    for (const file of selectedFiles.values()) {
        file.fileNode.classList.remove('border-[3px]', 'border-white');
    }
    selectedFiles.clear();
    if (isMovingFile)
        movingFileBannerCancelBtn.click();
    selectFileBanner.querySelector('.selected-count-text').textContent = '0';
    selectFileBanner.classList.add('hidden');
});

const selectionBox = document.getElementById('selection-box');
let isDragging = false;
let startX, startY;
let cachedFiles = [];
let animationFrameId = null;
fileViewWrapper.addEventListener('mousedown', (e) => {
    if (e.button !== 0 || e.target.closest('.file-node-wrapper')) return;

    isDragging = true;
    startX = e.clientX;
    startY = e.clientY;

    const files = document.querySelectorAll('.file-node-wrapper');

    cachedFiles = Array.from(files).map(fileEl => {
        const rect = fileEl.getBoundingClientRect();
        return {
            element: fileEl,
            left: rect.left,
            right: rect.right,
            top: rect.top,
            bottom: rect.bottom,
            isSelected: false
        };
    });

    selectionBox.style.display = 'block';
    selectionBox.style.left = `${startX}px`;
    selectionBox.style.top = `${startY}px`;
    selectionBox.style.width = '0px';
    selectionBox.style.height = '0px';
});

window.addEventListener('mousemove', (e) => {
    if (!isDragging) return;

    if (animationFrameId) cancelAnimationFrame(animationFrameId);

    animationFrameId = requestAnimationFrame(() => {
        const currentX = e.clientX;
        const currentY = e.clientY;

        const boxLeft = Math.min(startX, currentX);
        const boxTop = Math.min(startY, currentY);
        const boxWidth = Math.abs(startX - currentX);
        const boxHeight = Math.abs(startY - currentY);
        const boxRight = boxLeft + boxWidth;
        const boxBottom = boxTop + boxHeight;

        // Update the visual box
        selectionBox.style.left = `${boxLeft}px`;
        selectionBox.style.top = `${boxTop}px`;
        selectionBox.style.width = `${boxWidth}px`;
        selectionBox.style.height = `${boxHeight}px`;

        for (let i = 0; i < cachedFiles.length; i++) {
            const file = cachedFiles[i];

            const isIntersecting = !(
                file.left > boxRight ||
                file.right < boxLeft ||
                file.top > boxBottom ||
                file.bottom < boxTop
            );

            // Only touch the DOM if the state actually flipped (massive speed boost)
            if (isIntersecting !== file.isSelected) {
                file.isSelected = isIntersecting;
                const fileId = file.element.getAttribute('data-id');
                const fileMid = file.element.getAttribute('data-mId');
                const fileType = file.element.getAttribute('data-type');
                const fileName = file.element.getAttribute('data-name');
                if (!removeSelectedFile(fileId))
                    addSelectedFile(fileId, fileMid, fileType, fileName, file.element);
            }
        }
    });
});

window.addEventListener('mouseup', () => {
    if (!isDragging) return;
    isDragging = false;
    selectionBox.style.display = 'none';
    if (animationFrameId) cancelAnimationFrame(animationFrameId);

    // Clear memory
    cachedFiles = [];
});



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
    name: null,
    node: null
}

function clearTargetNode() {
    currentTargetNode.id = null;
    currentTargetNode.type = null;
    currentTargetNode.mId = null;
    currentTargetNode.name = null;
    currentTargetNode.node = null;
}

const fileDropZone = document.getElementById('file-zone');
fileDropZone.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    clearTargetNode();
    const targetNode = event.target.closest('.file-node-wrapper');

    addAsVideoButton.disabled = true;
    addAsAlbumButton.disabled = true;
    addAsGrouperButton.disabled = true;
    openMediaButton.disabled = true;
    addNameEntityButton.disabled = true;
    renameButton.disabled = true;
    moveButton.disabled = true;
    deleteFileButton.disabled = true;
    addAsVideoButton.classList.add('invisible');
    addAsAlbumButton.classList.add('invisible');
    addAsGrouperButton.classList.add('invisible');
    openMediaButton.classList.add('invisible');
    addNameEntityButton.classList.add('invisible');
    renameButton.classList.add('invisible');
    moveButton.classList.add('invisible');
    deleteFileButton.classList.add('invisible');

    if (!targetNode) {
        showCustomRightMenu(event.clientX, event.clientY);
        return;
    }

    currentTargetNode.id = targetNode.getAttribute('data-id');
    currentTargetNode.type = targetNode.getAttribute('data-type');
    currentTargetNode.mId = targetNode.getAttribute('data-mId');
    currentTargetNode.name = targetNode.getAttribute('data-name');
    currentTargetNode.node = targetNode;

    addSelectedFile(currentTargetNode.id, currentTargetNode.mId, currentTargetNode.type, currentTargetNode.name, targetNode)

    if (currentTargetNode.mId) {
        openMediaButton.disabled = false;
        openMediaButton.classList.remove('invisible');
        addNameEntityButton.disabled = false;
        addNameEntityButton.classList.remove('invisible');
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
    if (selectedFiles.size > 0) {
        renameButton.disabled = false;
        renameButton.classList.remove('invisible');
        moveButton.disabled = false;
        moveButton.classList.remove('invisible');
        deleteFileButton.disabled = false;
        deleteFileButton.classList.remove('invisible');
    }

    showCustomRightMenu(event.clientX, event.clientY);
});

fileDropZone.addEventListener('click', async (event) => {
    const targetNode = event.target.closest('.file-node-wrapper');
    if (!targetNode) return;
    clearTargetNode();

    const fileType = targetNode.getAttribute('data-type');
    const fileId = targetNode.getAttribute('data-id');
    const mediaId = targetNode.getAttribute('data-mId');
    const fileName = targetNode.getAttribute('data-name');

    if (!isMovingFile && selectedFiles.size > 0) {
        if (!removeSelectedFile(fileId))
            addSelectedFile(fileId, mediaId, fileType, fileName, targetNode);
        return;
    }

    const isDir = fileType === 'DIR' || fileType === 'ALBUM' || fileType === 'GROUPER';

    if (getIsInDeepSearch()) {
        clearSearch();
        if (isDir)
            await fetchMoreFilesAndMove(fileId, 0);
        else {
            const parentId = currentSearchFileItems.getFileItemById(fileId)?.parentId;
            if (!parentId) {
                alert('Failed to get parent id.');
                return;
            }
            await fetchMoreFilesAndMove(parentId, 0);
        }
        setIsInDeepSearch(false);
        return;
    }

    if (isDir) {
        const fileName = targetNode.getAttribute('data-name');

        if (isProcessing) return;
        setCurrentUri(fileId);
        const subFiles = await fetchMoreFiles(fileId);
        if (!subFiles) return;
        displayFileItem(subFiles, currentMainFileItems);
        addToCurrentPath(fileId, fileName);
    } else if (fileType === 'IMAGE' || fileType === 'VIDEO') {
        await openPreview(fileType, fileId);
    }
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
    if (selectedFiles.size === 0) {
        console.log('No file selected');
        return;
    }
    for (const [fileId, fileInfo] of selectedFiles.entries()) {
        if (fileInfo.fileType !== 'VIDEO') {
            console.log('File is not a video: ' + fileId);
            continue;
        }
        const response = await apiRequest(`/api/file/vid/${fileId}`, {
            method: 'POST'
        });
        if (!response.ok) {
            displayInfoMessage('Failed to add as video: ' + await response.text(), false);
            return;
        }
        displayInfoMessage(await response.text());
    }
    displayInfoMessage(`Processing ${selectedFiles.size} file(s) as video(s).`);
});

addAsAlbumButton.addEventListener('click', async function () {
    if (currentTargetNode.id === null) {
        console.log('No target selected');
        return;
    }
    if (selectedFiles.size === 0) {
        console.log('No file selected');
        return;
    }
    for (const [fileId, fileInfo] of selectedFiles.entries()) {
        if (fileInfo.fileType !== 'DIR') {
            console.log('File is not a directory: ' + fileId);
            continue;
        }
        const response = await apiRequest(`/api/file/album/${fileId}`, {
            method: 'POST'
        });
        if (!response.ok) {
            displayInfoMessage('Failed to add as album: ' + await response.text(), false);
            return
        }
        displayInfoMessage(await response.text());
    }
    displayInfoMessage(`Processing ${selectedFiles.size} dir(s) as album(s).`);
});

addAsGrouperButton.addEventListener('click', async function () {
    if (currentTargetNode.id === null) {
        console.log('No target selected');
        return;
    }
    if (selectedFiles.size === 0) {
        console.log('No file selected');
        return;
    }
    for (const [fileId, fileInfo] of selectedFiles.entries()) {
        if (fileInfo.fileType !== 'DIR') {
            console.log('File is not a directory: ' + fileId);
            continue;
        }
        const response = await apiRequest(`/api/file/grouper/${fileId}`, {
            method: 'POST'
        });
        if (!response.ok) {
            displayInfoMessage('Failed to add as grouper: ' + await response.text(), false);
            return;
        }
        displayInfoMessage(await response.text());
    }
    displayInfoMessage(`Processing ${selectedFiles.size} dir(s) as grouper(s).`);
});

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
    else if (currentTargetNode.type === 'GROUPER')
        url = `/page/album-grouper?grouperId=${currentTargetNode.mId}`;
    if (url)
        window.open(url);
});

deleteFileButton.addEventListener('click', async function () {
    if (currentTargetNode.id === null && currentTargetNode.mId === null) {
        console.log('No target selected');
        return;
    }
    if (selectedFiles.size === 0) {
        console.log('No file selected');
        return;
    }
    const deleteText = selectedFiles.size === 1 ? `file ${currentTargetNode.name}` : `${selectedFiles.size} files`;
    const confirmDelete = confirm(`Are you sure to delete ${deleteText}?`);
    if (!confirmDelete) return;
    const idsToDelete = [];
    for (const [fileId, fileInfo] of selectedFiles.entries()) {
        if (fileInfo.mediaId) {
            const response = await apiRequest(`/api/file/media/${fileInfo.mediaId}`, {
                method: 'DELETE'
            });
            if (!response.ok) {
                displayInfoMessage('Failed to delete media: ' + await response.text(), false);
                return;
            }
            displayInfoMessage("Processing to delete media: " + fileInfo.mediaId);
        } else {
            const response = await apiRequest(`/api/file/${fileId}`, {
                method: 'DELETE'
            });
            if (!response.ok) {
                displayInfoMessage('Failed to delete file: ' + await response.text(), false);
                return;
            }
            displayInfoMessage("Processing to delete file: " + fileInfo.fileName);
        }
        idsToDelete.push(fileId);
        removeSelectedFile(fileId);
        fileInfo.fileNode.remove();
        if (getIsSearching())
            currentSearchFileItems.removeFileItemInMapOnly(fileId);
        currentMainFileItems.removeFileItemInMapOnly(fileId);
    }
    const toDeleteSet = new Set(idsToDelete);
    if (getIsSearching())
        currentSearchFileItems.removeFileItemsInIdListOnly(toDeleteSet);
    currentMainFileItems.removeFileItemsInIdListOnly(toDeleteSet);
    if (toDeleteSet.size > 1)
        displayInfoMessage(`Processing to delete: ${deleteText}`);
});

function hasSameNameItem(name) {
    const item = currentMainFileItems.findOneFileItemWithExactName(name);
    return !!item;
}

newFolderButton.addEventListener('click', async function () {
    const sendCreateNewFolderRequest = async (name) => {
        const currentPath = getCurrentPath();
        if (!currentPath) {
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
        const sameNameItem = hasSameNameItem(newFolderName);
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
        displayFileItem([fileInfo], currentMainFileItems,false, false, true);
        displayInfoMessage(`Created folder: ${name}`, true, 30000);
    }
    openOverlayTextPrompt('New Folder', 'Untitled Folder', sendCreateNewFolderRequest);
});

function getCurrentFileItemById(id) {
    if (getIsSearching())
        return currentSearchFileItems.getFileItemById(id);
    return currentMainFileItems.getFileItemById(id);
}

renameButton.addEventListener('click', async function () {
    const currentFileItem = getCurrentFileItemById(currentTargetNode.id);
    if (!currentFileItem) {
        alert('No current file item');
        return;
    }
    const sendRenameRequest = async (newName) => {
        if (currentFileItem.name === newName) {
            displayInfoMessage('New name is the same as current name');
            return;
        }
        newName = newName.trim();
        if (newName.length === 0) {
            alert('New name cannot be empty');
            return;
        }
        const sameNameItem = hasSameNameItem(newName);
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
        const respondedName = await response.text();
        currentFileItem.name = respondedName;
        currentTargetNode.node.querySelector('.name').textContent = respondedName;
        currentTargetNode.node.dataset.name = respondedName;
        displayInfoMessage(`Renamed: ${respondedName}`, true, 30000);
    }
    openOverlayTextPrompt('Rename', currentFileItem.name, sendRenameRequest);
});

const movingFileBanner = document.getElementById('moving-file-banner');
const movingFileBannerCancelBtn = movingFileBanner.querySelector('.cancel-btn');
moveButton.addEventListener('click', async function () {
    if (!currentTargetNode.id) {
        alert('No target selected');
        return;
    }
    if (selectedFiles.size === 0) {
        alert('No file selected');
        return;
    }
    const moveFile = async (movingFileId, currentParentId, currentFileName) => {
        if (!movingFileId) {
            return 'Failed to get current file item: ' + movingFileId;
        }
        const currentPath = getCurrentPath();
        if (currentPath == null) {
            return 'Failed to get current path';
        }
        const currentFolderId = currentPath.id;
        if (currentFolderId === null) {
            return 'Failed to get current folder id';
        }
        if (currentParentId === currentFolderId) {
            return 'Item is already in the same folder';
        }
        if (movingFileId === currentFolderId) {
            return 'Cannot move item to itself';
        }
        if (getFullCurrentPathInIds().includes(movingFileId)) {
            return 'Cannot move item to its subfolder';
        }
        const sameNameItem = hasSameNameItem(currentFileName);
        if (sameNameItem) {
            alert('Cannot move item to a folder with item that has the same name');
            return null;
        }
        const response = await apiRequest(`/api/file/move`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                fileId: movingFileId,
                parentId: currentFolderId
            })
        });
        if (!response.ok) {
            return 'Failed to move file: ' + await response.text();
        }
        const fileInfo = await response.json();
        displayFileItem([fileInfo], currentMainFileItems,false,false,true);
        displayInfoMessage(`Moved: ${fileInfo.name}`, true, 30000);
    }

    const movingFiles = [];
    for (const fileId of selectedFiles.keys()) {
        movingFiles.push(getCurrentFileItemById(fileId));
    }

    const moveFiles = async () => {
        for (const movingFile of movingFiles) {
            const failedText = await moveFile(movingFile.id, movingFile.parentId, movingFile.name);
            if (failedText) {
                alert(failedText);
                return;
            }
        }
        movingFileBannerCancelBtn.click();
        selectFileBannerCancelBtn.click();
    }
    let currentFullPath;
    if (selectedFiles.size === 1) {
        const selectedFileId = selectedFiles.keys().next().value;
        const currentFileItem = getCurrentFileItemById(selectedFileId);
        currentFullPath = currentFileItem.name;
    } else {
        currentFullPath = `${selectedFiles.size} files`;
    }
    openMovingFileBanner(currentFullPath, moveFiles);
});

let isMovingFile = false;
function openMovingFileBanner(name, moveFunc) {
    isMovingFile = true;
    const movingPathText = movingFileBanner.querySelector('.moving-path-text');
    movingPathText.textContent = getFullCurrentPath() + name;
    movingPathText.title = name;

    movingFileBanner.querySelector('.move-btn').onclick = () => moveFunc();
    movingFileBanner.classList.remove('hidden');
}

movingFileBannerCancelBtn.onclick = () => {
    isMovingFile = false;
    movingFileBanner.querySelector('.moving-path-text').textContent = '';
    movingFileBanner.querySelector('.move-btn').onclick = null;
    movingFileBanner.classList.add('hidden');
}

document.addEventListener('click', () => {
    customRightMenu.style.display = 'none';
});


const infoMessageContainer = document.getElementById('info-message-container');
const messageText = infoMessageContainer.querySelector('.info-message');

let messageQueue = [];
let isProcessingMessage = false;
const MAX_QUEUE = 20;

let currentController = null;
let currentStartTime = 0;

export function displayInfoMessage(message, hasTimeout = true, timeoutTime = 5000) {
    if (messageQueue.length >= MAX_QUEUE) {
        messageQueue.shift();
    }

    messageQueue.push({ message, hasTimeout, timeoutTime });

    // If a message is currently showing → try to interrupt it
    if (isProcessingMessage && currentController) {
        const elapsed = Date.now() - currentStartTime;

        if (elapsed >= 500) {
            currentController.abort(); // cancel immediately
        } else {
            // wait until 500ms is reached, then cancel
            setTimeout(() => {
                if (currentController) currentController.abort();
            }, 500 - elapsed);
        }
    }

    if (!isProcessingMessage) {
        processQueue();
    }
}

async function processQueue() {
    if (messageQueue.length === 0) {
        isProcessingMessage = false;
        infoMessageContainer.classList.add('hidden');
        return;
    }

    isProcessingMessage = true;

    const currentItem = messageQueue.shift();

    messageText.textContent = currentItem.message;
    infoMessageContainer.classList.remove('hidden');

    currentStartTime = Date.now();
    currentController = new AbortController();

    try {
        let delay;
        if (messageQueue.length === 0) {
            // Only message
            if (!currentItem.hasTimeout) {
                // wait forever until aborted
                await new Promise((_, reject) => {
                    currentController.signal.addEventListener('abort', reject);
                });
                return;
            } else {
                delay = currentItem.timeoutTime;
            }
        } else {
            // Multiple messages → max 500ms
            delay = 500;
        }

        await cancellableDelay(delay, currentController.signal);
    } catch (e) {
        // Aborted → just move on
    }
``
    currentController = null;

    processQueue();
}

function cancellableDelay(ms, signal) {
    return new Promise((resolve, reject) => {
        const id = setTimeout(resolve, ms);

        signal.addEventListener('abort', () => {
            clearTimeout(id);
            reject(new Error('aborted'));
        });
    });
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



const previewOverlay = document.getElementById('previewOverlay');
const content = previewOverlay.querySelector('.previewContent');
const imgPreview = previewOverlay.querySelector('.previewImage');
const videoPreview = previewOverlay.querySelector('.previewVideo');

async function openPreview(type, fileId) {
    previewOverlay.classList.remove('hidden');
    previewOverlay.classList.add('flex');

    // Reset
    imgPreview.classList.add('hidden');
    videoPreview.classList.add('hidden');
    videoPreview.pause();
    videoPreview.src = "";

    const srcResponse = await apiRequest(`/api/file/download/${fileId}`);
    if (!srcResponse.ok) {
        alert('Failed to get preview: ' + await srcResponse.text());
        return;
    }
    const src = await srcResponse.text();

    if (type === 'IMAGE') {
        imgPreview.src = src;
        imgPreview.classList.remove('hidden');
    } else if (type === 'VIDEO') {
        videoPreview.src = src;
        videoPreview.classList.remove('hidden');
    }

    // Animate in
    setTimeout(() => {
        previewOverlay.classList.remove('opacity-0');
        content.classList.remove('scale-95', 'opacity-0');
        content.classList.add('scale-100', 'opacity-100');
    }, 10);
}

function closePreview() {
    previewOverlay.classList.add('opacity-0');
    content.classList.add('scale-95', 'opacity-0');

    setTimeout(() => {
        previewOverlay.classList.add('hidden');
        previewOverlay.classList.remove('flex');
        videoPreview.pause();
    }, 200);
}

previewOverlay.querySelector('.preview-close-btn').addEventListener('click', closePreview);

// Close when clicking outside content
previewOverlay.addEventListener('click', (e) => {
    if (e.target === previewOverlay) {
        closePreview();
    }
});