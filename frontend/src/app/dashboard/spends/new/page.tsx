"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

import Navbar from "../../../components/Navbar";
import { apiFetch } from "../../../lib/api";
import { initializeSession, logout } from "../../../lib/auth";

type User = {
    id: string;
    name: string;
    email: string;
};

type Category = {
    id: string;
    name: string;
};

type FormState = {
    categoryId: string;
    description: string;
    amount: string;
    currency: string;
    spendDate: string;
};

type FormErrors = Partial<Record<keyof FormState, string>>;

type SpendDetail = {
    id: string;
    categoryId: string;
    category: string;
    description: string;
    amount: number;
    currency: string;
    spendDate: string;
    receiptUrl: string | null;
};

type SpendFormMode = "create" | "edit" | "view";

type SpendFormPageProps = {
    mode?: SpendFormMode;
    spendId?: string;
};

const initialFormState: FormState = {
    categoryId: "",
    description: "",
    amount: "",
    currency: "",
    spendDate: new Date().toISOString().slice(0, 10),
};

export function SpendFormPage({
    mode = "create",
    spendId,
}: SpendFormPageProps) {
    const router = useRouter();
    const isEditing = mode === "edit";
    const isViewing = mode === "view";
    const isReadOnly = isViewing;

    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [categories, setCategories] = useState<Category[]>([]);
    const [form, setForm] = useState<FormState>(initialFormState);
    const [errors, setErrors] = useState<FormErrors>({});
    const [receiptFile, setReceiptFile] = useState<File | null>(null);
    const [receiptPreviewUrl, setReceiptPreviewUrl] = useState("");
    const [existingReceiptUrl, setExistingReceiptUrl] = useState("");
    const [existingReceiptObjectUrl, setExistingReceiptObjectUrl] = useState("");
    const [receiptError, setReceiptError] = useState("");
    const [submitError, setSubmitError] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    const isCategoryDisabled = useMemo(
        () => categories.length === 0,
        [categories.length]
    );

    useEffect(() => {
        async function load() {
            try {
                const currentUser = await initializeSession();

                if (!currentUser) {
                    router.replace("/");
                    return;
                }

                setUser(currentUser);

                const [categoriesResponse, spendResponse] = await Promise.all([
                    apiFetch("/api/v1/categories"),
                    (isEditing || isViewing) && spendId
                        ? apiFetch(`/api/v1/spends/${spendId}`)
                        : Promise.resolve(null),
                ]);

                if (!categoriesResponse.ok) {
                    throw new Error("Failed to load categories");
                }

                const data: Category[] = await categoriesResponse.json();
                setCategories(data);

                if (isEditing || isViewing) {
                    if (!spendId || !spendResponse || !spendResponse.ok) {
                        throw new Error("Failed to load spend");
                    }

                    const spend: SpendDetail = await spendResponse.json();

                    setForm({
                        categoryId: spend.categoryId,
                        description: spend.description ?? "",
                        amount: String(spend.amount),
                        currency: spend.currency,
                        spendDate: spend.spendDate,
                    });
                    setExistingReceiptUrl(spend.receiptUrl ?? "");
                }
            } catch (error) {
                console.error(error);
                setSubmitError("We could not load the form data. Please try again.");
            } finally {
                setIsLoading(false);
            }
        }

        load();
    }, [isEditing, isViewing, router, spendId]);

    useEffect(() => {
        if (!existingReceiptUrl) {
            return;
        }

        let objectUrl = "";
        let isActive = true;

        async function loadReceipt() {
            try {
                setReceiptError("");

                const response = await apiFetch(existingReceiptUrl);

                if (!response.ok) {
                    throw new Error("Failed to load receipt");
                }

                const blob = await response.blob();
                objectUrl = URL.createObjectURL(blob);

                if (isActive) {
                    setExistingReceiptObjectUrl(objectUrl);
                }
            } catch (error) {
                console.error(error);

                if (isActive) {
                    setReceiptError("We could not load the receipt image.");
                }
            }
        }

        loadReceipt();

        return () => {
            isActive = false;

            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
        };
    }, [existingReceiptUrl]);

    useEffect(() => {
        return () => {
            if (receiptPreviewUrl) {
                URL.revokeObjectURL(receiptPreviewUrl);
            }
        };
    }, [receiptPreviewUrl]);

    function updateField(
        field: keyof FormState,
        value: string
    ) {
        setForm((current) => ({
            ...current,
            [field]: field === "currency" ? value.toUpperCase() : value,
        }));

        setErrors((current) => ({
            ...current,
            [field]: undefined,
        }));
    }

    function updateReceipt(file: File | null) {
        setReceiptError("");

        if (!file) {
            setReceiptFile(null);
            setReceiptPreviewUrl((current) => {
                if (current) {
                    URL.revokeObjectURL(current);
                }

                return "";
            });
            return;
        }

        if (!file.type.startsWith("image/")) {
            setReceiptFile(null);
            setReceiptPreviewUrl((current) => {
                if (current) {
                    URL.revokeObjectURL(current);
                }

                return "";
            });
            setReceiptError("Choose an image file.");
            return;
        }

        setReceiptFile(file);
        setReceiptPreviewUrl((current) => {
            if (current) {
                URL.revokeObjectURL(current);
            }

            return URL.createObjectURL(file);
        });
    }

    function validateForm() {
        const nextErrors: FormErrors = {};
        const normalizedCurrency = form.currency.trim().toUpperCase();
        const amount = Number(form.amount);

        if (!form.categoryId) {
            nextErrors.categoryId = "Choose a category.";
        }

        if (!form.amount.trim()) {
            nextErrors.amount = "Enter an amount.";
        } else if (!Number.isFinite(amount) || amount <= 0) {
            nextErrors.amount = "Amount must be greater than 0.";
        }

        if (!normalizedCurrency) {
            nextErrors.currency = "Enter a currency.";
        } else if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
            nextErrors.currency = "Use a 3-letter code, like USD or ARS.";
        }

        if (!form.spendDate) {
            nextErrors.spendDate = "Choose a date.";
        }

        setErrors(nextErrors);
        return Object.keys(nextErrors).length === 0;
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitError("");

        if (isReadOnly) {
            return;
        }

        if (!validateForm()) {
            return;
        }

        setIsSaving(true);

        try {
            const data = {
                categoryId: form.categoryId,
                description: form.description.trim(),
                amount: Number(form.amount),
                currency: form.currency.trim().toUpperCase(),
                spendDate: form.spendDate,
            };

            const response = isEditing
                ? await apiFetch(
                    `/api/v1/spends/${spendId}`,
                    {
                        method: "PUT",
                        headers: {
                            "Content-Type": "application/json",
                        },
                        body: JSON.stringify(data),
                    }
                )
                : await createSpend(data, receiptFile);

            if (!response.ok) {
                throw new Error(
                    isEditing
                        ? "Failed to update spend"
                        : "Failed to create spend"
                );
            }

            router.push("/dashboard");
        } catch (error) {
            console.error(error);
            setSubmitError(
                isEditing
                    ? "We could not update the expense. Please review the data and try again."
                    : "We could not create the expense. Please review the data and try again."
            );
        } finally {
            setIsSaving(false);
        }
    }

    async function handleLogout() {
        await logout();
        router.replace("/");
    }

    if (isLoading) {
        return (
            <div className="p-6">
                Checking authentication...
            </div>
        );
    }

    if (!user) {
        return null;
    }

    return (
        <div className="max-w-3xl mx-auto p-6">
            <Navbar
                userName={user.name}
                onLogout={handleLogout}
            />

            <div className="mt-8 mb-6 flex items-center justify-between gap-4">
                <div>
                    <h1 className="text-3xl font-bold">
                        {isViewing
                            ? "Expense Detail"
                            : isEditing
                                ? "Edit Expense"
                                : "New Expense"}
                    </h1>
                    <p className="mt-2 text-sm opacity-75">
                        {isViewing
                            ? "You are viewing the expense details."
                            : isEditing
                            ? "You are editing this expense. Receipt changes are not available yet."
                            : "Enter the expense details. Receipt upload can be added later."}
                    </p>
                </div>

                <button
                    type="button"
                    onClick={() => router.push("/dashboard")}
                    className="px-4 py-2 rounded-lg border hover:bg-gray-100 hover:text-black transition"
                >
                    Back
                </button>
            </div>

            <form
                onSubmit={handleSubmit}
                className="space-y-5 border rounded-lg p-6"
                noValidate
            >
                {submitError && (
                    <div className="rounded border border-red-500 p-3 text-sm text-red-500">
                        {submitError}
                    </div>
                )}

                <div>
                    <label
                        htmlFor="receipt"
                        className="block text-sm font-medium mb-2"
                    >
                        Receipt photo
                    </label>

                    {!isEditing && !isViewing && (
                        <>
                            <input
                                id="receipt"
                                type="file"
                                accept="image/*"
                                onChange={(event) =>
                                    updateReceipt(event.target.files?.[0] ?? null)
                                }
                                disabled={isSaving}
                                className="w-full rounded-lg border bg-transparent px-3 py-2 disabled:opacity-60"
                            />
                            <p className="mt-1 text-sm opacity-75">
                                Optional. You can upload an image from the browser.
                            </p>
                        </>
                    )}

                    {(isEditing || isViewing) && !existingReceiptUrl && (
                        <p className="rounded-lg border px-3 py-2 text-sm opacity-75">
                            No receipt photo was uploaded for this expense.
                        </p>
                    )}

                    {(receiptPreviewUrl || existingReceiptObjectUrl) && (
                        <div className="mt-3 overflow-hidden rounded-lg border">
                            <img
                                src={receiptPreviewUrl || existingReceiptObjectUrl}
                                alt="Receipt photo"
                                className="max-h-96 w-full object-contain"
                            />
                        </div>
                    )}

                    {receiptError && (
                        <p className="mt-1 text-sm text-red-500">
                            {receiptError}
                        </p>
                    )}
                </div>

                <div>
                    <label
                        htmlFor="categoryId"
                        className="block text-sm font-medium mb-2"
                    >
                        Category
                    </label>
                    <select
                        id="categoryId"
                        value={form.categoryId}
                        onChange={(event) =>
                            updateField("categoryId", event.target.value)
                        }
                        disabled={isCategoryDisabled || isSaving || isReadOnly}
                        className="w-full rounded-lg border bg-transparent px-3 py-2 disabled:opacity-60"
                        aria-invalid={Boolean(errors.categoryId)}
                    >
                        <option value="">
                            {isCategoryDisabled
                                ? "No categories available"
                                : "Select a category"}
                        </option>
                        {categories.map((category) => (
                            <option
                                key={category.id}
                                value={category.id}
                            >
                                {category.name}
                            </option>
                        ))}
                    </select>
                    {errors.categoryId && (
                        <p className="mt-1 text-sm text-red-500">
                            {errors.categoryId}
                        </p>
                    )}
                </div>

                <div className="grid gap-5 sm:grid-cols-2">
                    <div>
                        <label
                            htmlFor="amount"
                            className="block text-sm font-medium mb-2"
                        >
                            Amount
                        </label>
                        <input
                            id="amount"
                            type="number"
                            min="0.01"
                            step="0.01"
                            inputMode="decimal"
                            value={form.amount}
                            onChange={(event) =>
                                updateField("amount", event.target.value)
                            }
                            disabled={isSaving}
                            readOnly={isReadOnly}
                            className="w-full rounded-lg border bg-transparent px-3 py-2 disabled:opacity-60"
                            aria-invalid={Boolean(errors.amount)}
                        />
                        {errors.amount && (
                            <p className="mt-1 text-sm text-red-500">
                                {errors.amount}
                            </p>
                        )}
                    </div>

                    <div>
                        <label
                            htmlFor="currency"
                            className="block text-sm font-medium mb-2"
                        >
                            Currency
                        </label>
                        <input
                            id="currency"
                            type="text"
                            maxLength={3}
                            value={form.currency}
                            onChange={(event) =>
                                updateField("currency", event.target.value)
                            }
                            disabled={isSaving}
                            readOnly={isReadOnly}
                            placeholder="USD"
                            className="w-full rounded-lg border bg-transparent px-3 py-2 uppercase disabled:opacity-60"
                            aria-invalid={Boolean(errors.currency)}
                        />
                        {errors.currency && (
                            <p className="mt-1 text-sm text-red-500">
                                {errors.currency}
                            </p>
                        )}
                    </div>
                </div>

                <div>
                    <label
                        htmlFor="spendDate"
                        className="block text-sm font-medium mb-2"
                    >
                        Date
                    </label>
                    <input
                        id="spendDate"
                        type="date"
                        value={form.spendDate}
                        onChange={(event) =>
                            updateField("spendDate", event.target.value)
                        }
                        disabled={isSaving}
                        readOnly={isReadOnly}
                        className="w-full rounded-lg border bg-transparent px-3 py-2 disabled:opacity-60"
                        aria-invalid={Boolean(errors.spendDate)}
                    />
                    {errors.spendDate && (
                        <p className="mt-1 text-sm text-red-500">
                            {errors.spendDate}
                        </p>
                    )}
                </div>

                <div>
                    <label
                        htmlFor="description"
                        className="block text-sm font-medium mb-2"
                    >
                        Description
                    </label>
                    <textarea
                        id="description"
                        value={form.description}
                        onChange={(event) =>
                            updateField("description", event.target.value)
                        }
                        disabled={isSaving}
                        readOnly={isReadOnly}
                        rows={4}
                        className="w-full resize-y rounded-lg border bg-transparent px-3 py-2 disabled:opacity-60"
                    />
                </div>

                <div className="flex justify-end gap-3 pt-2">
                    <button
                        type="button"
                        onClick={() => router.push("/dashboard")}
                        disabled={isSaving}
                        className="px-4 py-2 rounded-lg border disabled:opacity-60"
                    >
                        {isViewing ? "Back" : "Cancel"}
                    </button>
                    {!isViewing && (
                        <button
                            type="submit"
                            disabled={isSaving || isCategoryDisabled}
                            className="px-4 py-2 rounded-lg border hover:bg-gray-100 hover:text-black transition disabled:opacity-60"
                        >
                            {isSaving
                                ? "Saving..."
                                : isEditing
                                    ? "Update Expense"
                                    : "Create Expense"}
                        </button>
                    )}
                </div>
            </form>
        </div>
    );
}

async function createSpend(data: {
    categoryId: string;
    description: string;
    amount: number;
    currency: string;
    spendDate: string;
}, receiptFile: File | null) {
    const formData = new FormData();
    formData.append(
        "data",
        new Blob(
            [JSON.stringify(data)],
            { type: "application/json" }
        )
    );

    if (receiptFile) {
        formData.append("receipt", receiptFile);
    }

    return apiFetch(
        "/api/v1/spends",
        {
            method: "POST",
            body: formData,
        }
    );
}

export default function NewSpendPage() {
    return <SpendFormPage />;
}
