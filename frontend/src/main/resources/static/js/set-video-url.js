
export function setVideoUrl(videoContainerNode, playlistUrl = "p720/master.m3u8", restart = true, startPlaying = false) {
    const video = videoContainerNode.querySelector('video');
    const totalTime = videoContainerNode.querySelector('.total-time');
    const currentTime = video.currentTime;
    if (playlistUrl.endsWith(".m3u8")) {
        if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = playlistUrl + '?_=' + Date.now(); // cache-buster
            if (!restart) {
                video.addEventListener("loadedmetadata", () => {
                    if (video.duration && video.duration >= currentTime) {
                        video.currentTime = currentTime;
                    }
                }, { once: true });
            }
        } else if (Hls.isSupported()) {
            const hls = new Hls();
            hls.loadSource(playlistUrl + '?_=' + Date.now());
            hls.attachMedia(video);

            const onManifestParsed = () => {
                video.addEventListener("loadedmetadata", () => {
                    if (restart) {
                        video.currentTime = 0;
                    } else {
                        if (video.duration && video.duration >= currentTime) {
                            video.currentTime = currentTime;
                        }
                    }
                    hls.off(Hls.Events.MANIFEST_PARSED, onManifestParsed);
                }, { once: true });
            }
            hls.on(Hls.Events.MANIFEST_PARSED, onManifestParsed);

            if (!totalTime)
                return;
            const onLevelLoaded = (event, data) => {
                const d = data.details;
                if (!d.live) {
                    hls.off(Hls.Events.LEVEL_LOADED, onLevelLoaded);
                    totalTime.textContent = formatTime(video.duration);
                } else {
                    setTimeout(() => {
                        totalTime.textContent = formatTime(video.duration);
                    }, 100);
                }
            }
            hls.on(Hls.Events.LEVEL_LOADED, onLevelLoaded);
        }
    } else {
        video.src = playlistUrl;
        if (!restart) {
            video.addEventListener("loadedmetadata", () => {
                if (video.duration && video.duration >= currentTime) {
                    video.currentTime = currentTime;
                }
            }, { once: true });
        }
    }
    if (startPlaying) {
        video.play();
    }
}

const formatTime = s => `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;

const RESOLUTION = Object.freeze({
    p2160: '2160p',
    p1440: '1440p',
    p1080: '1080p',
    p720: '720p',
    p480: '480p',
    p360: '360p',
    p240: '240p',
});

export function setVideoResolution(videoContainerNode, videoBaseUrlRequest, originalResolution, defaultRes) {
    const mainResButton = videoContainerNode.querySelector('.res-button');
    mainResButton.textContent = RESOLUTION[defaultRes];
    const resMenu = videoContainerNode.querySelector('.res-menu');
    const resButtonTem = resMenu.querySelector('button');

    const resolutions = new Map();
    resolutions.set('original', 'Original');

    const baseResNumber = Number(originalResolution);

    for (const key of Object.keys(RESOLUTION)) {
        if (Number(key.slice(1)) < baseResNumber)
            resolutions.set(key, RESOLUTION[key]);
    }

    for (const [key, value] of resolutions) {
        const resButton = resButtonTem.cloneNode(true);
        resButton.classList.remove('hidden');
        resButton.dataset.res = key;
        resButton.textContent = value;
        resMenu.appendChild(resButton);
        resButton.addEventListener('click', async () => {
            mainResButton.textContent = value;
            resMenu.classList.add('hidden');
            const videoUrlRequest = videoBaseUrlRequest + '/' + key;
            const response = await fetch(videoUrlRequest);
            if (!response.ok) {
                alert("Failed to fetch video at resolution " + value);
                return;
            }
            const videoUrl = await response.text();
            setVideoUrl(videoContainerNode, videoUrl, false, true);
        });
    }
}