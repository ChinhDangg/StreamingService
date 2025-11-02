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

    const video = document.getElementById('video-preview');

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

async function quickViewContentInOverlay(mediaId, mediaType) {
    const pageDoc = (mediaType === 'VIDEO') ? await getVideoPageContent() : await getAlbumPageContent();

    // const queryString = window.location.search;
    // const urlParams = new URLSearchParams(queryString);
    // urlParams.forEach((value, key) => {
    //     console.log('key:', key, 'value:', value);
    // });

    const url = new URL(window.location.href);
    const urlParams = url.searchParams;
    urlParams.set('mediaId', mediaId);
    const newUrl = url.pathname + '?' + urlParams.toString() + url.hash;
    window.history.pushState({ path: newUrl }, '', newUrl);

    displayContentPageForOverlay(pageDoc, mediaType);
}

async function getAlbumPageContent() {
    // const response = await fetch('/page/album);
    // if (!response.ok) {
    //     alert("Failed to fetch album layout");
    //     return;
    // }
    //
    // let albumDoc = await response.json();

    let albumDoc = {
        "style": "\n        /* 1. Base style for the image wrapper when it is in full screen */\n        .image-container-wrapper:fullscreen {\n            background-color: black;\n            display: flex;\n            justify-content: center;\n            align-items: center;\n        }\n\n        /* 2. Style the IMAGE element when the class is applied (toggled by JS) */\n        .is-fullscreen-target {\n            /* Crucial: Override any previous size constraints to allow the image to use 100% of the wrapper */\n            width: 100vw !important;\n            height: 100vh !important;\n            max-width: 100vw !important;\n            max-height: 100vh !important;\n\n            /* The key property to preserve aspect ratio */\n            object-fit: contain !important;\n\n            /* Center image visually */\n            margin: auto;\n        }\n\n        /* 3. Hide the button when in full screen (prevents accidental clicks) */\n        .image-container-wrapper:fullscreen .fullscreen-trigger {\n            display: none;\n        }\n    ",
        "html": "<main id=\"main-container\" class=\"max-w-4xl mx-auto p-4 py-8\">\n\n    <div class=\"px-4 py-4 sm:px-0\">\n\n        <h1 class=\"main-title text-4xl font-extrabold tracking-tight text-white\">\n            Collection\n        </h1>\n\n        <div class=\"flex items-center space-x-4 mt-2 text-lg\">\n\n            <div class=\"authors-info-container flex items-center\">\n                <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-indigo-400 inline-block align-text-bottom\">\n                    <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.58-7.499-1.632Z\" />\n                </svg>\n                <a href=\"#\" class=\"author-info hidden mr-3 text-gray-300 font-semibold hover:text-indigo-400 transition\">\n                    Jane Doe\n                </a>\n            </div>\n\n            <span class=\"text-gray-600\">|</span>\n\n            <div class=\"universes-info-container flex items-center\">\n                <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-cyan-400 inline-block align-text-bottom\">\n                    <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M12 21a9.004 9.004 0 0 0 8.716-6.747M12 21a9.004 9.004 0 0 1-8.716-6.747M12 21v-4.5m0 0H3.328a9.004 9.004 0 0 1 7.85-4.887M12 16.5V9a2.25 2.25 0 0 0-2.25-2.25V4.5m2.25 12V19.5\" />\n                </svg>\n                <a href=\"#\" class=\"universe-info hidden text-gray-300 font-semibold hover:text-cyan-400 transition\">\n                    Celestial / Cityscape\n                </a>\n            </div>\n\n        </div>\n\n        <div class=\"border-t border-gray-700 mt-4\"></div>\n\n        <div class=\"mt-4\">\n            <span class=\"text-sm font-bold uppercase text-gray-500 mr-3\">Tags:</span>\n            <div class=\"tags-info-container inline-flex flex-wrap gap-2\">\n\n                <a href=\"#\" class=\"tag-info hidden text-xs font-medium px-3 py-1 rounded-full bg-gray-700 text-gray-300 hover:bg-indigo-600 hover:text-white transition cursor-pointer\">\n                    #Astrophotography\n                </a>\n\n            </div>\n        </div>\n    </div>\n\n    <div class=\"border-t border-gray-700 mt-4\"></div>\n\n    <div class=\"px-4 py-8 sm:px-0\">\n        <div id=\"album-video-container\" class=\"grid grid-cols-1 gap-6 mb-5\">\n            <div class=\"aspect-[16/9] w-2/3 mx-auto relative hidden video-container-wrapper\">\n                <div class=\"temp-video-holder relative w-full h-full\">\n                    <video class=\"w-full h-full bg-black cursor-pointer\"></video>\n                    <div class=\"play-overlay absolute inset-0 flex items-center justify-center cursor-pointer\">\n                        <svg class=\"play-icon w-20 h-20 text-white opacity-90 transition-opacity duration-300 hover:opacity-100\" viewBox=\"0 0 24 24\" fill=\"currentColor\">\n                            <path d=\"M8 5v14l11-7z\"/>\n                        </svg>\n                    </div>\n                </div>\n                <div class=\"video-holder\"></div>\n            </div>\n        </div>\n\n        <div id=\"album-image-container\" class=\"grid grid-cols-1 gap-6\">\n\n            <div id=\"image-wrapper-1\" class=\"image-container-wrapper hidden group relative overflow-hidden rounded-xl shadow-lg bg-gray-800 hover:shadow-2xl transition-shadow duration-300\">\n                <img class=\"image-to-fix object-cover w-full h-auto max-w-full transition-transform duration-500 group-hover:scale-105\"\n                        src=\"https://placehold.co/1920x1080\"\n                        alt=\"A beautiful landscape view\"\n                />\n\n                <button aria-label=\"View image in full screen\"\n                        data-target-id=\"image-wrapper-1\"\n                        class=\"fullscreen-trigger absolute top-4 right-4\n                              p-3 rounded-full bg-black/50 text-white\n                              opacity-0 group-hover:opacity-100\n                              transition-opacity duration-300\n                              hover:bg-black/70 hover:scale-110\">\n\n                    <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2.5\" stroke=\"currentColor\" class=\"w-6 h-6 pointer-events-none\">\n                        <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 16.5h-4.5m4.5 0v-4.5m0 4.5L15 15\" />\n                    </svg>\n                </button>\n            </div>\n\n        </div>\n    </div>\n</main>",
        "script": [
            "album-page.js"
        ]
    };

    return albumDoc;
}

