import {apiRequest} from "/static/js/common.js";
import Hls from "/static/js/lib/hls.mjs"
import {Client} from "/static/js/lib/stomp/index.js";

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
        const urlPolling = pollPlaylistUrl(fetchUrl, (_) => { return true; });
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

let stompClient = null;
let stompConnectPromise = null;
// Store job resolvers by jobId to avoid global state collisions
const pendingJobs = new Map();

export function pollPlaylistUrl(fetchUrl, disconnectAfterResult = null, maxWaitMs = 15000) {
    let cancelRequested = false;
    let previewTimeout = null;
    let currentJobId = null;

    const promise = new Promise(async (resolve, reject) => {
        const cleanup = () => {
            if (previewTimeout) clearTimeout(previewTimeout);
            if (currentJobId) pendingJobs.delete(currentJobId);
        };

        const disconnectSocketIfNeeded = (result) => {
            if (disconnectAfterResult && disconnectAfterResult(result)) {
                disconnectStomp();
            }
        };

        // Set global timeout
        previewTimeout = setTimeout(() => {
            cleanup();
            if (!cancelRequested) reject('Timeout waiting for job completion');
        }, maxWaitMs);

        try {
            // Connect & setup STOMP subscription ONCE
            await connectStompAsync();

            if (cancelRequested) {
                cleanup();
                reject('cancelled');
                return;
            }

            // Trigger job on backend
            const response = await apiRequest(fetchUrl);
            if (!response.ok) {
                cleanup();
                reject(await response.text());
                return;
            }

            const jobStatusInfo = await response.json();
            currentJobId = jobStatusInfo.jobId;

            // Check if backend processed it synchronously
            if (jobStatusInfo.result !== 'PROCESSING') {
                cleanup();
                let result = safeJsonParse(jobStatusInfo.result);
                disconnectSocketIfNeeded(result);
                resolve(result);
                return;
            }

            // Register listener directly for THIS jobId
            pendingJobs.set(currentJobId, (payload) => {
                cleanup();
                disconnectSocketIfNeeded(payload.result);
                resolve(payload.result);
            });

        } catch (error) {
            cleanup();
            reject(error);
        }
    });

    return {
        promise,
        cancel: () => {
            cancelRequested = true;
            if (currentJobId) pendingJobs.delete(currentJobId);
        }
    };
}

function connectStompAsync() {
    if (stompClient && stompClient.connected) {
        return Promise.resolve();
    }

    if (stompConnectPromise) {
        return stompConnectPromise;
    }

    stompConnectPromise = new Promise((resolve, reject) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const dynamicBrokerURL = `${protocol}//${window.location.host}/ws-stomp`;

        stompClient = new Client({ brokerURL: dynamicBrokerURL });

        stompClient.onConnect = (frame) => {
            console.log('Connected to STOMP broker');

            // SUBSCRIBE ONCE when client connects
            stompClient.subscribe('/user/queue/job-reply', (message) => {
                if (message.body) {
                    try {
                        const payload = JSON.parse(message.body);
                        payload.result = safeJsonParse(payload.result);

                        // Route payload to the specific waiting job promise!
                        if (payload.jobId && pendingJobs.has(payload.jobId)) {
                            const jobResolver = pendingJobs.get(payload.jobId);
                            jobResolver(payload);
                        }
                    } catch (e) {
                        console.error('Failed to process STOMP message', e);
                    }
                }
            });

            stompConnectPromise = null;
            resolve();
        };

        stompClient.onStompError = (frame) => {
            stompConnectPromise = null;
            reject(frame.headers['message']);
        };

        stompClient.activate();
    });

    return stompConnectPromise;
}

function safeJsonParse(data) {
    try {
        return JSON.parse(data);
    } catch (_) {
        return data;
    }
}

function disconnectStomp() {
    if (stompClient) {
        console.log('Disconnecting STOMP client');
        stompClient.deactivate();
        stompClient = null;
    }
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