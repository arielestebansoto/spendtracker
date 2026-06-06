export function getCookie(name: string): string | null {

    const value =
        document.cookie
            .split("; ")
            .find(row => row.startsWith(name + "="));

    return value
        ? decodeURIComponent(value.split("=")[1])
        : null;
}