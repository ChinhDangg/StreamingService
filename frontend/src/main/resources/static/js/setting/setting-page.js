import {STORAGE_KEY, defaultSettingStates, loadSettings} from "/static/js/setting/get-setting.js";

const toastEl = document.getElementById('toast');
const toastMsgEl = document.getElementById('toastMsg');
const resetBtn = document.getElementById('resetAllBtn');
let toastTimer = null;

function saveSettings(settings) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch (e) {
        console.warn('Storage save error:', e);
    }
}

// Visual update function for a switch button
function updateSwitchVisual(btn, isOn) {
    const knob = btn.querySelector('span[aria-hidden="true"]');
    btn.setAttribute('aria-checked', isOn ? 'true' : 'false');

    if (isOn) {
        btn.classList.remove('bg-gray-700');
        btn.classList.add('bg-indigo-600');
        knob.classList.remove('translate-x-0');
        knob.classList.add('translate-x-5');
    } else {
        btn.classList.remove('bg-indigo-600');
        btn.classList.add('bg-gray-700');
        knob.classList.remove('translate-x-5');
        knob.classList.add('translate-x-0');
    }
}

// Toast feedback helper
function showToast(message) {
    if (!toastEl || !toastMsgEl) return;
    toastMsgEl.textContent = message;
    toastEl.classList.remove('opacity-0', 'pointer-events-none');
    toastEl.classList.add('opacity-100');

    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toastEl.classList.remove('opacity-100');
        toastEl.classList.add('opacity-0', 'pointer-events-none');
    }, 2200);
}

// Initial apply to DOM
const currentSettings = loadSettings();
const switches = document.querySelectorAll('.toggle-btn');

switches.forEach(btn => {
    const id = btn.getAttribute('data-setting-id');
    if (id && currentSettings.hasOwnProperty(id)) {
        updateSwitchVisual(btn, currentSettings[id]);
    }

    // Click / Touch Toggle
    btn.addEventListener('click', function () {
        const isCurrentlyOn = btn.getAttribute('aria-checked') === 'true';
        const nextState = !isCurrentlyOn;

        updateSwitchVisual(btn, nextState);
        currentSettings[id] = nextState;
        saveSettings(currentSettings);

        showToast(`Saved preference`);
    });

    // Accessible Keyboard (Space & Enter already trigger button clicks by default)
});

// Handle Reset All Defaults
if (resetBtn) {
    resetBtn.addEventListener('click', function () {
        Object.assign(currentSettings, defaultSettingStates);
        saveSettings(currentSettings);
        switches.forEach(btn => {
            const id = btn.getAttribute('data-setting-id');
            if (id && defaultSettingStates.hasOwnProperty(id)) {
                updateSwitchVisual(btn, defaultSettingStates[id]);
            }
        });
        showToast('Reset all to defaults');
    });
}

// Sidebar link active state on click
const sidebarLinks = document.querySelectorAll('.sidebar-link');
sidebarLinks.forEach(link => {
    link.addEventListener('click', function () {
        sidebarLinks.forEach(l => {
            l.classList.remove('bg-gray-800/90', 'text-white', 'border', 'border-gray-700/40', 'shadow-sm');
            l.classList.add('text-gray-400');
        });
        this.classList.add('bg-gray-800/90', 'text-white', 'border', 'border-gray-700/40', 'shadow-sm');
        this.classList.remove('text-gray-400');
    });
});