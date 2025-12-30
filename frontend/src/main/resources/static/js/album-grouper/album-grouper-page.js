import { initializeHeader } from "/static/js/header.js";
import { displayContentInfo, helperCloneAndUnHideNode} from "/static/js/metadata-display.js";
import { quickViewContentInOverlay } from "/static/js/overlay.js";
import {apiRequest} from "/static/js/common.js";

async function initialize() {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    const albumGrouperId= urlParams.get('grouperId');

    if (!albumGrouperId) {
        alert('grouper Id not found');
        return;
    }

    await Promise.all([
        displayAlbumGrouperInfo(albumGrouperId)
    ]);
}

window.addEventListener('DOMContentLoaded', () => {
    initializeHeader();
    initialize();
});

async function displayAlbumGrouperInfo(albumGrouperId) {
    const response = await apiRequest(`/api/media/content/${albumGrouperId}`);
    if (!response.ok) {
        alert("Failed to fetch album grouper info");
        return;
    }
    const albumGrouperInfo = await response.json();

    // const albumGrouperInfo = {
    //     "childMediaIds": {
    //         "content": [
    //             11,
    //             10,
    //             9,
    //             8,
    //             7,
    //             6,
    //             5,
    //             4
    //         ],
    //         "page": 0,
    //         "size": 20,
    //         "hasNext": false
    //     },
    //     "id": 1,
    //     "title": "Test Album Sample 1",
    //     "thumbnail": "https://placehold.co/1920x1080",
    //     "tags": [
    //         "Astrophotography",
    //         "LongExposure",
    //         "Urban"
    //     ],
    //     "characters": [
    //         "Jane Doe",
    //         "John Doe",
    //     ],
    //     "universes": [
    //         "One Piece"
    //     ],
    //     "authors": [
    //         "Jane Doe",
    //         "John Doe",
    //     ],
    //     "length": 2,
    //     "size": 400111222,
    //     "width": 1920,
    //     "height": 1080,
    //     "uploadDate": "2025-10-30",
    //     "year": null,
    //     "mediaType": "VIDEO"
    // };

    const albumGrouperMainContainer = document.getElementById('main-grouper-container');
    displayContentInfo(albumGrouperInfo, albumGrouperMainContainer);
    displayListSectionInfo(albumGrouperInfo);
    displayThumbnailSectionInfo(albumGrouperInfo);
}

function displayThumbnailSectionInfo(albumGrouperInfo) {
    const thumbnailSection = document.getElementById('thumbnailSection');
    const vertical = albumGrouperInfo.height > albumGrouperInfo.width;
    const thumbnailItem = vertical ? thumbnailSection.querySelector('.vertical-item')
        : thumbnailSection.querySelector('.horizontal-item');
    thumbnailItem.classList.remove('hidden');
    thumbnailItem.querySelector('.length-note').innerText = albumGrouperInfo.length;
    thumbnailItem.querySelector('.resolution-note').innerText = `${albumGrouperInfo.width}x${albumGrouperInfo.height}`;
    thumbnailItem.querySelector('img').src = albumGrouperInfo.thumbnail;
}

let offset = 0;
let albumGrouperCountLength = 0;
let currentViewAlbumId = null;
let albumGrouperChildAlbumIds = [];
const albumInfoContentMap = new Map();

const SORT_ORDER = Object.freeze({
    Ascending: 'ASC',
    Descending: 'DESC'
});
let sortOrder = SORT_ORDER.Descending;

let albumGrouperInfo = null;

const listSection = document.getElementById('listSection');
const showMoreBtn = listSection.querySelector('.show-more-btn');

const scrollContainer = listSection.querySelector('.list-scroll-container');
const listItemTem = scrollContainer.querySelector('.list-item-node');

