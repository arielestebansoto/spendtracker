"use client";

type DeleteSpendModalProps = {
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting?: boolean;
};

export default function DeleteSpendModal({
  onConfirm,
  onCancel,
  isDeleting = false,
}: DeleteSpendModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-card rounded-xl shadow-xl w-full max-w-sm p-6">
        <h2 className="text-lg font-semibold">Delete spend?</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          This action cannot be undone. The spend will be permanently deleted.
        </p>

        <div className="mt-6 flex justify-end gap-3">
          <button
            onClick={onCancel}
            disabled={isDeleting}
            className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition disabled:opacity-60"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={isDeleting}
            className="px-4 py-2 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
          >
            {isDeleting ? "Deleting..." : "Delete"}
          </button>
        </div>
      </div>
    </div>
  );
}
