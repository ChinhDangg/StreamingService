import {setVideoUrl} from "./set-video-url.js";

const SORT_BY = Object.freeze({
    UPLOAD_DATE: 'UPLOAD_DATE',
    YEAR: 'YEAR',
    LENGTH: 'LENGTH',
    SIZE: 'SIZE'
});
const SORT_ORDERS = Object.freeze({
   ASC: 'ASC',
   DESC: 'DESC',
});
const KEYWORDS = Object.freeze({
    UNIVERSE: 'universes',
    CHARACTER: 'characters',
    AUTHOR: 'authors',
    TAG: 'tags'
});

const SEARCH_TYPES = Object.freeze({
    BASIC: 'search',
    KEYWORD: 'keyword',
    ADVANCE: 'advance'
});
const SEARCH_INFO = Object.freeze({
    PAGE: 'page',
    SORT_BY: 'sortBy',
    SORT_ORDER: 'sortOrder',
    SEARCH_STRING: 'searchString',
    KEYWORD_FIELD: 'keywordField',
    KEYWORD_VALUE_LIST: 'keywordValueList',
    ADVANCE_REQUEST_BODY: 'advanceRequestBody',
    SEARCH_TYPE: 'searchType'
});
const currentSearchInfo = new Map();
const keywordSearchMap = new Map();

let previousSortByButton = null;

async function initialize() {
    initializeSortByOptions();
    initializeAdvanceSearchArea();
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    currentSearchInfo.set(SEARCH_INFO.PAGE, urlParams.get(SEARCH_INFO.PAGE) || 0);
    currentSearchInfo.set(SEARCH_INFO.SORT_BY, urlParams.get(SEARCH_INFO.SORT_BY) || SORT_BY.UPLOAD_DATE);
    currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, urlParams.get(SEARCH_INFO.SORT_ORDER) || SORT_ORDERS.DESC);
    currentSearchInfo.set(SEARCH_INFO.SEARCH_STRING, urlParams.get(SEARCH_INFO.SEARCH_STRING));

    let field = null;
    let values = null;
    for (const key of Object.values(KEYWORDS)) {
        if (urlParams.has(key)) {
            field = key;
            values = urlParams.getAll(key);
            break; // stop at the first matching keyword
        }
    }
    currentSearchInfo.set(SEARCH_INFO.KEYWORD_FIELD, field);
    currentSearchInfo.set(SEARCH_INFO.KEYWORD_VALUE_LIST, values?.split(','));
    currentSearchInfo.set(SEARCH_INFO.ADVANCE_REQUEST_BODY, null);
    currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, urlParams.get(SEARCH_INFO.SEARCH_TYPE));
    await sendSearchRequestOnCurrentInfo();
}

initialize();

const searchForm = document.querySelector('#search-form');
searchForm.addEventListener('submit', (e) => {
    e.preventDefault();

    const searchString = document.querySelector('#search-input').value;
    validateSearchString(searchString);
});

function validateSearchString(searchString) {
    if (searchString.length < 2) {
        setAlertStatus('Invalid search string', 'Search string must be at least 2 characters');
    } else if (searchString.length > 200) {
        setAlertStatus('Invalid search string', 'Search string exceeded 200 characters');
    }
}

function initializeAdvanceSearchArea() {
    const advanceSearchForm = document.getElementById('advanceSearchForm');
    advanceSearchForm.querySelector('#advancedSearchBtn').addEventListener('click', () => {
        advanceSearchForm.querySelector('#advanceSearchSubmitBtn').classList.toggle('hidden');
        advanceSearchForm.querySelector('#advanceSearchContentContainer').classList.toggle('hidden');
        initializeKeywordSearchArea();
    });

    advanceSearchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const sortBy = advanceSearchForm.querySelector('input[name="sortBy"]:checked')?.value;
        const orderBy = advanceSearchForm.querySelector('input[name="orderBy"]:checked')?.value;
        const yearFrom = advanceSearchForm.querySelector('.year-from-input').value;
        const yearTo = advanceSearchForm.querySelector('.year-to-input').value;
        const uploadFrom = advanceSearchForm.querySelector('.upload-from-input').value;
        const uploadTo = advanceSearchForm.querySelector('.upload-to-input').value;

        const getRangeField = (field, from, to) => {
            return {
                field: field,
                from: from,
                to: to
            }
        }

        const rangeFields = [];
        if (yearFrom || yearTo) {
            rangeFields.push(getRangeField(SORT_BY.YEAR, yearFrom, yearTo));
        }
        if (uploadFrom || uploadTo) {
            rangeFields.push(getRangeField(SORT_BY.UPLOAD_DATE, uploadFrom, uploadTo));
        }

        const includeFields = [];
        const excludeFields = [];

        const getSearchField = (field, values, mustAll) => {
            return {
                field: field,
                values: values,
                mustAll: mustAll
            }
        }

        Object.keys(KEYWORDS).forEach(key => {
            const value = KEYWORDS[key];
            const keywordMap = keywordSearchMap.get(value);
            if (keywordMap.size !== 0) {
                const include = [];
                const exclude = [];
                keywordMap.forEach((value, key) => {
                   if (value.included)
                       include.push(key);
                   else
                       exclude.push(key);
                });
                if (include.length)
                    includeFields.push(getSearchField(value, include, true));
                if (exclude.length)
                    excludeFields.push(getSearchField(value, exclude, false));
            }
        });

        const advanceRequestBody = {
            includeFields: includeFields,
            excludeFields: excludeFields,
            rangeFields: rangeFields
        }
        console.log(advanceRequestBody);
    });
}

let keywordSearchAreaInitialized = false;
let keywordSearchTimeOut = null;

