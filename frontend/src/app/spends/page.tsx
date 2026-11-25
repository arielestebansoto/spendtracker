"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import { fetchSpendSummary, SpendSummary } from "@/app/lib/spendSummary";
import LoadingState from "@/app/components/LoadingState";
import EmptyState from "@/app/components/EmptyState";
import ErrorState from "@/app/components/ErrorState";
import Toast from "@/app/components/Toast";
import SpendStatusBadge from "@/app/components/SpendStatusBadge";
import { SpendStatus, isInProgress, usePollWhile } from "@/app/lib/spendStatus";

const PAGE_SIZE = 20;

type Spend = {
  id: string;
  category: string;
  amount: number;
  spendDate: string;
  description: string;
  status: SpendStatus;
};

type Category = {
  id: string;
  name: string;
};

type PageResponse<T> = {
  content: T[];
  totalElements: number;
};

type FilterValues = {
  categoryId: string;
  description: string;
  minAmount: string;
  maxAmount: string;
  startDate: string;
  endDate: string;
};

const emptyFilters: FilterValues = {
  categoryId: "",
  description: "",
  minAmount: "",
  maxAmount: "",
  startDate: "",
  endDate: "",
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

async function fetchSpends(filters: FilterValues, page: number, size: number) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.categoryId) params.set("categoryId", filters.categoryId);
  if (filters.description) params.set("description", filters.description);
  if (filters.minAmount) params.set("minAmount", filters.minAmount);
  if (filters.maxAmount) params.set("maxAmount", filters.maxAmount);
  if (filters.startDate) params.set("startDate", filters.startDate);
  if (filters.endDate) params.set("endDate", filters.endDate);

  const res = await apiFetch(`/api/v1/spends?${params}`);
  if (!res.ok) throw new Error("Failed to load spends");
  const data: PageResponse<Spend> = await res.json();
  return data;
}

// useSearchParams needs a Suspense boundary for the page to build.
export default function SpendsPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <SpendsPageContent />
    </Suspense>
  );
}

