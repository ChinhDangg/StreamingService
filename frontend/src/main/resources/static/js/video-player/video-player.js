lucide.createIcons();

const container = document.querySelector('[data-player="videoPlayerContainer"]');
const videoWrapper = container.querySelector('.video-wrapper');
const video = container.querySelector('.video-node');
const controls = container.querySelector('.video-controls');
const seekSlider = container.querySelector('.seek-slider');
const playPauseBtn = container.querySelector('.play-pause-button');
const replayBtn = container.querySelector('.replay-button');
const forwardBtn = container.querySelector('.forward-button');
const currentTimeEl = container.querySelector('.current-time');
const totalTimeEl = container.querySelector('.total-time');
const muteBtn = container.querySelector('.mute-button');
const volumeSlider = container.querySelector('.volume-slider');
const speedButton = container.querySelector('.speed-button');
const speedMenu = container.querySelector('.speed-menu');
const resButton = container.querySelector('.res-button');
const resMenu = container.querySelector('.res-menu');
const fullscreenBtn = container.querySelector('.fullscreen-button');

const formatTime = s => `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;

// --- Play / Pause ---
const togglePlay = () => video.paused ? video.play() : video.pause();
playPauseBtn.addEventListener('click', () => {
    resetHideTimer();
    togglePlay();
});

video.addEventListener('play', () => {
    playPauseBtn.innerHTML = `<i data-lucide="pause" class="w-4 h-4 sm:w-5 sm:h-5"></i>`;
    lucide.createIcons();
});

video.addEventListener('pause', () => {
    playPauseBtn.innerHTML = `<i data-lucide="play" class="w-4 h-4 sm:w-5 sm:h-5"></i>`;
    lucide.createIcons();
});

// --- Time + Slider ---
let isSeeking = false;
const updateSliderColor = val => {
    seekSlider.style.background = `linear-gradient(to right, #a855f7 0%, #a855f7 ${val}%, rgba(255,255,255,0.25) ${val}%, rgba(255,255,255,0.25) 100%)`;
};

video.addEventListener('timeupdate', () => {
    if (!isSeeking && video.duration) {
        const val = (video.currentTime / video.duration) * 100;
        seekSlider.value = val;
        updateSliderColor(val);
    }
    currentTimeEl.textContent = formatTime(video.currentTime);
});
video.addEventListener('loadedmetadata', () => {
    totalTimeEl.textContent = formatTime(video.duration);
    const isVertical = video.videoHeight > video.videoWidth;

    if (isVertical) {
        container.querySelectorAll('.padding-block').forEach(el => el.classList.add('hidden'));
    } else {
        container.querySelectorAll('.padding-block').forEach(el => el.classList.remove('hidden'));
    }
}, { once: true });

seekSlider.addEventListener('input', e => {
    resetHideTimer();
    isSeeking = true;
    const val = e.target.value;
    updateSliderColor(val);
    video.currentTime = (val / 100) * video.duration;
});
seekSlider.addEventListener('change', () => isSeeking = false);

// --- Volume ---
volumeSlider.addEventListener('input', () => {
    video.volume = volumeSlider.value;
    video.muted = video.volume === 0;
    updateVolumeIcon();
});
muteBtn.addEventListener('click', () => {
    resetHideTimer();
    video.muted = !video.muted;
    if (!video.muted && video.volume === 0) video.volume = 0.5;
    volumeSlider.value = video.muted ? 0 : video.volume;
    updateVolumeIcon();
});
function updateVolumeIcon() {
    let icon = 'volume-2';
    if (video.muted || video.volume === 0) icon = 'volume-x';
    else if (video.volume < 0.5) icon = 'volume-1';
    muteBtn.innerHTML = `<i data-lucide="${icon}" class="w-5 h-5"></i>`;
    lucide.createIcons();
}

// --- Speed Menu ---
speedButton.addEventListener('click', () => {
    resetHideTimer();
    speedMenu.classList.toggle('hidden');
    resMenu.classList.add('hidden');
});
speedMenu.querySelectorAll('button').forEach(btn => {
    btn.addEventListener('click', () => {
        const rate = parseFloat(btn.dataset.speed);
        video.playbackRate = rate;
        speedButton.textContent = `${rate}x`;
        speedMenu.classList.add('hidden');
    });
});

// --- Resolution Menu (UI only) ---
resButton.addEventListener('click', () => {
    resetHideTimer();
    resMenu.classList.toggle('hidden');
    speedMenu.classList.add('hidden');
});

// --- Fullscreen ---
fullscreenBtn.addEventListener('click', () => {
    requestFullscreenVideo();
});
document.addEventListener('fullscreenchange', () => {
    const icon = document.fullscreenElement ? 'minimize' : 'maximize';
    fullscreenBtn.innerHTML = `<i data-lucide="${icon}" class="w-5 h-5"></i>`;
    lucide.createIcons();

    // Reset visibility when leaving fullscreen
    if (!document.fullscreenElement) showControls();
});

function requestFullscreenVideo() {
    if (!document.fullscreenElement) {
        container.requestFullscreen();
    } else {
        document.exitFullscreen();
    }
}