function initializeKeywordSearchArea() {
    if (keywordSearchAreaInitialized) return;
    keywordSearchAreaInitialized = true;
    const fnKeywordSearch = async (pathVariable, value) => {
        // const response = await fetch(`/api/search-suggestion/${pathVariable}?s=${value}`);
        // if (!response.ok) {
        //     setAlertStatus('Search Suggestion Failed', response.statusText);
        //     return [];
        // }
        // return await response.json();
        return ['Author 1', 'Author 2', 'Author 3'];
    };
    const makeSearchFn = (path) => async (value) => fnKeywordSearch(path, value);

    const keywordSearchContainer = document.getElementById('keywordSearchContainer');
    const keywordSearchTem = keywordSearchContainer.querySelector('.keyword-search');

    const authorMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.AUTHOR), authorMap, KEYWORDS.AUTHOR, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    const characterMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.CHARACTER), authorMap, KEYWORDS.CHARACTER, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    const universeMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.UNIVERSE), authorMap, KEYWORDS.UNIVERSE, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    const tagMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.TAG), authorMap, KEYWORDS.TAG, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));

    keywordSearchMap.set(KEYWORDS.AUTHOR, authorMap);
    keywordSearchMap.set(KEYWORDS.CHARACTER, characterMap);
    keywordSearchMap.set(KEYWORDS.UNIVERSE, universeMap);
    keywordSearchMap.set(KEYWORDS.TAG, tagMap);
}

function addEventKeywordSearchArea(fnKeywordSearch, addedMap, searchLabel, searchContainer) {
    searchLabel = searchLabel.charAt(0).toUpperCase() + searchLabel.slice(1);
    searchContainer.querySelector('.search-label-text').textContent = searchLabel;

    const searchInput = searchContainer.querySelector('.search-input');
    searchInput.placeholder = 'Search ' + searchLabel;
    const searchDropDownUl = searchContainer.querySelector('.search-dropdown-ul');
    const searchDropDownLiTem = searchDropDownUl.querySelector('li');
    const selectedCount = searchContainer.querySelector('.selected-count');

    const selectedToggleBtn = searchContainer.querySelector('.selected-toggle-btn');
    const selectedDropDownUl = searchContainer.querySelector('.selected-dropdown-ul');
    const selectedDropDownLiTem = selectedDropDownUl.querySelector('li');
    const unselectedCount = searchContainer.querySelector('.unselected-count');

    searchInput.addEventListener('input', (e) => {
        clearTimeout(keywordSearchTimeOut);
        const value = e.target.value.trim();
        if (value.length < 2) {
            searchDropDownUl.classList.add('hidden');
            return;
        }

        keywordSearchTimeOut = setTimeout(async () => {
            const foundValue = await fnKeywordSearch(value);
            if (!foundValue.length) {
                searchDropDownUl.classList.add('hidden');
                return;
            }
            const first = searchDropDownUl.firstElementChild;
            if (first) searchDropDownUl.replaceChildren(first);
            foundValue.forEach(item => {
                const searchDropDownLi = helperCloneAndUnHideNode(searchDropDownLiTem);
                searchDropDownLi.textContent = item;
                const style = addedMap.has(item) ? 'bg-indigo-700' : 'hover:bg-gray-700';
                searchDropDownLi.classList.add(style);
                searchDropDownUl.appendChild(searchDropDownLi);
                searchDropDownLi.addEventListener('click', () => {
                    if (addedMap.has(item)) {
                        addedMap.get(item).selectedDropDownLi.remove();
                        addedMap.delete(item);
                        if (addedMap.size === 0) {
                            selectedToggleBtn.classList.add('hidden');
                            selectedDropDownUl.classList.add('hidden');
                        }
                    } else {
                        selectedToggleBtn.classList.remove('hidden');
                        const selectedDropDownLi = helperCloneAndUnHideNode(selectedDropDownLiTem);
                        selectedDropDownLi.querySelector('span').textContent = item;
                        selectedDropDownLi.classList.remove('hover:bg-gray-700');
                        addedMap.set(item, { included: true, selectedDropDownLi: selectedDropDownLi });
                        selectedDropDownUl.appendChild(selectedDropDownLi);

                        const inclusionBtn = selectedDropDownLi.querySelector('.inclusion-btn');
                        inclusionBtn.addEventListener('click', (e) => {
                            e.preventDefault();
                            const included = addedMap.get(item).included;
                            inclusionBtn.textContent = included ? 'Excluded' : 'Included';
                            const styleList = ['bg-green-600', 'hover:bg-green-700', 'bg-red-600', 'hover:bg-red-700']
                            styleList.forEach(style => inclusionBtn.classList.toggle(style));
                            addedMap.get(item).included = !included;
                            let falseCount = 0;
                            for (const value of addedMap.values()) {
                                if (value === false)
                                    falseCount++;
                            }
                            unselectedCount.textContent = falseCount;
                        });
                        selectedDropDownLi.querySelector('.remove-btn').addEventListener('click', (e) => {
                            e.preventDefault();
                            addedMap.get(item).selectedDropDownLi.remove();
                            addedMap.delete(item);
                            selectedCount.textContent = addedMap.size;
                            if (addedMap.size === 0) {
                                selectedToggleBtn.classList.add('hidden');
                                selectedDropDownUl.classList.add('hidden');
                            }
                        });
                    }
                    selectedCount.textContent = addedMap.size;
                    searchDropDownLi.classList.toggle('bg-indigo-700');
                    searchDropDownLi.classList.toggle('hover:bg-gray-700');
                });
            });
            searchDropDownUl.classList.remove('hidden');
        }, 500);

    });
    searchInput.addEventListener('blur', () => {
        setTimeout(() => {
            if (document.activeElement !== searchDropDownUl)
                searchDropDownUl.classList.add('hidden');
        }, 150);
        clearTimeout(keywordSearchTimeOut);
    });
    searchDropDownUl.addEventListener('blur', () => {
        setTimeout(() => {
            searchDropDownUl.classList.add('hidden');
        }, 150);
        clearTimeout(keywordSearchTimeOut);
    });

    selectedToggleBtn.addEventListener('click', () => {
        selectedDropDownUl.classList.toggle('hidden');
    });
    selectedDropDownUl.addEventListener('blur', () => {
        setTimeout(() => {
            selectedDropDownUl.classList.add('hidden');
        }, 150);
    });
}

