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
