# SLICE-10: Frontend - Spend Detail with Receipt

## Goal

Show receipt image on spend detail page.

---

## Tasks

### 10.1 Update Spend Detail Page

**File**: `frontend/src/app/spends/[id]/page.tsx`

Add receipt image display:

```tsx
{spend.receiptUrl && (
  <div className="mt-6">
    <h2 className="text-lg font-semibold mb-3">Receipt</h2>
    <div className="rounded-xl border border-border overflow-hidden">
      <img
        src={apiUrl(spend.receiptUrl)}
        alt="Receipt"
        className="w-full h-auto"
        loading="lazy"
      />
    </div>
  </div>
)}
```

### 10.2 Add Loading State for Image

```tsx
const [imageLoaded, setImageLoaded] = useState(false);

{spend.receiptUrl && (
  <div className="mt-6">
    <h2 className="text-lg font-semibold mb-3">Receipt</h2>
    <div className="rounded-xl border border-border overflow-hidden bg-muted">
      {!imageLoaded && (
        <div className="w-full h-48 flex items-center justify-center">
          <div className="animate-spin w-6 h-6 border-2 border-primary border-t-transparent rounded-full" />
        </div>
      )}
      <img
        src={apiUrl(spend.receiptUrl)}
        alt="Receipt"
        className={`w-full h-auto ${imageLoaded ? "" : "hidden"}`}
        loading="lazy"
        onLoad={() => setImageLoaded(true)}
      />
    </div>
  </div>
)}
```

### 10.3 Add "No Receipt" Placeholder

```tsx
{!spend.receiptUrl && (
  <div className="mt-6">
    <h2 className="text-lg font-semibold mb-3">Receipt</h2>
    <div className="rounded-xl border border-dashed border-border p-8 text-center">
      <p className="text-sm text-muted-foreground">No receipt uploaded</p>
    </div>
  </div>
)}
```

### 10.4 Add Receipt Upload Button (Optional)

If user wants to add a receipt to an existing spend:

```tsx
{!spend.receiptUrl && (
  <div className="mt-4">
    <ReceiptUpload
      onFileSelected={handleReceiptUpload}
      isProcessing={isUploading}
      disabled={isUploading}
    />
  </div>
)}
```

---

## Testing

1. Manual test: View spend with receipt - image loads correctly
2. Manual test: View spend without receipt - placeholder shows
3. Manual test: Lazy loading works (image loads on scroll)
4. Manual test: Loading spinner shows while image loads

---

## Rollback

- Revert `page.tsx` to original version