function initializeSortByOptions() {
    const sortByOptions = document.getElementById('sortByOptions');
    previousSortByButton = sortByOptions.querySelector('.upload-btn');
    sortByOptions.querySelector('.upload-btn').addEventListener('click',async (e) => {
        await sortByClick(e, SORT_BY.UPLOAD_DATE);
    });
    sortByOptions.querySelector('.year-btn').addEventListener('click', async (e) => {
        await sortByClick(e, SORT_BY.YEAR);
    });
    sortByOptions.querySelector('.length-btn').addEventListener('click', async (e) => {
        await sortByClick(e, SORT_BY.LENGTH);
    });
    sortByOptions.querySelector('.size-btn').addEventListener('click', async (e) => {
        await sortByClick(e, SORT_BY.SIZE);
    });
}

async function sortByClick(e, SORT_BY) {
    const target = e.target;
    currentSearchInfo.set(SEARCH_INFO.SORT_BY, SORT_BY);
    if (previousSortByButton) {
        previousSortByButton.classList.remove('bg-indigo-600');
        previousSortByButton.classList.add('bg-gray-800');
    }
    target.classList.add('bg-indigo-600');
    target.classList.remove('bg-gray-800');
    previousSortByButton = target;
    target.disabled = true;
    sendSearchRequestOnCurrentInfo().then(() => {
        target.disabled = false;
    });
}

async function sendSearchRequestOnCurrentInfo() {
    await sendSearchRequest(
        currentSearchInfo.get(SEARCH_INFO.SEARCH_TYPE),
        currentSearchInfo.get(SEARCH_INFO.PAGE),
        currentSearchInfo.get(SEARCH_INFO.SORT_BY),
        currentSearchInfo.get(SEARCH_INFO.SORT_ORDER),
        currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_FIELD),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST),
        currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY)
    );
}

document.getElementById('sortOrderButton').addEventListener('click', (e) => {
    if (currentSearchInfo.get(SEARCH_INFO.SORT_ORDER) === SORT_ORDERS.DESC) {
        e.target.innerText = 'Ascending';
        currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, SORT_ORDERS.ASC);
    } else {
        e.target.innerText = 'Descending';
        currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, SORT_ORDERS.DESC);
    }
    e.target.disabled = true;
    sendSearchRequestOnCurrentInfo().then(() => {
        e.target.disabled = false;
    });
});

let alertStatusTimer = null;
const alertStatus = document.getElementById('alert-status');
function setAlertStatus(boldStatus, normalText) {
    clearTimeout(alertStatusTimer);
    alertStatus.querySelector('#bold-status').innerText = boldStatus;
    alertStatus.querySelector('#normal-text').innerText = normalText;
    alertStatus.classList.remove('hidden');
    alertStatusTimer = setTimeout(() => {
        alertStatus.classList.add('hidden');
    }, 10000);
}

async function sendSearchRequest(searchType, page, sortBy, sortOrder,
                                 searchString = null,
                                 keywordField = null, keywordValueList = null,
                                 advanceRequestBody = null) {
    switch (searchType) {
        case SEARCH_TYPES.BASIC:
            return requestSearch(searchString, page, sortBy, sortOrder);
        case SEARCH_TYPES.KEYWORD:
            return requestKeywordSearch(keywordField, keywordValueList, page, sortBy, sortOrder);
        case SEARCH_TYPES.ADVANCE:
            return requestAdvanceSearch(advanceRequestBody, page, sortBy, sortOrder);
        default:
            console.log('default to search match all');
            return requestMatchAllSearch(page, sortBy, sortOrder);
    }
}

async function requestMatchAllSearch(page, sortBy, sortOrder) {
    const response = await fetch(getMatchAllSearchUrl(page, sortBy, sortOrder), {
        method: 'POST'
    });
    if (!response.ok) {
        setAlertStatus('Search Match All Failed', response.statusText);
        return;
    }
    const result = await response.json();
    displaySearchResults(result, null, sortBy, sortOrder);
}

async function requestSearch(searchString, page, sortBy, sortOrder) {
    const response = await fetch(getSearchUrl(SEARCH_TYPES.BASIC, page, sortBy, sortOrder, searchString), {
        method: 'POST'
    });
    if (!response.ok) {
        setAlertStatus('Search Failed', response.statusText);
        return;
    }
    const results = await response.json();
    displaySearchResults(results, SEARCH_TYPES.BASIC, sortBy, sortOrder, searchString);
}

async function requestKeywordSearch(field, valueList, page, sortBy, sortOrder) {
    const response = await fetch(getSearchUrl(SEARCH_TYPES.KEYWORD, page, sortBy, sortOrder, null, field, valueList), {
       method: 'POST'
    });
    if (!response.ok) {
        setAlertStatus('Search Keyword Failed', response.statusText);
        return;
    }
    const results = await response.json();
    displaySearchResults(results, SEARCH_TYPES.KEYWORD, sortBy, sortOrder, null, field, valueList);
}

async function requestAdvanceSearch(advanceRequestBody, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    const advanceSearchUrl = `/api/search/advance?${queryParams}`;
    const response = await fetch(advanceSearchUrl, {
        method: 'POST',
        contentType: 'application/json',
        body: JSON.stringify(advanceRequestBody)
    });
    if (!response.ok) {
        setAlertStatus('Search Advance Failed', response.statusText);
        return;
    }
    const results = await response.json();
    displaySearchResults(results, SEARCH_TYPES.ADVANCE, sortBy, sortOrder);
}

function getPageSearchUrl(searchType, page, sortBy, sortOrder,
                          searchString = null,
                          keywordField = null, keywordValueList = null) {
    const searchUrl = getSearchUrl(searchType, page, sortBy, sortOrder, searchString, keywordField, keywordValueList);
    return searchUrl.replace('api', 'page') + (searchType ? `&searchType=${searchType}` : '');
}