function SpendsPageContent() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  // The detail page adds ?deleted=1 after deleting a Spend so this page can confirm it.
  const [deletedMessage, setDeletedMessage] = useState<string | null>(() =>
    searchParams.has("deleted") ? "Spend deleted." : null
  );
  // Stable, so re-renders do not restart the toast timer.
  const dismissDeletedToast = useCallback(() => setDeletedMessage(null), []);

  // Drop the flag so a reload does not show the toast again.
  useEffect(() => {
    if (searchParams.has("deleted")) router.replace("/spends");
  }, [searchParams, router]);

  const [summary, setSummary] = useState<SpendSummary | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);

  const [spends, setSpends] = useState<Spend[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  // How many pages of PAGE_SIZE are on screen; "Load more spends" adds one.
  const [loadedPages, setLoadedPages] = useState(1);
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  // "Try again" on the error state bumps this counter to load the first page again.
  const [retryCount, setRetryCount] = useState(0);

  const [filtersOpen, setFiltersOpen] = useState(false);
  const [draft, setDraft] = useState<FilterValues>(emptyFilters);
  const [applied, setApplied] = useState<FilterValues>(emptyFilters);

  // Changes whenever the list starts over, so a "Load more" or a poll that was still running
  // does not mix results for the old filters into the new list.
  const listVersionRef = useRef(0);

  const hasActiveFilters = Object.values(applied).some((value) => value !== "");
  const hasMore = spends.length < totalElements;

  useEffect(() => {
    if (!user) return;
    fetchSpendSummary()
      .then(setSummary)
      .catch(() => {});
    apiFetch("/api/v1/categories")
      .then((res) => res.json())
      .then((data: Category[]) => setCategories(data))
      .catch(() => {});
  }, [user]);

  // A new set of filters (or a retry) starts the list over from the first page.
  useEffect(() => {
    if (!user) return;

    let cancelled = false;

    fetchSpends(applied, 0, PAGE_SIZE)
      .then((data) => {
        if (cancelled) return;
        setSpends(data.content);
        setTotalElements(data.totalElements);
        setLoadedPages(1);
        setStatus("ready");
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [user, applied, retryCount]);

  async function loadMore() {
    const version = listVersionRef.current;
    setIsLoadingMore(true);
    setLoadMoreFailed(false);
    try {
      const data = await fetchSpends(applied, loadedPages, PAGE_SIZE);
      if (version !== listVersionRef.current) return;
      // A Spend created since the first page shifts the pages by one; skip what is already shown.
      setSpends((current) => {
        const shownIds = new Set(current.map((spend) => spend.id));
        return [...current, ...data.content.filter((spend) => !shownIds.has(spend.id))];
      });
      setTotalElements(data.totalElements);
      setLoadedPages((pages) => pages + 1);
    } catch {
      setLoadMoreFailed(true);
    } finally {
      setIsLoadingMore(false);
    }
  }

  // While a Spend on screen is being processed, refresh every loaded Spend in one request,
  // and the cards, which change when it is done. A failed refresh keeps what is shown.
  const hasInProgress = spends.some((spend) => isInProgress(spend.status));
  usePollWhile(hasInProgress, async () => {
    const version = listVersionRef.current;
    try {
      const data = await fetchSpends(applied, 0, loadedPages * PAGE_SIZE);
      if (version !== listVersionRef.current) return;
      setSpends(data.content);
      setTotalElements(data.totalElements);
      setSummary(await fetchSpendSummary());
    } catch {
      // The next poll tries again.
    }
  });

  function startOver(filters: FilterValues) {
    listVersionRef.current += 1;
    setStatus("loading");
    setApplied(filters);
  }

  function applyFilters() {
    startOver(draft);
    setFiltersOpen(false);
  }

  function resetFilters() {
    setDraft(emptyFilters);
    startOver(emptyFilters);
  }

  function openFilters() {
    setDraft(applied);
    setFiltersOpen(true);
  }

  function closeFilters() {
    setDraft(applied);
    setFiltersOpen(false);
  }

  if (authLoading || !user) return <LoadingState />;
  if (status === "error") {
    return (
      <ErrorState
        message="Could not load spends."
        onRetry={() => {
          setStatus("loading");
          setRetryCount((count) => count + 1);
        }}
      />
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      {deletedMessage && <Toast message={deletedMessage} onDismiss={dismissDeletedToast} />}

      <h1 className="text-2xl font-bold mb-6">Spends</h1>

      <h2 className="text-sm font-medium text-muted-foreground mb-2">This month</h2>
      <div className="flex sm:grid sm:grid-cols-3 gap-3 sm:gap-4 overflow-x-auto sm:overflow-visible snap-x snap-mandatory mb-8 scrollbar-none">
        <SummaryCard label="Total spent" value={summary && formatCurrency(summary.totalSpent)} />
        <SummaryCard label="Spends" value={summary && String(summary.spendCount)} />
        <SummaryCard label="Average" value={summary && formatCurrency(summary.averageSpend)} />
      </div>

      <div className="mb-6">
        <button
          onClick={filtersOpen ? closeFilters : openFilters}
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
                    value={draft.categoryId}
                    onChange={(e) => setDraft((f) => ({ ...f, categoryId: e.target.value }))}
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
                    value={draft.description}
                    onChange={(e) => setDraft((f) => ({ ...f, description: e.target.value }))}
                    placeholder="Search description..."
                    className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Min amount</label>
                    <input
                      type="number"
                      value={draft.minAmount}
                      onChange={(e) => setDraft((f) => ({ ...f, minAmount: e.target.value }))}
                      placeholder="0"
                      min="0"
                      className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Max amount</label>
                    <input
                      type="number"
                      value={draft.maxAmount}
                      onChange={(e) => setDraft((f) => ({ ...f, maxAmount: e.target.value }))}
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
                    value={draft.startDate}
                    onChange={(e) => setDraft((f) => ({ ...f, startDate: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">End date</label>
                  <input
                    type="date"
                    value={draft.endDate}
                    onChange={(e) => setDraft((f) => ({ ...f, endDate: e.target.value }))}
                    className="w-full px-3 py-2 rounded-lg border border-border bg-background text-sm"
                  />
                </div>
              </div>

              <div className="mt-4 flex justify-end gap-2">
                {hasActiveFilters && (
                  <button
                    onClick={resetFilters}
                    className="px-3 py-1.5 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition"
                  >
                    Reset filters
                  </button>
                )}
                <button
                  onClick={applyFilters}
                  className="px-4 py-1.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition"
                >
                  Apply filters
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {status === "loading" ? (
        <LoadingState />
      ) : spends.length === 0 ? (
        hasActiveFilters ? (
          <EmptyState
            title="No matching spends"
            description="Try adjusting your filters."
            action={
              <button
                onClick={resetFilters}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition"
              >
                Clear filters
              </button>
            }
          />
        ) : (
          <EmptyState
            title="No spends yet"
            description="Tap + to add your first one."
          />
        )
      ) : (
        <>
          <div className="space-y-2">
            {spends.map((spend) => (
              <SpendRow key={spend.id} spend={spend} />
            ))}
          </div>

          {hasMore && (
            <div className="mt-6 text-center">
              <button
                onClick={loadMore}
                disabled={isLoadingMore}
                className="text-sm text-muted-foreground hover:text-foreground transition disabled:opacity-60"
              >
                {isLoadingMore ? "Loading..." : "Load more spends"}
              </button>
              {loadMoreFailed && (
                <p className="mt-2 text-sm text-destructive">Could not load more spends. Try again.</p>
              )}
            </div>
          )}
        </>
      )}

    </div>
  );
}

// `value` is null while the summary loads, or when it could not be loaded.
function SummaryCard({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="rounded-xl border border-border p-4 min-w-[65%] sm:min-w-0 snap-start">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="text-2xl font-bold mt-1">{value ?? "–"}</p>
    </div>
  );
}

function SpendRow({ spend }: { spend: Spend }) {
  return (
    <Link
      href={`/spends/${spend.id}`}
      className="flex items-center justify-between gap-4 px-4 py-3 rounded-lg border border-border hover:bg-accent transition"
    >
      <div className="min-w-0 flex-1">
        {spend.status === "PROCESSED" ? (
          <div className="flex items-center gap-3 min-w-0">
            <p className="text-sm font-medium shrink-0">{spend.category}</p>
            {spend.description && (
              <p className="text-xs text-muted-foreground truncate">{spend.description}</p>
            )}
          </div>
        ) : (
          // Provisional values are placeholders, so only the receipt state is shown.
          <div className="flex items-center gap-3">
            <p className="text-sm font-medium">Receipt</p>
            <SpendStatusBadge status={spend.status} />
          </div>
        )}
        <p className="text-xs text-muted-foreground mt-0.5">{formatDate(spend.spendDate)}</p>
      </div>

      {spend.status === "PROCESSED" && (
        <span className="text-sm font-semibold shrink-0">{formatCurrency(spend.amount)}</span>
      )}
    </Link>
  );
}
