let page = 0;
const sortBys = [
    'UPLOAD-DATE',
    'YEAR',
    'LENGTH',
    'SIZE'
];
const sortOrders = [
    'ASC',
    'DESC',
];
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

const SEARCH_TYPES = Object.freeze({
    BASIC: 'search',
    KEYWORD: 'keyword',
    ADVANCE: 'advance'
});

const searchHandlers = {
    [SEARCH_TYPES.BASIC]: async ({ searchString, page, sortBy, sortOrder }) =>
        requestSearch(searchString, page, sortBy, sortOrder),

    [SEARCH_TYPES.KEYWORD]: async ({ keywordField, keywordValueList, page, sortBy, sortOrder }) =>
        requestKeywordSearch(keywordField, keywordValueList, page, sortBy, sortOrder),

    [SEARCH_TYPES.ADVANCE]: async ({ requestBody, page, sortBy, sortOrder }) =>
        requestAdvanceSearch(requestBody, page, sortBy, sortOrder)
};

async function sendSearchRequest(options) {
    const { type } = options;
    const handler = searchHandlers[type];
    if (!handler) {
        throw new Error(`Unknown search type: ${type}`);
    }
    return handler(options);
}

async function requestSearch(searchString, page, sortBy, sortOrder) {

}

const KEYWORDS = Object.freeze({
    UNIVERSE: 'universes',
    CHARACTER: 'characters',
    TAG: 'tags'
});

async function requestKeywordSearch(field, valueList, page, sortBy, sortOrder) {

}

async function requestAdvanceSearch(searchRequestBody, page, sortBy, sortOrder) {

}

function getSearchUrl(searchString, page, sortBy, sortOrder) {
    const queryParams = new URLSearchParams({
        searchString: searchString,
        page: page,
        sortBy: sortBy,
        sortOrder: sortOrder
    });
    return `http://localhost/api/search?${queryParams}`
}

function displaySearchResults(results) {
    results = {
        "searchItems": [
            {
                "id": 1,
                "title": "Test Video Sample 1",
                "thumbnail": null,
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
                "width": null,
                "height": null
            }
        ],
        "page": 0,
        "pageSize": 20,
        "totalPages": 1,
        "total": 2
    }
}

function displaySearchItems() {

}

function displayPagination(page, totalPages, searchString, sortBy, sortOrder) {
    const pageContainer = document.getElementById('page-container');
    const pageLinkNodeTem = pageContainer.querySelector('.page-link-node');
    const pageNumContainer = document.getElementById('page-num-container');

    if (page === 0 && page === totalPages) {
        document.getElementById('pagination-node').classList.add('hidden');
        return;
    }

    pageNumContainer.innerHTML = '';
    for (let i = 0; i < totalPages; i++) {
        const pageLinkNode = helperCloneAndVisibleNode(pageLinkNodeTem);
        const page = i + 1;
        pageLinkNode.innerText = page;
        pageLinkNode.href = getSearchUrl(searchString, page, sortBy, sortOrder);

    }

}

function helperCloneAndVisibleNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}






