function getSearchUrl(searchType, page, sortBy, sortOrder,
                      searchString = null,
                      keywordField = null, keywordValueList = null) {
    switch (searchType) {
        case SEARCH_TYPES.BASIC:
            return getBasicSearchUrl(searchString, page, sortBy, sortOrder);
        case SEARCH_TYPES.KEYWORD:
            return getKeywordSearchUrl(keywordField, keywordValueList, page, sortBy, sortOrder);
        case SEARCH_TYPES.ADVANCE:
            return getAdvanceSearchUrl(page, sortBy, sortOrder);
        default:
            console.log('default to search match all');
            return getMatchAllSearchUrl(page, sortBy, sortOrder);
    }
}

function getMatchAllSearchUrl(page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `/api/search/match-all?${queryParams}`;
}

function getBasicSearchUrl(searchString, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        searchString: searchString,
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `/api/search?${queryParams}`;
}

function getKeywordSearchUrl(keywordField, valueList, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [keywordField]: valueList.join(','),
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `/api/${keywordField}?${queryParams}`;
}

function getAdvanceSearchUrl(page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `/api/search/advance?${queryParams}`;
}

displaySearchResults(null, SEARCH_TYPES.BASIC, SORT_BY.YEAR, SORT_ORDERS.DESC, "Genshin Impact");

function displaySearchResults(results, searchType, sortBy, sortOrder,
                              searchString,
                              keywordField, keywordValueList) {
    results = {
        "searchItems": [
            {
                "id": 1,
                "title": "Test Video Sample 1",
                "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
                "uploadDate": "2025-10-28",
                "authors" : [
                    "Jane Doe",
                    "John Doe",
                ],
                "mediaType": "VIDEO",
                "length": 657,
                "width": 1920,
                "height": 1080
            },
            {
                "id": 2,
                "title": "Test Video Sample 2",
                "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
                "uploadDate": "2025-10-28",
                "authors" : [
                    "Jane Doe",
                    "John Doe",
                ],
                "mediaType": "VIDEO",
                "length": 657,
                "width": 1920,
                "height": 1080
            },
            {
                "id": 3,
                "title": "Test Album",
                "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
                "uploadDate": "2025-10-28",
                "authors": [
                    "Jane Doe"
                ],
                "mediaType": "IMAGE",
                "length": 81,
                "width": 1080,
                "height": 1920
            }
        ],
        "page": 15,
        "pageSize": 20,
        "totalPages": 20,
        "total": 2
    }
    displayPagination(results.page, results.totalPages, searchType, sortBy, sortOrder,
        searchString, keywordField, keywordValueList);

    displaySearchItems(results.searchItems);
}

function formatTime(s) {
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
}

const videoContainer = document.getElementById('videoContainer-preview');

function displaySearchItems(searchItems) {
    const mainItemContainer = document.getElementById('main-item-container');
    if (searchItems.length === 0) {
        mainItemContainer.innerHTML = 'No results found.';
        return;
    }
    const searchItemsContainer = document.getElementById('search-item-container');
    const horizontalItemTem = searchItemsContainer.querySelector('.horizontal-item');
    const verticalItemTem = searchItemsContainer.querySelector('.vertical-item');

    searchItems.forEach(item => {
        const itemContainer = (item.width >= item.height) ? helperCloneAndUnHideNode(horizontalItemTem)
                                        : helperCloneAndUnHideNode(verticalItemTem);
        itemContainer.querySelector('.thumbnail-image').src = item.thumbnail;
        itemContainer.querySelector('.resolution-note').textContent = `${item.width}x${item.height}`;
        itemContainer.querySelector('.media-title').textContent = item.title;
        itemContainer.querySelector('.date-note').textContent = item.uploadDate;
        itemContainer.querySelector('.name-note').textContent = item.authors.join(", ");
        const itemLink = itemContainer.querySelector('.item-link');
        itemLink.href = `/api/media/content-page/${item.id}`;
        itemLink.addEventListener('click', async (e) => {
            e.preventDefault();
            await quickViewContentInOverlay(item.id, item.mediaType);
        });
        if (item.mediaType === 'VIDEO') {
            itemContainer.querySelector('.time-note').textContent = formatTime(item.length);
            const thumbnailContainer = itemContainer.querySelector('.thumbnail-container');
            const imageContainer = thumbnailContainer.querySelector('.image-container');
            thumbnailContainer.addEventListener('mouseenter', async () => {
                await requestVideoPreview(item.id, itemContainer);
            });
            thumbnailContainer.addEventListener('touchstart', async () => {
                await requestVideoPreview(item.id, itemContainer);
            });
            thumbnailContainer.addEventListener('mouseleave', async () => {
                videoContainer.classList.add('hidden');
                imageContainer.classList.remove('hidden');
            });
            thumbnailContainer.addEventListener('touchend', async () => {
                videoContainer.classList.add('hidden');
                imageContainer.classList.remove('hidden');
            });
        }
        else
            itemContainer.querySelector('.time-note').remove();

        mainItemContainer.appendChild(itemContainer);
    });
}

function createLoader(container) {
    // Create overlay wrapper
    const loader = document.createElement("div");
    loader.className = "custom-loader-overlay";
    loader.innerHTML = `
    <div class="custom-loader-spinner"></div>
  `;

    // Style: covers container but not entire page
    Object.assign(loader.style, {
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        background: "rgba(0,0,0,0.4)",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        zIndex: 999,
        borderRadius: "inherit",
    });

    // Ensure container can hold absolutely positioned children
    const computed = window.getComputedStyle(container);
    if (computed.position === "static") {
        container.style.position = "relative";
    }

    // Add the loader inside container
    container.appendChild(loader);

    // Spinner style
    const spinner = loader.querySelector(".custom-loader-spinner");
    Object.assign(spinner.style, {
        width: "48px",
        height: "48px",
        border: "6px solid #fff",
        borderTopColor: "transparent",
        borderRadius: "50%",
        animation: "spin 1s linear infinite",
    });

    // Add keyframes (only once)
    if (!document.getElementById("custom-loader-style")) {
        const style = document.createElement("style");
        style.id = "custom-loader-style";
        style.textContent = `
      @keyframes spin {
        to { transform: rotate(360deg); }
      }
    `;
        document.head.appendChild(style);
    }

    // Return the loader so caller can remove it later
    return loader;
}

