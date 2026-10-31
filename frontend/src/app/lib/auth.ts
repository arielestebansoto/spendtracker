import { apiFetch } from "./api";

export async function getCurrentUser() {

    const response = await apiFetch(
        "/api/v1/user/me"
    );

    if (!response.ok) {
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

export async function deleteAccount() {
    const response = await apiFetch("/api/v1/user/me", {
        method: "DELETE",
    });

    if (!response.ok) {
        throw new Error("Failed to delete account");
    }
}
