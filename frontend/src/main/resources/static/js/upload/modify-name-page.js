
const nameEntity = Object.freeze({
    Authors: 'authors',
    Characters: 'characters',
    Universes: 'universes',
    Tags: 'tags',
});

const modifyOption = Object.freeze({
    Add: 'ADD',
    Update: 'UPDATE',
});

function initialize() {
    initializeAddOption();
    initializeUpdateOption();
    initializeSearchName();
    initializeEntityTabs();
    initializeUploadImageInput();
    initializeModifyActionButtons();
}

let currentModifyOption = modifyOption.Add;
let currentNameEntity = nameEntity.Characters;
let currentModifyOptionBtn = null;

const addUpdateTitle = document.getElementById('addUpdateTitle');
const entityTypeInput = document.getElementById('entityTypeInput');
const entityIdInput = document.getElementById('entityIdInput');
const entityIdContainer = document.getElementById('entityIdContainer');
const entityNameInput = document.getElementById('entityNameInput');

const imageUploadContainer = document.getElementById('imageUploadContainer');

function initializeAddOption() {
    const addOptionBtn = document.getElementById('add-name-option-btn');
    addUpdateTitle.textContent = 'Add New Name Entry';
    currentModifyOptionBtn = addOptionBtn;
    currentModifyOptionBtn.classList.add('bg-indigo-600');
    currentModifyOptionBtn.classList.remove('bg-gray-700');
    entityIdContainer.classList.add('hidden');

    addOptionBtn.addEventListener('click', () => {
        if (currentModifyOption === modifyOption.Add) return;
        currentModifyOption = modifyOption.Add;
        addUpdateTitle.textContent = 'Add New Name Entry';
        addOptionBtn.classList.add('bg-indigo-600');
        addOptionBtn.classList.remove('bg-gray-700');
        currentModifyOptionBtn?.classList.remove('bg-indigo-600');
        currentModifyOptionBtn?.classList.add('bg-gray-700');
        currentModifyOptionBtn = addOptionBtn;
        
        entityIdContainer.classList.add('hidden');
    });
}

function initializeUpdateOption() {
    const updateOptionBtn = document.getElementById('update-name-option-btn');
    updateOptionBtn.addEventListener('click', () => {
        if (currentModifyOption === modifyOption.Update) return;
        currentModifyOption = modifyOption.Update;
        addUpdateTitle.textContent = 'Update Name Entry';
        updateOptionBtn.classList.add('bg-indigo-600');
        updateOptionBtn.classList.remove('bg-gray-700');
        currentModifyOptionBtn?.classList.remove('bg-indigo-600');
        currentModifyOptionBtn?.classList.add('bg-gray-700');
        currentModifyOptionBtn = updateOptionBtn;

        entityIdContainer.classList.remove('hidden');
    });
}

let currentEntityTabBtn = null;

function initializeEntityTabs() {
    currentNameEntity = nameEntity.Characters;
    entityTypeInput.value = Object.keys(nameEntity)[1];

    const entityTabContainer = document.getElementById('entity-tab-container');
    const entityTabTem = entityTabContainer.querySelector('.entity-tab-btn');
    for (const [key, value] of Object.entries(nameEntity)) {
        const entityTab = helperCloneAndUnHideNode(entityTabTem);
        entityTab.textContent = key;
        if (currentNameEntity === value) {
            currentEntityTabBtn = entityTab;
            entityTab.classList.add('bg-indigo-600');
            entityTab.classList.remove('bg-gray-700');
        }
        entityTab.addEventListener('click', () => {
            if (currentNameEntity === value) return;
            currentNameEntity = value;
            entityTypeInput.value = key;
            entityTab.classList.add('bg-indigo-600');
            entityTab.classList.remove('bg-gray-700');
            currentEntityTabBtn?.classList.remove('bg-indigo-600');
            currentEntityTabBtn?.classList.add('bg-gray-700');
            currentEntityTabBtn = entityTab;

            entityIdInput.value = '';
            entityNameInput.value = '';

            if (currentNameEntity === nameEntity.Authors || currentNameEntity === nameEntity.Tags) {
                imageUploadContainer.classList.add('hidden');
            } else {
                imageUploadContainer.classList.remove('hidden');
            }
        });
        entityTabContainer.appendChild(entityTab);
    }
}

const entityImageInput = document.getElementById('entityImageInput');
const currentImagePreview = document.getElementById('currentImagePreview');

function initializeUploadImageInput() {
    const entityImageInputBtn = document.getElementById('entityImageInputBtn');
    entityImageInputBtn.addEventListener('click', () => {
        entityImageInput.click();
    });
    entityImageInput.addEventListener('change', () => {
        const file = entityImageInput.files[0];
        if (!file) {
            currentImagePreview.src = '';
            currentImagePreview.classList.add('hidden');
            return;
        }
        const reader = new FileReader();
        reader.onload = (e) => {
            currentImagePreview.src = e.target.result;
            currentImagePreview.classList.remove('hidden');
        };
        reader.readAsDataURL(file);
    });
}