let previousPreviewVideoId = null;

async function requestVideoPreview(videoId, itemNode) {
    const thumbnailContainer = itemNode.querySelector('.thumbnail-container');
    thumbnailContainer.querySelector('.image-container').classList.add('hidden');
    videoContainer.classList.remove('hidden');

    if (previousPreviewVideoId === videoId) {
        thumbnailContainer.querySelector('.image-container').classList.add('hidden');
        return;
    }
    previousPreviewVideoId = videoId;

    if (!thumbnailContainer.contains(videoContainer))
        thumbnailContainer.appendChild(videoContainer);

    const loader = createLoader(thumbnailContainer);
    let playlistUrl;
    try {
        // simulate a slow async call
        thumbnailContainer.appendChild(loader);
        // const response = await fetch(`/api/videos/preview/${videoId}`);
        // if (!response.ok) {
        //     setAlertStatus('Preview Failed', response.statusText);
        //     return;
        // }
        // playlistUrl = await response.text()
    } catch (err) {
        console.error("Error:", err);
    } finally {
        // remove loading indicator
        loader.remove();
    }

    playlistUrl = "p720/master.m3u8";

    const video = videoContainer.querySelector('video');

    video.pause();
    video.removeAttribute('src');
    video.load();

    setVideoUrl(videoContainer, playlistUrl);

    video.addEventListener('mouseenter', () => {
        video.play();
    });
    video.addEventListener('touchstart', () => {
        video.play();
    });

    video.addEventListener('mouseleave', () => {
        video.pause();
        video.currentTime = 0;
    });
    video.addEventListener('touchend', () => {
        video.pause();
        video.currentTime = 0;
    });
}

async function quickViewContentInOverlay(mediaId, mediaType) {
    const url = new URL(window.location.href);
    const urlParams = url.searchParams;
    urlParams.set('mediaId', mediaId);
    const newUrl = url.pathname + '?' + urlParams.toString() + url.hash;
    window.history.pushState({ path: newUrl }, '', newUrl);

    await displayContentPageForOverlay(mediaType, mediaId);
}

const mediaDocMap = new Map();

async function getAlbumPageContent() {
    // const response = await fetch('/page/frag/album);
    // if (!response.ok) {
    //     alert("Failed to fetch album layout");
    //     return;
    // }
    //
    // let albumDoc = await response.json();
    let albumDoc = {
        "style": "\n        /* 1. Base style for the image wrapper when it is in full screen */\n        .image-container-wrapper:fullscreen {\n            background-color: black;\n            display: flex;\n            justify-content: center;\n            align-items: center;\n        }\n\n        /* 2. Style the IMAGE element when the class is applied (toggled by JS) */\n        .is-fullscreen-target {\n            /* Crucial: Override any previous size constraints to allow the image to use 100% of the wrapper */\n            width: 100vw !important;\n            height: 100vh !important;\n            max-width: 100vw !important;\n            max-height: 100vh !important;\n\n            /* The key property to preserve aspect ratio */\n            object-fit: contain !important;\n\n            /* Center image visually */\n            margin: auto;\n        }\n\n        /* 3. Hide the button when in full screen (prevents accidental clicks) */\n        .image-container-wrapper:fullscreen .fullscreen-trigger {\n            display: none;\n        }\n    ",
        "html": "<main id=\"main-container\" class=\"max-w-4xl mx-auto px-4 py-8\">\n\n    <div class=\"px-4 py-4 sm:px-0\">\n\n        <h1 class=\"main-title text-4xl font-extrabold tracking-tight text-white\">\n            C\n        </h1>\n\n        <div class=\"flex items-center space-x-4 mt-2 text-lg\">\n\n            <div class=\"flex items-center\">\n                <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-indigo-400 inline-block align-text-bottom\">\n                    <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.58-7.499-1.632Z\" />\n                </svg>\n                <div class=\"authors-info-container\">\n                    <a href=\"#\" class=\"author-info hidden mr-3 text-gray-300 font-semibold hover:text-indigo-400 transition\">\n                        Jane Doe\n                    </a>\n                </div>\n            </div>\n\n            <span class=\"text-gray-600\">|</span>\n\n            <div class=\"flex items-center\">\n                <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-cyan-400 inline-block align-text-bottom\">\n                    <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M12 21a9.004 9.004 0 0 0 8.716-6.747M12 21a9.004 9.004 0 0 1-8.716-6.747M12 21v-4.5m0 0H3.328a9.004 9.004 0 0 1 7.85-4.887M12 16.5V9a2.25 2.25 0 0 0-2.25-2.25V4.5m2.25 12V19.5\" />\n                </svg>\n                <div class=\"universes-info-container\">\n                    <a href=\"#\" class=\"universe-info hidden mr-3 text-gray-300 font-semibold hover:text-cyan-400 transition\">\n                        Celestial / Cityscape\n                    </a>\n                </div>\n            </div>\n        </div>\n\n        <div class=\"border-t border-gray-700 mt-4\"></div>\n\n        <div class=\"flex items-center mt-3\">\n            <svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" fill=\"white\" class=\"w-5 h-5 mr-2 inline-block align-text-bottom\" stroke=\"white\">\n                <path d=\"M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V20h14v-3.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05C16.48 13.6 19 14.86 19 16.5V20h4v-3.5c0-2.33-4.67-3.5-7-3.5z\"/>\n            </svg>\n            <div class=\"characters-info-container\">\n                <a href=\"#\" class=\"character-info hidden mr-3 text-gray-300 font-semibold hover:text-cyan-400 transition\">\n                    John Doe\n                </a>\n            </div>\n        </div>\n\n        <div class=\"mt-4\">\n            <span class=\"text-sm font-bold uppercase text-gray-500 mr-3\">Tags:</span>\n            <div class=\"tags-info-container inline-flex flex-wrap gap-2\">\n                <a href=\"#\" class=\"tag-info hidden text-xs font-medium px-3 py-1 rounded-full bg-gray-700 text-gray-300 hover:bg-indigo-600 hover:text-white transition cursor-pointer\">\n                    #Astrophotography\n                </a>\n            </div>\n        </div>\n    </div>\n\n    <div class=\"border-t border-gray-700 mt-4\"></div>\n\n    <div class=\"px-4 py-8 sm:px-0\">\n        <div id=\"album-video-container\" class=\"grid grid-cols-1 gap-6 mb-5\">\n            <div class=\"aspect-[16/9] w-2/3 mx-auto relative hidden video-container-wrapper\">\n                <div class=\"temp-video-holder relative w-full h-full\">\n                    <video class=\"w-full h-full bg-black cursor-pointer\"></video>\n                    <div class=\"play-overlay absolute inset-0 flex items-center justify-center cursor-pointer\">\n                        <svg class=\"play-icon w-20 h-20 text-white opacity-90 transition-opacity duration-300 hover:opacity-100\" viewBox=\"0 0 24 24\" fill=\"currentColor\">\n                            <path d=\"M8 5v14l11-7z\"/>\n                        </svg>\n                    </div>\n                </div>\n                <div class=\"video-holder\"></div>\n            </div>\n        </div> <!-- End of album-video-container -->\n\n        <div id=\"album-image-container\" class=\"grid grid-cols-1 gap-6\">\n            <div id=\"image-wrapper-1\" class=\"image-container-wrapper hidden group relative overflow-hidden rounded-xl shadow-lg bg-gray-800 hover:shadow-2xl transition-shadow duration-300\">\n                <img class=\"image-to-fix object-cover w-full h-auto max-w-full transition-transform duration-500 group-hover:scale-105\"\n                        src=\"https://placehold.co/1920x1080\"\n                        alt=\"A beautiful landscape view\"\n                />\n\n                <button aria-label=\"View image in full screen\"\n                        data-target-id=\"image-wrapper-1\"\n                        class=\"fullscreen-trigger absolute top-4 right-4\n                              p-3 rounded-full bg-black/50 text-white\n                              opacity-0 group-hover:opacity-100\n                              transition-opacity duration-300\n                              hover:bg-black/70 hover:scale-110\">\n\n                    <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2.5\" stroke=\"currentColor\" class=\"w-6 h-6 pointer-events-none\">\n                        <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 16.5h-4.5m4.5 0v-4.5m0 4.5L15 15\" />\n                    </svg>\n                </button>\n            </div>\n        </div> <!-- End of album-image-container -->\n    </div>\n</main>",
        "script": [
            "album-page.js"
        ]
    };
    return albumDoc;
}

