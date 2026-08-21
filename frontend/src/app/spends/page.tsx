"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import LoadingState from "@/app/components/LoadingState";
import EmptyState from "@/app/components/EmptyState";
import ErrorState from "@/app/components/ErrorState";
import DeleteSpendModal from "@/app/components/DeleteSpendModal";
import Toast, { ToastVariant } from "@/app/components/Toast";

type Spend = {
  id: string;
  category: string;
  amount: number;
  spendDate: string;
  description: string;
};

type PageResponse<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
};

function formatCurrency(amount: number) {
  return `$${amount.toLocaleString()}`;
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr + "T00:00:00");
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function SpendsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();

  const [spends, setSpends] = useState<Spend[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");

  const [deleteTarget, setDeleteTarget] = useState<Spend | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const [toast, setToast] = useState<{ message: string; variant: ToastVariant } | null>(null);

  useEffect(() => {
    if (!user) return;

    let cancelled = false;

    apiFetch(`/api/v1/spends?page=${currentPage}`)
      .then((res) => {
        if (!res.ok) throw new Error("Failed to load spends");
        return res.json();
      })
      .then((data: PageResponse<Spend>) => {
        if (cancelled) return;
        setSpends(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
        setPageSize(data.size);
        setStatus("ready");
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [user, currentPage]);

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      const response = await apiFetch(`/api/v1/spends/${deleteTarget.id}`, {
        method: "DELETE",
      });
      if (!response.ok) throw new Error("Failed to delete");

      setSpends((prev) => prev.filter((s) => s.id !== deleteTarget.id));
      const nextTotal = Math.max(totalElements - 1, 0);
      setTotalElements(nextTotal);
      setTotalPages(Math.ceil(nextTotal / pageSize));

      if (spends.length === 1 && currentPage > 0) {
        setCurrentPage((p) => p - 1);
      }

      setToast({ message: "Spend deleted.", variant: "success" });
    } catch {
      setToast({ message: "Could not delete spend. Try again.", variant: "error" });
    } finally {
      setIsDeleting(false);
      setDeleteTarget(null);
    }
  }

  if (authLoading || !user) return <LoadingState />;
  if (status === "error") return <ErrorState onRetry={() => setStatus("loading")} />;

  return (
    <div className="max-w-5xl mx-auto px-4 pt-8 pb-24 md:pb-8">
      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onDismiss={() => setToast(null)}
        />
      )}

      {deleteTarget && (
        <DeleteSpendModal
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
          isDeleting={isDeleting}
        />
      )}

      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold">Spends</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {totalElements} total {totalElements === 1 ? "spend" : "spends"}
          </p>
        </div>
        <Link
          href="/spends/new"
          className="hidden md:inline-flex items-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
          </svg>
          New spend
        </Link>
      </div>

      {status === "loading" ? (
        <LoadingState />
      ) : spends.length === 0 ? (
        <EmptyState
          title="No spends yet"
          description="Start tracking your expenses."
          action={
            <Link
              href="/spends/new"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition"
            >
              Create your first spend
            </Link>
          }
        />
      ) : (
        <>
          <div className="space-y-2">
            {spends.map((spend) => (
              <div
                key={spend.id}
                className="flex items-center justify-between px-4 py-3 rounded-lg border border-border hover:bg-accent transition"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-3">
                    <p className="text-sm font-medium">{spend.category}</p>
                    {spend.description && (
                      <p className="text-xs text-muted-foreground truncate hidden sm:block">
                        {spend.description}
                      </p>
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground mt-0.5">{formatDate(spend.spendDate)}</p>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  <span className="text-sm font-semibold">{formatCurrency(spend.amount)}</span>
                  <div className="flex gap-1">
                    <button
                      onClick={() => router.push(`/spends/${spend.id}/edit`)}
                      className="px-2.5 py-1 rounded text-xs border border-border hover:bg-accent transition"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => setDeleteTarget(spend)}
                      className="px-2.5 py-1 rounded text-xs border border-border text-destructive hover:bg-destructive hover:text-destructive-foreground transition"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-4 mt-6">
              <button
                disabled={currentPage === 0}
                onClick={() => setCurrentPage((p) => p - 1)}
                className="px-4 py-2 rounded-lg border border-border text-sm disabled:opacity-50 hover:bg-accent transition"
              >
                Previous
              </button>
              <span className="text-sm text-muted-foreground">
                Page {currentPage + 1} of {totalPages}
              </span>
              <button
                disabled={currentPage >= totalPages - 1}
                onClick={() => setCurrentPage((p) => p + 1)}
                className="px-4 py-2 rounded-lg border border-border text-sm disabled:opacity-50 hover:bg-accent transition"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}

      <Link
        href="/spends/new"
        className="fixed bottom-6 left-1/2 -translate-x-1/2 md:hidden inline-flex items-center gap-2 px-5 py-3 rounded-full bg-primary text-primary-foreground text-sm font-medium shadow-lg hover:opacity-90 transition z-50"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
        </svg>
        New spend
      </Link>
    </div>
  );
}