async function getVideoPageContent() {
    // const response = await fetch('/page/video');
    // if (!response.ok) {
    //     alert("Failed to fetch video page layout");
    //     return;
    // }
    //
    // let videoDoc = await response.json();

    let videoDoc = {
        "style": "\n        input[type=\"range\"].seek-slider {\n            appearance: none;\n            width: 100%;\n            height: 5px;\n            border-radius: 4px;\n            background: linear-gradient(to right, #a855f7 0%, #a855f7 0%, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0.25) 100%);\n            outline: none;\n            cursor: pointer;\n            transition: background-size 0.1s linear;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb {\n            appearance: none;\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            cursor: pointer;\n            transition: transform 0.1s;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb:hover {\n            transform: scale(1.2);\n        }\n        input[type=\"range\"].seek-slider::-moz-range-thumb {\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            border: none;\n            cursor: pointer;\n        }\n    ",
        "html": "<main id=\"main-container\" class=\"order-1 md:order-2 flex-1 p-4 rounded-lg\">\n\n    <div class=\"mt-3 space-y-4 mb-5\">\n        <h3 class=\"main-title text-xl font-semibold\">Video title number one</h3>\n    </div>\n\n    <div>\n        <div id=\"videoContainer\" tabindex=\"0\" class=\"relative aspect-video bg-black flex items-center justify-center overflow-hidden rounded-lg select-none\">\n            <video id=\"video\" class=\"w-full h-full bg-black cursor-pointer\"></video>\n\n            <!-- Controls -->\n            <div id=\"controls\"\n                 class=\"absolute bottom-0 left-0 right-0 flex flex-col bg-gradient-to-t from-black/80 via-black/50 to-transparent p-4\n                 opacity-0 transition-opacity duration-300 pointer-events-none\">\n\n                <!-- Seek Slider -->\n                <div class=\"w-full mb-3\">\n                    <input type=\"range\" id=\"seekSlider\" class=\"seek-slider\" min=\"0\" max=\"100\" value=\"0\" step=\"0.1\">\n                </div>\n\n                <!-- Control Buttons -->\n                <div class=\"flex items-center justify-between text-sm\">\n                    <div class=\"flex items-center space-x-3\">\n                        <button id=\"playPause\" class=\"p-2 rounded-full bg-white/20 hover:bg-white/40 transition\">\n                            <i data-lucide=\"play\" class=\"w-5 h-5\"></i>\n                        </button>\n\n                        <button onclick=\"replay()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Replay 5s\">\n                            <i data-lucide=\"rotate-ccw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n\n                        <button onclick=\"skip()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Forward 5s\">\n                            <i data-lucide=\"rotate-cw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n                    </div>\n\n                    <div class=\"flex items-center space-x-3\">\n                        <span id=\"currentTime\" class=\"text-gray-300\">0:00</span>\n                        <span class=\"text-gray-400\">/</span>\n                        <span id=\"totalTime\" class=\"text-gray-300\">0:00</span>\n\n                        <!-- Volume -->\n                        <div class=\"flex items-center space-x-2\">\n                            <button id=\"muteBtn\" class=\"p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Mute/Unmute\">\n                                <i data-lucide=\"volume-2\" class=\"w-5 h-5\"></i>\n                            </button>\n                            <input id=\"volumeSlider\" type=\"range\" min=\"0\" max=\"1\" step=\"0.01\" value=\"1\"\n                                   class=\"w-24 h-[3px] accent-purple-500 cursor-pointer\">\n                        </div>\n\n                        <!-- Compact Speed & Resolution -->\n                        <div class=\"flex items-center space-x-3 relative\">\n                            <div class=\"relative\">\n                                <button id=\"speedButton\" class=\"text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">1x</button>\n                                <div id=\"speedMenu\" class=\"absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.5\">0.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.75\">0.75x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1\">1x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.25\">1.25x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.5\">1.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"2\">2x</button>\n                                </div>\n                            </div>\n\n                            <div class=\"relative\">\n                                <button id=\"resButton\" class=\"text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">720p</button>\n                                <div id=\"resMenu\" class=\"absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"1080p\">1080p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"720p\">720p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"480p\">480p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"360p\">360p</button>\n                                </div>\n                            </div>\n                        </div>\n\n                        <!-- Fullscreen -->\n                        <button id=\"fullscreenBtn\" class=\"p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Fullscreen\">\n                            <i data-lucide=\"maximize\" class=\"w-5 h-5\"></i>\n                        </button>\n                    </div>\n                </div>\n            </div>\n        </div>\n    </div> <!-- End of video fragment div -->\n\n    <div class=\"universes-info-container flex items-center mt-5 mb-5 pb-3 border-b border-gray-700\">\n        <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-cyan-400 inline-block align-text-bottom\">\n            <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M12 21a9.004 9.004 0 0 0 8.716-6.747M12 21a9.004 9.004 0 0 1-8.716-6.747M12 21v-4.5m0 0H3.328a9.004 9.004 0 0 1 7.85-4.887M12 16.5V9a2.25 2.25 0 0 0-2.25-2.25V4.5m2.25 12V19.5\" />\n        </svg>\n        <a href=\"#\" class=\"universe-info hidden text-gray-300 font-semibold hover:text-cyan-400 transition\">\n            Celestial / Cityscape\n        </a>\n    </div>\n\n    <div class=\"authors-info-container flex items-center mt-5 mb-5 pb-3 border-b border-gray-700\">\n        <svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"2\" stroke=\"currentColor\" class=\"w-5 h-5 mr-2 text-indigo-400 inline-block align-text-bottom\">\n            <path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.58-7.499-1.632Z\" />\n        </svg>\n        <a href=\"#\" class=\"author-info hidden mr-3 text-gray-300 font-semibold hover:text-indigo-400 transition\">\n            Jane Doe\n        </a>\n    </div>\n\n    <div class=\"mt-4\">\n        <span class=\"text-sm font-bold uppercase text-gray-500 mr-3\">Tags:</span>\n        <div class=\"tags-info-container inline-flex flex-wrap gap-2\">\n            <a href=\"#\" class=\"tag-info hidden text-xs font-medium px-3 py-1 rounded-full bg-gray-700 text-gray-300 hover:bg-indigo-600 hover:text-white transition cursor-pointer\">\n                #Astrophotography\n            </a>\n        </div>\n    </div>\n\n</main>",
        "script": [
            "video-player.js",
            "video-page.js"
        ]
    };

    return videoDoc;
}

function displayContentPageForOverlay(pageDoc, contentName) {
    if (!pageDoc.html) {
        alert(`Failed to fetch content ${contentName} layout`);
        return;
    }

    if (pageDoc.style) {
        const newStyleElement = document.createElement('style');
        newStyleElement.textContent = pageDoc.style;
        document.head.appendChild(newStyleElement);
    }

    const quickViewOverlay = document.getElementById('quickViewOverlay');
    const overlayWrapper = quickViewOverlay.querySelector('.overlayContent-inner-wrapper');
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






