async function getVideoPageContent() {
    // const response = await fetch('/page/frag/video');
    // if (!response.ok) {
    //     alert("Failed to fetch video page layout");
    //     return;
    // }
    //
    // let videoDoc = await response.json();
    let videoDoc = {
        "style": "\n        input[type=\"range\"].seek-slider {\n            appearance: none;\n            width: 100%;\n            height: 5px;\n            border-radius: 4px;\n            background: linear-gradient(to right, #a855f7 0%, #a855f7 0%, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0.25) 100%);\n            outline: none;\n            cursor: pointer;\n            transition: background-size 0.1s linear;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb {\n            appearance: none;\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            cursor: pointer;\n            transition: transform 0.1s;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb:hover {\n            transform: scale(1.2);\n        }\n        input[type=\"range\"].seek-slider::-moz-range-thumb {\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            border: none;\n            cursor: pointer;\n        }\n    ",
        "html": "<main id=\"main-container\" class=\"order-1 md:order-2 flex-1 p-4 rounded-lg\">\n\n    <div class=\"mt-3 space-y-4 mb-5\">\n        <h3 class=\"main-title text-xl font-semibold\">Video title number one</h3>\n    </div>\n\n    <div>\n        <div data-player=\"videoPlayerContainer\" tabindex=\"0\" class=\"relative aspect-video bg-black flex items-center justify-center overflow-hidden rounded-lg select-none\">\n            <video class=\"video-node w-full h-full bg-black cursor-pointer\"></video>\n\n            <!-- Controls -->\n            <div class=\"video-controls absolute bottom-0 left-0 right-0 flex flex-col bg-gradient-to-t from-black/80 via-black/50 to-transparent p-4\n                 opacity-0 transition-opacity duration-300 pointer-events-none\">\n\n                <!-- Seek Slider -->\n                <div class=\"w-full mb-3\">\n                    <input type=\"range\" class=\"seek-slider\" min=\"0\" max=\"100\" value=\"0\" step=\"0.1\">\n                </div>\n\n                <!-- Control Buttons -->\n                <div class=\"flex items-center justify-between text-sm\">\n                    <div class=\"flex items-center space-x-3\">\n                        <button class=\"play-pause p-2 rounded-full bg-white/20 hover:bg-white/40 transition\">\n                            <i data-lucide=\"play\" class=\"w-5 h-5\"></i>\n                        </button>\n\n                        <button onclick=\"replay()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Replay 5s\">\n                            <i data-lucide=\"rotate-ccw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n\n                        <button onclick=\"skip()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Forward 5s\">\n                            <i data-lucide=\"rotate-cw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n                    </div>\n\n                    <div class=\"flex items-center space-x-3\">\n                        <span class=\"current-time text-gray-300\">0:00</span>\n                        <span class=\"text-gray-400\">/</span>\n                        <span class=\"total-time text-gray-300\">0:00</span>\n\n                        <!-- Volume -->\n                        <div class=\"flex items-center space-x-2\">\n                            <button class=\"mute-button p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Mute/Unmute\">\n                                <i data-lucide=\"volume-2\" class=\"w-5 h-5\"></i>\n                            </button>\n                            <input type=\"range\" min=\"0\" max=\"1\" step=\"0.01\" value=\"1\"\n                                   class=\"volume-slider w-24 h-[3px] accent-purple-500 cursor-pointer\">\n                        </div>\n\n                        <!-- Compact Speed & Resolution -->\n                        <div class=\"flex items-center space-x-3 relative\">\n                            <div class=\"relative\">\n                                <button class=\"speed-button text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">1x</button>\n                                <div class=\"speed-menu absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.5\">0.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.75\">0.75x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1\">1x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.25\">1.25x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.5\">1.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"2\">2x</button>\n                                </div>\n                            </div>\n\n                            <div class=\"relative\">\n                                <button class=\"res-button text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">720p</button>\n                                <div class=\"res-menu absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"1080p\">1080p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"720p\">720p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"480p\">480p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"360p\">360p</button>\n                                </div>\n                            </div>\n                        </div>\n\n                        <!-- Fullscreen -->\n                        <button class=\"fullscreen-button p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Fullscreen\">\n                            <i data-lucide=\"maximize\" class=\"w-5 h-5\"></i>\n                        </button>\n                    </div>\n                </div>\n            </div>\n        </div>\n    </div> <!-- End of video player insert div -->\n\n    <div class=\"flex items-center mt-5 pb-3 border-b border-gray-700\">\n        <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-cyan-400 inline-block align-text-bottom\">\n            <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M12 21a9.004 9.004 0 0 0 8.716-6.747M12 21a9.004 9.004 0 0 1-8.716-6.747M12 21v-4.5m0 0H3.328a9.004 9.004 0 0 1 7.85-4.887M12 16.5V9a2.25 2.25 0 0 0-2.25-2.25V4.5m2.25 12V19.5\" />\n        </svg>\n        <div class=\"universes-info-container\">\n            <a href=\"#\" class=\"universe-info hidden text-gray-300 font-semibold hover:text-cyan-400 transition\">\n                Celestial / Cityscape\n            </a>\n        </div>\n    </div>\n\n    <div class=\"flex items-center mt-3 mb-5 pb-3 border-b border-gray-700\">\n        <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-indigo-400 inline-block align-text-bottom\">\n            <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.58-7.499-1.632Z\" />\n        </svg>\n        <div class=\"authors-info-container \">\n            <a href=\"#\" class=\"author-info hidden mr-3 text-gray-300 font-semibold hover:text-indigo-400 transition\">\n                Jane Doe\n            </a>\n        </div>\n    </div>\n\n    <div class=\"mt-1\">\n        <div class=\"flex items-center mb-3\">\n            <svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" fill=\"white\" class=\"w-5 h-5 mr-2 inline-block align-text-bottom\" stroke=\"white\">\n                <path d=\"M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V20h14v-3.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05C16.48 13.6 19 14.86 19 16.5V20h4v-3.5c0-2.33-4.67-3.5-7-3.5z\"/>\n            </svg>\n            <div class=\"characters-info-container\">\n                <a href=\"#\" class=\"character-info hidden mr-3 text-gray-300 font-semibold hover:text-cyan-400 transition\">\n                    John Doe\n                </a>\n            </div>\n        </div>\n\n        <span class=\"text-sm font-bold uppercase text-gray-500 mr-3\">Tags:</span>\n        <div class=\"tags-info-container inline-flex flex-wrap gap-2\">\n            <a href=\"#\" class=\"tag-info hidden text-xs font-medium px-3 py-1 rounded-full bg-gray-700 text-gray-300 hover:bg-indigo-600 hover:text-white transition cursor-pointer\">\n                #Astrophotography\n            </a>\n        </div>\n    </div>\n\n</main>",
        "script": [
            "video-player.js",
            "video-page.js"
        ]
    };
    return videoDoc;
}

