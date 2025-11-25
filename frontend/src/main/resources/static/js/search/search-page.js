import {setVideoUrl} from "/static/js/set-video-url.js";
import {displayPagination} from "/static/js/pagination.js";
import {
    helperCloneAndUnHideNode,
    initializeNameBrowseOptions,
    setAlertStatus,
    validateSearchString
} from "/static/js/header.js";
import {quickViewContentInOverlay} from "/static/js/overlay.js";

const SORT_BY = Object.freeze({
    Upload: 'UPLOAD_DATE',
    Year: 'YEAR',
    Length: 'LENGTH',
    Size: 'SIZE'
});
const SORT_ORDERS = Object.freeze({
   Ascending: 'Asc',
   Descending: 'Desc',
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
    KEYWORD_FIELD: 'keyField',
    KEYWORD_VALUE_LIST: 'keys',
    KEYWORD_MATCH_ALL: 'matchAll',
    ADVANCE_REQUEST_BODY: 'advanceRequestBody',
    SEARCH_TYPE: 'searchType'
});
const currentSearchInfo = new Map();
const keywordSearchMap = new Map();

let previousSortByButton = null;

async function initialize() {
    const url = new URL(window.location.href);
    const urlParams = new URLSearchParams(window.location.search);
    currentSearchInfo.set(SEARCH_INFO.PAGE, urlParams.get(SEARCH_INFO.PAGE) || 0);
    currentSearchInfo.set(SEARCH_INFO.SORT_BY, urlParams.get(SEARCH_INFO.SORT_BY) || SORT_BY.Upload);
    currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, urlParams.get(SEARCH_INFO.SORT_ORDER) || SORT_ORDERS.Descending);
    currentSearchInfo.set(SEARCH_INFO.SEARCH_STRING, urlParams.get(SEARCH_INFO.SEARCH_STRING));

    let field = null;
    for (const value of Object.values(KEYWORDS)) {
        if (url.pathname.endsWith(value)) {
            field = value;
            break;
        }
    }
    currentSearchInfo.set(SEARCH_INFO.KEYWORD_FIELD, field);
    currentSearchInfo.set(SEARCH_INFO.KEYWORD_VALUE_LIST, urlParams.get(SEARCH_INFO.KEYWORD_VALUE_LIST));
    currentSearchInfo.set(SEARCH_INFO.KEYWORD_MATCH_ALL, urlParams.get(SEARCH_INFO.KEYWORD_MATCH_ALL) === 'true');

    const savedAdvanceRequest = JSON.parse(sessionStorage.getItem(SEARCH_INFO.ADVANCE_REQUEST_BODY) || "null");
    currentSearchInfo.set(SEARCH_INFO.ADVANCE_REQUEST_BODY, savedAdvanceRequest);

    if (currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING))
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.BASIC);
    else if (currentSearchInfo.get(SEARCH_INFO.KEYWORD_FIELD) && currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST))
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.KEYWORD);
    else if (currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY))
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.ADVANCE);
    else
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, urlParams.get(SEARCH_INFO.SEARCH_TYPE));

    console.log(currentSearchInfo);

    initializeSearchPageTitle();
    initializeBasicSearchArea();
    initializeSortByOptions();
    initializeSortOrderOptions();
    initializeAdvanceSearchArea();

    await sendSearchRequestOnCurrentInfo(false);
}

window.addEventListener('DOMContentLoaded',() => {
    initializeNameBrowseOptions();
    initialize();
});

function initializeSearchPageTitle() {
    const searchPageTitle = document.getElementById('header-page-title');
    searchPageTitle.href = '/page/search';
    searchPageTitle.addEventListener('click', async (e) => {
        e.preventDefault();
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, null);
        currentSearchInfo.set(SEARCH_INFO.PAGE, 0);
        currentSearchInfo.set(SEARCH_INFO.SORT_BY, SORT_BY.Upload);
        currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, SORT_ORDERS.Descending);
        e.target.disabled = true;
        await sendSearchRequestOnCurrentInfo().then(() => {
            e.target.disabled = false;
            initializeSortByOptions();
            initializeSortOrderOptions();
        });
    });
}

