import {initializeEditThumbnail, setEditMethod} from "/static/js/upload/modify-thumbnail.js";

const editThumbnailSection = document.getElementById('edit-thumbnail-section');

const thumbnailGetCurrentBtn = editThumbnailSection.querySelector('.thumbnail-get-current-btn');
const currentThumbnailTextInput = editThumbnailSection.querySelector('.current-thumbnail-text-input');

const uploadThumbnailInput = editThumbnailSection.querySelector('#upload-thumbnail-input');
const thumbnailPreviewImg = editThumbnailSection.querySelector('.thumbnail-preview-img');
const thumbnailPreviewContainer = editThumbnailSection.querySelector('.thumbnail-preview-container');

const albumImageContainer = document.getElementById('album-image-container');

let lastIndex = null;

window.addEventListener('DOMContentLoaded', () => {
    initializeAlbumGetCurrentIndex();
});

function initializeAlbumGetCurrentIndex() {
    editThumbnailSection.querySelector('.get-current-container').classList.remove('hidden');

    thumbnailGetCurrentBtn.addEventListener('click', () => {
        uploadThumbnailInput.value = '';

        let maxLength = albumImageContainer.childElementCount - 1;

        let index;
        try {
            index = Number.parseInt(currentThumbnailTextInput.value);
            if (isNaN(index)) {
                index = 0;
            }
            if (lastIndex === index) {
                index++;
            }
        } catch (err) {
            index = 0;
        }
        if (index >= maxLength) {
            index = 0;
        }
        lastIndex = index;

        if (maxLength > 0 && index < maxLength) {
            const image = albumImageContainer.children[index + 1].querySelector('img');
            thumbnailPreviewImg.src = image.src;
            thumbnailPreviewContainer.classList.remove('hidden');
            currentThumbnailTextInput.value = index;
            setEditMethod('current');
        }
    });
}