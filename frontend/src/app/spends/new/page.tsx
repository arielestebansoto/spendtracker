"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import LoadingState from "@/app/components/LoadingState";
import ReceiptCapture from "@/app/components/ReceiptCapture";
import { floatingButtonClass } from "@/app/components/NewSpendButton";

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

const initialFormState: FormState = {
  categoryId: "",
  description: "",
  amount: "",
  spendDate: new Date().toISOString().slice(0, 10),
};

// Every way to add a Spend on one page: upload a file, fill in the form, or (on a phone) the camera
// button in the spot where the "+" was.
export default function NewSpendPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();

  const [categories, setCategories] = useState<Category[]>([]);
  const [isSaving, setIsSaving] = useState(false);
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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError("");

    if (!validateForm()) return;

    setIsSaving(true);
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
      router.push(`/spends/${created.id}?created=manual`);
    } catch {
      setIsSaving(false);
      setSubmitError("Could not create spend. Please review the data and try again.");
    }
  }

  // Back to wherever the user came from; straight to the spends list when there is no history.
  function goBack() {
    if (window.history.length > 1) router.back();
    else router.push("/spends");
  }

  if (authLoading || !user) return <LoadingState />;

  return (
    // Bottom padding on a phone, so the camera button never covers the form's buttons.
    <div className="max-w-xl md:max-w-5xl mx-auto px-4 pt-8 pb-28 md:pb-8">
      <button
        type="button"
        onClick={goBack}
        className="mb-3 text-sm text-muted-foreground hover:text-foreground transition"
      >
        ← Back
      </button>
      <h1 className="text-2xl font-bold mb-6">New spend</h1>

      {/* Phone: one column, upload on top and the form below. Desktop: upload left, form right,
          each under its own heading. */}
      <div className="md:grid md:grid-cols-2 md:gap-6">
        <div>
          <h2 className="hidden md:block text-lg font-semibold mb-4">Receipt</h2>
          <ReceiptCapture source="file">
            {(openFilePicker) => (
              <button
                type="button"
                onClick={openFilePicker}
                className="w-full border-2 border-dashed border-border rounded-xl p-6 md:p-8 text-center hover:border-primary/50 transition"
              >
                <span className="hidden md:flex w-12 h-12 mb-2 rounded-full bg-muted items-center justify-center mx-auto">
                  <svg className="w-6 h-6 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                  </svg>
                </span>
                <span className="block text-sm font-medium">Upload a receipt</span>
                <span className="block mt-1 text-xs text-muted-foreground">
                  A photo or a 1-page PDF, up to 5 MB.
                </span>
              </button>
            )}
          </ReceiptCapture>
        </div>

        <div className="my-6 flex items-center gap-3 text-xs text-muted-foreground md:hidden">
          <div className="flex-1 border-t border-border" />
          or enter it manually
          <div className="flex-1 border-t border-border" />
        </div>

        <div>
          <h2 className="hidden md:block text-lg font-semibold mb-4">Manual entry</h2>
          <ManualForm
            form={form}
            errors={errors}
            categories={categories}
            isCategoryDisabled={isCategoryDisabled}
            isSaving={isSaving}
            submitError={submitError}
            onUpdateField={updateField}
            onSubmit={handleSubmit}
            onCancel={goBack}
          />
        </div>
      </div>

      {/* Phone only: the camera, in the spot where the "+" was. */}
      <ReceiptCapture source="camera">
        {(openCamera) => (
          <button
            type="button"
            onClick={openCamera}
            aria-label="Take a photo of the receipt"
            className={`md:hidden ${floatingButtonClass}`}
          >
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </button>
        )}
      </ReceiptCapture>
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
