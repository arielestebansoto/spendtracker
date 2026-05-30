import { apiFetch } from "./api";
const API_CSRF_URL = process.env.NEXT_PUBLIC_API_CSRF_URL;

export async function initializeSession() {

    await apiFetch("/api/v1/security/csrf");
    
    return getCurrentUser();
}

export async function getCurrentUser() {

    const response = await apiFetch(
        "/api/v1/user/me"
    );

    if (response.status === 401) {
        return null;
    }

    return response.json();
}

export async function logout() {
    
    const response = await apiFetch(
        "/api/v1/auth/logout",
        {
            method: "POST",
        }
    );

    if (!response.ok) {
        throw new Error("Logout failed");
    }
}

export async function initializeSecurity() {
    await apiFetch("/api/v1/security/csrf");
}