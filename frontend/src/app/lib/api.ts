import { getCookie } from "./csrf";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "";

export function apiUrl(path: string) {
    return `${API_URL}${path}`;
}

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
        apiUrl(path),
        {
            ...options,
            credentials: "include",
            headers
        }
    );

    return response;
}