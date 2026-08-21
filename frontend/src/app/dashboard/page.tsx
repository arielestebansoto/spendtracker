"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/app/components/AuthProvider";
import { fetchDashboardSummary, DashboardSummary } from "@/app/lib/mock";
import LoadingState from "@/app/components/LoadingState";
import EmptyState from "@/app/components/EmptyState";
import ErrorState from "@/app/components/ErrorState";

function formatCurrency(amount: number) {
  return `$${amount.toLocaleString()}`;
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr + "T00:00:00");
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

export default function DashboardPage() {
  const { user, isLoading: authLoading } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");

  useEffect(() => {
    if (!user) return;

    let cancelled = false;

    fetchDashboardSummary()
      .then((data) => {
        if (!cancelled) {
          setSummary(data);
          setStatus("ready");
        }
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [user]);

  if (authLoading || !user) return <LoadingState />;
  if (status === "error") return <ErrorState onRetry={() => setStatus("loading")} />;
  if (status === "loading" || !summary) return <LoadingState />;

  const hasSpends = summary.recentSpends.length > 0;

  return (
    <div className="max-w-5xl mx-auto px-4 pt-8 pb-24 md:pb-8">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold">Dashboard</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Here&apos;s your spending overview.
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

      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="rounded-xl border border-border p-4">
          <p className="text-sm text-muted-foreground">Total spent</p>
          <p className="text-2xl font-bold mt-1">{formatCurrency(summary.totalSpent)}</p>
        </div>
        <div className="rounded-xl border border-border p-4">
          <p className="text-sm text-muted-foreground">Spends</p>
          <p className="text-2xl font-bold mt-1">{summary.spendCount}</p>
        </div>
        <div className="rounded-xl border border-border p-4">
          <p className="text-sm text-muted-foreground">Average</p>
          <p className="text-2xl font-bold mt-1">{formatCurrency(summary.averageSpend)}</p>
        </div>
      </div>

      <div>
        <h2 className="text-lg font-semibold mb-4">Recent spends</h2>

        {!hasSpends ? (
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
          <div className="space-y-2">
            {summary.recentSpends.map((spend) => (
              <div
                key={spend.id}
                className="flex items-center justify-between px-4 py-3 rounded-lg border border-border hover:bg-accent transition"
              >
                <div className="flex items-center gap-4 min-w-0">
                  <div className="min-w-0">
                    <p className="text-sm font-medium truncate">{spend.category}</p>
                    {spend.description && (
                      <p className="text-xs text-muted-foreground truncate">{spend.description}</p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-4 shrink-0">
                  <span className="text-xs text-muted-foreground">{formatDate(spend.spendDate)}</span>
                  <span className="text-sm font-semibold">{formatCurrency(spend.amount)}</span>
                </div>
              </div>
            ))}
          </div>
        )}

        {hasSpends && (
          <div className="mt-4 text-center">
            <Link
              href="/spends"
              className="text-sm text-muted-foreground hover:text-foreground transition"
            >
              View all spends →
            </Link>
          </div>
        )}
      </div>

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