let searchTimeOut = null;
const searchEntryList = document.getElementById('searchEntryList');

function initializeSearchName() {
    const searchInput = document.getElementById('searchNameInput');

    const searchEntryTem = searchEntryList.querySelector('.search-entry-item');
    const addSearchEntry = (nameEntity) => {
        const searchEntry = helperCloneAndUnHideNode(searchEntryTem);
        searchEntry.textContent = nameEntity.name;
        searchEntry.addEventListener('click', () => {
            entityIdInput.value = nameEntity.id;
            entityNameInput.value = nameEntity.name;
        });
        searchEntryList.appendChild(searchEntry);
    }

    const searchName = async (nameString) => {
        const response = await fetch(`/api/search/name/${currentNameEntity}?name=${nameString}`);
        if (!response.ok) {
            alert('Failed to fetch name info: ' + await response.text());
            return;
        }
        const nameEntityInfo = await response.json();
        // const nameEntityInfo = [
        //     {id: 1, name: 'name1', thumbnail: 'thumbnail1'},
        //     {id: 2, name: 'name2', thumbnail: 'thumbnail1'},
        // ];
        const first = searchEntryList.firstElementChild;
        if (first) searchEntryList.replaceChildren(first);
        if (nameEntityInfo.length === 0) {
            const searchEntry = helperCloneAndUnHideNode(searchEntryTem);
            searchEntry.textContent = 'No matching name found. Please try another name.'
            searchEntryList.appendChild(searchEntry);
        }
        nameEntityInfo.forEach(nameEntity => addSearchEntry(nameEntity));
    }

    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimeOut);
        const searchInputValue = searchInput.value.trim();
        if (searchInputValue.length < 2) {
            const first = searchEntryList.firstElementChild;
            if (first) searchEntryList.replaceChildren(first);
            return;
        }

        searchTimeOut = setTimeout(async () => {
            await searchName(searchInputValue)
        }, 500);
    });
}

function initializeModifyActionButtons() {
    const clearBtn = document.getElementById('clearBtn');
    clearBtn.addEventListener('click', () => {
        entityIdInput.value = '';
        entityNameInput.value = '';
        entityImageInput.value = '';
        currentImagePreview.src = '';
        currentImagePreview.classList.add('hidden');
    });
    document.getElementById('saveBtn').addEventListener('click', async () => {
        const name = entityNameInput.value.trim();
        if (name.length < 3) {
            showWarningMessage('Name must be at least 3 characters');
            return;
        }
        if (name.length > 200) {
            showWarningMessage('Name exceeded 200 characters');
            return;
        }
        if (currentModifyOption === modifyOption.Add
            && (currentNameEntity === nameEntity.Characters || currentNameEntity === nameEntity.Universes)) {
            if (!(entityImageInput.files.length > 0 && entityImageInput.files[0].type.startsWith("image/"))) {
                showWarningMessage('Please upload an image for new name entry');
                return;
            }
        }
        else if (currentModifyOption === modifyOption.Update) {
            if (!entityIdInput.value) {
                showWarningMessage('Please select an existing name entry to update');
                return;
            }
        }
        if (entityImageInput.files.length > 0 && !entityImageInput.files[0].type.startsWith("image/")) {
            showWarningMessage('Uploaded file is not an image type.');
            return;
        }

        const formData = new FormData();
        formData.append("name", entityNameInput.value);
        if (entityImageInput.files.length > 0) {
            formData.append("thumbnail", entityImageInput.files[0]);
        }

        let url = `/api/modify/name/${currentNameEntity}`;
        let method = 'POST';
        let body = formData;
        if (currentModifyOption === modifyOption.Update) {
            url += `/${entityIdInput.value}`;
            method = 'PUT';
        }

        if (currentNameEntity === nameEntity.Authors || currentNameEntity === nameEntity.Tags) {
            body = entityNameInput.value;
        }

        const response = await fetch(url, {
            method: method,
            body: body
        });
        if (currentModifyOption === modifyOption.Add && response.status !== 201) {
            alert('Failed to add new name entry: ' + await response.text());
            return;
        }
        if (currentModifyOption === modifyOption.Update && response.status !== 200) {
            alert('Failed to update name entry: ' + await response.text());
            return;
        }
        showSuccessMessage('Name entry is ' + currentModifyOption + ' successfully');
        clearBtn.click();
    });
}

let warningMessageTimeout = null;
const warningMessage = document.getElementById('warningMessage');
function showWarningMessage(message) {
    clearTimeout(warningMessageTimeout);
    warningMessage.textContent = message;
    warningMessage.classList.remove('hidden');
    warningMessageTimeout = setTimeout(() => {
        warningMessage.classList.add('hidden');
    }, 10000);
}

let successMessageTimeout = null;
const successMessage = document.getElementById('successMessage');
function showSuccessMessage(message) {
    clearTimeout(successMessageTimeout);
    successMessage.textContent = message;
    successMessage.classList.remove('hidden');
    successMessageTimeout = setTimeout(() => {
        successMessage.classList.add('hidden');
    }, 10000);
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}

initialize();