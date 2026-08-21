"use client";

import { useEffect, useState } from "react";
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

  useEffect(() => {
    async function load() {
      try {
        const response = await apiFetch(`/api/v1/spends/${spendId}`);
        if (!response.ok) throw new Error("Not found");
        setSpend(await response.json());
      } catch {
        setError(true);
      } finally {
        setIsLoading(false);
      }
    }
    if (user) load();
  }, [user, spendId]);

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
    </div>
  );
}
