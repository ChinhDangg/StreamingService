
export function setVideoUrl(videoContainerNode, playlistUrl = "p720/master.m3u8") {
    const video = videoContainerNode.querySelector('video');
    const totalTime = videoContainerNode.querySelector('.total-time');
    if (playlistUrl.endsWith(".m3u8")) {
        if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = playlistUrl + '?_=' + Date.now(); // cache-buster
        } else if (Hls.isSupported()) {
            const hls = new Hls({ startPosition: 0 });
            hls.loadSource(playlistUrl + '?_=' + Date.now());
            hls.attachMedia(video);
            const onLevelLoaded = (event, data) => {
                const d = data.details;
                console.log('HLS details:', d);
                console.log('live: ', d.live);
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
    }
}

const formatTime = s => `${Math.floor(s / 60)}:${Math.floor(s % 60).toString().padStart(2, '0')}`;