let searchIsSubmitting = false;
function initializeBasicSearchArea() {
    const basicSearchForm = document.querySelector('#search-form');
    const newBasicSearchForm = basicSearchForm.cloneNode(true);
    basicSearchForm.parentNode.replaceChild(newBasicSearchForm, basicSearchForm);

    if (currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING))
        newBasicSearchForm.querySelector('#search-input').value = currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING);
    newBasicSearchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        if (searchIsSubmitting)
            return;

        const searchString = validateSearchString(document.querySelector('#search-input').value);
        if (!searchString)
            return;
        currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.BASIC);
        currentSearchInfo.set(SEARCH_INFO.SEARCH_STRING, searchString);
        currentSearchInfo.set(SEARCH_INFO.PAGE, 0);

        searchIsSubmitting = true;
        sendSearchRequestOnCurrentInfo().then(() => {
            searchIsSubmitting = false;
        });
    });
}

let advanceIsSubmitting = false;
const advanceSearchForm = document.getElementById('advanceSearchForm');

function initializeAdvanceSearchArea() {
    const advanceSearchBtn = advanceSearchForm.querySelector('#advancedSearchBtn');
    advanceSearchBtn.addEventListener('click', () => {
        advanceSearchForm.querySelector('#advanceSearchSubmitBtn').classList.toggle('hidden');
        advanceSearchForm.querySelector('#advanceSearchContentContainer').classList.toggle('hidden');
        initializeKeywordSearchArea();
    });
    if (currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST) || currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY)) {
        advanceSearchBtn.click();
    }

    const advanceSortByOptions = document.getElementById('advanceSortByOptions');
    const sortByOptionTem = advanceSortByOptions.querySelector('.sort-by-option');
    Object.entries(SORT_BY).forEach(([key, value]) => {
        const sortByOption = helperCloneAndUnHideNode(sortByOptionTem);
        const input = sortByOption.querySelector('input');
        input.name = 'sortBy';
        input.value = value;
        sortByOption.querySelector('span').textContent = key;
        advanceSortByOptions.appendChild(sortByOption);
    });

    const advanceSortOrderOptions = document.getElementById('advanceSortOrderOptions');
    const sortOrderOptionTem = advanceSortOrderOptions.querySelector('.sort-order-option');
    Object.entries(SORT_ORDERS).forEach(([key, value]) => {
        const sortOrderOption = helperCloneAndUnHideNode(sortOrderOptionTem);
        const input = sortOrderOption.querySelector('input');
        input.name = 'sortOrder';
        input.value = value;
        sortOrderOption.querySelector('span').textContent = key;
        advanceSortOrderOptions.appendChild(sortOrderOption);
    });

    advanceSearchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        if (advanceIsSubmitting)
            return;
        const sortBy = advanceSearchForm.querySelector('input[name="sortBy"]:checked')?.value;
        const orderBy = advanceSearchForm.querySelector('input[name="sortOrder"]:checked')?.value;
        const yearFrom = advanceSearchForm.querySelector('.year-from-input');
        const yearTo = advanceSearchForm.querySelector('.year-to-input');
        const uploadFrom = advanceSearchForm.querySelector('.upload-from-input');
        const uploadTo = advanceSearchForm.querySelector('.upload-to-input');

        const savedAdvanceRequestBody = currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY);
        if (savedAdvanceRequestBody) {
            const rangeFields = savedAdvanceRequestBody.rangeFields;
            if (rangeFields.length) {
                const yearRangeField = rangeFields.find(field => field.field === 'year');
                const uploadRangeField = rangeFields.find(field => field.field === 'uploadDate');
                if (yearRangeField) {
                    yearFrom.value = yearRangeField.from;
                    yearTo.values = yearRangeField.to;
                }
                if (uploadRangeField) {
                    uploadFrom.value = uploadRangeField.from;
                    uploadTo.values = uploadRangeField.to;
                }
            }
        }

        const getRangeField = (field, from, to) => {
            if (from > to) {
                alert(`${field} from ${from} cannot be greater than to ${to}`);
                return;
            }
            return {
                field: field,
                from: from,
                to: to
            }
        }

        const rangeFields = [];
        if (yearFrom.value || yearTo.values) {
            rangeFields.push(getRangeField('year', yearFrom.value, yearTo.values));
        }
        if (uploadFrom.value || uploadTo.value) {
            rangeFields.push(getRangeField('uploadDate', uploadFrom.value, uploadTo.value));
        }

        const includeFields = [];
        const excludeFields = [];

        const getSearchField = (field, values, matchAll) => {
            return {
                field: field,
                values: values,
                matchAll: matchAll
            }
        }

        let keywordCount = 0;
        Object.keys(KEYWORDS).forEach(key => {
            const value = KEYWORDS[key];
            const keywordMap = keywordSearchMap.get(value).keywordMap;
            const matchAll = keywordSearchMap.get(value).matchAll;
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
                    includeFields.push(getSearchField(value, include, matchAll));
                if (exclude.length)
                    excludeFields.push(getSearchField(value, exclude, true)); // always exclude so match all is not needed
                keywordCount++;
            }
        });

        // only one keyword field entered and no range field entered
        // perform simple keyword search instead.
        if (keywordCount === 1 && includeFields.length === 1 && rangeFields.length === 0) {
            currentSearchInfo.set(SEARCH_INFO.SEARCH_TYPE, SEARCH_TYPES.KEYWORD);
            currentSearchInfo.set(SEARCH_INFO.PAGE, 0);
            currentSearchInfo.set(SEARCH_INFO.SORT_BY, sortBy);
            currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, orderBy);
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_FIELD, includeFields[0].field);
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_VALUE_LIST, includeFields[0].values);
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_MATCH_ALL, includeFields[0].matchAll);
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
        currentSearchInfo.set(SEARCH_INFO.PAGE, 0);
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
            setAlertStatus('Search Suggestion Failed', await response.text());
            return [];
        }
        return await response.json();
        //return ['Author 1', 'Author 2', 'Author 3'];
    };
    const makeSearchFn = (path) => async (value) => fnKeywordSearch(path, value);

    const keywordSearchContainer = document.getElementById('keywordSearchContainer');
    const keywordSearchTem = keywordSearchContainer.querySelector('.keyword-search');

    keywordSearchMap.set(KEYWORDS.AUTHOR, { keywordMap: new Map(), matchAll: false });
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.AUTHOR), keywordSearchMap.get(KEYWORDS.AUTHOR),
        KEYWORDS.AUTHOR, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    keywordSearchMap.set(KEYWORDS.CHARACTER, { keywordMap: new Map(), matchAll: false });
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.CHARACTER), keywordSearchMap.get(KEYWORDS.CHARACTER),
        KEYWORDS.CHARACTER, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    keywordSearchMap.set(KEYWORDS.UNIVERSE, { keywordMap: new Map(), matchAll: false });
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.UNIVERSE), keywordSearchMap.get(KEYWORDS.UNIVERSE),
        KEYWORDS.UNIVERSE, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
    keywordSearchMap.set(KEYWORDS.TAG, { keywordMap: new Map(), matchAll: false });
    addEventKeywordSearchArea(makeSearchFn(KEYWORDS.TAG), keywordSearchMap.get(KEYWORDS.TAG), KEYWORDS.TAG, keywordSearchContainer.appendChild(helperCloneAndUnHideNode(keywordSearchTem)));
}

