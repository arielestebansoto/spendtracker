"use client";

import { useEffect, useRef, useState, DragEvent } from "react";
import { compressImage } from "@/app/lib/compressImage";
import { MAX_RECEIPT_SIZE } from "@/app/lib/receipt";

type ReceiptUploadProps = {
  // Called when the user confirms the upload of the selected (already compressed) file.
  onUpload: (file: File) => void;
  // Called when the selection changes, so the parent can clear a previous upload error.
  onSelectionChange?: () => void;
  error?: string;
  isProcessing?: boolean;
  disabled?: boolean;
};

type Selection = {
  file: File;
  previewUrl: string | null;
};

export default function ReceiptUpload({
  onUpload,
  onSelectionChange,
  error,
  isProcessing = false,
  disabled = false,
}: ReceiptUploadProps) {
  const [selection, setSelection] = useState<Selection | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);
  const [localError, setLocalError] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);

  // Free the preview's object URL when it is replaced or the component unmounts.
  useEffect(() => {
    const previewUrl = selection?.previewUrl;
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [selection]);

  async function handleFile(original: File) {
    setLocalError("");
    setSelection(null);
    onSelectionChange?.();

    const isPdf = original.type === "application/pdf";
    if (!isPdf && !original.type.startsWith("image/")) {
      setLocalError("Upload a photo or a PDF.");
      return;
    }

    let file = original;
    if (!isPdf) {
      setIsPreparing(true);
      try {
        file = await compressImage(original);
      } catch {
        setLocalError("We could not read this image. Try a JPEG or PNG photo.");
        return;
      } finally {
        setIsPreparing(false);
      }
    }

    if (file.size > MAX_RECEIPT_SIZE) {
      setLocalError(`The file is ${formatSize(file.size)}. The limit is 5 MB.`);
      return;
    }

    setSelection({ file, previewUrl: isPdf ? null : URL.createObjectURL(file) });
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    // Reset so picking the same file again still fires a change.
    e.target.value = "";
    if (file) handleFile(file);
  }

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
    if (disabled) return;
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  }

  function handleChange() {
    setSelection(null);
    setLocalError("");
    onSelectionChange?.();
    fileInputRef.current?.click();
  }

  const shownError = localError || error;

  return (
    <div className="space-y-4">
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*,application/pdf"
        onChange={handleInputChange}
        className="hidden"
        disabled={disabled}
      />
      <input
        ref={cameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={handleInputChange}
        className="hidden"
        disabled={disabled}
      />

      {isProcessing || isPreparing ? (
        <div className="border-2 border-dashed border-border rounded-xl p-8 text-center space-y-2">
          <div className="animate-spin w-8 h-8 border-2 border-primary border-t-transparent rounded-full mx-auto" />
          <p className="text-sm text-muted-foreground">
            {isProcessing ? "Uploading receipt..." : "Preparing image..."}
          </p>
        </div>
      ) : selection ? (
        <SelectedFile
          selection={selection}
          disabled={disabled}
          onUpload={() => onUpload(selection.file)}
          onChange={handleChange}
        />
      ) : (
        <div
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => !disabled && fileInputRef.current?.click()}
          className={`
            border-2 border-dashed rounded-xl p-8 text-center cursor-pointer
            transition-colors duration-200
            ${isDragging ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"}
            ${disabled ? "opacity-60 cursor-not-allowed" : ""}
          `}
        >
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
          </div>
        </div>
      )}

      <p className="text-xs text-muted-foreground text-center">
        A photo or a 1-page PDF, up to 5 MB. Photos are resized before upload.
      </p>

      {shownError && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
          {shownError}
        </div>
      )}

      {!selection && (
        <div className="md:hidden">
          <button
            type="button"
            onClick={() => cameraInputRef.current?.click()}
            disabled={disabled || isPreparing}
            className="block w-full py-3 px-4 rounded-lg border border-border text-center text-sm font-medium hover:bg-accent transition disabled:opacity-60 disabled:cursor-not-allowed"
          >
            Open Camera
          </button>
        </div>
      )}
    </div>
  );
}

function SelectedFile({
  selection,
  disabled,
  onUpload,
  onChange,
}: {
  selection: Selection;
  disabled: boolean;
  onUpload: () => void;
  onChange: () => void;
}) {
  const { file, previewUrl } = selection;

  return (
    <div className="border border-border rounded-xl p-4 space-y-4">
      {previewUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- local object URL, nothing for next/image to optimize
        <img src={previewUrl} alt="Receipt preview" className="max-h-64 mx-auto rounded-lg" />
      ) : (
        <div className="w-12 h-12 rounded-lg bg-muted flex items-center justify-center mx-auto text-xs font-semibold text-muted-foreground">
          PDF
        </div>
      )}

      <div className="text-center text-sm">
        <p className="font-medium text-foreground break-all">{file.name}</p>
        <p className="text-muted-foreground">
          {file.type === "application/pdf" ? "PDF" : "JPEG image"} · {formatSize(file.size)}
        </p>
      </div>

      <div className="flex gap-3">
        <button
          type="button"
          onClick={onChange}
          disabled={disabled}
          className="flex-1 py-2.5 rounded-lg border border-border text-sm font-medium hover:bg-accent transition disabled:opacity-60"
        >
          Change
        </button>
        <button
          type="button"
          onClick={onUpload}
          disabled={disabled}
          className="flex-1 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition disabled:opacity-60"
        >
          Upload
        </button>
      </div>
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
