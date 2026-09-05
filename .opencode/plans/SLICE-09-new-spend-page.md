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
   - Backend creates spend with AI classification
   - Redirect to spend detail page
4. When "Enter manually" is clicked:
   - Hide receipt upload
   - Show traditional form (current behavior)

**Desktop Layout** (`md:` and above):
1. Two-column layout:
   - Left: Receipt upload with drag-and-drop
   - Right: Manual form
2. When receipt is uploaded:
   - Show processing state
   - Backend creates spend with AI classification
   - Redirect to spend detail page
3. User can always use manual form regardless of upload state

**Key States**:
- `mode`: "receipt" | "manual" (mobile only)
- `status`: "idle" | "uploading" | "processing" | "error"
- `form`: FormState (manual mode only)

### 9.2 Add API Helper for Receipt Upload

**File**: `frontend/src/app/lib/receipt.ts` (new)

```typescript
import { apiFetch } from "./api";

export type CreateSpendFromReceiptResponse = {
  id: string;
  categoryId: string;
  categoryName: string;
  description: string;
  amount: number;
  spendDate: string;
};

export async function createSpendFromReceipt(
  file: File,
  categoryId?: string
): Promise<CreateSpendFromReceiptResponse> {
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

**Note**: The backend handles both receipt upload AND spend creation in a single call. There is no separate upload endpoint.

### 9.3 Update Form State

The form state (manual mode only):

```typescript
type FormState = {
  categoryId: string;
  description: string;
  amount: string;
  spendDate: string;
};
```

### 9.4 Handle Receipt Response

When the backend returns the created spend data:

```typescript
const created = await createSpendFromReceipt(file, categoryId);

// Receipt uploaded, spend created, AI classified it
// Redirect to spend detail page
navigate(`/spends/${created.id}`);
```

---

## Testing

1. Manual test: Mobile - camera opens automatically
2. Manual test: Mobile - toggle between receipt and manual mode
3. Manual test: Desktop - drag and drop receipt
4. Manual test: Desktop - manual form works independently
5. Manual test: Receipt upload creates spend and redirects

---

## Rollback

- Revert to original `page.tsx`
- Delete `receipt.ts`
