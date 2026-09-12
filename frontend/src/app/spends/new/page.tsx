"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import { createSpendFromReceipt } from "@/app/lib/receipt";
import LoadingState from "@/app/components/LoadingState";
import ReceiptUpload from "@/app/components/ReceiptUpload";

type Category = {
  id: string;
  name: string;
};

type FormState = {
  categoryId: string;
  description: string;
  amount: string;
  spendDate: string;
};

type FormErrors = Partial<Record<keyof FormState, string>>;

type Mode = "receipt" | "manual";
type Status = "idle" | "uploading" | "processing" | "error";

const initialFormState: FormState = {
  categoryId: "",
  description: "",
  amount: "",
  spendDate: new Date().toISOString().slice(0, 10),
};

export default function NewSpendPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();

  const [categories, setCategories] = useState<Category[]>([]);
  const [mode, setMode] = useState<Mode>("receipt");
  const [status, setStatus] = useState<Status>("idle");
  const [form, setForm] = useState<FormState>(initialFormState);
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState("");

  const isCategoryDisabled = useMemo(() => categories.length === 0, [categories.length]);

  useEffect(() => {
    async function load() {
      try {
        const response = await apiFetch("/api/v1/categories");
        if (response.ok) {
          setCategories(await response.json());
        }
      } catch {
        // categories will remain empty
      }
    }
    if (user) load();
  }, [user]);

  function updateField(field: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function validateForm() {
    const nextErrors: FormErrors = {};
    const amount = Number(form.amount);

    if (!form.amount.trim()) {
      nextErrors.amount = "Enter an amount.";
    } else if (!Number.isFinite(amount) || amount <= 0) {
      nextErrors.amount = "Amount must be greater than 0.";
    }
    if (!form.spendDate) nextErrors.spendDate = "Choose a date.";

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  async function handleReceiptUpload(file: File) {
    setStatus("processing");
    setSubmitError("");

    try {
      const created = await createSpendFromReceipt(file, form.categoryId || undefined);
      router.push(`/spends/${created.id}`);
    } catch {
      setStatus("error");
      setSubmitError("Failed to process receipt. Please try again or enter details manually.");
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError("");

    if (!validateForm()) return;

    setStatus("uploading");
    try {
      const response = await apiFetch("/api/v1/spends", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          categoryId: form.categoryId,
          description: form.description.trim(),
          amount: Number(form.amount),
          spendDate: form.spendDate,
        }),
      });

      if (!response.ok) throw new Error("Failed to create spend");

      const created = await response.json();
      router.push(`/spends/${created.id}`);
    } catch {
      setStatus("error");
      setSubmitError("Could not create spend. Please review the data and try again.");
    }
  }

  if (authLoading || !user) return <LoadingState />;

  return (
    <div className="max-w-2xl mx-auto px-4 py-8 md:max-w-5xl">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">New spend</h1>
        <p className="text-sm text-muted-foreground mt-1">Upload a receipt or enter details manually.</p>
      </div>

      {/* Desktop: two-column layout */}
      <div className="hidden md:grid md:grid-cols-2 md:gap-6">
        {/* Left: Receipt upload */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">Receipt</h2>
          <ReceiptUpload
            onFileSelected={handleReceiptUpload}
            isProcessing={status === "processing"}
            disabled={status === "uploading" || status === "processing"}
          />
        </div>

        {/* Right: Manual form */}
        <div>
          <h2 className="text-lg font-semibold mb-4">Manual entry</h2>
          <ManualForm
            form={form}
            errors={errors}
            categories={categories}
            isCategoryDisabled={isCategoryDisabled}
            isSaving={status === "uploading" || status === "processing"}
            submitError={submitError}
            onUpdateField={updateField}
            onSubmit={handleSubmit}
            onCancel={() => router.back()}
          />
        </div>
      </div>

      {/* Mobile: conditional layout */}
      <div className="md:hidden">
        {mode === "receipt" ? (
          <div className="space-y-4">
            <ReceiptUpload
              onFileSelected={handleReceiptUpload}
              isProcessing={status === "processing"}
              disabled={status === "uploading" || status === "processing"}
            />
            <button
              type="button"
              onClick={() => {
                setMode("manual");
                setSubmitError("");
                setStatus("idle");
              }}
              disabled={status === "uploading" || status === "processing"}
              className="w-full py-3 rounded-lg border border-border text-sm font-medium hover:bg-accent transition disabled:opacity-60"
            >
              Enter manually
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <ManualForm
              form={form}
              errors={errors}
              categories={categories}
              isCategoryDisabled={isCategoryDisabled}
              isSaving={status === "uploading" || status === "processing"}
              submitError={submitError}
              onUpdateField={updateField}
              onSubmit={handleSubmit}
              onCancel={() => router.back()}
            />
            <button
              type="button"
              onClick={() => {
                setMode("receipt");
                setSubmitError("");
                setStatus("idle");
              }}
              disabled={status === "uploading" || status === "processing"}
              className="w-full py-3 rounded-lg border border-border text-sm font-medium hover:bg-accent transition disabled:opacity-60"
            >
              Upload receipt instead
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function ManualForm({
  form,
  errors,
  categories,
  isCategoryDisabled,
  isSaving,
  submitError,
  onUpdateField,
  onSubmit,
  onCancel,
}: {
  form: FormState;
  errors: FormErrors;
  categories: Category[];
  isCategoryDisabled: boolean;
  isSaving: boolean;
  submitError: string;
  onUpdateField: (field: keyof FormState, value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <form onSubmit={onSubmit} className="space-y-5" noValidate>
      {submitError && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
          {submitError}
        </div>
      )}

      <div>
        <label htmlFor="amount" className="block text-sm font-medium mb-1.5">
          Amount <span className="text-destructive">*</span>
        </label>
        <input
          id="amount"
          type="number"
          min="0.01"
          step="0.01"
          inputMode="decimal"
          placeholder="0.00"
          value={form.amount}
          onChange={(e) => onUpdateField("amount", e.target.value)}
          disabled={isSaving}
          className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
          aria-invalid={Boolean(errors.amount)}
        />
        {errors.amount && <p className="mt-1 text-xs text-destructive">{errors.amount}</p>}
      </div>

      <div>
        <label htmlFor="spendDate" className="block text-sm font-medium mb-1.5">
          Date <span className="text-destructive">*</span>
        </label>
        <input
          id="spendDate"
          type="date"
          value={form.spendDate}
          onChange={(e) => onUpdateField("spendDate", e.target.value)}
          disabled={isSaving}
          className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
          aria-invalid={Boolean(errors.spendDate)}
        />
        {errors.spendDate && <p className="mt-1 text-xs text-destructive">{errors.spendDate}</p>}
      </div>

      <div>
        <label htmlFor="categoryId" className="block text-sm font-medium mb-1.5">
          Category <span className="text-muted-foreground">(optional)</span>
        </label>
        <select
          id="categoryId"
          value={form.categoryId}
          onChange={(e) => onUpdateField("categoryId", e.target.value)}
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
      </div>

      <div>
        <label htmlFor="description" className="block text-sm font-medium mb-1.5">
          Description <span className="text-muted-foreground">(optional)</span>
        </label>
        <textarea
          id="description"
          value={form.description}
          onChange={(e) => onUpdateField("description", e.target.value)}
          disabled={isSaving}
          rows={3}
          placeholder="Optional"
          className="w-full resize-none rounded-lg border border-border bg-card px-3 py-2.5 text-sm disabled:opacity-60 focus:outline-none focus:ring-2 focus:ring-ring"
        />
      </div>

      <div className="flex justify-end gap-3 pt-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          className="px-4 py-2.5 rounded-lg border border-border text-sm disabled:opacity-60 hover:bg-accent transition"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={isSaving}
          className="px-4 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
        >
          {isSaving ? "Saving..." : "Create spend"}
        </button>
      </div>
    </form>
  );
}
