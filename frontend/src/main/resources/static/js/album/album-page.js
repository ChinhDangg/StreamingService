import { initializeHeader } from "/static/js/header.js";
import { setVideoUrl, setVideoResolution } from "/static/js/set-video-url.js";
import { displayContentInfo, helperCloneAndUnHideNode } from "/static/js/metadata-display.js";

let albumId = null;

export async function initialize(id = null, albumInfo = null) {
    if (id) albumId = id;
    else {
        const queryString = window.location.search;
        const urlParams = new URLSearchParams(queryString);
        albumId = urlParams.get('mediaId');
    }

    if (!albumId) {
        alert("No albumId provided");
        return;
    }

    await Promise.all([
        displayAlbumInfo(albumId, albumInfo),
        displayAlbumItems(albumId),
    ]);
}

window.addEventListener('DOMContentLoaded', () => {
    initializeHeader();
    initialize();
});

async function displayAlbumInfo(albumId, albumInfo = null) {
    if (!albumInfo) {
        const response = await fetch(`/api/media/content/${albumId}`);
        if (!response.ok) {
            alert("Failed to fetch album info");
            return;
        }
        albumInfo = await response.json();
    }

    // const albumInfo = {
    //     "childMediaIds": null,
    //     "id": 1,
    //     "title": "Test Album Sample 1",
    //     "thumbnail": null,
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
    //     "length": 657,
    //     "size": 400111222,
    //     "width": 1920,
    //     "height": 1080,
    //     "uploadDate": "2025-10-30",
    //     "year": null
    // };
    const albumMainContainer = document.getElementById('main-album-container');
    displayContentInfo(albumInfo, albumMainContainer);
}

const BATCH_SIZE = 5;
let currentBatch = 0;

const videoContainer = document.getElementById('album-video-container');
const imageContainer = document.getElementById('album-image-container');

const addImageItem = (item, imageContainer, imageWrapperTem) => {
    const imageId = `image-wrapper-${currentBatch}`;
    const imageWrapper = helperCloneAndUnHideNode(imageWrapperTem);
    imageWrapper.id = imageId;
    const imageElement = imageWrapper.querySelector('img');
    imageElement.src = item.url;
    imageElement.alt = `image-${currentBatch}`;
    const buttonElement = imageWrapper.querySelector('button');
    buttonElement.addEventListener('click', () => {
        enterFullScreen();
        showFullScreen(imageId);
    });
    imageWrapper.addEventListener('click', () => {
        enterFullScreen();
        showFullScreen(imageId);
    });
    imageContainer.appendChild(imageWrapper);
}

const addVideoItem = (item, videoContainer, videoWrapperTem) => {
    const videoWrapper = helperCloneAndUnHideNode(videoWrapperTem);
    videoWrapper.querySelector('.temp-video-holder').addEventListener('click', async () => {
        await requestVideo(item.url, videoWrapper);
    });
    videoContainer.appendChild(videoWrapper);
}

const addMediaItem = (start, end) => {
    const imageWrapperTem = imageContainer.querySelector('.image-container-wrapper');
    const videoWrapperTem = videoContainer.querySelector('.video-container-wrapper');
    for (let i = start; i < end; i++) {
        const item = albumResUrlMap.get(albumResolution)[i];
        if (item.type === 'IMAGE') {
            addImageItem(item, imageContainer, imageWrapperTem);
        } else if (item.type === 'VIDEO') {
            addVideoItem(item, videoContainer, videoWrapperTem);
        }
        currentBatch++;
        //console.log('currentBatch', currentBatch);
    }
}

const RESOLUTION = Object.freeze({
    original: 'Original',
    p1080: '1080p',
    p720: '720p',
    p480: '480p',
});

const albumResUrlMap = new Map();
const albumCheckResizedMap = new Map();
let albumResolution = Object.keys(RESOLUTION)[3];

