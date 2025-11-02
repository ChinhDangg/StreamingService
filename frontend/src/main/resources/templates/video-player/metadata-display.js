export function displayContentInfo(mediaInfo) {
    const mainContainer = document.getElementById('main-container');
    mainContainer.querySelector('.main-title').textContent = mediaInfo.title;

    if (mediaInfo.authors) {
        const authorContainer = mainContainer.querySelector('.authors-info-container');
        const authorNodeTem = authorContainer.querySelector('.author-info');
        mediaInfo.authors.forEach(author => {
            const authorNode = helperCloneAndUnHideNode(authorNodeTem);
            authorNode.href = '/search-page/author/' + author;
            authorNode.textContent = author;
            authorContainer.appendChild(authorNode);
        });
    }

    if (mediaInfo.universes) {
        const universeContainer = mainContainer.querySelector('.universes-info-container');
        const universeNodeTem = universeContainer.querySelector('.universe-info');
        mediaInfo.universes.forEach(universe => {
            const universeNode = helperCloneAndUnHideNode(universeNodeTem);
            universeNode.href = '/search-page/universe/' + universe;
            universeNode.textContent = universe;
            universeContainer.appendChild(universeNode);
        });
    }

    if (mediaInfo.tags) {
        const tagContainer = mainContainer.querySelector('.tags-info-container');
        const tagNodeTem = tagContainer.querySelector('.tag-info');
        mediaInfo.tags.forEach(tag => {
            const tagNode = helperCloneAndUnHideNode(tagNodeTem);
            tagNode.href = '/search-page/tag/' + tag;
            tagNode.textContent = tag;
            tagContainer.appendChild(tagNode);
        });
    }
}

export function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}