"use client";

import Link from "next/link";

type NavbarProps = {
    userName?: string;
    onLogout?: () => void;
};

export default function Navbar({
    userName,
    onLogout,
}: NavbarProps) {
    return (
        <header className="border-b">
            <div className="container mx-auto px-6 h-16 flex items-center justify-between">
                <Link
                    href="/"
                    className="text-lg font-semibold"
                >
                    Spend Tracker AI
                </Link>

                {userName && onLogout && (
                    <div className="flex items-center gap-4">
                        <span className="text-sm">
                            {userName}
                        </span>

                        <button
                            onClick={onLogout}
                            className="px-4 py-2 rounded-lg border hover:bg-gray-100 hover:text-black transition"
                        >
                            Logout
                        </button>
                    </div>
                )}
            </div>
        </header>
  );
}