const fetchCheckResized = async (albumId, start) => {
    if (albumResolution === Object.keys(RESOLUTION)[0]) return true;
    if (albumCheckResizedMap.get(albumResolution) >= start + BATCH_SIZE
        || albumCheckResizedMap.get(albumResolution) >= albumResUrlMap.get(albumResolution).length) {
        return true;
    }
    const response = await fetch(`/api/album/${albumId}/${albumResolution}/${start}/check-resized`, {
        method: 'POST',
    });
    if (!response.ok) {
        alert("Failed to fetch album items");
        return false;
    }
    const resizedCount = await response.text();
    albumCheckResizedMap.set(albumResolution, parseInt(resizedCount));
    return true;
};

function initializeResolutionSelector() {
    const resolutionSelector = document.getElementById('resolution-select');
    Object.keys(RESOLUTION).forEach(key => {
        const option = document.createElement('option');
        option.value = key;
        option.textContent = RESOLUTION[key];
        resolutionSelector.appendChild(option);
    });
    resolutionSelector.value = albumResolution;
    resolutionSelector.addEventListener('change', async () => {
        albumResolution = resolutionSelector.value;
        if (!albumResUrlMap.has(albumResolution)) {
            const albumItems = await fetchAlbumItemUrlsByResolution(albumId, albumResolution);
            if (!albumItems) return;
            albumResUrlMap.set(albumResolution, albumItems);
        }
        const imageItems = imageContainer.querySelectorAll('.image-container-wrapper');
        let isImageCount = 0;
        currentBatch = BATCH_SIZE;
        for (let i = 1; i < imageItems.length; i++) {
            if (i >= currentBatch) {
                imageItems[i].remove();
                continue;
            }
            while (isImageCount < albumResUrlMap.get(albumResolution).length) {
                if (albumResUrlMap.get(albumResolution)[isImageCount].type === 'IMAGE') {
                    imageItems[i].querySelector('img').src = albumResUrlMap.get(albumResolution)[isImageCount].url;
                    isImageCount++;
                    break;
                }
                isImageCount++;
            }
        }
        observer.observe(sentinel);
    });
}

async function fetchAlbumItemUrlsByResolution(albumId, resolution) {
    const response = await fetch(`/api/album/${albumId}/${resolution}`);
    if (!response.ok) {
        alert("Failed to fetch album items");
        return [];
    }
    return await response.json();
}

const sentinel = document.createElement("div");
let observer;

async function displayAlbumItems(albumId) {
    const albumItems = await fetchAlbumItemUrlsByResolution(albumId, albumResolution);
    if (!albumItems) return;
    albumResUrlMap.set(albumResolution, albumItems);

    // albumItems = [
    //     {
    //         "type": "VIDEO",
    //         "url": "/api/album/2/vid/0"
    //     },
    //     {
    //         "type": "IMAGE",
    //         "url": "https://placehold.co/1920x1080"
    //     },
    //     {
    //         "type": "IMAGE",
    //         "url": "https://placehold.co/1080x1920"
    //     }
    // ]

    for (const item of albumItems) {
        if (item.type === "IMAGE") {
            initializeResolutionSelector();
            break;
        }
    }

    const firstVideo = videoContainer.firstElementChild;
    if (firstVideo) videoContainer.replaceChildren(firstVideo);

    const firstImage = imageContainer.firstElementChild;
    if (firstImage) imageContainer.replaceChildren(firstImage);

    addMediaItem(0, Math.min(BATCH_SIZE, albumItems.length));

    if (currentBatch >= albumItems.length) {
        return;
    }

    document.getElementById('main-album-container').appendChild(sentinel);

    observer = new IntersectionObserver(async (entries) => {
        if (entries[0].isIntersecting) {
            //console.log('intersecting');
            if (currentBatch >= albumItems.length) {
                //console.log('no more items');
                observer.unobserve(sentinel);
                return;
            }
            const resized = await fetchCheckResized(albumId, currentBatch);
            if (!resized) {
                observer.unobserve(sentinel);
                return;
            }
            const start = currentBatch;
            const end = Math.min(currentBatch + BATCH_SIZE, albumItems.length);
            addMediaItem(start, end);
            if (currentBatch >= albumItems.length) {
                //console.log('looped and reached end')
                observer.unobserve(sentinel);
            }
        }
    }, { rootMargin: '1000px' }); // start prefetching before user reaches bottom
    observer.observe(sentinel);
}

