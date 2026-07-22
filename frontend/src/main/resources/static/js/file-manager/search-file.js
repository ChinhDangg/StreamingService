import {FileManager} from "/static/js/file-manager/FileManager.js";
import {apiRequest} from "/static/js/common.js";
import {
    currentMainFileItems,
    displayFileItem,
    getCurrentPath,
    observeSentinel, setObserver, setObserverToFetchMore, unobserveSentinel, disconnectObserver
} from "/static/js/file-manager/file-manager-page.js";

const searchForm = document.getElementById('search-form');
const searchInput = searchForm.querySelector('.search-input');
const searchButton = searchForm.querySelector('.search-btn');
const clearSearchButton = searchForm.querySelector('.clear-search-btn');
const recursiveToggle = searchForm.querySelector('.recursive-toggle');

searchForm.addEventListener("submit", async function (e) {
    e.preventDefault();
});

searchButton.addEventListener("click", async function () {
    await searchFiles(searchInput.value);
});

export const currentSearchFileItems = new FileManager();
let isInDeepSearch = false;
export function getIsInDeepSearch() {
    return isInDeepSearch;
}
export function setIsInDeepSearch(deepSearch) {
    isInDeepSearch = deepSearch;
}

let isSearching = false;
export function getIsSearching() {
    return isSearching;
}
export function setIsSearching(searching) {
    isSearching = searching;
}

async function searchFiles(searchTerm) {
    if (!searchTerm || searchTerm.length === 0) {
        clearSearch();
    }
    if (searchTerm.length < 2) {
        isInDeepSearch = false;
        return;
    }
    isSearching = true;
    clearSearchButton.classList.remove('hidden');
    if (currentMainFileItems.getCurrentFilePage() === null && !recursiveToggle.checked) {
        console.log('searching locally');
        let filteredFileIds = currentMainFileItems.findFileItemsWithNameAndReturnTheirIds(searchTerm);
        filteredFileIds = filteredFileIds.length === 0 ? null : filteredFileIds;
        displayFileItem(filteredFileIds, currentMainFileItems, true, false, false, true);
    } else {
        if (recursiveToggle.checked)
            isInDeepSearch = true;
        displayFileItem([], currentSearchFileItems,true, true, false);
        unobserveSentinel();
        setObserverToSearch(searchTerm);
        await fetchSearchFiles(searchTerm, null);
        observeSentinel();
    }
}

async function fetchSearchFiles(searchString, page) {
    const currentPath = getCurrentPath();
    if (!currentPath)
        return;
    const response = await apiRequest('/api/file/search', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            parentId: currentPath.id,
            searchString: searchString,
            isRecursive: recursiveToggle.checked,
            pageCursor: page
        })
    });
    if (!response.ok) {
        alert('Failed to search files');
        return;
    }
    const searchResult = await response.json();
    if (searchResult.hasNext)
        currentSearchFileItems.setCurrentFilePage(searchResult.nextCursor);
    else
        currentSearchFileItems.setCurrentFilePage(null);
    const subFiles = searchResult.content;
    displayFileItem(subFiles, currentSearchFileItems,false, false, true);
}

clearSearchButton.addEventListener("click", async function () {
    clearSearch();
});

export function clearSearch() {
    isSearching = false;
    searchInput.value = '';
    clearSearchButton.classList.add('hidden');
    setObserverToFetchMore();
    displayFileItem([], currentMainFileItems, true, false, false, true);
}

function setObserverToSearch(searchString) {
    disconnectObserver();
    const searchObserver = new IntersectionObserver(async (entries) => {
        if (entries[0].isIntersecting) {
            console.log('Intersecting in search');
            if (currentSearchFileItems.getCurrentFilePage() === null) {
                unobserveSentinel();
                return;
            }
            await fetchSearchFiles(searchString, currentSearchFileItems.getCurrentFilePage());
        }
    }, { rootMargin: '500px' });
    setObserver(searchObserver);
}