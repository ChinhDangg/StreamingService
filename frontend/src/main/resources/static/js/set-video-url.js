import {apiRequest} from "/static/js/common.js";

export function setVideoUrl(videoContainerNode, playlistUrl, restart = true, startPlaying = false, autoReplay = true) {
    const video = videoContainerNode.querySelector('video');
    const totalTime = videoContainerNode.querySelector('.total-time');
    const currentTime = video.currentTime;
    if (playlistUrl.endsWith(".m3u8")) {
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

        if (totalTime) {
            const onLevelLoaded = (event, data) => {
                const d = data.details;
                if (!d.live) {
                    hls.off(Hls.Events.LEVEL_LOADED, onLevelLoaded);
                    totalTime.textContent = formatTime(d.totalduration);
                } else {
                    setTimeout(() => {
                        totalTime.textContent = formatTime(d.totalduration);
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
    video.addEventListener("ended", () => {
        if (autoReplay) {
            video.currentTime = 0;
            video.play();
        }
    });
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
    if (resButtonTem) resMenu.replaceChildren(resButtonTem);

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
            await requestVideoPartial(videoUrlRequest, videoContainerNode, false, true);
        });
    }
}

export async function requestVideoPartial(fetchUrl, container, restart = true, startPlaying = false) {
    const loader = createLoader();

    let playlistUrl;
    try {
        container.appendChild(loader);
        const urlPolling = pollPlaylistUrl(fetchUrl);
        playlistUrl = await urlPolling.promise;
    } catch (err) {
        if (err === 'cancelled') {
            return 'Video Cancelled';
        }
        if (err === 'timeout') {
            return 'Preview Timeout';
        }
        return 'Video Failed ' + err;
    } finally {
        container.removeChild(loader);
    }
    setVideoUrl(container, playlistUrl, restart, startPlaying);
    return null;
}

export function pollPlaylistUrl(fetchUrl, maxWaitMs = 5000, intervalMs = 500) {
    let cancelRequested = false;
    let previewInterval = null;
    let previewTimeout = null;

    const promise = new Promise((resolve, reject) => {
        previewTimeout = setTimeout(() => {
            clearInterval(previewInterval);
            if (!cancelRequested) reject('Timeout');
        }, maxWaitMs);

        previewInterval = setInterval(async () => {
            if (cancelRequested) {
                console.log('request cancelled');
                clearInterval(previewInterval);
                clearTimeout(previewTimeout);
                reject('cancelled');
                return;
            }

            let response;
            try {
                response = await apiRequest(fetchUrl);
            } catch (err) {
                clearInterval(previewInterval);
                clearTimeout(previewTimeout);
                reject('network error: ' + err);
                return;
            }
            if (!response.ok) {
                clearInterval(previewInterval);
                clearTimeout(previewTimeout);
                reject(await response.text());
                return;
            }

            if (response.headers.get('X-Media-State')) {
                clearInterval(previewInterval);
                clearTimeout(previewTimeout);
                resolve(fetchUrl);
                return;
            }

            const playlistUrl = await response.text();
            if (playlistUrl !== 'PROCESSING') {
                clearInterval(previewInterval);
                clearTimeout(previewTimeout);
                resolve(playlistUrl);
            }
        }, intervalMs);
    });

    return {
        promise,
        cancel: () => {
            cancelRequested = true;
        }
    };
}

export function createLoader() {
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
        pointerEvents: "none", // prevents loader from intercepting mouse events on container
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        zIndex: 999,
        borderRadius: "inherit",
    });

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