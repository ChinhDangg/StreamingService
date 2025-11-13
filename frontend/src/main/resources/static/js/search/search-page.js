import {setVideoUrl} from "/static/js/set-video-url.js";
import { displayPagination } from "/static/js/pagination.js";

const SORT_BY = Object.freeze({
    UPLOAD: 'UPLOAD_DATE',
    YEAR: 'YEAR',
    LENGTH: 'LENGTH',
    SIZE: 'SIZE'
});
const SORT_ORDERS = Object.freeze({
   ASC: 'Asc',
   DESC: 'Desc',
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
    const urlParams = new URLSearchParams(window.location.search);
    currentSearchInfo.set(SEARCH_INFO.PAGE, urlParams.get(SEARCH_INFO.PAGE) || 0);
    currentSearchInfo.set(SEARCH_INFO.SORT_BY, urlParams.get(SEARCH_INFO.SORT_BY) || SORT_BY.UPLOAD);
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
    currentSearchInfo.set(SEARCH_INFO.KEYWORD_VALUE_LIST, values);
    currentSearchInfo.set(SEARCH_INFO.ADVANCE_REQUEST_BODY, null);
    if (currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING))
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.BASIC);
    else if (currentSearchInfo.get(SEARCH_INFO.KEYWORD_FIELD) && currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST))
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.KEYWORD);
    else if (currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY))
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.ADVANCE);
    else
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, urlParams.get(SEARCH_INFO.SEARCH_TYPE));

    initializeSearchPageTitle();
    initializeSortByOptions();
    initializeSortOrderOptions();
    initializeAdvanceSearchArea();

    await sendSearchRequestOnCurrentInfo(false);
}

window.addEventListener('DOMContentLoaded', async () => {
    await initialize();
});

function initializeSearchPageTitle() {
    const searchPageTitle = document.getElementById('header-page-title');
    searchPageTitle.href = '/page/search';
    searchPageTitle.addEventListener('click', async (e) => {
        e.preventDefault();
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, null);
        currentSearchInfo.set(SEARCH_INFO.PAGE, 0);
        currentSearchInfo.set(SEARCH_INFO.SORT_BY, SORT_BY.UPLOAD);
        currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, SORT_ORDERS.DESC);
        e.target.disabled = true;
        await sendSearchRequestOnCurrentInfo().then(() => {
            e.target.disabled = false;
            initializeSortByOptions();
            initializeSortOrderOptions();
        });
    });
}

let searchIsSubmitting = false;
document.querySelector('#search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    if (searchIsSubmitting)
        return;

    const searchString = validateSearchString(document.querySelector('#search-input').value);
    if (!searchString)
        return;
    currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.BASIC);
    currentSearchInfo.set(SEARCH_INFO.SEARCH_STRING, searchString);

    searchIsSubmitting = true;
    sendSearchRequestOnCurrentInfo().then(() => {
        searchIsSubmitting = false;
    });
});

function validateSearchString(searchString) {
    searchString = searchString.trim();
    if (searchString.length < 2) {
        setAlertStatus('Invalid search string', 'Search string must be at least 2 characters');
        return false;
    } else if (searchString.length > 200) {
        setAlertStatus('Invalid search string', 'Search string exceeded 200 characters');
        return false;
    }
    return searchString;
}

let advanceIsSubmitting = false;

function initializeAdvanceSearchArea() {
    const advanceSearchForm = document.getElementById('advanceSearchForm');
    advanceSearchForm.querySelector('#advancedSearchBtn').addEventListener('click', () => {
        advanceSearchForm.querySelector('#advanceSearchSubmitBtn').classList.toggle('hidden');
        advanceSearchForm.querySelector('#advanceSearchContentContainer').classList.toggle('hidden');
        initializeKeywordSearchArea();
    });

    advanceSearchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        if (advanceIsSubmitting)
            return;
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
            rangeFields.push(getRangeField(SORT_BY.UPLOAD, uploadFrom, uploadTo));
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

        let keywordCount = 0;
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
                keywordCount++;
            }
        });

        // only one keyword field entered and no range field entered
        // perform simple keyword search instead.
        if (keywordCount === 1 && includeFields.length === 1 && rangeFields.length === 0) {
            currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.KEYWORD);
            currentSearchInfo.set(SEARCH_INFO.SORT_BY, sortBy);
            currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, orderBy);
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_FIELD, includeFields[0].field);
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_VALUE_LIST, includeFields[0].values);
            advanceIsSubmitting = true;
            sendSearchRequestOnCurrentInfo().then(() => {
                advanceIsSubmitting = false;
            });
            return;
        }

        const advanceRequestBody = {
            includeFields: includeFields,
            excludeFields: excludeFields,
            rangeFields: rangeFields
        }

        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.ADVANCE);
        currentSearchInfo.set(SEARCH_INFO.SORT_BY, sortBy);
        currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, orderBy);
        currentSearchInfo.set(SEARCH_INFO.ADVANCE_REQUEST_BODY, advanceRequestBody);
        advanceIsSubmitting = true;
        sendSearchRequestOnCurrentInfo().then(() => {
            advanceIsSubmitting = false;
        });
    });
}

