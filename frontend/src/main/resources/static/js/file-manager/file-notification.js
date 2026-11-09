const PUSHED_BACK_TIMEOUT = 30000;
const MAX_BANNERS = 20;

// banner -> its running timeout
const bannerTimeouts = new Map();
// banner -> its stack. Banners with no timeout yet, they stay until a new notification pushes them back
const persistentBanners = new Map();

const infoStack = createStack('info-notification-stack');
const errorStack = createStack('error-notification-stack');

export function displayInfoMessage(message, hasTimeout = true, timeoutTime = 5000) {
    addBanner(infoStack, message, hasTimeout, timeoutTime);
}

export function displayErrorMessage(message, hasTimeout = false, timeoutTime = 5000) {
    addBanner(errorStack, message, hasTimeout, timeoutTime);
}

function createStack(containerId) {
    const container = document.getElementById(containerId);
    const stack = {
        container,
        list: container.querySelector('.notification-list'),
        template: container.querySelector('.notification-template'),
        clearAllRow: container.querySelector('.clear-all-notification-row'),
    };
    container.querySelector('.clear-all-notification-button').addEventListener('click', () => {
        [...stack.list.children].forEach(banner => closeBanner(stack, banner));
    });
    return stack;
}

function addBanner(stack, message, hasTimeout, timeoutTime) {
    pushBackPersistentBanners();

    if (stack.list.childElementCount >= MAX_BANNERS) {
        closeBanner(stack, stack.list.lastElementChild);
    }

    const banner = stack.template.content.firstElementChild.cloneNode(true);
    banner.querySelector('.notification-message').textContent = Array.isArray(message)
        ? message.join('\n')
        : message;
    banner.querySelector('.close-notification-button').addEventListener('click', () => {
        closeBanner(stack, banner);
    });
    stack.list.prepend(banner);

    if (hasTimeout) {
        startTimeout(stack, banner, timeoutTime);
    } else {
        persistentBanners.set(banner, stack);
    }

    updateStack(stack);
}

function pushBackPersistentBanners() {
    persistentBanners.forEach((stack, banner) => startTimeout(stack, banner, PUSHED_BACK_TIMEOUT));
    persistentBanners.clear();
}

function startTimeout(stack, banner, time) {
    bannerTimeouts.set(banner, setTimeout(() => closeBanner(stack, banner), time));
}

function closeBanner(stack, banner) {
    clearTimeout(bannerTimeouts.get(banner));
    bannerTimeouts.delete(banner);
    persistentBanners.delete(banner);
    banner.remove();
    updateStack(stack);
}

function updateStack(stack) {
    const bannerCount = stack.list.childElementCount;
    stack.container.classList.toggle('hidden', bannerCount === 0);
    stack.clearAllRow.classList.toggle('hidden', bannerCount < 2);
}
