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
                "id": 2,
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
        searchString, keywordField, keywordValueList, advanceRequestBody);

    displaySearchItems(results.searchItems);
}

function formatTime(s) {
    return `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;
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

const videoContainer = document.getElementById('videoContainer');

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
        itemContainer.querySelector('.resolution-note').textContent = `${item.width}x${item.height}`;
        itemContainer.querySelector('.media-title').textContent = item.title;
        itemContainer.querySelector('.date-note').textContent = item.uploadDate;
        itemContainer.querySelector('.name-note').textContent = item.authors.join(", ");

        mainItemContainer.appendChild(itemContainer);
    });
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

    const video = document.getElementById('video');

    video.pause();
    video.removeAttribute('src');
    video.load(); // force media element reset

    if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = playlistUrl + '?_=' + Date.now(); // cache-buster
    } else if (Hls.isSupported()) {
        const hls = new Hls({ startPosition: 0 });
        hls.loadSource(playlistUrl + '?_=' + Date.now());
        hls.attachMedia(video);
    }

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

function displayPagination(page, totalPages, searchType, sortBy, sortOrder,
                           searchString = null,
                           keywordField = null, keywordValueList = null,
                           advanceRequestBody = null) {
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

    paginationTop.innerHTML = '';
    const paginationNodeDup = helperCloneAndUnHideNode(pageNodeBottom.firstElementChild);
    paginationTop.appendChild(paginationNodeDup);
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






















