

async function displayAlbumInfo(albumId) {
    // const response = await fetch(`/api/media/content/${albumId}`);
    // if (!response.ok) {
    //     alert("Failed to fetch album info");
    //     return;
    // }
    //
    // const albumInfo = await response.json();

    const albumInfo = {
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

    const albumContainer = document.getElementById('album-main-container');
    albumContainer.querySelector('.album-title').textContent = albumInfo.title;

    if (albumInfo.authors) {
        const authorContainer = albumContainer.querySelector('.authors-info-container');
        const authorNodeTem = authorContainer.querySelector('.author-info');
        albumInfo.authors.forEach(author => {
            const authorNode = helperCloneAndUnHideNode(authorNodeTem);
            authorNode.href = '/search-page/author/' + author;
            authorNode.textContent = author;
            authorContainer.appendChild(authorNode);
        });
    }

    if (albumInfo.universes) {
        const universeContainer = albumContainer.querySelector('.universes-info-container');
        const universeNodeTem = universeContainer.querySelector('.universe-info');
        albumInfo.universes.forEach(universe => {
            const universeNode = helperCloneAndUnHideNode(universeNodeTem);
            universeNode.href = '/search-page/universe/' + universe;
            universeNode.textContent = universe;
            universeContainer.appendChild(universeNode);
        });
    }

    if (albumInfo.tags) {
        const tagContainer = albumContainer.querySelector('.tags-info-container');
        const tagNodeTem = tagContainer.querySelector('.tag-info');
        albumInfo.tags.forEach(tag => {
            const tagNode = helperCloneAndUnHideNode(tagNodeTem);
            tagNode.href = '/search-page/tag/' + tag;
            tagNode.textContent = tag;
            tagContainer.appendChild(tagNode);
        });
    }
}

await displayAlbumInfo(1);
await displayAlbumItems(1);

async function displayAlbumItems(albumId) {
    // const response = await fetch(`/api/album/${albumId}/p1080`);
    // if (!response.ok) {
    //     alert("Failed to fetch album items");
    //     return;
    // }
    // const albumItems = await response.json();

    const albumItems = [
        {
            "type": "VIDEO",
            "url": "/api/album/2/vid/0"
        },
        {
            "type": "VIDEO",
            "url": "/api/album/2/vid/0"
        },
        {
            "type": "IMAGE",
            "url": "https://placehold.co/1920x1080"
        },
        {
            "type": "IMAGE",
            "url": "https://placehold.co/1920x1080"
        },
        {
            "type": "IMAGE",
            "url": "https://placehold.co/1080x1920"
        },
    ]

    const videoContainer = document.getElementById('album-video-container');
    const imageContainer = document.getElementById('album-image-container');

    const imageWrapperTem = imageContainer.querySelector('.image-container-wrapper');
    const videoWrapperTem = videoContainer.querySelector('.video-container-wrapper');

    let count = 0;
    albumItems.forEach(item => {
        if (item.type === 'IMAGE') {
            const imageId = `image-wrapper-${count}`;
            const imageWrapper = helperCloneAndUnHideNode(imageWrapperTem);
            imageWrapper.id = imageId;
            const imageElement = imageWrapper.querySelector('img');
            imageElement.src = item.url;
            imageElement.alt = `image-${count}`;
            const buttonElement = imageWrapper.querySelector('button');
            buttonElement.setAttribute('data-target-id', imageId);
            buttonElement.addEventListener('click', (e) => clickFullScreen(e));
            imageContainer.appendChild(imageWrapper);
        } else if (item.type === 'VIDEO') {
            const videoWrapper = helperCloneAndUnHideNode(videoWrapperTem);
            videoWrapper.querySelector('.temp-video-holder').addEventListener('click', async () => {
                console.log('clicked');
                await requestVideo(item.url, videoWrapper);
            });
            videoContainer.appendChild(videoWrapper);
        }
        count++;
    });
}

function clickFullScreen(e) {
    e.stopPropagation();

    const targetId = e.currentTarget.getAttribute('data-target-id');
    const wrapperElement = document.getElementById(targetId);
    const imageElement = wrapperElement ? wrapperElement.querySelector('img') : null;

    if (wrapperElement && imageElement) {

        // 1. Add the class to enforce aspect ratio CSS
        imageElement.classList.add('is-fullscreen-target');

        // 2. Request full screen on the WRAPPER element
        enterFullScreen(wrapperElement);

        // 3. Revert state when exiting full screen
        document.addEventListener('fullscreenchange', function handler() {
            if (!document.fullscreenElement) {
                // Remove the temporary class
                imageElement.classList.remove('is-fullscreen-target');

                document.removeEventListener('fullscreenchange', handler);
            }
        });
    }
}

function enterFullScreen(element) {
    if (element.requestFullscreen) {
        element.requestFullscreen();
    } else if (element.webkitRequestFullscreen) {
        element.webkitRequestFullscreen();
    } else if (element.msRequestFullscreen) {
        element.msRequestFullscreen();
    }
}

let previousVideoWrapper = null;
async function requestVideo(videoUrlRequest, videoWrapper) {
    if (!await getVidePlayer())
        return;
    // const response = await fetch(videoUrlRequest);
    // if (!response.ok) {
    //     alert("Failed to fetch video");
    //     return;
    // }
    // const videoUrl = await response.text();
    // setVideoUrl(videoUrl);
    if (previousVideoWrapper) {
        previousVideoWrapper.querySelector('.temp-video-holder').classList.remove('hidden');
    }
    previousVideoWrapper = videoWrapper;
    videoWrapper.querySelector('.temp-video-holder').classList.add('hidden');
    videoWrapper.querySelector('.video-holder').appendChild(videoPlayer);
    setVideoUrl();
}

function setVideoUrl(playlistUrl = "p720/master.m3u8") {
    const video = document.getElementById('video');
    if (playlistUrl.endsWith(".m3u8")) {
        if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = playlistUrl + '?_=' + Date.now(); // cache-buster
        } else if (Hls.isSupported()) {
            const hls = new Hls({ startPosition: 0 });
            hls.loadSource(playlistUrl + '?_=' + Date.now());
            hls.attachMedia(video);
        }
    } else {
        video.src = playlistUrl;
    }
}