function addEventKeywordSearchArea(fnKeywordSearch, keywordSearchMap, searchLabel, searchContainer) {
    searchContainer.querySelector('.search-label-text').textContent = searchLabel.charAt(0).toUpperCase() + searchLabel.slice(1);

    const addedMap = keywordSearchMap.keywordMap;

    const searchInput = searchContainer.querySelector('.search-input');
    searchInput.placeholder = 'Search ' + searchLabel;
    const searchDropDownUl = searchContainer.querySelector('.search-dropdown-ul');
    const searchDropDownLiTem = searchDropDownUl.querySelector('li');
    const selectedCount = searchContainer.querySelector('.selected-count');

    const checkboxMatchAll = searchContainer.querySelector('.checkbox-match-all');

    const selectedToggleBtn = searchContainer.querySelector('.selected-toggle-btn');
    const selectedDropDownUl = searchContainer.querySelector('.selected-dropdown-ul');
    const selectedDropDownLiTem = selectedDropDownUl.querySelector('li');
    const unselectedCount = searchContainer.querySelector('.unselected-count');

    const addSelectedItem = (item, clickAtFirst = false) => {
        const selectedDropDownLi = helperCloneAndUnHideNode(selectedDropDownLiTem);
        selectedDropDownLi.querySelector('span').textContent = item;
        selectedDropDownLi.classList.remove('hover:bg-gray-700');
        addedMap.set(item, { included: true, selectedDropDownLi: selectedDropDownLi });
        selectedDropDownUl.appendChild(selectedDropDownLi);

        const inclusionBtn = selectedDropDownLi.querySelector('.inclusion-btn');
        inclusionBtn.addEventListener('click', (e) => {
            e.preventDefault();
            addedMap.get(item).included = !addedMap.get(item).included;
            const included = addedMap.get(item).included;
            inclusionBtn.textContent = included ? 'Included' : 'Excluded';
            const includedClass = ['bg-green-600', 'hover:bg-green-700'];
            const excludedClass = ['bg-red-600', 'hover:bg-red-700'];
            if (included) {
                inclusionBtn.classList.remove(...excludedClass);
                inclusionBtn.classList.add(...includedClass);
            } else {
                inclusionBtn.classList.remove(...includedClass);
                inclusionBtn.classList.add(...excludedClass);
            }
            let falseCount = 0;
            for (const value of addedMap.values()) {
                if (value.included === false)
                    falseCount++;
            }
            unselectedCount.textContent = falseCount;
        });
        if (clickAtFirst) {
            inclusionBtn.click();
        }
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

    const addSearchedItem = (item) => {
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
                addSelectedItem(item);
            }
            selectedCount.textContent = addedMap.size;
            searchDropDownLi.classList.toggle('bg-indigo-700');
            searchDropDownLi.classList.toggle('hover:bg-gray-700');
        });
    }

    searchInput.addEventListener('input', (e) => {
        clearTimeout(keywordSearchTimeOut);
        const value = e.target.value.trim();
        if (value.length < 2) {
            searchDropDownUl.classList.add('hidden');
            return;
        }

        keywordSearchTimeOut = setTimeout(async () => {
            const first = searchDropDownUl.firstElementChild;
            if (first) searchDropDownUl.replaceChildren(first);

            const foundValue = await fnKeywordSearch(value);
            if (!foundValue.length) {
                const searchDropDownLi = helperCloneAndUnHideNode(searchDropDownLiTem);
                searchDropDownLi.textContent = 'No results found for ' + value;
                searchDropDownUl.appendChild(searchDropDownLi);
                searchDropDownUl.classList.remove('hidden');
                return;
            }
            foundValue.forEach(item => {
                addSearchedItem(item);
            });
            searchDropDownUl.classList.remove('hidden');
        }, 500);
    });

    keywordSearchMap.matchAll = checkboxMatchAll.checked;
    checkboxMatchAll.addEventListener('change', (e) => {
        keywordSearchMap.matchAll = e.target.checked;
    });

    const keywordItems = currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST);
    if (keywordItems && searchLabel === currentSearchInfo.get(SEARCH_INFO.KEYWORD_FIELD)) {
        const items = keywordItems.split(',');
        items.forEach(item => addSelectedItem(item));
        selectedCount.textContent = addedMap.size;
        selectedToggleBtn.classList.remove('hidden');
    }

    const advanceBodyRequest = currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY);
    if (advanceBodyRequest) {
        const includeFields = advanceBodyRequest.includeFields;
        for (const includeField of includeFields) {
            if (includeField.field === searchLabel) {
                includeField.values.forEach(item => addSelectedItem(item));
                selectedCount.textContent = addedMap.size;
                selectedToggleBtn.classList.remove('hidden');
            }
        }
        const excludeFields = advanceBodyRequest.excludeFields;
        for (const excludeField of excludeFields) {
            if (excludeField.field === searchLabel) {
                excludeField.values.forEach(item => addSelectedItem(item, true));
            }
        }
    }

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
    selectedToggleBtn.addEventListener('blur', () => {
        setTimeout(() => {
            if (selectedDropDownUl.contains(document.activeElement)) {
                selectedDropDownUl.focus();
                return;
            }
            selectedDropDownUl.classList.add('hidden');
        }, 150);
    });
    selectedDropDownUl.addEventListener('blur', () => {
        setTimeout(() => {
            if (selectedDropDownUl.contains(document.activeElement)) {
                selectedDropDownUl.focus();
                return;
            }
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
        await sortByClick(e, SORT_BY.Upload);
    });
    sortByOptions.querySelector('.year-btn').addEventListener('click', async (e) => {
        await sortByClick(e, SORT_BY.Year);
    });
    sortByOptions.querySelector('.length-btn').addEventListener('click', async (e) => {
        await sortByClick(e, SORT_BY.Length);
    });
    sortByOptions.querySelector('.size-btn').addEventListener('click', async (e) => {
        await sortByClick(e, SORT_BY.Size);
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
    sortOrderButton.innerText = currentSearchInfo.get(SEARCH_INFO.SORT_ORDER) === SORT_ORDERS.Descending ? 'Descending' : 'Ascending';

    sortOrderButton.addEventListener('click', (e) => {
        if (currentSearchInfo.get(SEARCH_INFO.SORT_ORDER) === SORT_ORDERS.Descending) {
            e.target.innerText = 'Ascending';
            currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, SORT_ORDERS.Ascending);
        } else {
            e.target.innerText = 'Descending';
            currentSearchInfo.set(SEARCH_INFO.SORT_ORDER, SORT_ORDERS.Descending);
        }
        e.target.disabled = true;
        sendSearchRequestOnCurrentInfo().then(() => {
            e.target.disabled = false;
        });
    });
}

async function sendSearchRequestOnCurrentInfo(updatePage = true) {
    if (currentSearchInfo.get(SEARCH_INFO.SEARCH_TYPE) !== SEARCH_TYPES.ADVANCE) {
        keywordSearchMap.forEach(map => {
            map.keywordMap.forEach(value => {
                value.selectedDropDownLi.querySelector('.remove-btn').click();
            });
            map.keywordMap.clear();
        });
        sessionStorage.removeItem(SEARCH_INFO.ADVANCE_REQUEST_BODY);
        advanceSearchForm.querySelector('#advanceSearchSubmitBtn').classList.add('hidden');
        advanceSearchForm.querySelector('#advanceSearchContentContainer').classList.add('hidden');
    }
    await sendSearchRequest(
        currentSearchInfo.get(SEARCH_INFO.SEARCH_TYPE),
        currentSearchInfo.get(SEARCH_INFO.PAGE),
        currentSearchInfo.get(SEARCH_INFO.SORT_BY),
        currentSearchInfo.get(SEARCH_INFO.SORT_ORDER),
        currentSearchInfo.get(SEARCH_INFO.SEARCH_STRING),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_FIELD),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_VALUE_LIST),
        currentSearchInfo.get(SEARCH_INFO.KEYWORD_MATCH_ALL),
        currentSearchInfo.get(SEARCH_INFO.ADVANCE_REQUEST_BODY),
        updatePage
    );
}

