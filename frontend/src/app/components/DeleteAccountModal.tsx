"use client";

import { useState } from "react";

interface DeleteAccountModalProps {
    onConfirm: () => Promise<void>;
    onCancel: () => void;
}

export default function DeleteAccountModal({
    onConfirm,
    onCancel,
}: DeleteAccountModalProps) {
    const [confirmText, setConfirmText] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const isConfirmEnabled = confirmText === "DELETE";

    const handleDelete = async () => {
        if (!isConfirmEnabled) {
            return;
        }

        setLoading(true);
        setError(null);

        try {
            await onConfirm();
        } catch {
            setError("Failed to delete account. Please try again.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
            <div className="max-w-lg w-full mx-4 bg-card border rounded-lg shadow-lg p-8">
                <h2 className="text-2xl font-bold mb-4 text-red-600">
                    Delete Account
                </h2>

                <div className="space-y-4 mb-6">
                    <p className="text-muted-foreground">
                        You are about to permanently delete your account.
                        This action cannot be undone.
                    </p>

                    <ul className="list-disc list-inside text-muted-foreground space-y-2">
                        <li>
                            All your expenses will be permanently deleted
                        </li>
                        <li>
                            All receipt files will be permanently removed
                            from our servers
                        </li>
                        <li>
                            You will be logged out immediately
                        </li>
                        <li>
                            This data cannot be recovered
                        </li>
                    </ul>
                </div>

                <div className="mb-6">
                    <label className="block text-sm font-medium mb-2">
                        Type <span className="font-bold">DELETE</span> to
                        confirm:
                    </label>
                    <input
                        type="text"
                        value={confirmText}
                        onChange={(e) => setConfirmText(e.target.value)}
                        placeholder="DELETE"
                        className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                        disabled={loading}
                    />
                </div>

                {error && (
                    <p className="text-sm text-red-500 mb-4">{error}</p>
                )}

                <div className="flex gap-4">
                    <button
                        onClick={onCancel}
                        disabled={loading}
                        className="flex-1 py-3 rounded-lg border hover:bg-accent transition disabled:opacity-50"
                    >
                        Cancel
                    </button>

                    <button
                        onClick={handleDelete}
                        disabled={!isConfirmEnabled || loading}
                        className="flex-1 py-3 rounded-lg bg-red-600 text-white font-medium hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                    >
                        {loading ? "Deleting..." : "Delete Account"}
                    </button>
                </div>
            </div>
        </div>
    );
}
