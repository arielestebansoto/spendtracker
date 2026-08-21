"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import LoadingState from "@/app/components/LoadingState";
import ErrorState from "@/app/components/ErrorState";
import Toast, { ToastVariant } from "@/app/components/Toast";

type Category = { id: string; name: string };

type FormState = {
  categoryId: string;
  description: string;
  amount: string;
  spendDate: string;
};

type FormErrors = Partial<Record<keyof FormState, string>>;

export default function EditSpendPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const params = useParams();
  const spendId = params.id as string;

  const [categories, setCategories] = useState<Category[]>([]);
  const [form, setForm] = useState<FormState | null>(null);
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);
  const [toast, setToast] = useState<{ message: string; variant: ToastVariant } | null>(null);

  const isCategoryDisabled = useMemo(() => categories.length === 0, [categories.length]);

  useEffect(() => {
    async function load() {
      try {
        const [catRes, spendRes] = await Promise.all([
          apiFetch("/api/v1/categories"),
          apiFetch(`/api/v1/spends/${spendId}`),
        ]);

        if (catRes.ok) setCategories(await catRes.json());
        if (!spendRes.ok) throw new Error("Not found");

        const spend = await spendRes.json();
        setForm({
          categoryId: spend.categoryId,
          description: spend.description ?? "",
          amount: String(spend.amount),
          spendDate: spend.spendDate,
        });
      } catch {
        setError(true);
      } finally {
        setIsLoading(false);
      }
    }
    if (user) load();
  }, [user, spendId]);

  function updateField(field: keyof FormState, value: string) {
    setForm((current) => (current ? { ...current, [field]: value } : current));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function validateForm() {
    if (!form) return false;
    const nextErrors: FormErrors = {};
    const amount = Number(form.amount);

    if (!form.categoryId) nextErrors.categoryId = "Choose a category.";
    if (!form.amount.trim()) {
      nextErrors.amount = "Enter an amount.";
    } else if (!Number.isFinite(amount) || amount <= 0) {
      nextErrors.amount = "Amount must be greater than 0.";
    }
    if (!form.spendDate) nextErrors.spendDate = "Choose a date.";

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError("");
    if (!form || !validateForm()) return;

    setIsSaving(true);
    try {
      const response = await apiFetch(`/api/v1/spends/${spendId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryId: form.categoryId,
          description: form.description.trim(),
          amount: Number(form.amount),
          spendDate: form.spendDate,
        }),
      });

      if (!response.ok) throw new Error("Failed to update");

      setToast({ message: "Spend updated!", variant: "success" });
      setTimeout(() => router.push("/spends"), 500);
    } catch {
      setSubmitError("Could not update spend. Please review and try again.");
    } finally {
      setIsSaving(false);
    }
  }

  if (authLoading || !user) return <LoadingState />;
  if (error) return <ErrorState message="Could not load spend." onRetry={() => router.refresh()} />;
  if (isLoading || !form) return <LoadingState />;

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      {toast && (
        <Toast message={toast.message} variant={toast.variant} onDismiss={() => setToast(null)} />
      )}

      <div className="mb-6">
        <h1 className="text-2xl font-bold">Edit spend</h1>
        <p className="text-sm text-muted-foreground mt-1">Update the expense details.</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {submitError && (
          <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
            {submitError}
          </div>
        )}

        <div>
          <label htmlFor="categoryId" className="block text-sm font-medium mb-1.5">
            Category
          </label>
          <select
            id="categoryId"
            value={form.categoryId}
            onChange={(e) => updateField("categoryId", e.target.value)}
            disabled={isCategoryDisabled || isSaving}
            className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
            aria-invalid={Boolean(errors.categoryId)}
          >
            <option value="">
              {isCategoryDisabled ? "No categories available" : "Select a category"}
            </option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
          {errors.categoryId && <p className="mt-1 text-xs text-destructive">{errors.categoryId}</p>}
        </div>

        <div>
          <label htmlFor="amount" className="block text-sm font-medium mb-1.5">
            Amount
          </label>
          <input
            id="amount"
            type="number"
            min="0.01"
            step="0.01"
            inputMode="decimal"
            value={form.amount}
            onChange={(e) => updateField("amount", e.target.value)}
            disabled={isSaving}
            className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
            aria-invalid={Boolean(errors.amount)}
          />
          {errors.amount && <p className="mt-1 text-xs text-destructive">{errors.amount}</p>}
        </div>

        <div>
          <label htmlFor="spendDate" className="block text-sm font-medium mb-1.5">
            Date
          </label>
          <input
            id="spendDate"
            type="date"
            value={form.spendDate}
            onChange={(e) => updateField("spendDate", e.target.value)}
            disabled={isSaving}
            className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
            aria-invalid={Boolean(errors.spendDate)}
          />
          {errors.spendDate && <p className="mt-1 text-xs text-destructive">{errors.spendDate}</p>}
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-medium mb-1.5">
            Description
          </label>
          <textarea
            id="description"
            value={form.description}
            onChange={(e) => updateField("description", e.target.value)}
            disabled={isSaving}
            rows={3}
            placeholder="Optional"
            className="w-full resize-none rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => router.back()}
            disabled={isSaving}
            className="px-4 py-2.5 rounded-lg border border-border text-sm disabled:opacity-60 hover:bg-accent transition"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSaving || isCategoryDisabled}
            className="px-4 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
          >
            {isSaving ? "Saving..." : "Update spend"}
          </button>
        </div>
      </form>
    </div>
  );
}