const quickViewOverlay = document.getElementById('quickViewOverlay');
const quickViewTitle = quickViewOverlay.querySelector('.quick-view-title');
const leftButtons = quickViewOverlay.querySelector('.left-buttons');
const rightButtons = quickViewOverlay.querySelector('.right-buttons');
const leftNextBtn = leftButtons.querySelector('.next-btn');
const rightNextBtn = rightButtons.querySelector('.next-btn');
const leftPrevBtn = leftButtons.querySelector('.prev-btn');
const rightPrevBtn = rightButtons.querySelector('.prev-btn');

const showStepControls = (itemId) => {
    const albumIndex = albumGrouperChildAlbumIds.indexOf(itemId);
    if ((sortOrder === SORT_ORDER.Ascending && albumIndex >= albumGrouperInfo.length - 1)
        || (sortOrder === SORT_ORDER.Descending && albumIndex === 0)) {
        leftNextBtn.classList.add('invisible');
        rightNextBtn.classList.add('invisible');
    } else {
        leftNextBtn.classList.remove('invisible');
        rightNextBtn.classList.remove('invisible');
    }
    if ((sortOrder === SORT_ORDER.Ascending && albumIndex === 0)
        || (sortOrder === SORT_ORDER.Descending && albumIndex >= albumGrouperInfo.length - 1)) {
        leftPrevBtn.classList.add('invisible');
        rightPrevBtn.classList.add('invisible');
    } else {
        leftPrevBtn.classList.remove('invisible');
        rightPrevBtn.classList.remove('invisible');
    }
};

let viewInOverlayTimeout = null;
const getMediaContentAndShowInOverlay = async (itemId) => {
    let mediaInfo;
    if (albumInfoContentMap.has(itemId)) {
        mediaInfo = albumInfoContentMap.get(itemId);
    } else {
        mediaInfo = await fetchMediaContent(itemId);
        albumInfoContentMap.set(itemId, mediaInfo);
    }
    if (!mediaInfo) return;
    currentViewAlbumId = itemId;
    let itemNum = albumGrouperChildAlbumIds.indexOf(itemId);
    if (sortOrder === SORT_ORDER.Descending)
        itemNum = albumGrouperInfo.length - itemNum;
    else
        itemNum = itemNum + 1;
    quickViewTitle.querySelector('span').textContent = 'Item ' + itemNum;
    showStepControls(itemId);
    clearTimeout(viewInOverlayTimeout);
    viewInOverlayTimeout = setTimeout(async () => {
        await quickViewContentInOverlay(itemId, mediaInfo.mediaType, mediaInfo);
    }, 300);
};

const addItem = (id) => {
    if (albumGrouperChildAlbumIds.includes(id)) return;
    const listItem = helperCloneAndUnHideNode(listItemTem);
    listItem.href = `/api/media/content-page/${id}`;
    let title;
    if (sortOrder === SORT_ORDER.Descending) {
        title = `Item: ${albumGrouperCountLength}`;
        albumGrouperCountLength--;
    }
    else {
        albumGrouperCountLength++;
        title = `Item: ${albumGrouperCountLength}`;
    }
    listItem.innerText = title;
    scrollContainer.appendChild(listItem);
    albumGrouperChildAlbumIds.push(id);
    listItem.addEventListener('click', async (e) => {
        e.preventDefault();
        e.target.disabled = true;
        getMediaContentAndShowInOverlay(id).then(() => {
            e.target.disabled = false;
        });
    });
}

async function getNextGrouperInfo(grouperId) {
    const grouperInfo = await fetchGrouperNext(grouperId);
    if (!grouperInfo.content.length) return;
    grouperInfo.content.forEach(id => {
        addItem(id);
    });
    scrollContainer.scrollTo({
        top: scrollContainer.scrollHeight,
        behavior: 'smooth'
    });
}

