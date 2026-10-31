import { getCookie } from "./csrf";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "";

const SAFE_METHODS = ["GET", "HEAD", "OPTIONS"];

export function apiUrl(path: string) {
    return `${API_URL}${path}`;
}

export async function apiFetch(
    path: string,
    options: RequestInit = {}
) {

    const headers = new Headers(options.headers);

    const method = (options.method ?? "GET").toUpperCase();

    if (!SAFE_METHODS.includes(method)) {
        const csrfToken = await getCsrfToken();

        if (csrfToken) {
            headers.set(
                "X-XSRF-TOKEN",
                csrfToken
            );
        }
    }

    const response = await fetch(
        apiUrl(path),
        {
            ...options,
            credentials: "include",
            headers
        }
    );

    return response;
}

// Shared while in flight: each /csrf call without the cookie writes a new token, so two parallel
// calls would leave the first request with a token the cookie no longer holds.
let csrfRequest: Promise<Response> | null = null;

// The backend deletes the XSRF-TOKEN cookie on login and writes a new one only when the token is
// read, so ask for it when it is missing instead of sending a request that would get a 403.
// Always send the cookie value: the token in the /csrf response body is masked and is rejected.
async function getCsrfToken() {

    const csrfToken = getCookie("XSRF-TOKEN");

    if (csrfToken) {
        return csrfToken;
    }

    csrfRequest ??= fetch(
        apiUrl("/api/v1/security/csrf"),
        { credentials: "include" }
    ).finally(() => {
        csrfRequest = null;
    });

    await csrfRequest;

    return getCookie("XSRF-TOKEN");
}
