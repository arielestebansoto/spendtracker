"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import LoadingState from "@/app/components/LoadingState";
import ErrorState from "@/app/components/ErrorState";

type SpendDetail = {
  id: string;
  categoryId: string;
  category: string;
  description: string;
  amount: number;
  spendDate: string;
  receiptUrl: string | null;
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

  useEffect(() => {
    async function load() {
      try {
        const response = await apiFetch(`/api/v1/spends/${spendId}`);
        if (!response.ok) throw new Error("Not found");
        const data: SpendDetail = await response.json();
        setSpend(data);
        if (data.receiptUrl) fetchReceipt(data.receiptUrl);
      } catch {
        setError(true);
      } finally {
        setIsLoading(false);
      }
    }
    if (user) load();
  }, [user, spendId, fetchReceipt]);

  if (authLoading || !user) return <LoadingState />;
  if (error) return <ErrorState message="Could not load spend." onRetry={() => router.refresh()} />;
  if (isLoading || !spend) return <LoadingState />;

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Spend detail</h1>
        <div className="flex gap-2">
          <button
            onClick={() => router.push(`/spends/${spendId}/edit`)}
            className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition"
          >
            Edit
          </button>
          <button
            onClick={() => router.back()}
            className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition"
          >
            Back
          </button>
        </div>
      </div>

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
