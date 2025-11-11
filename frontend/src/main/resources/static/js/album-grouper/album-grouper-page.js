import { displayContentInfo, helperCloneAndUnHideNode} from "/static/js/metadata-display.js";

async function initialize() {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    const albumGrouperId= urlParams.get('mediaId');

    if (!albumGrouperId) {
        alert('Media Id not found');
        window.location.href = '/';
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
        window.location.href = '/';
        return;
    }
    const albumGrouperInfo = await response.json();

    // const albumGrouperInfo = {
    //     "childMediaIds": [
    //         5, 4, 3, 2, 1
    //     ],
    //     "id": 1,
    //     "title": "Test Album Sample 1",
    //     "thumbnail": "https://placehold.co/1080x1920",
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
    //     "length": 60,
    //     "size": 400111222,
    //     "width": 1080,
    //     "height": 1920,
    //     "uploadDate": "2025-10-30",
    //     "year": null
    // };

    displayContentInfo(albumGrouperInfo);
    displayListSectionInfo(albumGrouperInfo);
    displayThumbnailSectionInfo(albumGrouperInfo);
}

function displayThumbnailSectionInfo(albumGrouperInfo) {
    const thumbnailSection = document.getElementById('thumbnailSection');
    const horizontal = albumGrouperInfo.width > albumGrouperInfo.height;
    const thumbnailItem = horizontal ? thumbnailSection.querySelector('.horizontal-item')
        : thumbnailSection.querySelector('.vertical-item');
    thumbnailItem.classList.remove('hidden');
    thumbnailItem.querySelector('.length-note').innerText = albumGrouperInfo.length;
    thumbnailItem.querySelector('.resolution-note').innerText = `${albumGrouperInfo.width}x${albumGrouperInfo.height}`;
    thumbnailItem.querySelector('img').src = albumGrouperInfo.thumbnail;
}

let albumGrouperLength = 0;

function displayListSectionInfo(albumGrouperInfo) {
    const listSection = document.getElementById('listSection');
    listSection.querySelector('.list-title').textContent = `List (${albumGrouperInfo.length})`;

    albumGrouperLength = albumGrouperInfo.length;

    const showMoreBtn = listSection.querySelector('.show-more-btn');

    if (albumGrouperLength === 0) {
        showMoreBtn.classList.add('hidden');
        return;
    }

    const scrollContainer = listSection.querySelector('.list-scroll-container');

    const listItemTem = scrollContainer.querySelector('.list-item-node');

    const addItem = (id) => {
        const listItem = helperCloneAndUnHideNode(listItemTem);
        listItem.innerText = `Item: ${albumGrouperLength}`
        listItem.href = `/api/media/content-page/${id}`;
        albumGrouperLength--;
        scrollContainer.appendChild(listItem);
    }

    albumGrouperInfo.childMediaIds.forEach(id => {
        addItem(id);
    });

    if (albumGrouperLength === 0) {
        listSection.querySelector('.show-more-btn').classList.add('hidden');
        return;
    }

    showMoreBtn.classList.remove('hidden');
    showMoreBtn.addEventListener('click', async () => {
        const response = await fetch(`/api/media/grouper-next/${albumGrouperInfo.id}`);
        if (!response.ok) {
            alert("Failed to grouper next list");
            return;
        }
        const grouperInfo = await response.json();
        // const grouperInfo = {
        //     "content": [
        //         101, // First Long ID
        //         102, // Second Long ID
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
    });
}