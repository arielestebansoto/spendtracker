"use client";

import Link from "next/link";

type NavbarProps = {
    userName?: string;
    onLogout?: () => void;
    onDeleteAccount?: () => void;
};

export default function Navbar({
    userName,
    onLogout,
    onDeleteAccount,
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

                        {onDeleteAccount && (
                            <button
                                onClick={onDeleteAccount}
                                className="px-4 py-2 rounded-lg border text-red-600 hover:bg-red-50 hover:text-red-700 transition"
                            >
                                Delete Account
                            </button>
                        )}

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
