import {FileManager} from "/static/js/file-manager/FileManager.js";
import {apiRequest, handleResponse} from "/static/js/common.js";
import {
    currentMainFileItems,
    displayFileItem,
    getCurrentPath,
    setObserverToFetchMore, unobserveSentinel, setPageObserver, setCurrentViewState, ViewState, getPreviousViewState
} from "/static/js/file-manager/file-manager-page.js";
import {displayErrorMessage, displayInfoMessage} from "/static/js/file-manager/file-notification.js";

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

async function searchFiles(searchTerm) {
    if (!searchTerm || searchTerm.length === 0) {
        clearSearch();
    }
    if (searchTerm.length < 2) {
        isInDeepSearch = false;
        return;
    }
    setCurrentViewState(ViewState.SEARCH);
    clearSearchButton.classList.remove('hidden');
    if (currentMainFileItems.getCurrentFilePage() === null && !recursiveToggle.checked) {
        displayInfoMessage('Searching locally');
        let filteredFileIds = currentMainFileItems.findFileItemsWithNameAndReturnTheirIds(searchTerm);
        filteredFileIds = filteredFileIds.length === 0 ? null : filteredFileIds;
        displayFileItem(filteredFileIds, currentMainFileItems, true, false, false, true);
    } else {
        if (recursiveToggle.checked) {
            displayInfoMessage('Searching recursively');
            isInDeepSearch = true;
        } else
            displayInfoMessage('Searching');
        displayFileItem([], currentSearchFileItems,true, true, false);
        await fetchSearchFiles(searchTerm, null);
        setObserverToSearch(searchTerm);
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
    const { success, data, message } = await handleResponse(response);
    if (!success) {
        displayErrorMessage(`Failed to search files: ${message}`);
        return;
    }
    const searchResult = data;
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
    searchInput.value = '';
    clearSearchButton.classList.add('hidden');
    setCurrentViewState(getPreviousViewState());
    setObserverToFetchMore();
    displayFileItem([], currentMainFileItems, true, false, false, true);
}

function setObserverToSearch(searchString) {
    const entriesCallback = async (entries) => {
        if (entries[0].isIntersecting) {
            console.log('Intersecting in search');
            if (currentSearchFileItems.getCurrentFilePage() === null) {
                unobserveSentinel();
                return;
            }
            await fetchSearchFiles(searchString, currentSearchFileItems.getCurrentFilePage());
        }
    }
    setPageObserver(entriesCallback);
}