async function sendSearchRequest(searchType, page, sortBy, sortOrder,
                                 searchString = null,
                                 keywordField = null, keywordValueList = null, keywordMatchAll = null,
                                 advanceRequestBody = null,
                                 updatePage = true) {
    if (updatePage)
        updatePageUrl(searchType, page, sortBy, sortOrder, searchString, keywordField, keywordValueList, keywordMatchAll);
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
            currentSearchInfo.set(SEARCH_INFO.KEYWORD_MATCH_ALL, keywordMatchAll);
            return requestKeywordSearch(keywordField, keywordValueList, keywordMatchAll, page, sortBy, sortOrder);
        case SEARCH_TYPES.ADVANCE:
            currentSearchInfo.set(SEARCH_INFO.ADVANCE_REQUEST_BODY, advanceRequestBody);
            sessionStorage.setItem(SEARCH_INFO.ADVANCE_REQUEST_BODY, JSON.stringify(advanceRequestBody));
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
        setAlertStatus('Search Match All Failed', await response.text());
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
        setAlertStatus('Search Failed', await response.text());
        return;
    }
    const results = await response.json();
    displaySearchResults(results, SEARCH_TYPES.BASIC, sortBy, sortOrder, searchString);
}

async function requestKeywordSearch(field, valueList, keywordMatchAll, page, sortBy, sortOrder) {
    const response = await fetch(getSearchUrl(SEARCH_TYPES.KEYWORD, page, sortBy, sortOrder, null, field, valueList, keywordMatchAll), {
       method: 'POST'
    });
    if (!response.ok) {
        setAlertStatus('Search Keyword Failed', await response.text());
        return;
    }
    const results = await response.json();
    displaySearchResults(results, SEARCH_TYPES.KEYWORD, sortBy, sortOrder, null, field, valueList);
}

