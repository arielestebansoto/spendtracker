"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { initializeSession, logout } from "../lib/auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

type User = {
  id: string;
  name: string;
  email: string;
};

type Spend = {
  id: string;
  category: string;
  amount: number;
  currency: string;
  spendDate: string;
  description: string;
};

type PageResponse<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export default function DashboardPage() {
    const router = useRouter();

    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    const [spends, setSpends] = useState<Spend[]>([]);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    useEffect(() => {
        async function load() {
            try {
                const currentUser = await initializeSession();

                if (!currentUser) {
                    router.replace("/");
                    return;
                }

                setUser(currentUser);
            } finally {
                setIsLoading(false);
            }
        }

        load();
    }, [router]);

    useEffect(() => {
        if (!user) {
            return;
        }

        loadSpends(currentPage);
    }, [user, currentPage]);

    async function loadSpends(page: number) {
        try {
            const response = await fetch(
                `${API_URL}/api/v1/spends?page=${page}`,
                {
                credentials: "include",
                }
            );

            if (!response.ok) {
                throw new Error("Failed to load spends");
            }

            const data: PageResponse<Spend> = await response.json();

            setSpends(data.content);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch (error) {
            console.error(error);
        }
    }

    async function handleLogout() {
        await logout();
        router.replace("/");
    }

    if (isLoading) {
        return (
            <div className="p-6">
                Checking authentication...
            </div>
        );
    }

    if (!user) {
        return null;
    }

    return (
        <div className="max-w-6xl mx-auto p-6">
            <div className="flex justify-between items-center mb-8">
                <div>
                    <h1 className="text-3xl font-bold">
                        Dashboard
                    </h1>

                    <p className="mt-2">
                        Welcome {user.name}
                    </p>
                </div>

                <button
                    onClick={handleLogout}
                    className="px-4 py-2 rounded-lg border hover:bg-gray-100 hover:text-black transition"
                >
                    Logout
                </button>
            </div>

            <div className="flex justify-between items-center mb-6">
                <p>
                    Showing {spends.length} of {totalElements} expenses
                </p>

                <button
                    onClick={() =>
                        router.push("/dashboard/spends/new")
                    }
                    className="px-4 py-2 rounded-lg border hover:bg-gray-100 hover:text-black transition"
                >
                    New Expense
                </button>
            </div>

            <div className="overflow-x-auto border rounded-lg">
                <table className="w-full">
                    <thead>
                        <tr className="border-b">
                            <th className="text-left p-4">Date</th>
                            <th className="text-left p-4">Category</th>
                            <th className="text-left p-4">Amount</th>
                            <th className="text-left p-4">
                                Description
                            </th>
                            <th className="text-left p-4">
                                Actions
                            </th>
                        </tr>
                    </thead>

                    <tbody>
                        {spends.map((spend) => (
                            <tr
                                key={spend.id}
                                className="border-b"
                            >
                                <td className="p-4">
                                    {spend.spendDate}
                                </td>

                                <td className="p-4">
                                    {spend.category}
                                </td>

                                <td className="p-4">
                                    {spend.currency} {spend.amount}
                                </td>

                                <td className="p-4">
                                    {spend.description}
                                </td>

                                <td className="p-4">
                                    <div className="flex gap-2">
                                        <button
                                            onClick={() =>
                                                router.push(
                                                `/dashboard/spends/${spend.id}`
                                                )
                                            }
                                            className="px-3 py-1 border rounded"
                                        >
                                            View
                                        </button>

                                        <button
                                            onClick={() =>
                                                router.push(
                                                `/dashboard/spends/${spend.id}/edit`
                                                )
                                            }
                                            className="px-3 py-1 border rounded"
                                        >
                                            Edit
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}

                        {spends.length === 0 && (
                            <tr>
                                <td
                                    colSpan={5}
                                    className="p-8 text-center"
                                >
                                    No expenses found
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            <div className="flex justify-center items-center gap-4 mt-6">
                <button
                    disabled={currentPage === 0}
                    onClick={() =>
                        setCurrentPage((p) => p - 1)
                    }
                    className="px-4 py-2 border rounded disabled:opacity-50"
                >
                    Previous
                </button>

                <span>
                    Page {currentPage + 1} of{" "}
                    {Math.max(totalPages, 1)}
                </span>

                <button
                    disabled={
                        totalPages === 0 ||
                        currentPage >= totalPages - 1
                    }
                    onClick={() =>
                        setCurrentPage((p) => p + 1)
                    }
                    className="px-4 py-2 border rounded disabled:opacity-50"
                >
                    Next
                </button>
            </div>
        </div>
    );
}