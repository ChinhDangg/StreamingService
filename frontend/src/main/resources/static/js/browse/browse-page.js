import { initializeHeader, helperCloneAndUnHideNode} from "/static/js/header.js";
import { displayPagination } from "/static/js/pagination.js";

const NameEntry = Object.freeze({
    Characters: 'characters',
    Universes: 'universes',
    Authors: 'authors',
    Tags: 'tags',
});

const SortBy = Object.freeze({
    Name: 'NAME',
    Upload: 'UPLOAD_DATE',
    Total: 'LENGTH',
});

const SortOrder = Object.freeze({
    Ascending: 'ASC',
    Descending: 'DESC',
});

let currentNameEntry = getCurrentNameEntry();
let currentPage = 0;
let currentSortBy = SortBy.Name;
let currentSortOrder = SortOrder.Ascending;

let previousSortByButton = null;

function getCurrentNameEntry(url = window.location.href) {
    const pathName = new URL(url).pathname;
    for (const [key, value] of Object.entries(NameEntry)) {
        if (pathName.endsWith(value)) {
            return key;
        }
    }
    return Object.keys(NameEntry)[0];
}

async function initialize() {
    initializeCurrentOptions();

    removeRedirectBrowseOptions();

    const fetched = await fetchNameItems(NameEntry[currentNameEntry], currentPage, currentSortBy, currentSortOrder, false);
    if (!fetched) return;

    initializeSortByOptions();
    initializeSortByOrderOptions();
}

window.addEventListener('DOMContentLoaded', () => {
    initializeHeader();
    initialize();
});

function initializeCurrentOptions() {
    const urlParams = new URLSearchParams(window.location.search);
    currentPage = Number(urlParams.get('p')) || 0;
    currentSortBy = urlParams.get('by') || SortBy.Name;
    currentSortOrder = urlParams.get('order') || SortOrder.Ascending;
}

function removeRedirectBrowseOptions () {
    const browseOptionContainer = document.getElementById('browse-option-container');
    browseOptionContainer.querySelectorAll('a').forEach(browseOption => {
        if (browseOption.href) {
            const nameEntry = getCurrentNameEntry(browseOption.href);
            browseOption.addEventListener('click', async (e) => {
                e.preventDefault();
                if (currentNameEntry === nameEntry) return;
                currentNameEntry = nameEntry;
                await fetchNameItems(NameEntry[currentNameEntry], currentPage, currentSortBy, currentSortOrder);
            });
        }
    });
}

function initializeSortByOptions() {
    const sortByContainer = document.getElementById('sort-by-option-container');
    sortByContainer.classList.remove('hidden');
    const optionBtnTem = sortByContainer.querySelector('button');
    for (const [key, value] of Object.entries(SortBy)) {
        const optionBtn = helperCloneAndUnHideNode(optionBtnTem);
        if (currentSortBy === value) {
            optionBtn.classList.add('bg-indigo-600');
            optionBtn.classList.remove('bg-gray-800');
            previousSortByButton = optionBtn;
        }
        optionBtn.textContent = key;
        optionBtn.addEventListener('click', async () => {
            if (previousSortByButton === optionBtn) return;
            currentSortBy = value;
            previousSortByButton?.classList.remove('bg-indigo-600');
            previousSortByButton?.classList.add('bg-gray-800');
            optionBtn.classList.add('bg-indigo-600');
            optionBtn.classList.remove('bg-gray-800');
            previousSortByButton = optionBtn;
            await fetchNameItems(NameEntry[currentNameEntry], currentPage, currentSortBy, currentSortOrder);
        });
        sortByContainer.appendChild(optionBtn);
    }
}

function initializeSortByOrderOptions() {
    const sortOrderContainer = document.getElementById('sort-order-option-container');
    sortOrderContainer.classList.remove('hidden');
    const optionBtn = sortOrderContainer.querySelector('button');
    optionBtn.textContent = getSortOrderText(currentSortOrder);
    optionBtn.addEventListener('click', async () => {
        currentSortOrder = currentSortOrder === SortOrder.Ascending ? SortOrder.Descending : SortOrder.Ascending;
        optionBtn.textContent = getSortOrderText(currentSortOrder);
        await fetchNameItems(NameEntry[currentNameEntry], currentPage, currentSortBy, currentSortOrder);
    });
}

function getSortOrderText(order) {
    for (const [key, value] of Object.entries(SortOrder)) {
        if (value === order) return key;
    }
}

