import { getCookie } from "./csrf";

export async function apiFetch(
    path: string,
    options: RequestInit = {}
) {

    const headers = new Headers(options.headers);

    const csrfToken = getCookie("XSRF-TOKEN");

    if (
        csrfToken &&
        options.method &&
        options.method !== "GET"
    ) {
        headers.set(
            "X-XSRF-TOKEN",
            csrfToken
        );
    }

    const response = await fetch(
        `${path}`,
        {
            ...options,
            credentials: "include",
            headers
        }
    );

    return response;
}