let currentFullScreen = null;
const wrapperImageFullScreen = document.getElementById('image-wrapper-fullscreen');

function showFullScreen(imageWrapperId) {
    const imageToShow = document.getElementById(imageWrapperId);
    if (!imageToShow) {
        return;
    }
    currentFullScreen = imageWrapperId;

    const imageElement = wrapperImageFullScreen.querySelector('img');
    imageElement.src = imageToShow.querySelector('img').src;
}

function enterFullScreen() {
    const imageElement = wrapperImageFullScreen.querySelector('img');
    // 1. Add the class to enforce aspect ratio CSS
    imageElement.classList.add('is-fullscreen-target');

    wrapperImageFullScreen.classList.remove('hidden');

    // 2. Request full screen on the WRAPPER element
    if (wrapperImageFullScreen.requestFullscreen) {
        wrapperImageFullScreen.requestFullscreen();
    } else if (wrapperImageFullScreen.webkitRequestFullscreen) {
        wrapperImageFullScreen.webkitRequestFullscreen();
    } else if (wrapperImageFullScreen.msRequestFullscreen) {
        wrapperImageFullScreen.msRequestFullscreen();
    }

    wrapperImageFullScreen.addEventListener('touchstart', fullScreenTouchStart);
    wrapperImageFullScreen.addEventListener('touchend', fullScreenTouchEnd);
    wrapperImageFullScreen.addEventListener('click', fullScreenClick);

    // 3. Revert state when exiting full screen
    document.addEventListener('fullscreenchange', function handler() {
        if (!document.fullscreenElement) {
            // Remove the temporary class
            imageElement.classList.remove('is-fullscreen-target');

            document.removeEventListener('fullscreenchange', handler);

            wrapperImageFullScreen.classList.add('hidden');

            wrapperImageFullScreen.removeEventListener('touchstart', fullScreenTouchStart);
            wrapperImageFullScreen.removeEventListener('touchend', fullScreenTouchEnd);
            wrapperImageFullScreen.removeEventListener('click', fullScreenClick);
        }
    });
}

const fullScreenClick = async (e) => {
    const rect = wrapperImageFullScreen.getBoundingClientRect();
    const clickY = e.clientY - rect.top;
    const half = rect.height / 2;

    if (clickY < half) { // top half
        await showNextFullScreen();
    } else {
        showPreviousFullScreen();
    }
}

document.addEventListener("keydown", async (e) => {
    if (!document.fullscreenElement) return;

    if (e.key === "ArrowRight") {
        await showNextFullScreen();
    } else if (e.key === "ArrowLeft") {
        showPreviousFullScreen();
    }
});

let isShowingNextFullScreen = false;
async function showNextFullScreen() {
    if (isShowingNextFullScreen) return;
    isShowingNextFullScreen = true;
    const dashIndex = currentFullScreen.lastIndexOf('-')+1;
    const idStr = currentFullScreen.slice(dashIndex, currentFullScreen.length);
    const id = parseInt(idStr);
    const albumLength = albumResUrlMap.get(albumResolution).length;
    if (id >= albumLength) {
        isShowingNextFullScreen = false;
        return;
    }
    const nextId = currentFullScreen.slice(0, dashIndex) + (id + 1);
    showFullScreen(nextId);

    if (currentBatch >= albumLength) {
        isShowingNextFullScreen = false;
        return;
    }
    if (id < currentBatch-2) {
        isShowingNextFullScreen = false;
        return;
    }
    const resized = await fetchCheckResized(albumId, currentBatch);
    if (!resized) {
        isShowingNextFullScreen = false;
        return;
    }
    const start = currentBatch;
    const end = Math.min(currentBatch + BATCH_SIZE, albumLength);
    addMediaItem(start, end);
    isShowingNextFullScreen = false;
}