async function fetchNameItems(nameEntry, p, by, order, pushtoHistory = true) {
    const urlParams = new URLSearchParams({ p, by, order });
    const response = await fetch(`/api/media/${nameEntry}?${urlParams}`);
    if (!response.ok) {
        alert("Failed to fetch name items");
        return false;
    }
    const nameItems = await response.json();

    if (pushtoHistory) {
        const url = new URL(window.location.href);
        const pageBrowseUrl = getBrowsePageUrl(currentPage);
        const newUrl = url.origin + pageBrowseUrl;
        window.history.pushState({ path: newUrl }, '', newUrl);
    }
    // const nameItems = {
    //     "content": [
    //         {
    //             "name": "Kafka",
    //             "length": 60,
    //             "uploadDate": "2025-11-04",
    //             "thumbnail": "https://placehold.co/1080x1920"
    //         },
    //         {
    //             "name": "Kafka",
    //             "length": 60,
    //             "uploadDate": "2025-11-04",
    //             "thumbnail": "https://placehold.co/1920x1080"
    //         }
    //     ],
    //     "pageable": {
    //         "pageNumber": 0,
    //         "pageSize": 20,
    //         "sort": {
    //             "orders": []
    //         }
    //     },
    //     "total": 1
    // };

    await displayItem(nameItems);
    displayPagination(nameItems.pageable.pageNumber,
        Math.trunc((nameItems.total + nameItems.pageable.pageSize - 1) / nameItems.pageable.pageSize),
        getBrowsePageUrl, pageClickHandler);

    return true;
}

async function displayItem(nameItems) {
    const browseTitle = document.getElementById('browse-page-title');
    browseTitle.querySelector('.title').textContent = currentNameEntry;
    browseTitle.querySelector('.total').textContent = nameItems.total;

    const hasThumbnail = NameEntry[currentNameEntry] === NameEntry.Characters || NameEntry[currentNameEntry] === NameEntry.Universes;
    const mainItemContainer = document.getElementById('main-item-container');
    const mainTextItemContainer = document.getElementById('main-text-item-container');
    const nameContainer = hasThumbnail ? mainItemContainer : mainTextItemContainer;

    mainItemContainer.innerHTML = '';
    mainTextItemContainer.innerHTML = '';
    nameContainer.classList.remove('hidden');

    if (nameItems.total === 0) {
        nameContainer.innerText = 'No items found';
        return;
    }

    const browseContainer = document.getElementById('browse-item-container');

    const loadImage = (imgElement, src) => {
        return new Promise((resolve, reject) => {
            imgElement.onload = () => {
                resolve(imgElement);
            };
            imgElement.onerror = (err) => {
                reject(new Error(`Failed to load image: ${src}`));
            };
            // start fetching the image data
            imgElement.src = src;
        });
    }

    for (const item of nameItems.content) {
        let itemNode;
        if (hasThumbnail) {
            const loadedImage = document.createElement('img');
            await loadImage(loadedImage, item.thumbnail);

            const horizontal = loadedImage.naturalWidth >= loadedImage.naturalHeight;
            const itemNodeTem = horizontal ? browseContainer.querySelector('.horizontal-item') : browseContainer.querySelector('.vertical-item');
            itemNode = helperCloneAndUnHideNode(itemNodeTem);
            const itemNodeImg = itemNode.querySelector('img');
            loadedImage.className = itemNodeImg.className;
            loadedImage.style.cssText = itemNodeImg.style.cssText;
            itemNodeImg.replaceWith(loadedImage);
        } else {
            itemNode = helperCloneAndUnHideNode(browseContainer.querySelector('.text-item'));
        }
        itemNode.querySelector('.name-title').textContent = item.name;
        itemNode.querySelector('.date-note').textContent = item.uploadDate;
        itemNode.querySelector('.total-note').textContent = item.length;
        itemNode.querySelector('.item-link').href = `/page/search?${NameEntry[currentNameEntry]}=${item.name}`;
        nameContainer.appendChild(itemNode);
    }
}

function getBrowsePageUrl(page) {
    const urlParams = new URLSearchParams({ p: page, by: currentSortBy, order: currentSortOrder});
    return `/page/browse/${NameEntry[currentNameEntry]}?${urlParams}`;
}

async function pageClickHandler(e, page) {
    e.preventDefault();
    currentPage = page;
    e.target.disabled = true;
    fetchNameItems(NameEntry[currentNameEntry], page, currentSortBy, currentSortOrder).then(() => {
        e.target.disabled = false;
    });
}