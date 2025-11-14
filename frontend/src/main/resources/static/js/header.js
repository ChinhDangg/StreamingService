
export function initializeHeader() {
    console.log('initialize header');
    initializeHeaderPageTitle();
    initializeSearchForm();
    initializeNameBrowseOptions();
}

function initializeHeaderPageTitle() {
    const searchPageTitle = document.getElementById('header-page-title');
    searchPageTitle.href = '/page/search';
}

function initializeSearchForm() {
    console.log('initialize search form from header page');
    document.querySelector('#search-form').addEventListener('submit', (e) => {
        e.preventDefault();

        const searchString = validateSearchString(document.querySelector('#search-input').value);
        if (!searchString)
            return;

        window.location.href = `/page/search?searchString=${searchString}`;
    });
}

export function validateSearchString(searchString) {
    searchString = searchString.trim();
    if (searchString.length < 2) {
        setAlertStatus('Invalid search string', 'Search string must be at least 2 characters');
        return false;
    } else if (searchString.length > 100) {
        setAlertStatus('Invalid search string', 'Search string exceeded 200 characters');
        return false;
    }
    return searchString;
}

let alertStatusTimer = null;
const alertStatus = document.getElementById('alert-status');
export function setAlertStatus(boldStatus, normalText) {
    clearTimeout(alertStatusTimer);
    alertStatus.querySelector('#bold-status').innerText = boldStatus;
    alertStatus.querySelector('#normal-text').innerText = normalText;
    alertStatus.classList.remove('hidden');
    alertStatusTimer = setTimeout(() => {
        alertStatus.classList.add('hidden');
    }, 10000);
}

const NameEntry = Object.freeze({
    Characters: 'characters',
    Universes: 'universes',
    Authors: 'authors',
    Tags: 'tags',
});

export function initializeNameBrowseOptions() {
    const browseOptionContainer = document.getElementById('browse-option-container');
    const first = browseOptionContainer.firstElementChild;
    if (first) browseOptionContainer.replaceChildren(first);
    for (const [key, value] of Object.entries(NameEntry)) {
        const browseOption = helperCloneAndUnHideNode(first);
        browseOption.href = `/page/browse/${value}`;
        browseOption.textContent = key;
        browseOptionContainer.appendChild(browseOption);
    }
}

export function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}