"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { initializeSession, logout } from "../lib/auth";

export default function DashboardPage() {

    const router = useRouter();

    const [user, setUser] = useState<any>(null);

    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {

        async function load() {

            try {

                const currentUser = await initializeSession();

                if (!currentUser) {
                    router.replace("/login");
                    return;
                }

                setUser(currentUser);

            } finally {

                setIsLoading(false);
            }
        }

        load();

    }, []);

    async function handleLogout() {

        await logout();

        router.replace("/");
    }

    if (isLoading) {
        return (
            <div>
                Checking authentication...
            </div>
        );
    }

    if (!user) {
        return null;
    }

    return (
        <div>
            <h1>Dashboard</h1>

            <p>
                Welcome {user.name}
            </p>

            <button onClick={handleLogout}>
                Logout
            </button>
        </div>
    );
}