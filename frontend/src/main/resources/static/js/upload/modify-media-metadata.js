import { displayContentInfo } from "/static/js/metadata-display.js";

let mediaId = null;

async function initialize() {
    const urlParams = new URLSearchParams(window.location.search);
    mediaId = urlParams.get('mediaId') || urlParams.get('grouperId');
    if (!mediaId) {
        alert("No mediaId found-bad request");
    }
    initializeEditTitle();
    await initializeEdit();
}

window.addEventListener('DOMContentLoaded', () => {
    initialize();
});


function initializeEditTitle() {
    const editTitleBtn = document.getElementById('editTitleBtn');

    const editTitleArea = document.getElementById('edit-title-area');
    const editTitleInput = editTitleArea.querySelector('.edit-title-input');
    const mainContainer = document.getElementById(document.body.dataset.mainContainerId);
    const mainTitle = mainContainer.querySelector('.main-title');

    editTitleBtn.addEventListener('click', () => {
        editTitleArea.classList.toggle('hidden');
        editTitleInput.value = mainTitle.textContent.trim();
    });

    const saveEditTitleBtn = editTitleArea.querySelector('.save-edit-title-btn');
    saveEditTitleBtn.addEventListener('click', async () => {
        const newTitle = editTitleInput.value.trim();
        if (newTitle.length === 0) {
            alert('Title cannot be empty');
            return;
        }
        const response = await fetch(`/api/modify/media/title/${mediaId}`, {
            method: 'PUT',
            body: newTitle
        });
        if (!response.ok) {
            alert(`Failed to save title: ${await response.text()}`);
            return;
        }
        mainTitle.textContent = await response.text();
        editTitleArea.classList.add('hidden');
    });
}


const NameEntity = Object.freeze({
    CHARACTERS: 'characters',
    UNIVERSES: 'universes',
    AUTHORS: 'authors',
    TAGS: 'tags',
});

let currentNameEntity = null;
const allNameEntityMap = new Map();
let currentNameEntityEditMap = null;

const editAreaContainer = document.getElementById('editAreaContainer');
const currentArea = editAreaContainer.querySelector('#currentArea');
const addingArea = editAreaContainer.querySelector('#addingArea');
const removingArea = editAreaContainer.querySelector('#removingArea');
const removingAreaContainer = editAreaContainer.querySelector('#removingAreaContainer');
const tempLi = editAreaContainer.querySelector('.temp-edit-node-li');

function addToCurrent(name, id) {
    const li = helperCloneAndUnHideNode(tempLi);
    li.querySelector('.text-name').textContent = name;
    li.addEventListener('click', () => {
        currentNameEntityEditMap.get('current').delete(id);
        currentArea.removeChild(li);
        addToRemoving(name, id);
    });
    currentNameEntityEditMap.get('current').set(id, name);
    currentArea.appendChild(li);
}

const addToAdding = (name, id) => {
    const li = helperCloneAndUnHideNode(tempLi);
    li.querySelector('.text-name').textContent = name;
    li.addEventListener('click', () => {
        currentNameEntityEditMap.get('adding').delete(id);
        addingArea.removeChild(li);
    });
    currentNameEntityEditMap.get('adding').set(id, name);
    addingArea.appendChild(li);
}

function addToRemoving(name, id) {
    const li = helperCloneAndUnHideNode(tempLi);
    li.querySelector('.text-name').textContent = name;
    li.addEventListener('click', () => {
        currentNameEntityEditMap.get('removing').delete(id);
        if (currentNameEntityEditMap.get('removing').size === 0)
            removingAreaContainer.classList.add('hidden');
        removingArea.removeChild(li);
        addToCurrent(name, id);
    });
    currentNameEntityEditMap.get('removing').set(id, name);
    removingArea.appendChild(li);
    removingAreaContainer.classList.remove('hidden');
}