async function requestAdvanceSearch(advanceRequestBody, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [SEARCH_INFO.PAGE]: page,
        [SEARCH_INFO.SORT_BY]: sortBy,
        [SEARCH_INFO.SORT_ORDER]: sortOrder
    });
    const advanceSearchUrl = `/api/search/advance?${queryParams}`;
    const response = await fetch(advanceSearchUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(advanceRequestBody)
    });
    if (!response.ok) {
        setAlertStatus('Search Advance Failed', await response.text());
        return;
    }
    const results = await response.json();
    displaySearchResults(results, SEARCH_TYPES.ADVANCE, sortBy, sortOrder);
}

function updatePageUrl(searchType, page, sortBy, sortOrder,
                       searchString = null,
                       keywordField = null, keywordValueList = null, keywordMatchAll = null) {
    const url = new URL(window.location.href);
    const pageSearchUrl = getPageSearchUrl(page, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, keywordMatchAll);
    const newUrl = url.origin + pageSearchUrl;
    window.history.pushState({ path: newUrl }, '', newUrl);
}

function getPageSearchUrl(page, searchType, sortBy, sortOrder,
                          searchString = null,
                          keywordField = null, keywordValueList = null, keywordMatchAll = null) {
    const searchUrl = getSearchUrl(searchType, page, sortBy, sortOrder, searchString, keywordField, keywordValueList, keywordMatchAll);
    return searchUrl.replace('api', 'page') + (searchType ? `&searchType=${searchType}` : '');
}

