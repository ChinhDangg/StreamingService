document.getElementById('login-form').addEventListener('submit', async function (e) {
    e.preventDefault();
    const username = document.getElementById('email').value;
    const login = {
        username: username,
        password: document.getElementById('password').value,
    }
    const response = await fetch('/auth/login', {
        method: 'POST',
        credentials: "include",
        body: JSON.stringify(login),
        headers: {
            'Content-Type': 'application/json',
            'X-Login-Username': username,
        }
    });
    if (response.ok) {
        const params = new URLSearchParams(window.location.search);
        window.location.href = params.get('r') || '/';
    } else if (response.status === 401) {
        document.getElementById('login-error').classList.remove('hidden');
    }
});