function showPreviousFullScreen() {
    const dashIndex = currentFullScreen.lastIndexOf('-')+1;
    const idStr = currentFullScreen.slice(dashIndex, currentFullScreen.length);
    const id = parseInt(idStr);
    if (id <= 1) return;
    const prevId = currentFullScreen.slice(0, dashIndex) + (id - 1);
    showFullScreen(prevId);
}

let touchStartX = 0;
let touchStartY = 0;
let touchEndX = 0;
let touchEndY = 0;

const SWIPE_MIN_DISTANCE = 50;    // min horizontal movement
const SWIPE_MAX_OFF_AXIS = 70;    // max vertical drift allowed

const fullScreenTouchStart = (e) => {
    if (!document.fullscreenElement) return;
    const t = e.changedTouches[0];
    touchStartX = t.screenX;
    touchStartY = t.screenY;
};

const fullScreenTouchEnd = async (e) => {
    if (!document.fullscreenElement) return;
    const t = e.changedTouches[0];
    touchEndX = t.screenX;
    touchEndY = t.screenY;

    const dx = touchEndX - touchStartX;
    const dy = touchEndY - touchStartY;

    // Ignore mostly vertical movements
    if (Math.abs(dy) > SWIPE_MAX_OFF_AXIS) return;

    // Horizontal swipe left
    if (dx < -SWIPE_MIN_DISTANCE) {
        await showNextFullScreen();
    }

    // Horizontal swipe right
    if (dx > SWIPE_MIN_DISTANCE) {
        showPreviousFullScreen();
    }
}

let previousVideoWrapper = null;
async function requestVideo(videoUrlRequest, videoWrapper) {
    if (!await getVideoPlayer())
        return;
    const videoDefaultRes = 'p480';
    const response = await fetch(videoUrlRequest + "/" + videoDefaultRes);
    if (!response.ok) {
        alert("Failed to fetch video");
        return;
    }
    const videoUrl = await response.text();
    if (previousVideoWrapper) {
        previousVideoWrapper.querySelector('.temp-video-holder').classList.remove('hidden');
    }

    previousVideoWrapper = videoWrapper;
    videoWrapper.querySelector('.temp-video-holder').classList.add('hidden');
    videoWrapper.querySelector('.video-holder').appendChild(videoPlayer);

    setVideoUrl(videoPlayer, videoUrl);
    setVideoResolution(videoPlayer, videoUrlRequest, 1080, videoDefaultRes);
}

