"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import LoadingState from "@/app/components/LoadingState";
import ErrorState from "@/app/components/ErrorState";
import SpendStatusBadge from "@/app/components/SpendStatusBadge";
import { SpendStatus, isInProgress, usePollWhile } from "@/app/lib/spendStatus";

type SpendDetail = {
  id: string;
  categoryId: string;
  category: string;
  description: string;
  amount: number;
  spendDate: string;
  receiptUrl: string | null;
  status: SpendStatus;
};

function formatCurrency(amount: number) {
  return `$${amount.toLocaleString()}`;
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr + "T00:00:00");
  return date.toLocaleDateString("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

export default function ViewSpendPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const params = useParams();
  const spendId = params.id as string;

  const [spend, setSpend] = useState<SpendDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);
  const [receiptDataUrl, setReceiptDataUrl] = useState<string | null>(null);
  const [receiptContentType, setReceiptContentType] = useState<string | null>(null);

  const fetchReceipt = useCallback(async (url: string) => {
    try {
      const response = await apiFetch(url);
      if (!response.ok) throw new Error("Failed to load receipt");
      setReceiptContentType(response.headers.get("content-type"));
      const blob = await response.blob();
      const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result as string);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      });
      setReceiptDataUrl(dataUrl);
    } catch {
      setReceiptDataUrl(null);
    }
  }, []);

  // Each poll bumps this counter, which re-runs the fetch below.
  const [pollCount, setPollCount] = useState(0);
  const receiptRequestedRef = useRef(false);

  useEffect(() => {
    async function load() {
      try {
        const response = await apiFetch(`/api/v1/spends/${spendId}`);
        if (!response.ok) throw new Error("Not found");
        const data: SpendDetail = await response.json();
        setSpend(data);
        // The receipt URL does not change between polls, so the image is fetched only once.
        if (data.receiptUrl && !receiptRequestedRef.current) {
          receiptRequestedRef.current = true;
          fetchReceipt(data.receiptUrl);
        }
      } catch {
        // A failed background refresh keeps the current data; the next poll tries again.
        if (pollCount === 0) setError(true);
      } finally {
        setIsLoading(false);
      }
    }
    if (user) load();
  }, [user, spendId, fetchReceipt, pollCount]);

  // Reclassify only recalculates the Category. The Spend comes back in progress, which locks it
  // and starts the polling below until it is PROCESSED again.
  const [isReclassifying, setIsReclassifying] = useState(false);
  const [reclassifyError, setReclassifyError] = useState(false);

  async function reclassify() {
    setIsReclassifying(true);
    setReclassifyError(false);
    try {
      const response = await apiFetch(`/api/v1/spends/${spendId}/reclassify`, { method: "POST" });
      if (!response.ok) throw new Error("Reclassify failed");
      const data: SpendDetail = await response.json();
      setSpend(data);
    } catch {
      setReclassifyError(true);
    } finally {
      setIsReclassifying(false);
    }
  }

  // Poll only while the Spend is still being processed.
  usePollWhile(spend !== null && isInProgress(spend.status), () =>
    setPollCount((count) => count + 1)
  );

  if (authLoading || !user) return <LoadingState />;
  if (error) return <ErrorState message="Could not load spend." onRetry={() => router.refresh()} />;
  if (isLoading || !spend) return <LoadingState />;

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold">Spend detail</h1>
          <SpendStatusBadge status={spend.status} />
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => router.push(`/spends/${spendId}/edit`)}
            disabled={isInProgress(spend.status)}
            className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Edit
          </button>
          {spend.receiptUrl && (
            <button
              onClick={reclassify}
              disabled={isReclassifying || spend.status !== "PROCESSED"}
              className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Reclassify
            </button>
          )}
          <button
            onClick={() => router.back()}
            className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition"
          >
            Back
          </button>
        </div>
      </div>

      {reclassifyError && (
        <p className="mb-4 text-sm text-destructive">
          Could not reclassify this spend. Please try again.
        </p>
      )}

      {isInProgress(spend.status) && (
        <div className="rounded-xl border border-border p-6">
          <p className="text-sm font-medium">We are reading your receipt</p>
          <p className="mt-1 text-sm text-muted-foreground">
            The amount, date, description and category will appear here when it is done. This page
            updates by itself.
          </p>
        </div>
      )}

      {spend.status === "FAILED" && (
        <div className="rounded-xl border border-destructive/50 bg-destructive/10 p-6">
          <p className="text-sm font-medium text-destructive">We could not process this receipt</p>
          <p className="mt-1 text-sm text-muted-foreground">
            The image is still saved. You can enter the details by editing this spend, or delete it
            from the spends list.
          </p>
        </div>
      )}

      {spend.status === "PROCESSED" && (
        <div className="rounded-xl border border-border p-6 space-y-4">
          <div>
            <p className="text-sm text-muted-foreground">Category</p>
            <p className="text-sm font-medium mt-1">{spend.category}</p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Amount</p>
            <p className="text-2xl font-bold mt-1">{formatCurrency(spend.amount)}</p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Date</p>
            <p className="text-sm font-medium mt-1">{formatDate(spend.spendDate)}</p>
          </div>
          {spend.description && (
            <div>
              <p className="text-sm text-muted-foreground">Description</p>
              <p className="text-sm font-medium mt-1">{spend.description}</p>
            </div>
          )}
        </div>
      )}

      {spend.status === "PROCESSED" && spend.category === "Uncategorized" && (
        <div className="mt-6 rounded-xl border border-border bg-accent/50 p-4 flex items-start justify-between gap-4">
          <div>
            <p className="text-sm font-medium">This spend could not be categorized</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Create a custom rule to improve classification for your future spends.
            </p>
          </div>
          <button
            onClick={() => router.push("/rules")}
            className="shrink-0 px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition"
          >
            Create new rule for next spends
          </button>
        </div>
      )}

      <div className="mt-6">
        <h2 className="text-lg font-semibold mb-3">Receipt</h2>
        {spend.receiptUrl ? (
          <div className="rounded-xl border border-border overflow-hidden bg-muted">
            {receiptDataUrl ? (
              receiptContentType?.startsWith("image/") ? (
                <img
                  src={receiptDataUrl}
                  alt="Receipt"
                  className="w-full h-auto"
                />
              ) : receiptContentType === "application/pdf" ? (
                <iframe
                  src={receiptDataUrl}
                  title="Receipt"
                  className="w-full h-[600px]"
                />
              ) : (
                <a
                  href={receiptDataUrl}
                  download="receipt"
                  className="block p-8 text-center text-sm text-primary underline"
                >
                  Download receipt
                </a>
              )
            ) : (
              <div className="w-full h-48 flex items-center justify-center">
                <div className="animate-spin w-6 h-6 border-2 border-primary border-t-transparent rounded-full" />
              </div>
            )}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-border p-8 text-center">
            <p className="text-sm text-muted-foreground">No receipt uploaded</p>
          </div>
        )}
      </div>
    </div>
  );
}