let previousQuickViewMediaId = null;

async function displayContentPageForOverlay(mediaType, mediaId) {
    const quickViewOverlay = document.getElementById('quickViewOverlay');
    const overlayWrapper = quickViewOverlay.querySelector('.overlayContent-inner-wrapper');

    if (mediaDocMap.has(mediaType)) {
        const { mod, node } = mediaDocMap.get(mediaType);
        overlayWrapper.innerHTML = '';
        overlayWrapper.appendChild(node);
        if (previousQuickViewMediaId !== null && previousQuickViewMediaId !== mediaId) {
            mod.initialize();
            previousQuickViewMediaId = mediaId;
        }
        openOverlay();
        return;
    }

    const pageDoc = (mediaType === 'VIDEO') ? await getVideoPageContent() : await getAlbumPageContent();

    if (!pageDoc.html) {
        alert(`Failed to fetch content ${mediaType} layout`);
        return;
    }

    if (pageDoc.style) {
        if (!document.getElementById(`style-${mediaType}`)) {
            const newStyleElement = document.createElement('style');
            newStyleElement.id = `style-${mediaType}`;
            newStyleElement.textContent = pageDoc.style;
            document.head.appendChild(newStyleElement);
        }
    }

    overlayWrapper.innerHTML = pageDoc.html;

    if (pageDoc.script) {
        pageDoc.script.forEach(script => {
            if (document.getElementById(script)) {
                // help prevent duplicate script tags
                document.getElementById(script).remove();
            }
            const scriptElement = document.createElement('script');
            scriptElement.src = script + '?v=' + Date.now();
            scriptElement.id = script;
            if (script.endsWith('.js')) {
                scriptElement.type = 'module';
                scriptElement.onload = async () => {
                    const mod = await import(scriptElement.src);
                    if (typeof mod.initialize === 'function') {
                        mediaDocMap.set(mediaType, { mod: mod, node: overlayWrapper.firstElementChild });
                    }
                };
            }
            document.body.appendChild(scriptElement);
        });
    }

    openOverlay();
}

const overlay = document.getElementById('quickViewOverlay');
function openOverlay() {
    overlay.classList.remove('hidden');
    overlay.classList.add('flex');
    document.body.style.overflow = 'hidden'; // disable background scroll
}