function getSearchUrl(searchType, page, sortBy, sortOrder,
                      searchString = null,
                      keywordField = null, keywordValueList = null, keywordMatchAll = null) {
    switch (searchType) {
        case SEARCH_TYPES.BASIC:
            return getBasicSearchUrl(searchString, page, sortBy, sortOrder);
        case SEARCH_TYPES.KEYWORD:
            return getKeywordSearchUrl(keywordField, keywordValueList, keywordMatchAll, page, sortBy, sortOrder);
        case SEARCH_TYPES.ADVANCE:
            return getAdvanceSearchUrl(page, sortBy, sortOrder);
        default:
            console.log('default to search match all');
            return getMatchAllSearchUrl(page, sortBy, sortOrder);
    }
}

function getMatchAllSearchUrl(page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [SEARCH_INFO.PAGE]: page,
        [SEARCH_INFO.SORT_BY]: sortBy,
        [SEARCH_INFO.SORT_ORDER]: sortOrder
    });
    return `/api/search/match-all?${queryParams}`;
}

function getBasicSearchUrl(searchString, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [SEARCH_INFO.SEARCH_STRING]: searchString,
        [SEARCH_INFO.PAGE]: page,
        [SEARCH_INFO.SORT_BY]: sortBy,
        [SEARCH_INFO.SORT_ORDER]: sortOrder
    });
    return `/api/search?${queryParams}`;
}

function getKeywordSearchUrl(keywordField, keywordValueList, keywordMatchAll, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [SEARCH_INFO.KEYWORD_VALUE_LIST]: keywordValueList,
        [SEARCH_INFO.KEYWORD_MATCH_ALL]: keywordMatchAll,
        [SEARCH_INFO.PAGE]: page,
        [SEARCH_INFO.SORT_BY]: sortBy,
        [SEARCH_INFO.SORT_ORDER]: sortOrder
    });
    return `/api/search/${keywordField}?${queryParams}`;
}

function getAdvanceSearchUrl(page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [SEARCH_INFO.PAGE]: page,
        [SEARCH_INFO.SORT_BY]: sortBy,
        [SEARCH_INFO.SORT_ORDER]: sortOrder
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
        if (item.mediaType !== 'GROUPER') {
            itemLink.addEventListener('click', async (e) => {
                e.preventDefault();
                await quickViewContentInOverlay(item.id, item.mediaType);
            });
        }
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
            setAlertStatus('Preview Failed', await response.text());
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