// --- Auto-hide Controls (all screen sizes) ---
let hideTimer;
const showControls = () => {
    controls.style.opacity = '1';
    controls.style.pointerEvents = 'auto';
};
const hideControls = () => {
    controls.style.opacity = '0';
    controls.style.pointerEvents = 'none';

    resMenu.classList.add('hidden');
    speedMenu.classList.add('hidden');
};
const resetHideTimer = () => {
    console.log("Resetting hide timer: ");
    showControls();
    clearTimeout(hideTimer);
    hideTimer = setTimeout(hideControls, 3000);
};

// desktop
container.addEventListener('mouseleave', hideControls);
container.addEventListener('mouseenter', showControls);

container.addEventListener('contextmenu', (event) => {
    event.preventDefault();
});

// --- Unified Skip Controls ---
const SKIP_SECONDS = 5;
let lastClickTime = 0;
const DOUBLE_TAP_THRESHOLD = 300; // ms
let singleTapTimer = null;
let skipTimeTotalTimer = null;
let skipTimeTotal = 0;
let replayTimeTotalTimer = null;
let replayTimeTotal = 0;

function replay() {
    clearInterval(replayTimeTotalTimer);
    video.currentTime = Math.max(video.currentTime - SKIP_SECONDS, 0);
    replayTimeTotal -= SKIP_SECONDS;
    showFeedback("⏪ " + replayTimeTotal + "s");
    replayTimeTotalTimer = setTimeout(() => {
        replayTimeTotal = 0;
    }, 500);
}
function skip() {
    clearInterval(skipTimeTotalTimer);
    video.currentTime = Math.min(video.currentTime + SKIP_SECONDS, video.duration);
    skipTimeTotal += SKIP_SECONDS;
    showFeedback("⏩ " + skipTimeTotal + "s");
    skipTimeTotalTimer = setTimeout(() => {
        skipTimeTotal = 0;
    }, 500);
}

// --- visual feedback overlay (like YouTube pulse) ---
const feedbackEl = document.createElement("div");
feedbackEl.className = "absolute inset-0 flex items-center justify-center text-white text-3xl font-semibold opacity-0 select-none pointer-events-none transition-opacity duration-300";
container.appendChild(feedbackEl);

function showFeedback(text) {
    feedbackEl.textContent = text;
    feedbackEl.style.opacity = "1";
    clearTimeout(feedbackEl._timer);
    feedbackEl._timer = setTimeout(() => {
        feedbackEl.style.opacity = "0";
    }, 400);
}

replayBtn.addEventListener('click', () => {
    resetHideTimer()
    replay();
});
forwardBtn.addEventListener('click', () => {
    resetHideTimer()
    skip();
});

let holdTimer;
const holdDuration = 1000;
let isLongPress = false;
let videoSpeedWithLongPress = 3.00;

function handleLongPress() {
    isLongPress = false;
    holdTimer = setTimeout(() => {
        console.log("Hold detected! Triggering action...");
        isLongPress = true;
        if (videoSpeedWithLongPress === 2.00) {
            videoSpeedWithLongPress = 3.00;
        } else if (videoSpeedWithLongPress === 3.00)
            videoSpeedWithLongPress = 2.00;
        video.playbackRate = videoSpeedWithLongPress;
        showFeedback(videoSpeedWithLongPress + "x");
    }, holdDuration);
}

videoWrapper.addEventListener('pointerdown', () => {
    handleLongPress();
});

videoWrapper.addEventListener('pointerup', () => {
    clearTimeout(holdTimer);
    video.playbackRate = 1.00;
});

videoWrapper.addEventListener('pointerleave', () => {
    clearTimeout(holdTimer);
    video.playbackRate = 1.00;
});

videoWrapper.addEventListener('click', e => {
    if (!isLongPress) {
        handleClickOrTap(e.clientY, e.timeStamp, false);
    }
});

function handleClickOrTap(clientY, time) {
    const now = time;
    const isDouble = now - lastClickTime < DOUBLE_TAP_THRESHOLD;

    lastClickTime = now;

    if (isDouble) {
        clearTimeout(singleTapTimer);
        handleDouble(clientY);
    } else {
        // Delay single tap a bit to check if second comes
        singleTapTimer = setTimeout(() => {
            resetHideTimer();
            togglePlay();
        }, DOUBLE_TAP_THRESHOLD + 100);
    }
}

function handleDouble(clientY) {
    const rect = container.getBoundingClientRect();
    const relativeY = clientY - rect.top;

    if (relativeY < rect.height / 2) {
        skip();
    } else {
        replay();
    }
}

document.addEventListener('keydown', e => {
    // Avoid interfering with form inputs or system shortcuts
    if (['INPUT', 'TEXTAREA'].includes(e.target.tagName)) return;

    switch (e.key.toLowerCase()) {
        case 'arrowleft':
            e.preventDefault();
            replay(); // reuse unified function
            break;

        case 'arrowright':
            e.preventDefault();
            skip(); // reuse unified function
            break;

        case 'f':
            requestFullscreenVideo()
            break;

        case ' ': // Space
        case 'k':
            e.preventDefault();
            togglePlay(); // same play/pause toggle
            break;

        default:
            break;
    }
});

// Start timer immediately
resetHideTimer();