let keywordSearchAreaInitialized = false;
let keywordSearchTimeOut = null;

function initializeKeywordSearchArea() {
    if (keywordSearchAreaInitialized) return;
    keywordSearchAreaInitialized = true;
    const fnKeywordSearch = async (pathVariable, value) => {
        const response = await fetch(`/api/search/suggestion/${pathVariable}?s=${value}`);
        if (!response.ok) {
            setAlertStatus('Search Suggestion Failed', response.statusText);
            return [];
        }
        return await response.json();
        //return ['Author 1', 'Author 2', 'Author 3'];
    };
    const makeSearchFn = (path) => async (value) => fnKeywordSearch(path, value);

    const keywordSearchContainer = document.getElementById('keywordSearchContainer');
    const keywordSearchTem = keywordSearchContainer.querySelector('.keyword-search');

    const authorMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.AUTHOR), authorMap, KEYWORDS.AUTHOR, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    const characterMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.CHARACTER), characterMap, KEYWORDS.CHARACTER, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    const universeMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.UNIVERSE), universeMap, KEYWORDS.UNIVERSE, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    const tagMap = new Map();
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.TAG), tagMap, KEYWORDS.TAG, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));

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
    let foundKey= Object.keys(SORT_BY).find(key => SORT_BY[key] === currentSearchInfo.get(SEARCH_INFO.SORT_BY)).toLowerCase();
    if (previousSortByButton) {
        previousSortByButton.classList.remove('bg-indigo-600');
        previousSortByButton.classList.add('bg-gray-800');
    }
    previousSortByButton = sortByOptions.querySelector(`.${foundKey}-btn`);
    setSortByStyleToSelected(previousSortByButton);

    sortByOptions.querySelector('.upload-btn').addEventListener('click',async (e) => {
        await sortByClick(e, SORT_BY.UPLOAD);
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
    setSortByStyleToSelected(target);
    previousSortByButton = target;
    target.disabled = true;
    sendSearchRequestOnCurrentInfo().then(() => {
        target.disabled = false;
    });
}

function setSortByStyleToSelected(target) {
    target.classList.add('bg-indigo-600');
    target.classList.remove('bg-gray-800');
}

function initializeSortOrderOptions() {
    const sortOrderButton = document.getElementById('sortOrderButton');
    sortOrderButton.innerText = currentSearchInfo.get(SEARCH_INFO.SORT_ORDER) === SORT_ORDERS.DESC ? 'Descending' : 'Ascending';

    sortOrderButton.addEventListener('click', (e) => {
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
}

async function sendSearchRequestOnCurrentInfo(updatePage = true) {
    await sendSearchRequest(
        currentSearchInfo.get(SEARCH_INFO.SEARCH_TYPE),
        currentSearchInfo.get(SEARCH_INFO.PAGE),
        currentSearchInfo.get(SEARCH_INFO.SORT_BY),
        currentSearchInfo.get(SEARCH_INFO.SORT_ORDER),
        currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_FIELD),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST),
        currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY),
        updatePage
    );
}

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
                                 advanceRequestBody = null,
                                 updatePage = true) {
    if (updatePage)
        updatePageUrl(searchType, page, sortBy, sortOrder, searchString, keywordField, keywordValueList);
    currentSearchInfo.clear();
    currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, searchType);
    currentSearchInfo.set(SEARCH_INFO.PAGE, page);
    currentSearchInfo.set(SEARCH_INFO.SORT_BY, sortBy);
    currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, sortOrder);
    switch (searchType) {
        case SEARCH_TYPES.BASIC:
            currentSearchInfo.set(SEARCH_INFO.SEARCH_STRING, searchString);
            return requestSearch(searchString, page, sortBy, sortOrder);
        case SEARCH_TYPES.KEYWORD:
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_FIELD, keywordField);
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_VALUE_LIST, keywordValueList);
            return requestKeywordSearch(keywordField, keywordValueList, page, sortBy, sortOrder);
        case SEARCH_TYPES.ADVANCE:
            currentSearchInfo.set(SEARCH_INFO.ADVANCE_REQUEST_BODY, advanceRequestBody);
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

