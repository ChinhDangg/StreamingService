export const STORAGE_KEY = 'app_settings_states';

// Default presets
export const defaultSettingStates = {
    original_resolution: false
};

export function loadSettings() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) {
            return Object.assign({}, defaultSettingStates, JSON.parse(raw));
        }
    } catch (e) {
        console.warn('Storage read error:', e);
    }
    return Object.assign({}, defaultSettingStates);
}

export function getResolutionSetting() {
    const currentSetting = loadSettings();
    return currentSetting.original_resolution;
}

export function getResolutionSettingAsHeader() {
    return { 'X-Original-Resolution': getResolutionSetting() }
}