export function displayPagination(page, totalPages, getPageUrl, pageClickHandler) {
    const paginationTop = document.getElementById('pagination-node-top');
    const pageNodeBottom = document.getElementById('pagination-node-bottom');

    if (page === 0 && page === totalPages) {
        if (paginationTop)
            paginationTop.classList.add('hidden');
        pageNodeBottom.classList.add('hidden');
        return;
    }

    const pageContainer = pageNodeBottom.querySelector('.page-container');
    const pageLinkNodeTem = pageContainer.querySelector('.page-link-node');
    const pageNumContainer = pageNodeBottom.querySelector('.page-num-container');

    const leftControl = pageNodeBottom.querySelector('.page-left-control');
    const rightControl = pageNodeBottom.querySelector('.page-right-control');
    const goFirstControl = leftControl.querySelector('.page-first-control');
    const goLastControl = rightControl.querySelector('.page-last-control');

    rightControl.replaceChildren(goLastControl)

    // --- prev / first ---
    if (page > 0) {
        const prevControl = leftControl.querySelector('.page-prev-control');
        prevControl.classList.remove('hidden');
        const prevIndex = page - 1;
        prevControl.href = getPageUrl(prevIndex);
        prevControl.onclick = async (e) => await pageClickHandler(e, prevIndex);

        const prevControlDup = helperCloneAndUnHideNode(prevControl);
        prevControlDup.onclick = async (e) => await pageClickHandler(e, prevIndex);
        rightControl.appendChild(prevControlDup);

        goFirstControl.href = getPageUrl(0);
        goFirstControl.onclick = async (e) => await pageClickHandler(e, 0);
    } else {
        leftControl.querySelector('.page-prev-control').classList.add('hidden');
        goFirstControl.classList.add('hidden');
    }

    // --- next / last ---
    if (page < totalPages - 1) {
        const nextControl = leftControl.querySelector('.page-next-control');
        nextControl.classList.remove('hidden');
        const nextIndex = page + 1;
        nextControl.href = getPageUrl(nextIndex);
        nextControl.onclick = async (e) => await pageClickHandler(e, nextIndex);

        const nextControlDup = helperCloneAndUnHideNode(nextControl);
        nextControlDup.onclick = async (e) => await pageClickHandler(e, nextIndex);
        rightControl.appendChild(nextControlDup);

        goLastControl.href = getPageUrl(totalPages - 1);
        goLastControl.onclick = async (e) => await pageClickHandler(e, totalPages - 1);
    } else {
        leftControl.querySelector('.page-next-control').classList.add('hidden');
        goLastControl.classList.add('hidden');
    }

    // --- numbered pages ---
    const start = Math.max(page - 2, 0);
    const maxPageShow = start + 5;

    pageNumContainer.innerHTML = '';
    for (let i = start; i < totalPages; i++) {
        let currentPage = i;
        let reachedMax = false;

        if (currentPage === maxPageShow) {
            if (maxPageShow < totalPages - 1) {
                const threeDots = helperCloneAndUnHideNode(pageContainer.querySelector('.page-dots'));
                pageNumContainer.appendChild(threeDots);
                currentPage = totalPages - 1;
            }
            reachedMax = true;
        }

        const pageLinkNode = (currentPage !== page)
            ? helperCloneAndUnHideNode(pageLinkNodeTem)
            : helperCloneAndUnHideNode(pageContainer.querySelector('.page-selected-link-node'));

        pageLinkNode.innerText = currentPage + 1;
        pageLinkNode.href = getPageUrl(currentPage);

        if (currentPage === page) {
            pageLinkNode.onclick = (e) => e.preventDefault();
        } else {
            pageLinkNode.onclick = async (e) => await pageClickHandler(e, currentPage);
        }

        pageNumContainer.appendChild(pageLinkNode);

        if (reachedMax) break;
    }

    pageNodeBottom.classList.remove('hidden');

    if (!paginationTop)
        return;
    // --- clone bottom block to top and preserve click handlers ---
    paginationTop.classList.remove('hidden');
    paginationTop.innerHTML = '';
    const paginationNodeDup = helperCloneAndUnHideNode(pageNodeBottom.firstElementChild);
    paginationTop.appendChild(paginationNodeDup);

    // copy hrefs and on-clicks from bottom to top anchors
    const bottomAnchors = pageNodeBottom.querySelectorAll('a');
    const topAnchors = paginationTop.querySelectorAll('a');
    topAnchors.forEach((topA, i) => {
        const bottomA = bottomAnchors[i];
        if (bottomA) {
            topA.href = bottomA.href;
            topA.onclick = bottomA.onclick;
        }
    });
}

function helperCloneAndUnHideNode(node) {
    const clone = node.cloneNode(true);
    clone.classList.remove('hidden');
    return clone;
}