# SLICE-08: Frontend - Receipt Upload Component

## Goal

Build a reusable receipt upload component for desktop and mobile.

---

## Tasks

### 8.1 Create ReceiptUpload Component

**File**: `frontend/src/app/components/ReceiptUpload.tsx` (new)

```tsx
"use client";

import { useState, useRef, DragEvent } from "react";

type ReceiptUploadProps = {
  onFileSelected: (file: File) => void;
  isProcessing?: boolean;
  disabled?: boolean;
};

export default function ReceiptUpload({
  onFileSelected,
  isProcessing = false,
  disabled = false,
}: ReceiptUploadProps) {
  const [preview, setPreview] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Handle file selection
  function handleFile(file: File) {
    // Validate file type (client-side)
    const allowedTypes = ["image/jpeg", "image/png", "image/webp", "application/pdf"];
    if (!allowedTypes.includes(file.type)) {
      alert("File type not allowed. Please upload JPEG, PNG, WebP, or PDF.");
      return;
    }

    // Validate file size (10MB)
    if (file.size > 10 * 1024 * 1024) {
      alert("File size exceeds 10MB limit.");
      return;
    }

    // Create preview for images
    if (file.type.startsWith("image/")) {
      const reader = new FileReader();
      reader.onload = (e) => setPreview(e.target?.result as string);
      reader.readAsDataURL(file);
    } else {
      setPreview(null);
    }

    onFileSelected(file);
  }

  // Drag and drop handlers
  function handleDragOver(e: DragEvent) {
    e.preventDefault();
    setIsDragging(true);
  }

  function handleDragLeave(e: DragEvent) {
    e.preventDefault();
    setIsDragging(false);
  }

  function handleDrop(e: DragEvent) {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  }

  // File input handler
  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
  }

  return (
    <div className="space-y-4">
      {/* Drop zone */}
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        className={`
          border-2 border-dashed rounded-xl p-8 text-center cursor-pointer
          transition-colors duration-200
          ${isDragging ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"}
          ${disabled ? "opacity-60 cursor-not-allowed" : ""}
        `}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,application/pdf"
          onChange={handleInputChange}
          className="hidden"
          disabled={disabled}
        />

        {isProcessing ? (
          <div className="space-y-2">
            <div className="animate-spin w-8 h-8 border-2 border-primary border-t-transparent rounded-full mx-auto" />
            <p className="text-sm text-muted-foreground">Processing receipt...</p>
          </div>
        ) : preview ? (
          <img
            src={preview}
            alt="Receipt preview"
            className="max-h-48 mx-auto rounded-lg"
          />
        ) : (
          <div className="space-y-2">
            <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center mx-auto">
              <svg className="w-6 h-6 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
            <p className="text-sm text-foreground font-medium">
              Take a photo or upload a file
            </p>
            <p className="text-xs text-muted-foreground">
              JPEG, PNG, WebP, or PDF up to 10MB
            </p>
          </div>
        )}
      </div>

      {/* Mobile camera button */}
      <div className="md:hidden">
        <input
          type="file"
          accept="image/*"
          capture="environment"
          onChange={handleInputChange}
          className="hidden"
          id="camera-input"
          disabled={disabled}
        />
        <label
          htmlFor="camera-input"
          className={`
            block w-full py-3 px-4 rounded-lg border border-border text-center text-sm font-medium
            cursor-pointer hover:bg-accent transition
            ${disabled ? "opacity-60 cursor-not-allowed" : ""}
          `}
        >
          Open Camera
        </label>
      </div>
    </div>
  );
}
```

---

## Testing

1. Manual test: Test drag and drop on desktop
2. Manual test: Test file picker on mobile
3. Manual test: Test camera on mobile
4. Manual test: Test file type validation
5. Manual test: Test file size validation

---

## Rollback

- Delete `ReceiptUpload.tsx`
