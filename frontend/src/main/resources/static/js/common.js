let media_id = null;
export function setMediaId(id) {
    media_id = id;
}
export function getMediaId() {
    return media_id;
}

let media_length = 0;
export function setMediaLength(length) {
    media_length = length;
}
export function getMediaLength() {
    return media_length;
}

const CSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

export async function apiRequest(url, options = {}) {
    if (["POST","PUT","DELETE","PATCH"].includes(options.method?.toUpperCase())) {
        options.headers = {
            ...(options.headers || {}),
            [CSRF_TOKEN_HEADER]: getCsrfToken()
        };
    }
    let res = await fetch(url, options);

    if (res.status === 401) {
        const refreshRes = await fetch("/auth/refresh", {
            method: "POST",
            headers: { [CSRF_TOKEN_HEADER]: getCsrfToken() }
        });

        if (refreshRes.ok) {
            // new cookies issued
            if (options.headers)
                options.headers[CSRF_TOKEN_HEADER] = getCsrfToken();
            return await fetch(url, options); // retry original call
        }

        // refresh failed → force login
        redirectToLogin();
        return;
    }

    return res;
}

function getCsrfToken() {
    const name = "XSRF-TOKEN=";
    const decoded = decodeURIComponent(document.cookie);
    const parts = decoded.split('; ');

    for (const part of parts) {
        if (part.startsWith(name)) {
            return part.substring(name.length);
        }
    }
    return null;
}

function redirectToLogin() {
    const current = window.location.pathname + window.location.search;
    const loginPage = "/page/login";

    // if already on login page, do nothing
    if (window.location.pathname === loginPage) {
        return;
    }

    // URL encode
    const encoded = encodeURIComponent(current);

    window.location.href = `${loginPage}?r=${encoded}`;
}

const ALLOWED_MIME_TYPES = [
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/gif",
    "image/webp"
];

function validateImageType(file) {
    return ALLOWED_MIME_TYPES.includes(file.type);
}

const ALLOWED_EXTENSIONS = [".png", ".jpg", ".jpeg", "gif", ".webp"];

function validateImageExtension(file) {
    const name = file.name.toLowerCase();
    return ALLOWED_EXTENSIONS.some(ext => name.endsWith(ext));
}

const MAX_SIZE_MB = 5;

function validateFileSize(file) {
    return file.size < MAX_SIZE_MB * 1024 * 1024;
}

async function validateImageMagicBytes(file) {
    const buffer = await file.slice(0, 12).arrayBuffer();
    const bytes = new Uint8Array(buffer);

    // PNG
    const isPNG =
        bytes.length >= 8 &&
        bytes[0] === 0x89 &&
        bytes[1] === 0x50 &&
        bytes[2] === 0x4E &&
        bytes[3] === 0x47 &&
        bytes[4] === 0x0D &&
        bytes[5] === 0x0A &&
        bytes[6] === 0x1A &&
        bytes[7] === 0x0A;

    // JPEG
    const isJPEG =
        bytes.length >= 3 &&
        bytes[0] === 0xFF &&
        bytes[1] === 0xD8 &&
        bytes[2] === 0xFF;

    // WebP (RIFF....WEBP)
    const isWEBP =
        bytes.length >= 12 &&
        bytes[0] === 0x52 && // R
        bytes[1] === 0x49 && // I
        bytes[2] === 0x46 && // F
        bytes[3] === 0x46 &&
        bytes[8] === 0x57 && // W
        bytes[9] === 0x45 && // E
        bytes[10] === 0x42 && // B
        bytes[11] === 0x50;

    return isPNG || isJPEG || isWEBP;
}

export async function validateImage(file) {
    if (!file) {
        throw new Error('No file provided');
    }

    if (!validateImageType(file)) {
        throw new Error('Invalid file type. Allowed types: ' + ALLOWED_MIME_TYPES.join(', '));
    }

    if (!validateImageExtension(file)) {
        throw new Error('Invalid file extension. Allowed extensions: ' + ALLOWED_EXTENSIONS.join(', '));
    }

    if (!(await validateImageMagicBytes(file))) {
        throw new Error("Invalid image file signature");
    }

    if (!validateFileSize(file)) {
        throw new Error(`File too large (max ${MAX_SIZE_MB}MB)`);
    }

    return true;
}