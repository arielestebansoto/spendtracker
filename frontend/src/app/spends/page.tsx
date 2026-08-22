"use client";

import { useEffect, useState, useRef } from "react";
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

type Category = {
  id: string;
  name: string;
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

function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
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

  const [categories, setCategories] = useState<Category[]>([]);
  const [filtersOpen, setFiltersOpen] = useState(false);

  const [categoryId, setCategoryId] = useState<string>("");
  const [description, setDescription] = useState("");
  const [minAmount, setMinAmount] = useState<string>("");
  const [maxAmount, setMaxAmount] = useState<string>("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");

  const debouncedDescription = useDebounce(description, 300);
  const debouncedMinAmount = useDebounce(minAmount, 300);
  const debouncedMaxAmount = useDebounce(maxAmount, 300);

  const hasActiveFilters = categoryId || description || minAmount || maxAmount || startDate || endDate;

  useEffect(() => {
    if (!user) return;
    apiFetch("/api/v1/categories")
      .then((res) => res.json())
      .then((data: Category[]) => setCategories(data))
      .catch(() => {});
  }, [user]);

  useEffect(() => {
    if (!user) return;

    let cancelled = false;

    const params = new URLSearchParams({ page: String(currentPage) });
    if (categoryId) params.set("categoryId", categoryId);
    if (debouncedDescription) params.set("description", debouncedDescription);
    if (debouncedMinAmount) params.set("minAmount", debouncedMinAmount);
    if (debouncedMaxAmount) params.set("maxAmount", debouncedMaxAmount);
    if (startDate) params.set("startDate", startDate);
    if (endDate) params.set("endDate", endDate);

    apiFetch(`/api/v1/spends?${params}`)
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
  }, [user, currentPage, categoryId, debouncedDescription, debouncedMinAmount, debouncedMaxAmount, startDate, endDate]);

  const prevFiltersRef = useRef({
    categoryId, debouncedDescription, debouncedMinAmount, debouncedMaxAmount, startDate, endDate
  });

  useEffect(() => {
    const prev = prevFiltersRef.current;
    const changed =
      prev.categoryId !== categoryId ||
      prev.debouncedDescription !== debouncedDescription ||
      prev.debouncedMinAmount !== debouncedMinAmount ||
      prev.debouncedMaxAmount !== debouncedMaxAmount ||
      prev.startDate !== startDate ||
      prev.endDate !== endDate;

    if (changed && currentPage !== 0) {
      setCurrentPage(0);
    }

    prevFiltersRef.current = {
      categoryId, debouncedDescription, debouncedMinAmount, debouncedMaxAmount, startDate, endDate
    };
  }, [categoryId, debouncedDescription, debouncedMinAmount, debouncedMaxAmount, startDate, endDate, currentPage]);

  function resetFilters() {
    setCategoryId("");
    setDescription("");
    setMinAmount("");
    setMaxAmount("");
    setStartDate("");
    setEndDate("");
  }

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

      <div className="mb-6">
        <button
          onClick={() => setFiltersOpen(!filtersOpen)}
          className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition ${
            hasActiveFilters
              ? "bg-primary text-primary-foreground"
              : "border border-border hover:bg-accent"
          }`}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 6h9.75M10.5 6a1.5 1.5 0 1 1-3 0m3 0a1.5 1.5 0 1 0-3 0M3.75 6H7.5m3 12h9.75m-9.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-3.75 0H7.5m9-6h3.75m-3.75 0a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m-9.75 0h9.75" />
          </svg>
          Filters
          {hasActiveFilters && (
            <span className="px-1.5 py-0.5 rounded-full bg-primary text-primary-foreground text-xs">
              Active
            </span>
          )}
        </button>

        <div className={`grid transition-all duration-200 ease-in-out ${filtersOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]"}`}>
          <div className="overflow-hidden">
            <div className="mt-3 p-4 rounded-lg border border-border bg-card shadow-lg">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">Category</label>
                <select
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                >
                  <option value="">All categories</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>{cat.name}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">Description</label>
                <input
                  type="text"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Search description..."
                  className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Min amount</label>
                  <input
                    type="number"
                    value={minAmount}
                    onChange={(e) => setMinAmount(e.target.value)}
                    placeholder="0"
                    min="0"
                    className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Max amount</label>
                  <input
                    type="number"
                    value={maxAmount}
                    onChange={(e) => setMaxAmount(e.target.value)}
                    placeholder="No limit"
                    min="0"
                    className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">Start date</label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-muted-foreground mb-1">End date</label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                />
              </div>
            </div>

            {hasActiveFilters && (
              <div className="mt-4 flex justify-end">
                <button
                  onClick={resetFilters}
                  className="px-3 py-1.5 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition"
                >
                  Reset filters
                </button>
              </div>
            )}
          </div>
          </div>
        </div>
      </div>

      {status === "loading" ? (
        <LoadingState />
      ) : spends.length === 0 ? (
        <EmptyState
          title={hasActiveFilters ? "No matching spends" : "No spends yet"}
          description={hasActiveFilters ? "Try adjusting your filters." : "Start tracking your expenses."}
          action={
            hasActiveFilters ? (
              <button
                onClick={resetFilters}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition"
              >
                Clear filters
              </button>
            ) : (
              <Link
                href="/spends/new"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition"
              >
                Create your first spend
              </Link>
            )
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