function closeOverlay(e) {
    // close only if clicked outside main content
    if (e.target === e.currentTarget || e.target === overlay) {
        overlay.classList.add('hidden');
        overlay.classList.remove('flex');
        document.body.style.overflow = ''; // re-enable background scroll
        const quickViewOverlay = document.getElementById('quickViewOverlay');
        quickViewOverlay.querySelector('.overlayContent-inner-wrapper').innerHTML = '';
    }
}

document.getElementById('overlayContent').addEventListener('click', (e) => {
    closeOverlay(e);
});

document.getElementById('closeOverlayBtn').addEventListener('click', () => {
    overlay.classList.add('hidden');
    overlay.classList.remove('flex');
    document.body.style.overflow = '';
});

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !overlay.classList.contains('hidden')) {
        overlay.classList.add('hidden');
        overlay.classList.remove('flex');
        document.body.style.overflow = '';
    }
});

function displayPagination(page, totalPages, searchType, sortBy, sortOrder,
                           searchString = null,
                           keywordField = null, keywordValueList = null) {
    const paginationTop = document.getElementById('pagination-node-top');
    const pageNodeBottom = document.getElementById('pagination-node-bottom');

    if (page === 0 && page === totalPages) {
        paginationTop.classList.add('hidden');
        paginationTop.innerHTML = '';
        pageNodeBottom.classList.add('hidden');
        pageNodeBottom.innerHTML = '';
        return;
    }

    const pageContainer = pageNodeBottom.querySelector('.page-container');
    const pageLinkNodeTem = pageContainer.querySelector('.page-link-node');
    const pageNumContainer = pageNodeBottom.querySelector('.page-num-container');

    const leftControl = pageNodeBottom.querySelector('.page-left-control');
    const rightControl = pageNodeBottom.querySelector('.page-right-control');
    const goFirstControl = leftControl.querySelector('.page-first-control');
    const goLastControl = rightControl.querySelector('.page-last-control');

    // --- prev / first ---
    if (page > 0) {
        const prevControl = leftControl.querySelector('.page-prev-control');
        prevControl.classList.remove('hidden');
        const prevIndex = page - 1;
        prevControl.href = getPageSearchUrl(searchType, prevIndex, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        prevControl.onclick = async (e) => await pageClickHandler(e, prevIndex);

        const prevControlDup = helperCloneAndUnHideNode(prevControl);
        prevControlDup.onclick = async (e) => await pageClickHandler(e, prevIndex);
        rightControl.appendChild(prevControlDup);

        goFirstControl.href = getPageSearchUrl(searchType, 0, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        goFirstControl.onclick = async (e) => await pageClickHandler(e, 0);
    } else {
        leftControl.querySelector('.page-prev-control').classList.add('hidden');
        goFirstControl.classList.add('hidden');
    }

    // --- next / last ---
    if (page < totalPages - 1) {
        const nextControl = leftControl.querySelector('.page-next-control');
        nextControl.classList.remove('hidden');
        const nextIndex = page + 1;
        nextControl.href = getPageSearchUrl(searchType, nextIndex, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        nextControl.onclick = async (e) => await pageClickHandler(e, nextIndex);

        const nextControlDup = helperCloneAndUnHideNode(nextControl);
        nextControlDup.onclick = async (e) => await pageClickHandler(e, nextIndex);
        rightControl.appendChild(nextControlDup);

        goLastControl.href = getPageSearchUrl(searchType, totalPages - 1, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        goLastControl.onclick = async (e) => await pageClickHandler(e, totalPages - 1);
    } else {
        leftControl.querySelector('.page-next-control').classList.add('hidden');
        goLastControl.classList.add('hidden');
    }

    // --- numbered pages ---
    const start = Math.max(page - 2, 0);
    const maxPageShow = start + 5;

    pageNumContainer.innerHTML = '';
    for (let i = start; i < totalPages; i++) {
        let currentPage = i;
        let reachedMax = false;

        if (currentPage === maxPageShow) {
            if (maxPageShow < totalPages - 1) {
                const threeDots = helperCloneAndUnHideNode(pageContainer.querySelector('.page-dots'));
                pageNumContainer.appendChild(threeDots);
                currentPage = totalPages - 1;
            }
            reachedMax = true;
        }

        const pageLinkNode = (currentPage !== page)
            ? helperCloneAndUnHideNode(pageLinkNodeTem)
            : helperCloneAndUnHideNode(pageContainer.querySelector('.page-selected-link-node'));

        pageLinkNode.innerText = currentPage + 1;
        pageLinkNode.href = getPageSearchUrl(searchType, currentPage, sortBy, sortOrder, searchString, keywordField, keywordValueList);

        if (currentPage === page) {
            pageLinkNode.onclick = (e) => e.preventDefault();
        } else {
            pageLinkNode.onclick = async (e) => await pageClickHandler(e, currentPage);
        }

        pageNumContainer.appendChild(pageLinkNode);

        if (reachedMax) break;
    }

    // --- clone bottom block to top and preserve click handlers ---
    paginationTop.innerHTML = '';
    const paginationNodeDup = helperCloneAndUnHideNode(pageNodeBottom.firstElementChild);
    paginationTop.appendChild(paginationNodeDup);

    // copy hrefs and onclicks from bottom to top anchors
    const bottomAnchors = pageNodeBottom.querySelectorAll('a');
    const topAnchors = paginationTop.querySelectorAll('a');
    topAnchors.forEach((topA, i) => {
        const bottomA = bottomAnchors[i];
        if (bottomA) {
            topA.href = bottomA.href;
            topA.onclick = bottomA.onclick;
        }
    });

    paginationTop.classList.remove('hidden');
    pageNodeBottom.classList.remove('hidden');
}

async function pageClickHandler(e, page) {
    e.preventDefault();
    currentSearchInfo.set(SEARCH_INFO.PAGE, page);
    e.target.disabled = true;
    sendSearchRequestOnCurrentInfo().then(() => {
        e.target.disabled = false;
    });
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}






