async function initializeEdit() {

    const fetchNameEntityInfoAndAddToCurrent = async () => {
        if (currentNameEntityEditMap.get('current').size === 0 && currentNameEntityEditMap.get('removing').size === 0) {
            const response = await fetch(`/api/modify/media/${currentNameEntity}/${mediaId}`);
            if (!response.ok) {
                alert(`Failed to get current ${nameEntity}:  ` + await response.text());
                return;
            }
            const currentNameEntities = await response.json();
            // const currentNameEntities = [
            //     { id: 1, name: 'Jane' },
            //     { id: 2, name: 'John' },
            // ];

            currentNameEntities.forEach(nameEntity => {
                currentNameEntityEditMap.get('current').set(nameEntity.id, nameEntity.name);
            });
        }

        currentArea.innerHTML = '';
        currentNameEntityEditMap.get('current').forEach((name, id) => {
            addToCurrent(name, id);
        })
    }

    const currentEditNameTitle = editAreaContainer.querySelector('.current-edit-name-title');

    const setNameEntityEditMap = (nameEntity) => {
        currentNameEntity = nameEntity;
        currentEditNameTitle.textContent = `${nameEntity}`;
        if (allNameEntityMap.has(currentNameEntity)) {
            currentNameEntityEditMap = allNameEntityMap.get(currentNameEntity);
        }
        else {
            allNameEntityMap.set(currentNameEntity, new Map());
            currentNameEntityEditMap = allNameEntityMap.get(currentNameEntity);
            currentNameEntityEditMap.set('current', new Map());
            currentNameEntityEditMap.set('adding', new Map());
            currentNameEntityEditMap.set('removing', new Map());
        }
    }

    const setAddingArea = () => {
        addingArea.innerHTML = '';
        currentNameEntityEditMap.get('adding').forEach((name, id) => {
            addToAdding(name, id);
        });
    }

    const setRemovingArea = () => {
        removingArea.innerHTML = '';
        removingAreaContainer.classList.add('hidden');
        currentNameEntityEditMap.get('removing').forEach((name, id) => {
            addToRemoving(name, id);
        });
    }

    const startEdit = async (nameEntity) => {
        setNameEntityEditMap(nameEntity);
        await fetchNameEntityInfoAndAddToCurrent();
        setAddingArea();
        setRemovingArea();
        editAreaContainer.classList.remove('hidden');
    }

    document.getElementById('editUniverseBtn').addEventListener('click', async () => {
        await startEdit(NameEntity.UNIVERSES);
    });

    document.getElementById('editCharacterBtn').addEventListener('click', async () => {
        await startEdit(NameEntity.CHARACTERS);
        console.log(allNameEntityMap);
    });

    document.getElementById('editAuthorBtn').addEventListener('click', async () => {
        await startEdit(NameEntity.AUTHORS);
    });

    document.getElementById('editTagBtn').addEventListener('click', async () => {
        await startEdit(NameEntity.TAGS);
    });

    editAreaContainer.querySelector('.close-edit-name-btn').addEventListener('click', () => {
        editAreaContainer.classList.add('hidden');
    });

    editAreaContainer.querySelector('.save-edit-name-btn').addEventListener('click', async () => {
        const adding = [];
        currentNameEntityEditMap.get('adding').forEach((name, id) => {
            adding.push({name, id});
        });
        const removing = [];
        currentNameEntityEditMap.get('removing').forEach((name, id) => {
            removing.push({name, id});
        });
        if (removing.length === 0 && adding.length === 0) {
            alert('No edit found');
            return;
        }
        const body = {
            adding: adding,
            removing: removing,
            nameEntity: currentNameEntity.toUpperCase(),
        }
        const response = await fetch(`/api/modify/media/update/${mediaId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            alert(`Failed to save edit: ${await response.text()}`);
            return;
        }
        editAreaContainer.classList.add('hidden');
        allNameEntityMap.get(currentNameEntity).get('current').clear();
        allNameEntityMap.get(currentNameEntity).get('adding').clear();
        allNameEntityMap.get(currentNameEntity).get('removing').clear();
        const updatedNameEntities = await response.json();
        const names = [];
        for (const { id, name } of updatedNameEntities) {
            names.push(name);
            allNameEntityMap.get(currentNameEntity).get('current').set(id, name);
        }
        const mediaInfoTemp = {
            [currentNameEntity]: names,
        };
        const mainContainer = document.getElementById(document.body.dataset.mainContainerId);
        displayContentInfo(mediaInfoTemp, mainContainer);
    });

    await initializeEditAddingArea();
}

async function initializeEditAddingArea() {

    let searchTimeOut = null;
    const searchInput = editAreaContainer.querySelector('.adding-search-input');
    const searchEntryList = editAreaContainer.querySelector('.search-dropdown-ul');

    const searchEntryTem = searchEntryList.querySelector('li');
    const addSearchEntry = (nameEntity) => {
        const searchEntry = helperCloneAndUnHideNode(searchEntryTem);
        searchEntry.textContent = nameEntity.name;
        searchEntry.addEventListener('click', () => {
            if (currentNameEntityEditMap.get('adding').has(nameEntity.id)) return;
            if (currentNameEntityEditMap.get('current').has(nameEntity.id)) return;
            addToAdding(nameEntity.name, nameEntity.id);
        });
        searchEntryList.appendChild(searchEntry);
        searchEntryList.classList.remove('hidden');
    }

    const searchName = async (nameString) => {
        const response = await fetch(`/api/modify/name/${currentNameEntity}?name=${nameString}`);
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

    searchInput.addEventListener('blur', () => {
        setTimeout(() => {
            if (document.activeElement === searchEntryList)
                return
            const first = searchEntryList.firstElementChild;
            if (first) searchEntryList.replaceChildren(first);
            searchEntryList.classList.add('hidden');
        }, 100)
    });
    searchEntryList.addEventListener('blur', () => {
        setTimeout(() => {
            const first = searchEntryList.firstElementChild;
            if (first) searchEntryList.replaceChildren(first);
            searchEntryList.classList.add('hidden');
        }, 100);
    })
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}