function updatePageUrl(searchType, page, sortBy, sortOrder,
                       searchString = null,
                       keywordField = null, keywordValueList = null) {
    const url = new URL(window.location.href);
    const pageSearchUrl = getPageSearchUrl(page, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList);
    const newUrl = url.origin + pageSearchUrl;
    window.history.pushState({ path: newUrl }, '', newUrl);
}

function getPageSearchUrl(page, searchType, sortBy, sortOrder,
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
        s: valueList,
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `/api/search/${keywordField}?${queryParams}`;
}

function getAdvanceSearchUrl(page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `/api/search/advance?${queryParams}`;
}

function displaySearchResults(results, searchType, sortBy, sortOrder,
                              searchString,
                              keywordField, keywordValueList) {
    // results = {
    //     "searchItems": [
    //         {
    //             "id": 1,
    //             "title": "Test Video Sample 1",
    //             "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
    //             "uploadDate": "2025-10-28",
    //             "authors" : [
    //                 "Jane Doe",
    //                 "John Doe",
    //             ],
    //             "mediaType": "VIDEO",
    //             "length": 657,
    //             "width": 1920,
    //             "height": 1080
    //         },
    //         {
    //             "id": 3,
    //             "title": "Test Album",
    //             "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
    //             "uploadDate": "2025-10-28",
    //             "authors": [
    //                 "Jane Doe"
    //             ],
    //             "mediaType": "IMAGE",
    //             "length": 81,
    //             "width": 1080,
    //             "height": 1920
    //         }
    //     ],
    //     "page": 15,
    //     "pageSize": 20,
    //     "totalPages": 20,
    //     "total": 2
    // }
    const getPageUrl = (page) => getPageSearchUrl(page, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList);
    displayPagination(results.page, results.totalPages, getPageUrl, pageClickHandler);

    displaySearchItems(results.searchItems);
}

async function pageClickHandler(e, page) {
    e.preventDefault();
    currentSearchInfo.set(SEARCH_INFO.PAGE, page);
    e.target.disabled = true;
    sendSearchRequestOnCurrentInfo().then(() => {
        e.target.disabled = false;
    });
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
    mainItemContainer.innerHTML = '';

    const searchItemsContainer = document.getElementById('search-item-container');
    const horizontalItemTem = searchItemsContainer.querySelector('.horizontal-item');
    const verticalItemTem = searchItemsContainer.querySelector('.vertical-item');

    searchItems.forEach(item => {
        const itemContainer = (item.width >= item.height) ? helperCloneAndUnHideNode(horizontalItemTem)
                                        : helperCloneAndUnHideNode(verticalItemTem);
        if (item.thumbnail)
            itemContainer.querySelector('.thumbnail-image').src = item.thumbnail;
        itemContainer.querySelector('.resolution-note').textContent = (item.width && item.height) ? `${item.width}x${item.height}` : '';
        itemContainer.querySelector('.media-title').textContent = item.title;
        itemContainer.querySelector('.date-note').textContent = item.uploadDate;
        itemContainer.querySelector('.name-note').textContent = (item.authors && item.authors.length) ? item.authors.join(", ") : 'Unknown';
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

async function requestVideoPreview(videoId, itemNode) {
    const thumbnailContainer = itemNode.querySelector('.thumbnail-container');
    thumbnailContainer.querySelector('.image-container').classList.add('hidden');
    videoContainer.classList.remove('hidden');

    if (thumbnailContainer.contains(videoContainer)) {
        thumbnailContainer.querySelector('.image-container').classList.add('hidden');
        return;
    } else {
        thumbnailContainer.appendChild(videoContainer);
    }

    const loader = createLoader(thumbnailContainer);
    let playlistUrl;
    try {
        thumbnailContainer.appendChild(loader);
        const response = await fetch(`/api/videos/preview/${videoId}`);
        if (!response.ok) {
            setAlertStatus('Preview Failed', response.statusText);
            return;
        }
        playlistUrl = await response.text()
    } catch (err) {
        console.error("Error:", err);
        setAlertStatus('Preview Failed', err.message);
        return;
    } finally {
        loader.remove(); // remove loading indicator
    }

    const video = videoContainer.querySelector('video');

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
    const response = await fetch('/page/frag/album');
    if (!response.ok) {
        alert("Failed to fetch album layout");
        return;
    }

    let albumDoc = await response.json();
    // let albumDoc = {
    //     "style": "",
    //     "html": "",
    //     "script": [
    //         "album-page.js"
    //     ]
    // };
    return albumDoc;
}

async function getVideoPageContent() {
    const response = await fetch('/page/frag/video');
    if (!response.ok) {
        alert("Failed to fetch video page layout");
        return;
    }

    let videoDoc = await response.json();
    // let videoDoc = {
    //     "style": "",
    //     "html": "",
    //     "script": [
    //         "video-player.js",
    //         "video-page.js"
    //     ]
    // };
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

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}