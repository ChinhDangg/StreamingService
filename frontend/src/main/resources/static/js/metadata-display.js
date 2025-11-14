
export function displayContentInfo(mediaInfo, mainContainer) {
    mainContainer.querySelector('.main-title').textContent = mediaInfo.title;

    if (mediaInfo.authors) {
        const authorContainer = mainContainer.querySelector('.authors-info-container');
        const first = authorContainer.firstElementChild;
        if (first) authorContainer.replaceChildren(first);

        const authorNodeTem = authorContainer.querySelector('.author-info');
        mediaInfo.authors.forEach(author => {
            const authorNode = helperCloneAndUnHideNode(authorNodeTem);
            authorNode.href = `/page/search?authors=${author}`;
            authorNode.textContent = author;
            authorContainer.appendChild(authorNode);
        });
    }

    if (mediaInfo.universes) {
        const universeContainer = mainContainer.querySelector('.universes-info-container');
        const first = universeContainer.firstElementChild;
        if (first) universeContainer.replaceChildren(first);

        const universeNodeTem = universeContainer.querySelector('.universe-info');
        mediaInfo.universes.forEach(universe => {
            const universeNode = helperCloneAndUnHideNode(universeNodeTem);
            universeNode.href = `/page/search?universes=${universe}`;
            universeNode.textContent = universe;
            universeContainer.appendChild(universeNode);
        });
    }

    if (mediaInfo.characters) {
        const characterContainer = mainContainer.querySelector('.characters-info-container');
        const first = characterContainer.firstElementChild;
        if (first) characterContainer.replaceChildren(first);

        const characterNodeTem = characterContainer.querySelector('.character-info');
        mediaInfo.characters.forEach(character => {
            const characterNode = helperCloneAndUnHideNode(characterNodeTem);
            characterNode.href = `/page/search?characters=${character}`;
            characterNode.textContent = character;
            characterContainer.appendChild(characterNode);
        });
    }

    if (mediaInfo.tags) {
        const tagContainer = mainContainer.querySelector('.tags-info-container');
        const first = tagContainer.firstElementChild;
        if (first) tagContainer.replaceChildren(first);

        const tagNodeTem = tagContainer.querySelector('.tag-info');
        mediaInfo.tags.forEach(tag => {
            const tagNode = helperCloneAndUnHideNode(tagNodeTem);
            tagNode.href = `/page/search?tags=${tag}`;
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