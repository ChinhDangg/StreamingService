import {initializeEditThumbnail, setEditMethod} from "/static/js/upload/modify-thumbnail.js";

const editThumbnailSection = document.getElementById('edit-thumbnail-section');

const thumbnailGetCurrentBtn = editThumbnailSection.querySelector('.thumbnail-get-current-btn');
const currentThumbnailTextInput = editThumbnailSection.querySelector('.current-thumbnail-text-input');

const uploadThumbnailInput = editThumbnailSection.querySelector('#upload-thumbnail-input');
const thumbnailPreviewImg = editThumbnailSection.querySelector('.thumbnail-preview-img');
const thumbnailPreviewContainer = editThumbnailSection.querySelector('.thumbnail-preview-container');

window.addEventListener('DOMContentLoaded', () => {
    initializeVideoGetCurrentTimestamp();
});

function initializeVideoGetCurrentTimestamp() {
    editThumbnailSection.querySelector('.get-current-container').classList.remove('hidden');

    const videoContainer = document.querySelector('[data-player="videoPagePlayerContainer"]');

    thumbnailGetCurrentBtn.addEventListener('click', () => {
        uploadThumbnailInput.value = '';

        const videoElement = videoContainer.querySelector('video');
        if (!videoElement && !videoElement.src && !videoElement.currentTime) {
            currentThumbnailTextInput.value = 'No video src or time founds';
            thumbnailPreviewImg.src = '';
            thumbnailPreviewContainer.classList.add('hidden');
            return;
        }
        currentThumbnailTextInput.value = videoElement.currentTime;
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");

        canvas.width = videoElement.videoWidth;
        canvas.height = videoElement.videoHeight;

        ctx.drawImage(videoElement, 0, 0, canvas.width, canvas.height);

        canvas.toBlob(blob => {
            thumbnailPreviewImg.src = URL.createObjectURL(blob);
        }, "image/jpeg", 0.9);
        thumbnailPreviewContainer.classList.remove('hidden');

        setEditMethod('current');
    });
}