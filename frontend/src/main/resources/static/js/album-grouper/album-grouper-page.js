import { displayContentInfo, helperCloneAndUnHideNode} from "/static/js/metadata-display.js";
import { quickViewContentInOverlay } from "/static/js/overlay.js";

async function initialize() {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    const albumGrouperId= urlParams.get('mediaId');

    if (!albumGrouperId) {
        alert('Media Id not found');
        return;
    }

    await Promise.all([
        displayAlbumGrouperInfo(albumGrouperId)
    ]);
}

window.addEventListener('DOMContentLoaded', initialize);

async function displayAlbumGrouperInfo(albumGrouperId) {
    const response = await fetch(`/api/media/content/${albumGrouperId}`);
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

let albumGrouperCountLength = 0;
let currentViewAlbumId = null;
let albumGrouperChildAlbumIds = [];
const albumInfoContentMap = new Map();
let sortOrder = 'DESC';

function displayListSectionInfo(albumGrouperInfo) {
    const listSection = document.getElementById('listSection');
    listSection.querySelector('.list-title').textContent = `List (${albumGrouperInfo.length})`;

    const showMoreBtn = listSection.querySelector('.show-more-btn');

    const childMediaIdsSlice = albumGrouperInfo.childMediaIds;
    if (!childMediaIdsSlice) {
        showMoreBtn.classList.add('hidden');
        return;
    }

    albumGrouperCountLength = childMediaIdsSlice.content.length;

    if (albumGrouperCountLength === 0) {
        showMoreBtn.classList.add('hidden');
        return;
    }

    const scrollContainer = listSection.querySelector('.list-scroll-container');
    const listItemTem = scrollContainer.querySelector('.list-item-node');

    const quickViewOverlay = document.getElementById('quickViewOverlay');
    const leftButtons = quickViewOverlay.querySelector('.left-buttons');
    const rightButtons = quickViewOverlay.querySelector('.right-buttons');
    const leftNextBtn = leftButtons.querySelector('.next-btn');
    const rightNextBtn = rightButtons.querySelector('.next-btn');
    const leftPrevBtn = leftButtons.querySelector('.prev-btn');
    const rightPrevBtn = rightButtons.querySelector('.prev-btn');

    const showStepControls = (itemId) => {
        const albumIndex = albumGrouperChildAlbumIds.indexOf(itemId);
        if ((sortOrder === 'ASC' && albumIndex >= albumGrouperInfo.length - 1)
            || (sortOrder === 'DESC' && albumIndex === 0)) {
            leftNextBtn.classList.add('hidden');
            rightNextBtn.classList.add('hidden');
        } else {
            leftNextBtn.classList.remove('hidden');
            rightNextBtn.classList.remove('hidden');
        }
        if ((sortOrder === 'ASC' && albumIndex === 0)
            || (sortOrder === 'DESC' && albumIndex >= albumGrouperInfo.length - 1)) {
            leftPrevBtn.classList.add('hidden');
            rightPrevBtn.classList.add('hidden');
        } else {
            leftPrevBtn.classList.remove('hidden');
            rightPrevBtn.classList.remove('hidden');
        }
    };

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
        showStepControls(itemId);
        await quickViewContentInOverlay(itemId, mediaInfo.mediaType, mediaInfo);
    };

    const addItem = (id) => {
        const listItem = helperCloneAndUnHideNode(listItemTem);
        listItem.innerText = `Item: ${albumGrouperCountLength}`
        listItem.href = `/api/media/content-page/${id}`;
        albumGrouperCountLength--;
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

    const getNextGrouperInfo = async (grouperId) => {
        const grouperInfo = await fetchGrouperNext(grouperId);
        if (!grouperInfo) return;
        grouperInfo.content.forEach(id => {
            addItem(id);
        });
        scrollContainer.scrollTo({
            top: scrollContainer.scrollHeight,
            behavior: 'smooth'
        });
        if (!grouperInfo.hasNext) {
            this.remove();
        }
    }

    const viewNextAlbum = async () => {
        const currentAlbumIdIndex = albumGrouperChildAlbumIds.indexOf(currentViewAlbumId);
        if (currentAlbumIdIndex === -1) return;
        const dif = sortOrder === 'ASC' ? 1 : -1;
        const nextAlbumIdIndex = currentAlbumIdIndex + dif;
        if (sortOrder === 'ASC') {
            if (nextAlbumIdIndex >= albumGrouperChildAlbumIds.length) {
                await getNextGrouperInfo(albumGrouperInfo.id);
            }
            if (nextAlbumIdIndex >= albumGrouperChildAlbumIds.length) return;
        }
        else if (sortOrder === 'DESC') {
            if (nextAlbumIdIndex < 0) {
                await getNextGrouperInfo(albumGrouperInfo.id);
            }
            if (nextAlbumIdIndex < 0) return;
        }
        const nextAlbumId = albumGrouperChildAlbumIds[nextAlbumIdIndex];
        await getMediaContentAndShowInOverlay(nextAlbumId);
        const nextTwoAlbumIdIndex = nextAlbumIdIndex + dif;
        if ((sortOrder === 'ASC' && nextTwoAlbumIdIndex >= albumGrouperInfo.length)
            || (sortOrder === 'DESC' && nextTwoAlbumIdIndex < 0)) {
            leftNextBtn.classList.add('hidden');
            rightNextBtn.classList.add('hidden');
        }
        leftPrevBtn.classList.remove('hidden');
        rightPrevBtn.classList.remove('hidden');
    };

    const viewPrevAlbum = async () => {
        const currentAlbumIdIndex = albumGrouperChildAlbumIds.indexOf(currentViewAlbumId);
        if (currentAlbumIdIndex === -1) return;
        const dif = (sortOrder === 'ASC' ? 1 : -1);
        const prevAlbumIdIndex = currentAlbumIdIndex - dif;
        if (sortOrder === 'ASC' && prevAlbumIdIndex < 0) return;
        else if (sortOrder === 'DESC' && prevAlbumIdIndex >= albumGrouperChildAlbumIds.length) return;
        const prevAlbumId = albumGrouperChildAlbumIds[prevAlbumIdIndex];
        await getMediaContentAndShowInOverlay(prevAlbumId);
        const prevTwoAlbumIdIndex = prevAlbumIdIndex - dif;
        if ((sortOrder === 'ASC' && prevTwoAlbumIdIndex < 0)
            || (sortOrder === 'DESC' && prevTwoAlbumIdIndex >= albumGrouperInfo.length)) {
            leftPrevBtn.classList.add('hidden');
            rightPrevBtn.classList.add('hidden');
        }
        leftNextBtn.classList.remove('hidden');
        rightNextBtn.classList.remove('hidden');
    };

    leftNextBtn.addEventListener('click', viewNextAlbum);
    rightNextBtn.addEventListener('click', viewNextAlbum);
    leftPrevBtn.addEventListener('click', viewPrevAlbum);
    rightPrevBtn.addEventListener('click', viewPrevAlbum);

    childMediaIdsSlice.content.forEach(id => {
        addItem(id);
    });

    if (albumGrouperCountLength === 0) {
        listSection.querySelector('.show-more-btn').classList.add('hidden');
        return;
    }

    showMoreBtn.classList.remove('hidden');
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
}

async function fetchMediaContent(mediaId) {
    const response = await fetch(`/api/media/content/${mediaId}`);
    if (!response.ok) {
        alert("Failed to fetch media info");
        return false;
    }
    return await response.json();
}

async function fetchGrouperNext(albumGrouperId) {
    const response = await fetch(`/api/media/grouper-next/${albumGrouperId}?o=${albumGrouperCountLength}`);
    if (!response.ok) {
        alert("Failed to grouper next list");
        return null;
    }
    return await response.json();
}
