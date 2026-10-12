"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/app/components/AuthProvider";
import { apiFetch } from "@/app/lib/api";
import {
  ClassificationRule,
  createRule,
  deleteRule,
  listRules,
  updateRule,
} from "@/app/lib/classificationRules";
import LoadingState from "@/app/components/LoadingState";
import ErrorState from "@/app/components/ErrorState";
import EmptyState from "@/app/components/EmptyState";
import Toast, { ToastVariant } from "@/app/components/Toast";

type Category = { id: string; name: string };

type FormState = {
  id: string | null;
  condition: string;
  categoryId: string;
};

type FormErrors = {
  condition?: string;
  categoryId?: string;
};

const MAX_CONDITION_LENGTH = 128;

export default function RulesPage() {
  const { user, isLoading: authLoading } = useAuth();

  const [rules, setRules] = useState<ClassificationRule[]>([]);
  const [maxRules, setMaxRules] = useState(10);
  const [categories, setCategories] = useState<Category[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<FormState>({ id: null, condition: "", categoryId: "" });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitError, setSubmitError] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<ClassificationRule | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const [toast, setToast] = useState<{ message: string; variant: ToastVariant } | null>(null);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;

    listRules()
      .then((data) => {
        if (cancelled) return;
        setRules(data.rules);
        setMaxRules(data.maxRules);
        setStatus("ready");
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });

    apiFetch("/api/v1/categories")
      .then((res) => (res.ok ? res.json() : []))
      .then((data: Category[]) => {
        if (!cancelled) setCategories(data);
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [user]);

  const atLimit = rules.length >= maxRules;

  function openCreateForm() {
    setForm({ id: null, condition: "", categoryId: "" });
    setErrors({});
    setSubmitError("");
    setShowForm(true);
  }

  function openEditForm(rule: ClassificationRule) {
    setForm({ id: rule.id, condition: rule.condition, categoryId: rule.categoryId });
    setErrors({});
    setSubmitError("");
    setShowForm(true);
  }

  function closeForm() {
    setShowForm(false);
    setForm({ id: null, condition: "", categoryId: "" });
    setErrors({});
    setSubmitError("");
  }

  function updateField(field: "condition" | "categoryId", value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: undefined }));
  }

  function validateForm() {
    const nextErrors: FormErrors = {};
    const condition = form.condition.trim();

    if (!condition) {
      nextErrors.condition = "Enter a condition.";
    } else if (condition.length > MAX_CONDITION_LENGTH) {
      nextErrors.condition = `Condition must be at most ${MAX_CONDITION_LENGTH} characters.`;
    }

    if (!form.categoryId) {
      nextErrors.categoryId = "Choose a category.";
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!validateForm()) return;

    setIsSaving(true);
    setSubmitError("");

    try {
      const condition = form.condition.trim();
      if (form.id) {
        const updated = await updateRule(form.id, condition, form.categoryId);
        setRules((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
        setToast({ message: "Rule updated.", variant: "success" });
      } else {
        const created = await createRule(condition, form.categoryId);
        setRules((prev) => [...prev, created]);
        setToast({ message: "Rule created.", variant: "success" });
      }
      closeForm();
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "Something went wrong.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);

    try {
      await deleteRule(deleteTarget.id);
      setRules((prev) => prev.filter((r) => r.id !== deleteTarget.id));
      setToast({ message: "Rule deleted.", variant: "success" });
    } catch {
      setToast({ message: "Could not delete rule. Try again.", variant: "error" });
    } finally {
      setIsDeleting(false);
      setDeleteTarget(null);
    }
  }

  if (authLoading || !user) return <LoadingState />;
  if (status === "loading") return <LoadingState />;
  if (status === "error")
    return <ErrorState message="Could not load rules." onRetry={() => window.location.reload()} />;

  return (
    <div className="max-w-3xl mx-auto px-4 pt-8 pb-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">Classification rules</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Custom rules give the assistant extra context to classify your future spends.
          </p>
        </div>
        <span className="shrink-0 rounded-lg border border-border px-3 py-1.5 text-sm font-medium">
          Rules {rules.length}/{maxRules}
        </span>
      </div>

      {!showForm && (
        <div className="mb-6">
          <button
            onClick={openCreateForm}
            disabled={atLimit}
            className="px-4 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
          >
            New rule
          </button>
          {atLimit && (
            <p className="mt-2 text-sm text-muted-foreground">
              You have reached the maximum of {maxRules} rules. Edit or delete a rule to add another.
            </p>
          )}
        </div>
      )}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="mb-6 rounded-xl border border-border p-6 space-y-4"
        >
          <h2 className="text-lg font-semibold">
            {form.id ? "Edit rule" : "New rule"}
          </h2>

          {submitError && (
            <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
              {submitError}
            </div>
          )}

          <div>
            <label htmlFor="condition" className="block text-sm font-medium mb-1">
              Condition
            </label>
            <input
              id="condition"
              type="text"
              value={form.condition}
              maxLength={MAX_CONDITION_LENGTH}
              onChange={(e) => updateField("condition", e.target.value)}
              placeholder='e.g. "electricity bill"'
              className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
              aria-invalid={!!errors.condition}
            />
            <div className="mt-1 flex items-center justify-between">
              {errors.condition ? (
                <p className="text-xs text-destructive">{errors.condition}</p>
              ) : (
                <span />
              )}
              <p className="text-xs text-muted-foreground">
                {form.condition.length}/{MAX_CONDITION_LENGTH}
              </p>
            </div>
          </div>

          <div>
            <label htmlFor="categoryId" className="block text-sm font-medium mb-1">
              Category
            </label>
            <select
              id="categoryId"
              value={form.categoryId}
              onChange={(e) => updateField("categoryId", e.target.value)}
              className="w-full rounded-lg border border-border bg-card px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
              aria-invalid={!!errors.categoryId}
            >
              <option value="">Select a category</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
            {errors.categoryId && (
              <p className="mt-1 text-xs text-destructive">{errors.categoryId}</p>
            )}
          </div>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={closeForm}
              disabled={isSaving}
              className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition disabled:opacity-60"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSaving || (!form.id && atLimit)}
              className="px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
            >
              {isSaving ? "Saving..." : form.id ? "Save changes" : "Create rule"}
            </button>
          </div>
        </form>
      )}

      {rules.length === 0 ? (
        <EmptyState
          title="No rules yet"
          description="Create a rule to help classify receipts that are hard to categorize."
        />
      ) : (
        <ul className="space-y-3">
          {rules.map((rule) => (
            <li
              key={rule.id}
              className="rounded-xl border border-border p-4 flex items-start justify-between gap-4"
            >
              <div className="min-w-0">
                <p className="text-sm font-medium break-words">{rule.condition}</p>
                <p className="text-xs text-muted-foreground mt-1">{rule.categoryName}</p>
              </div>
              <div className="flex gap-2 shrink-0">
                <button
                  onClick={() => openEditForm(rule)}
                  className="px-3 py-1.5 rounded-lg border border-border text-sm hover:bg-accent transition"
                >
                  Edit
                </button>
                <button
                  onClick={() => setDeleteTarget(rule)}
                  className="px-3 py-1.5 rounded-lg border border-border text-sm text-destructive hover:bg-accent transition"
                >
                  Delete
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-card rounded-xl shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold">Delete rule?</h2>
            <p className="mt-2 text-sm text-muted-foreground">
              This action cannot be undone. The rule will be permanently deleted.
            </p>
            <div className="mt-6 flex justify-end gap-3">
              <button
                onClick={() => setDeleteTarget(null)}
                disabled={isDeleting}
                className="px-4 py-2 rounded-lg border border-border text-sm hover:bg-accent transition disabled:opacity-60"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                disabled={isDeleting}
                className="px-4 py-2 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
              >
                {isDeleting ? "Deleting..." : "Delete"}
              </button>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onDismiss={() => setToast(null)}
        />
      )}
    </div>
  );
}
