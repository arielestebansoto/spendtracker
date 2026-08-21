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
    if (!isConfirmEnabled) return;

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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-card rounded-xl shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-destructive">Delete account</h2>

        <p className="mt-2 text-sm text-muted-foreground">
          You are about to permanently delete your account. This action cannot be undone.
        </p>

        <ul className="mt-3 text-sm text-muted-foreground space-y-1 list-disc list-inside">
          <li>All your expenses will be permanently deleted</li>
          <li>You will be logged out immediately</li>
          <li>This data cannot be recovered</li>
        </ul>

        <div className="mt-5">
          <label className="block text-sm font-medium mb-1.5">
            Type <span className="font-bold">DELETE</span> to confirm:
          </label>
          <input
            type="text"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder="DELETE"
            disabled={loading}
            className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        {error && (
          <p className="mt-3 text-sm text-destructive">{error}</p>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <button
            onClick={onCancel}
            disabled={loading}
            className="px-4 py-2.5 rounded-lg border border-border text-sm hover:bg-accent transition disabled:opacity-60"
          >
            Cancel
          </button>
          <button
            onClick={handleDelete}
            disabled={!isConfirmEnabled || loading}
            className="px-4 py-2.5 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
          >
            {loading ? "Deleting..." : "Delete account"}
          </button>
        </div>
      </div>
    </div>
  );
}