const sortOrderButton = document.getElementById('sortOrderButton');
sortOrderButton.addEventListener('click', async () => {
    sortOrder = sortOrder === SORT_ORDER.Descending ? SORT_ORDER.Ascending : SORT_ORDER.Descending;
    sortOrderButton.textContent = sortOrder === SORT_ORDER.Descending ? 'Descending' : 'Ascending';
    if (sortOrder === SORT_ORDER.Descending) {
        albumGrouperCountLength = albumGrouperInfo.length;
    } else {
        albumGrouperCountLength = 0;
    }
    const first = scrollContainer.firstElementChild;
    if (first) scrollContainer.replaceChildren(first);
    if (albumGrouperChildAlbumIds.length === albumGrouperInfo.length) {
        if (sortOrder === SORT_ORDER.Descending)
            albumGrouperChildAlbumIds.sort((a, b) => b - a);
        else
            albumGrouperChildAlbumIds.sort((a, b) => a - b);
        const copy = [...albumGrouperChildAlbumIds];
        albumGrouperChildAlbumIds.length = 0;
        copy.forEach(id => {
            addItem(id);
        });
        albumGrouperChildAlbumIds = copy;
        scrollContainer.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
        return;
    }
    const batchSize = 20;
    offset = Math.floor((albumGrouperChildAlbumIds.length - 1) / batchSize);
    if (offset < 0) return;
    await getNextGrouperInfo(albumGrouperInfo.id);
});

const viewNextAlbum = async () => {
    const currentAlbumIdIndex = albumGrouperChildAlbumIds.indexOf(currentViewAlbumId);
    if (currentAlbumIdIndex === -1) return;
    const dif = sortOrder === SORT_ORDER.Ascending ? 1 : -1;
    const nextAlbumIdIndex = currentAlbumIdIndex + dif;
    if (sortOrder === SORT_ORDER.Ascending) {
        if (nextAlbumIdIndex >= albumGrouperChildAlbumIds.length) {
            await getNextGrouperInfo(albumGrouperInfo.id);
        }
        if (nextAlbumIdIndex >= albumGrouperChildAlbumIds.length) return;
    }
    else if (sortOrder === SORT_ORDER.Descending) {
        if (nextAlbumIdIndex < 0) {
            await getNextGrouperInfo(albumGrouperInfo.id);
        }
        if (nextAlbumIdIndex < 0) return;
    }
    const nextAlbumId = albumGrouperChildAlbumIds[nextAlbumIdIndex];
    await getMediaContentAndShowInOverlay(nextAlbumId);
    const nextTwoAlbumIdIndex = nextAlbumIdIndex + dif;
    if ((sortOrder === SORT_ORDER.Ascending && nextTwoAlbumIdIndex >= albumGrouperInfo.length)
        || (sortOrder === SORT_ORDER.Descending && nextTwoAlbumIdIndex < 0)) {
        leftNextBtn.classList.add('invisible');
        rightNextBtn.classList.add('invisible');
    }
    leftPrevBtn.classList.remove('invisible');
    rightPrevBtn.classList.remove('invisible');
};

const viewPrevAlbum = async () => {
    const currentAlbumIdIndex = albumGrouperChildAlbumIds.indexOf(currentViewAlbumId);
    if (currentAlbumIdIndex === -1) return;
    const dif = (sortOrder === SORT_ORDER.Ascending ? 1 : -1);
    const prevAlbumIdIndex = currentAlbumIdIndex - dif;
    if (sortOrder === SORT_ORDER.Ascending && prevAlbumIdIndex < 0) return;
    else if (sortOrder === SORT_ORDER.Descending && prevAlbumIdIndex >= albumGrouperChildAlbumIds.length) return;
    const prevAlbumId = albumGrouperChildAlbumIds[prevAlbumIdIndex];
    await getMediaContentAndShowInOverlay(prevAlbumId);
    const prevTwoAlbumIdIndex = prevAlbumIdIndex - dif;
    if ((sortOrder === SORT_ORDER.Ascending && prevTwoAlbumIdIndex < 0)
        || (sortOrder === SORT_ORDER.Descending && prevTwoAlbumIdIndex >= albumGrouperInfo.length)) {
        leftPrevBtn.classList.add('invisible');
        rightPrevBtn.classList.add('invisible');
    }
    leftNextBtn.classList.remove('invisible');
    rightNextBtn.classList.remove('invisible');
};