let videoPlayer = null;
async function getVidePlayer() {
    if (videoPlayer)
        return true;

    // const response = await fetch('/page/video');
    // if (!response.ok) {
    //     alert("Failed to fetch video player");
    //     return;
    // }
    //
    // let videoPlayerDoc = await response.json();

    let videoPlayerDoc = {
        "style": "\n        input[type=\"range\"].seek-slider {\n            appearance: none;\n            width: 100%;\n            height: 5px;\n            border-radius: 4px;\n            background: linear-gradient(to right, #a855f7 0%, #a855f7 0%, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0.25) 100%);\n            outline: none;\n            cursor: pointer;\n            transition: background-size 0.1s linear;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb {\n            appearance: none;\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            cursor: pointer;\n            transition: transform 0.1s;\n        }\n        input[type=\"range\"].seek-slider::-webkit-slider-thumb:hover {\n            transform: scale(1.2);\n        }\n        input[type=\"range\"].seek-slider::-moz-range-thumb {\n            width: 12px;\n            height: 12px;\n            border-radius: 50%;\n            background: #a855f7;\n            border: none;\n            cursor: pointer;\n        }\n    ",
        "html": "\n        <div id=\"videoContainer\" tabindex=\"0\" class=\"relative aspect-video bg-black flex items-center justify-center overflow-hidden rounded-lg select-none\">\n            <video id=\"video\" class=\"w-full h-full bg-black cursor-pointer\"></video>\n\n            <!-- Controls -->\n            <div id=\"controls\"\n                 class=\"absolute bottom-0 left-0 right-0 flex flex-col bg-gradient-to-t from-black/80 via-black/50 to-transparent p-4\n                 opacity-0 transition-opacity duration-300 pointer-events-none\">\n\n                <!-- Seek Slider -->\n                <div class=\"w-full mb-3\">\n                    <input type=\"range\" id=\"seekSlider\" class=\"seek-slider\" min=\"0\" max=\"100\" value=\"0\" step=\"0.1\">\n                </div>\n\n                <!-- Control Buttons -->\n                <div class=\"flex items-center justify-between text-sm\">\n                    <div class=\"flex items-center space-x-3\">\n                        <button id=\"playPause\" class=\"p-2 rounded-full bg-white/20 hover:bg-white/40 transition\">\n                            <i data-lucide=\"play\" class=\"w-5 h-5\"></i>\n                        </button>\n\n                        <button onclick=\"replay()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Replay 5s\">\n                            <i data-lucide=\"rotate-ccw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n\n                        <button onclick=\"skip()\" class=\"relative p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Forward 5s\">\n                            <i data-lucide=\"rotate-cw\" class=\"w-5 h-5\"></i>\n                            <span class=\"absolute bottom-0 right-1 text-[10px] font-bold\">5</span>\n                        </button>\n                    </div>\n\n                    <div class=\"flex items-center space-x-3\">\n                        <span id=\"currentTime\" class=\"text-gray-300\">0:00</span>\n                        <span class=\"text-gray-400\">/</span>\n                        <span id=\"totalTime\" class=\"text-gray-300\">0:00</span>\n\n                        <!-- Volume -->\n                        <div class=\"flex items-center space-x-2\">\n                            <button id=\"muteBtn\" class=\"p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Mute/Unmute\">\n                                <i data-lucide=\"volume-2\" class=\"w-5 h-5\"></i>\n                            </button>\n                            <input id=\"volumeSlider\" type=\"range\" min=\"0\" max=\"1\" step=\"0.01\" value=\"1\"\n                                   class=\"w-24 h-[3px] accent-purple-500 cursor-pointer\">\n                        </div>\n\n                        <!-- Compact Speed & Resolution -->\n                        <div class=\"flex items-center space-x-3 relative\">\n                            <div class=\"relative\">\n                                <button id=\"speedButton\" class=\"text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">1x</button>\n                                <div id=\"speedMenu\" class=\"absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.5\">0.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"0.75\">0.75x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1\">1x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.25\">1.25x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"1.5\">1.5x</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-speed=\"2\">2x</button>\n                                </div>\n                            </div>\n\n                            <div class=\"relative\">\n                                <button id=\"resButton\" class=\"text-sm text-gray-200 bg-white/10 hover:bg-white/25 rounded px-2 py-1\">720p</button>\n                                <div id=\"resMenu\" class=\"absolute bottom-full mb-1 hidden flex-col bg-black/80 backdrop-blur-sm rounded text-xs\">\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"1080p\">1080p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"720p\">720p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"480p\">480p</button>\n                                    <button class=\"px-3 py-1 hover:bg-purple-600 w-full\" data-res=\"360p\">360p</button>\n                                </div>\n                            </div>\n                        </div>\n\n                        <!-- Fullscreen -->\n                        <button id=\"fullscreenBtn\" class=\"p-2 rounded-full bg-white/20 hover:bg-white/40 transition\" title=\"Fullscreen\">\n                            <i data-lucide=\"maximize\" class=\"w-5 h-5\"></i>\n                        </button>\n                    </div>\n                </div>\n            </div>\n        </div>\n    ",
        "script": [
            "https://cdn.jsdelivr.net/npm/hls.js@latest",
            "https://unpkg.com/lucide@latest",
            "video-player.js"
        ]
    }

    if (!videoPlayerDoc.html) {
        alert("Failed to fetch video player");
        return false;
    }

    if (videoPlayerDoc.style) {
        const newStyleElement = document.createElement('style');
        newStyleElement.textContent = videoPlayerDoc.style;
        document.head.appendChild(newStyleElement);
    }

    let videoPlayerContainer = document.createElement('div');
    videoPlayerContainer.innerHTML = videoPlayerDoc.html;
    document.body.appendChild(videoPlayerContainer);

    if (videoPlayerDoc.script) {
        videoPlayerDoc.script.forEach(scriptUrl => {
            const newScriptElement = document.createElement('script');
            newScriptElement.src = scriptUrl;
            document.body.appendChild(newScriptElement);
        });
    }

    videoPlayer = videoPlayerContainer.firstElementChild;
    videoPlayerContainer.remove();

    return true;
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}