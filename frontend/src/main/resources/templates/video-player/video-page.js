import { setVideoUrl} from "./set-video-url.js";
import { displayContentInfo } from "./metadata-display.js";

const container = document.querySelector('[data-player="videoPlayerContainer"]');
container.dataset.player = 'videoPagePlayerContainer';

export async function initialize() {
    const queryString = window.location.search;
    const urlParams = new URLSearchParams(queryString);
    const videoId = urlParams.get('mediaId');

    await Promise.all([
        getVideoUrl(videoId),
        displayVideoInfo(videoId),
    ]);
}

await initialize();

async function displayVideoInfo(videoId) {
    // const response = await fetch(`/api/media/content/${videoId}`);
    // if (!response.ok) {
    //     alert("Failed to fetch album info");
    //     return;
    // }
    //
    // const videoInfo = await response.json();

    const videoInfo = {
        "childMediaIds": null,
        "id": 1,
        "title": "Test Album Sample 1",
        "thumbnail": null,
        "tags": [
            "Astrophotography",
            "LongExposure",
            "Urban"
        ],
        "characters": [
            "Jane Doe",
            "John Doe",
        ],
        "universes": [
            "One Piece"
        ],
        "authors": [
            "Jane Doe",
            "John Doe",
        ],
        "length": 657,
        "size": 400111222,
        "width": 1920,
        "height": 1080,
        "uploadDate": "2025-10-30",
        "year": null
    };

    displayContentInfo(videoInfo);
}

async function getVideoUrl(videoId) {
    // const response = await fetch(`/api/videos/partial/${videoId}/p720`);
    // if (!response.ok) {
    //     alert("Failed to fetch video");
    //     return;
    // }
    // const videoUrl = await response.text();
    // setVideoUrl(videoUrl);
    const container = document.querySelector('[data-player="videoPagePlayerContainer"]');
    setVideoUrl(container);
}