leftNextBtn.addEventListener('click', viewNextAlbum);
rightNextBtn.addEventListener('click', viewNextAlbum);
leftPrevBtn.addEventListener('click', viewPrevAlbum);
rightPrevBtn.addEventListener('click', viewPrevAlbum);

showMoreBtn.addEventListener('click', async () => {
    await getNextGrouperInfo(albumGrouperInfo.id);
    // const grouperInfo = {
    //     "content": [
    //         101,
    //         102,
    //     ],
    //     "pageable": {
    //         "pageNumber": 0,
    //         "pageSize": 10,
    //         "sort": {
    //             "empty": false,
    //             "sorted": true,
    //             "unsorted": false
    //         },
    //         "offset": 0,
    //         "paged": true,
    //         "unpaged": false
    //     },
    //     "last": false,
    //     "size": 10,
    //     "number": 0,
    //     "sort": {
    //         "empty": false,
    //         "sorted": true,
    //         "unsorted": false
    //     },
    //     "numberOfElements": 10,
    //     "first": true,
    //     "empty": false,
    //     "hasNext": true,
    //     "hasPrevious": false
    // };
});

function displayListSectionInfo(grouperInfo) {
    albumGrouperInfo = grouperInfo;
    listSection.querySelector('.list-title').textContent = `List (${albumGrouperInfo.length})`;

    const childMediaIdsSlice = albumGrouperInfo.childMediaIds.content;
    if (!childMediaIdsSlice.length) {
        showMoreBtn.classList.add('hidden');
        sortOrderButton.classList.add('hidden');
        return;
    }

    sortOrderButton.classList.remove('hidden');
    showMoreBtn.classList.remove('hidden');

    if (sortOrder === SORT_ORDER.Descending)
        albumGrouperCountLength = albumGrouperInfo.length;
    else
        albumGrouperCountLength = 0;

    if (albumGrouperInfo.length === 0) {
        showMoreBtn.classList.add('hidden');
        return;
    }

    childMediaIdsSlice.forEach(id => {
        addItem(id);
    });

    if (albumGrouperCountLength === 0 || albumGrouperCountLength === albumGrouperInfo.length) {
        listSection.querySelector('.show-more-btn').classList.add('hidden');
    }
}

export function addNewAlbumItem(newId) {
    albumGrouperInfo.length = albumGrouperInfo.length + 1;
    albumGrouperCountLength = albumGrouperInfo.length;
    listSection.querySelector('.list-title').textContent = `List (${albumGrouperInfo.length})`;
    if (sortOrder === SORT_ORDER.Descending) {
        albumGrouperChildAlbumIds.unshift(newId);
    } else {
        albumGrouperChildAlbumIds.push(newId);
    }
    const first = scrollContainer.firstElementChild;
    if (first) scrollContainer.replaceChildren(first);
    const copy = [...albumGrouperChildAlbumIds];
    albumGrouperChildAlbumIds.length = 0;
    copy.forEach(id => {
        addItem(id);
    });
    albumGrouperChildAlbumIds = copy;
}

async function fetchMediaContent(mediaId) {
    const response = await apiRequest(`/api/media/content/${mediaId}`);
    if (!response.ok) {
        alert("Failed to fetch media info");
        return false;
    }
    return await response.json();
}

async function fetchGrouperNext(albumGrouperId) {
    const response = await apiRequest(`/api/media/grouper-next/${albumGrouperId}?offset=${offset}&order=${sortOrder}`);
    if (!response.ok) {
        alert("Failed to grouper next list");
        return null;
    }
    return await response.json();
}
