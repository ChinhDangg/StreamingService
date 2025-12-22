import {apiRequest} from "/static/js/common.js";

export async function quickViewContentInOverlay(mediaId, mediaType, mediaInfo = null) {
    const url = new URL(window.location.href);
    const urlParams = url.searchParams;
    urlParams.set('mediaId', mediaId);
    const newUrl = url.pathname + '?' + urlParams.toString() + url.hash;
    window.history.pushState({ path: newUrl }, '', newUrl);

    await displayContentPageForOverlay(mediaType, mediaId, mediaInfo);
}

const mediaDocMap = new Map();

async function getAlbumPageContent() {
    const response = await apiRequest('/page/frag/album');
    if (!response.ok) {
        alert("Failed to fetch album layout");
        return;
    }

    let albumDoc = await response.json();
    // let albumDoc = {
    //     "style": "",
    //     "html": "",
    //     "script": [
    //         "album-page.js"
    //     ]
    // };
    return albumDoc;
}

async function getVideoPageContent() {
    const response = await apiRequest('/page/frag/video');
    if (!response.ok) {
        alert("Failed to fetch video page layout");
        return;
    }

    let videoDoc = await response.json();
    // let videoDoc = {
    //     "style": "",
    //     "html": "",
    //     "script": [
    //         "video-player.js",
    //         "video-page.js"
    //     ]
    // };
    return videoDoc;
}

let previousQuickViewMediaId = null;

async function displayContentPageForOverlay(mediaType, mediaId, mediaInfo = null) {
    const quickViewOverlay = document.getElementById('quickViewOverlay');
    const overlayWrapper = quickViewOverlay.querySelector('.overlayContent-inner-wrapper');

    if (mediaDocMap.has(mediaType)) {
        const { mod, node } = mediaDocMap.get(mediaType);
        overlayWrapper.innerHTML = '';
        overlayWrapper.appendChild(node);
        if (previousQuickViewMediaId !== null && previousQuickViewMediaId !== mediaId) {
            mod.initialize(mediaId, mediaInfo);
            previousQuickViewMediaId = mediaId;
            openOverlay(true);
        } else {
            openOverlay();
        }
        return;
    }

    previousQuickViewMediaId = mediaId;

    const pageDoc = (mediaType === 'VIDEO') ? await getVideoPageContent() : await getAlbumPageContent();

    if (!pageDoc.html) {
        alert(`Failed to fetch content ${mediaType} layout`);
        return;
    }

    if (pageDoc.style) {
        if (!document.getElementById(`style-${mediaType}`)) {
            const newStyleElement = document.createElement('style');
            newStyleElement.id = `style-${mediaType}`;
            newStyleElement.textContent = pageDoc.style;
            document.head.appendChild(newStyleElement);
        }
    }

    overlayWrapper.innerHTML = pageDoc.html;

    if (pageDoc.script) {
        pageDoc.script.forEach(script => {
            if (document.getElementById(script)) {
                // help prevent duplicate script tags
                document.getElementById(script).remove();
            }
            const scriptElement = document.createElement('script');
            scriptElement.src = script + '?v=' + Date.now();
            scriptElement.id = script;
            if (script.endsWith('.js')) {
                scriptElement.type = 'module';
                scriptElement.onload = async () => {
                    const mod = await import(scriptElement.src);
                    if (typeof mod.initialize === 'function') {
                        mediaDocMap.set(mediaType, { mod: mod, node: overlayWrapper.firstElementChild });
                        mod.initialize(mediaId, mediaInfo);
                    }
                };
            }
            document.body.appendChild(scriptElement);
        });
    }

    openOverlay();
}

const overlay = document.getElementById('quickViewOverlay');
function openOverlay(scrollToTop = false) {
    overlay.classList.remove('hidden');
    overlay.classList.add('flex');
    document.body.style.overflow = 'hidden'; // disable background scroll
    if (scrollToTop)
        document.getElementById('overlayContent').scrollTop = 0;
}

function closeOverlay() {
    // close only if clicked outside main content
    //if (e.target === e.currentTarget || e.target === overlay) {
    overlay.classList.add('hidden');
    overlay.classList.remove('flex');
    document.body.style.overflow = ''; // re-enable background scroll
    const quickViewOverlay = document.getElementById('quickViewOverlay');
    quickViewOverlay.querySelector('.overlayContent-inner-wrapper').innerHTML = '';
}

document.getElementById('overlayContent').addEventListener('click', (e) => {
    closeOverlay(e);
});

document.getElementById('closeOverlayBtn').addEventListener('click', (e) => {
    closeOverlay(e);
});

document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !overlay.classList.contains('hidden')) {
        overlay.classList.add('hidden');
        overlay.classList.remove('flex');
        document.body.style.overflow = '';
    }
});