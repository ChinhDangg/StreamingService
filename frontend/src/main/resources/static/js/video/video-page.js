import { setVideoUrl, setVideoResolution } from "/static/js/set-video-url.js";
import { displayContentInfo } from "/static/js/metadata-display.js";

const container = document.querySelector('[data-player="videoPlayerContainer"]');
container.dataset.player = 'videoPagePlayerContainer';

export async function initialize() {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    const videoId = urlParams.get('mediaId');

    await Promise.all([
        displayVideoInfo(videoId),
    ]);
}

initialize();

async function displayVideoInfo(videoId) {
    const response = await fetch(`/api/media/content/${videoId}`);
    if (!response.ok) {
        alert("Failed to fetch video info");
        return;
    }

    const videoInfo = await response.json();

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

    displayContentInfo(videoInfo);
    const originalRes = videoInfo.width > videoInfo.height ? videoInfo.height : videoInfo.width;
    await getVideoUrl(videoId, originalRes);
}

async function getVideoUrl(videoId, originalRes) {
    const baseUrl = `/api/videos/partial/${videoId}`;
    let defaultRes = 'p' + (originalRes > 720 ? 720 : originalRes);
    const response = await fetch(baseUrl + "/" + defaultRes);
    if (!response.ok) {
        alert("Failed to fetch video");
        return;
    }
    const videoUrl = await response.text();
    const container = document.querySelector('[data-player="videoPagePlayerContainer"]');
    setVideoUrl(container, videoUrl);
    setVideoResolution(container, baseUrl, originalRes, defaultRes);
}