let videoPlayer = null;
async function getVideoPlayer() {
    if (videoPlayer)
        return true;

    const response = await fetch('/page/frag/video-player');
    if (!response.ok) {
        alert("Failed to fetch video player");
        return;
    }
    let videoPlayerDoc = await response.json();

    // let videoPlayerDoc = {
    //     "style": "\n        input[type=\"range\"].seek-slider {\n            appearance: none;\n            width: 100%;\n            height: 5px;\n            border-radius: 4px;\n            background: linear-gradient(to right, #a855f7 0%, #a855f7 0%, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0.25) 100%);\n            outline: none;\n            cursor: pointer;\n            transition: background-size 0.1s linear;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb {\n            appearance: none;\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            cursor: pointer;\n            transition: transform 0.1s;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb:hover {\n            transform: scale(1.2);\n        }\n        input[type=\"range\"].seek-slider::-moz-range-thumb {\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            border: none;\n            cursor: pointer;\n        }\n    ",
    //     "html": "\n        <div data-player=\"videoPlayerContainer\" tabindex=\"0\" class=\"relative aspect-video bg-black flex items-center justify-center overflow-hidden rounded-lg select-none\">\n            <video class=\"video-node w-full h-full bg-black cursor-pointer\"></video>\n\n            <!-- Controls -->\n            <div class=\"video-controls absolute bottom-0 left-0 right-0 flex flex-col bg-gradient-to-t from-black/80 via-black/50 to-transparent p-4\n                 opacity-0 transition-opacity duration-300 pointer-events-none\">\n\n                <!-- Seek Slider -->\n                <div class=\"w-full mb-3\">\n                    <input type=\"range\" class=\"seek-slider\" min=\"0\" max=\"100\" value=\"0\" step=\"0.1\">\n                </div>\n\n                <!-- Control Buttons -->\n                <div class=\"flex items-center justify-between text-sm\">\n                    <div class=\"flex items-center space-x-3\">\n                        <button class=\"play-pause p-2 rounded-full bg-white/20 hover:bg-white/40 transition\">\n                            <i data-lucide=\"play\" class=\"w-5 h-5\"></i>\n                        </button>\n\n                        <button onclick=\"replay()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Replay 5s\">\n                            <i data-lucide=\"rotate-ccw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n\n                        <button onclick=\"skip()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Forward 5s\">\n                            <i data-lucide=\"rotate-cw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n                    </div>\n\n                    <div class=\"flex items-center space-x-3\">\n                        <span class=\"current-time text-gray-300\">0:00</span>\n                        <span class=\"text-gray-400\">/</span>\n                        <span class=\"total-time text-gray-300\">0:00</span>\n\n                        <!-- Volume -->\n                        <div class=\"flex items-center space-x-2\">\n                            <button class=\"mute-button p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Mute/Unmute\">\n                                <i data-lucide=\"volume-2\" class=\"w-5 h-5\"></i>\n                            </button>\n                            <input type=\"range\" min=\"0\" max=\"1\" step=\"0.01\" value=\"1\"\n                                   class=\"volume-slider w-24 h-[3px] accent-purple-500 cursor-pointer\">\n                        </div>\n\n                        <!-- Compact Speed & Resolution -->\n                        <div class=\"flex items-center space-x-3 relative\">\n                            <div class=\"relative\">\n                                <button class=\"speed-button text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">1x</button>\n                                <div class=\"speed-menu absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.5\">0.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.75\">0.75x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1\">1x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.25\">1.25x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.5\">1.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"2\">2x</button>\n                                </div>\n                            </div>\n\n                            <div class=\"relative\">\n                                <button class=\"res-button text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">720p</button>\n                                <div class=\"res-menu absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"1080p\">1080p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"720p\">720p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"480p\">480p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"360p\">360p</button>\n                                </div>\n                            </div>\n                        </div>\n\n                        <!-- Fullscreen -->\n                        <button class=\"fullscreen-button p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Fullscreen\">\n                            <i data-lucide=\"maximize\" class=\"w-5 h-5\"></i>\n                        </button>\n                    </div>\n                </div>\n            </div>\n        </div>\n    ",
    //     "script": [
    //         "/static/js/video-player/video-player.js"
    //     ]
    // };

    if (!videoPlayerDoc.html) {
        alert("Failed to fetch video player");
        return false;
    }

    if (videoPlayerDoc.style) {
        if (!document.getElementById('video-player-style')) {
            const newStyleElement = document.createElement('style');
            newStyleElement.id = 'video-player-style';
            newStyleElement.textContent = videoPlayerDoc.style;
            document.head.appendChild(newStyleElement);
        }
    }

    let videoPlayerContainer = document.createElement('div');
    videoPlayerContainer.innerHTML = videoPlayerDoc.html;
    document.body.appendChild(videoPlayerContainer);

    let videoScript = null;
    if (videoPlayerDoc.script) {
        videoPlayerDoc.script.forEach(scriptUrl => {
            if (document.getElementById(scriptUrl)) {
                document.getElementById(scriptUrl).remove();
            }
            const scriptElement = document.createElement('script');
            scriptElement.src = scriptUrl;
            scriptElement.id = scriptUrl;
            if (scriptUrl.endsWith('.js')) {
                scriptElement.type = 'module';
                videoScript = scriptElement;
            }
            document.body.appendChild(scriptElement);
        });
    }

    videoPlayer = videoPlayerContainer.firstElementChild;
    videoScript.onload = () => {
        videoPlayer.dataset.player = 'albumVideoPlayerContainer';
        videoPlayerContainer.remove();
    };

    return true;
}