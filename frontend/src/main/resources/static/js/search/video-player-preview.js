const video = document.getElementById('video');
const container = document.getElementById('videoContainer');

const playlistUrl = "/p720/master.m3u8";

video.pause();
video.removeAttribute('src');
video.load(); // force media element reset

if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = playlistUrl + '?_=' + Date.now(); // cache-buster
} else if (Hls.isSupported()) {
    const hls = new Hls({ startPosition: 0 });
    hls.loadSource(playlistUrl + '?_=' + Date.now());
    hls.attachMedia(video);
}

video.addEventListener('mouseenter', () => {
   video.play();
});

video.addEventListener('mouseleave', () => {
    video.pause();
    video.currentTime = 0;
})