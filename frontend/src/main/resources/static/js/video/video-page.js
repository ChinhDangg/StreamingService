import {initializeHeader, setAlertStatus} from "/static/js/header.js";
import {
    setVideoResolution,
    requestVideoPartial
} from "/static/js/set-video-url.js";
import { displayContentInfo } from "/static/js/metadata-display.js";

const container = document.querySelector('[data-player="videoPlayerContainer"]');
container.dataset.player = 'videoPagePlayerContainer';

export async function initialize(videoId = null, videoInfo = null) {
    if (!videoId) {
        const queryString = window.location.search;
        const urlParams = new URLSearchParams(queryString);
        videoId = urlParams.get('mediaId');
    }

    if (!videoId) {
        alert("No videoId provided");
        return;
    }

    await Promise.all([
        displayVideoInfo(videoId, videoInfo),
    ]);
}

window.addEventListener('DOMContentLoaded', () => {
    initializeHeader();
    initialize();
});

async function displayVideoInfo(videoId, videoInfo = null) {
    if (!videoInfo) {
        const response = await fetch(`/api/media/content/${videoId}`);
        if (!response.ok) {
            alert("Failed to fetch video info");
            return;
        }
        videoInfo = await response.json();
    }

    // const videoInfo = {
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

    const videoMainContainer = document.getElementById('main-video-container');
    displayContentInfo(videoInfo, videoMainContainer);
    const originalRes = videoInfo.width > videoInfo.height ? videoInfo.height : videoInfo.width;
    await getVideoUrl(videoId, originalRes);
}

async function getVideoUrl(videoId, originalRes) {
    const baseUrl = `/api/videos/partial/${videoId}`;
    let defaultRes = 'p' + (originalRes > 480 ? 480 : originalRes);
    const fetchUrl = baseUrl + "/" + defaultRes;
    const container = document.querySelector('[data-player="videoPagePlayerContainer"]');
    const requestMessage = await requestVideoPartial(fetchUrl, container);
    if (requestMessage !== null) {
        setAlertStatus(requestMessage);
        return;
    }
    setVideoResolution(container, baseUrl, originalRes, defaultRes);
}