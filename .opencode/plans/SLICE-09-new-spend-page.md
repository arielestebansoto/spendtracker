# SLICE-09: Frontend - New Spend Page Redesign

## Goal

Redesign spend creation to support both receipt upload and manual entry, with different layouts for mobile and desktop.

---

## Tasks

### 9.1 Redesign New Spend Page

**File**: `frontend/src/app/spends/new/page.tsx` (rewrite)

**Mobile Layout** (`< md`):
1. Receipt upload is primary (camera opens automatically)
2. "Enter manually" toggle button at bottom
3. When receipt is uploaded:
   - Show processing state
   - Auto-fill form with classified data
   - User reviews and edits
4. When "Enter manually" is clicked:
   - Hide receipt upload
   - Show traditional form (current behavior)

**Desktop Layout** (`md:` and above):
1. Two-column layout:
   - Left: Receipt upload with drag-and-drop
   - Right: Manual form
2. When receipt is uploaded:
   - Left: Show receipt preview
   - Right: Auto-fill form with classified data
3. User can always edit the form regardless of upload state

**Key States**:
- `mode`: "receipt" | "manual" (mobile only)
- `status`: "idle" | "uploading" | "processing" | "ready" | "error"
- `form`: FormState (auto-filled or manual)

### 9.2 Add API Helper for Receipt Upload

**File**: `frontend/src/app/lib/receipt.ts` (new)

```typescript
import { apiFetch } from "./api";

export type ReceiptUploadResponse = {
  receiptUrl: string;
  contentType: string;
  size: number;
};

export async function uploadReceipt(
  spendId: string,
  file: File
): Promise<ReceiptUploadResponse> {
  const formData = new FormData();
  formData.append("receipt", file);

  const response = await apiFetch(`/api/v1/spends/${spendId}/receipt`, {
    method: "POST",
    body: formData,
    // Note: Don't set Content-Type header, browser will set it with boundary
  });

  if (!response.ok) {
    throw new Error("Failed to upload receipt");
  }

  return response.json();
}

export async function createSpendFromReceipt(
  file: File,
  categoryId?: string
): Promise<CreateSpendResponse> {
  const formData = new FormData();
  formData.append("receipt", file);
  if (categoryId) {
    formData.append("categoryId", categoryId);
  }

  const response = await apiFetch("/api/v1/spends/from-receipt", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error("Failed to create spend from receipt");
  }

  return response.json();
}
```

### 9.3 Update Form State

The form state will be extended to include receipt-related data:

```typescript
type FormState = {
  categoryId: string;
  description: string;
  amount: string;
  spendDate: string;
  receiptFile: File | null;
  receiptPreview: string | null;
};
```

### 9.4 Handle OCR Response

When the backend returns classified data, auto-fill the form:

```typescript
const classified = await createSpendFromReceipt(file, categoryId);

setForm({
  categoryId: classified.categoryId || "",
  description: classified.description || "",
  amount: classified.amount?.toString() || "",
  spendDate: classified.spendDate || new Date().toISOString().slice(0, 10),
  receiptFile: file,
  receiptPreview: URL.createObjectURL(file),
});
```

---

## Testing

1. Manual test: Mobile - camera opens automatically
2. Manual test: Mobile - toggle between receipt and manual mode
3. Manual test: Desktop - drag and drop receipt
4. Manual test: Desktop - manual form works independently
5. Manual test: Auto-fill works correctly
6. Manual test: User can edit auto-filled data

---

## Rollback

- Revert to original `page.tsx`
- Delete `receipt.ts`
