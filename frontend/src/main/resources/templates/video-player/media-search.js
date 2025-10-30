let page = 0;
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
let currentSortBy = 0;
let currentSortOrder = 0;

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

const KEYWORDS = Object.freeze({
    UNIVERSE: 'universes',
    CHARACTER: 'characters',
    TAG: 'tags'
});

const SEARCH_TYPES = Object.freeze({
    BASIC: 'search',
    KEYWORD: 'keyword',
    ADVANCE: 'advance'
});

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
            throw new Error('Unknown searchType');
    }
}

async function requestSearch(searchString, page, sortBy, sortOrder) {

}

async function requestKeywordSearch(field, valueList, page, sortBy, sortOrder) {

}

async function requestAdvanceSearch(advanceRequestBody, page, sortBy, sortOrder) {

}

function getSearchUrl(searchType, page, sortBy, sortOrder,
                      searchString = null,
                      keywordField = null, keywordValueList = null) {
    switch (searchType) {
        case SEARCH_TYPES.BASIC:
            return getBasicSearchUrl(searchString, page, sortBy, sortOrder);
        case SEARCH_TYPES.KEYWORD:
            return getKeywordSearchUrl(keywordField, keywordValueList, page, sortBy, sortOrder);
        default:
            return '/';
    }
}

function getBasicSearchUrl(searchString, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        searchString: searchString,
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `http://localhost/api/search?${queryParams}`
}

function getKeywordSearchUrl(keywordField, valueList, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        [keywordField]: valueList.join(','),
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `http://localhost/api/${keywordField}?${queryParams}`
}

displaySearchResults(null, SEARCH_TYPES.BASIC, SORT_BY.YEAR, SORT_ORDERS.DESC, "Genshin Impact");

function displaySearchResults(results, searchType, sortBy, sortOrder,
                              searchString,
                              keywordField, keywordValueList,
                              advanceRequestBody) {
    results = {
        "searchItems": [
            {
                "id": 1,
                "title": "Test Video Sample 1",
                "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
                "uploadDate": "2025-10-28",
                "length": 657,
                "width": 1920,
                "height": 1080
            },
            {
                "id": 2,
                "title": "Test Album",
                "thumbnail": "/thumbnail-cache/p480/2_p480.jpg",
                "uploadDate": "2025-10-28",
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
        searchString, keywordField, keywordValueList, advanceRequestBody);
}

function displaySearchItems() {

}

function displayPagination(page, totalPages, searchType, sortBy, sortOrder,
                           searchString = null,
                           keywordField = null, keywordValueList = null,
                           advanceRequestBody = null) {
    const pageContainer = document.getElementById('page-container');
    const pageLinkNodeTem = pageContainer.querySelector('.page-link-node');
    const pageNumContainer = document.getElementById('page-num-container');

    if (page === 0 && page === totalPages) {
        document.getElementById('pagination-node').classList.add('hidden');
        return;
    }

    const leftControl = document.getElementById('page-left-control');
    const rightControl = document.getElementById('page-right-control');
    const goFirstControl = leftControl.querySelector('.page-first-control');
    const goLastControl = rightControl.querySelector('.page-last-control');

    if (page > 0) {
        const prevControl = leftControl.querySelector('.page-prev-control');
        prevControl.classList.remove('hidden');
        const prevIndex = page - 1;
        prevControl.href = getSearchUrl(searchType, prevIndex, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        prevControl.addEventListener("click", async (e) => {
            await pageClickHandler(e, prevIndex, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
        });
        const prevControlDup = helperCloneAndUnHideNode(prevControl);
        prevControlDup.addEventListener("click", async (e) => {
            await pageClickHandler(e, prevIndex, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
        });
        rightControl.appendChild(prevControlDup);

        goFirstControl.href = getSearchUrl(searchType, 0, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        goFirstControl.addEventListener("click", async (e) => {
            await pageClickHandler(e, 0, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
        });
    } else {
        leftControl.querySelector('.page-prev-control').classList.add('hidden');
        goFirstControl.classList.add('hidden');
    }

    if (page < totalPages - 1) {
        const nextControl = leftControl.querySelector('.page-next-control');
        nextControl.classList.remove('hidden');
        const nextIndex = page + 1;
        nextControl.href = getSearchUrl(searchType, nextIndex, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        nextControl.addEventListener("click", async (e) => {
            await pageClickHandler(e, nextIndex, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
        });
        const nextControlDup = helperCloneAndUnHideNode(nextControl);
        nextControlDup.addEventListener("click", async (e) => {
            await pageClickHandler(e, nextIndex, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
        });
        rightControl.appendChild(nextControlDup);

        goLastControl.href = getSearchUrl(searchType, totalPages-1, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        goLastControl.addEventListener("click", async (e) => {
            await pageClickHandler(e, totalPages-1, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
        });
    } else {
        leftControl.querySelector('.page-next-control').classList.add('hidden');
        goLastControl.classList.add('hidden');
    }

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

        const pageLinkNode = (currentPage !== page) ? helperCloneAndUnHideNode(pageLinkNodeTem)
                            : helperCloneAndUnHideNode(pageContainer.querySelector('.page-selected-link-node'));
        pageLinkNode.innerText = currentPage + 1;
        pageLinkNode.href = getSearchUrl(searchType, currentPage, sortBy, sortOrder, searchString, keywordField, keywordValueList);
        if (currentPage === page) {
            pageLinkNode.addEventListener('click', (e) => {
                e.preventDefault();
            });
        } else {
            pageLinkNode.addEventListener('click', async (e) => {
                await pageClickHandler(e, currentPage, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
            });
        }
        pageNumContainer.appendChild(pageLinkNode);

        if (reachedMax)
            break;
    }
}

async function pageClickHandler(e, page,
                                searchType, sortBy, sortOrder,
                                searchString = null,
                                keywordField = null, keywordValueList = null,
                                advanceRequestBody = null) {
    e.preventDefault();
    const result = await sendSearchRequest(searchType, page, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
    displaySearchResults(result, searchType, sortBy, sortOrder, searchString, keywordField, keywordValueList, advanceRequestBody);
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}






















