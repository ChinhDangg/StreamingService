import {apiRequest, getMediaId, getMediaLength, validateImage} from "/static/js/common.js";

const editThumbnailSection = document.getElementById('edit-thumbnail-section');
let editMethod = null;

export function setEditMethod(type) {
    editMethod = type;
}

window.addEventListener('DOMContentLoaded', async () => {
    await initializeEditThumbnail();
});

export async function initializeEditThumbnail() {

    const editThumbnailBtn = document.getElementById('edit-thumbnail-btn');
    editThumbnailBtn.addEventListener('click', () => {
        editThumbnailSection.querySelector('.edit-thumbnail-content').classList.toggle('hidden');
    });

    const currentThumbnailTextInput = editThumbnailSection.querySelector('.current-thumbnail-text-input');

    const uploadThumbnailInput = editThumbnailSection.querySelector('#upload-thumbnail-input');
    const thumbnailPreviewImg = editThumbnailSection.querySelector('.thumbnail-preview-img');
    const thumbnailPreviewContainer = editThumbnailSection.querySelector('.thumbnail-preview-container');
    uploadThumbnailInput.addEventListener('change', async () => {
        const file = uploadThumbnailInput.files[0];
        if (!file) {
            currentThumbnailTextInput.value = '';
            thumbnailPreviewImg.src = '';
            thumbnailPreviewContainer.classList.add('hidden');
            return;
        }
        currentThumbnailTextInput.value = file.name;

        const reader = new FileReader();
        reader.onload = (e) => {
            thumbnailPreviewImg.src = e.target.result;
            thumbnailPreviewContainer.classList.remove('hidden');
        };
        reader.readAsDataURL(file);
        editMethod = 'upload';
    });

    const saveEditThumbnailBtn = editThumbnailSection.querySelector('.save-edit-thumbnail-btn');
    saveEditThumbnailBtn.addEventListener('click', async () => {
        if (!editMethod) {
            alert('Please select an edit method');
            return;
        }
        if (getMediaId() === null) {
            alert('Media id not found');
            return;
        }
        let updated = false;
        if (editMethod === 'upload') {
            const file = uploadThumbnailInput.files[0];
            if (!file) {
                alert('Please select a file to upload');
                return;
            }
            if (file.size <= 0) {
                alert('File size is 0');
                return;
            }
            try {
                await validateImage(file);
            } catch (err) {
                alert(err.message);
                return;
            }
            const formData = new FormData();
            formData.append('thumbnail', file);
            const response = await apiRequest(`/api/modify/media/thumbnail/${getMediaId()}`, {
                method: 'PUT',
                body: formData
            });
            if (!response.ok) {
                alert('Failed to upload thumbnail');
                return;
            }
            updated = true;
        } else if (editMethod === 'current') {
            let timeStamp = currentThumbnailTextInput.value.trim();
            if (!timeStamp) {
                alert('Please enter a num length');
                return;
            }
            try {
                timeStamp = Number.parseFloat(timeStamp);
            } catch (err) {
                alert('Invalid num length - not a number');
                return;
            }
            if (timeStamp < 0 || timeStamp > getMediaLength()) {
                alert('Invalid num length - out of range');
                return;
            }
            const formData = new FormData();
            formData.append('num', timeStamp.toString());
            const response = await apiRequest(`/api/modify/media/thumbnail/${getMediaId()}`, {
                method: 'PUT',
                body: formData
            });
            if (!response.ok) {
                alert('Failed to update thumbnail with time stamp: ' + timeStamp);
                return;
            }
            updated = true;
        }
        if (updated) {
            alert('Thumbnail updated successfully');
        }
    });
}