"use client";

import { ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { compressImage } from "@/app/lib/compressImage";
import {
  createSpendFromReceipt,
  MAX_RECEIPT_SIZE,
  RECEIPT_ERROR_MESSAGES,
  ReceiptUploadError,
  suggestsManualEntry,
} from "@/app/lib/receipt";

// "camera" opens the phone's camera; "file" opens the file picker (photo or 1-page PDF).
type Source = "camera" | "file";

type Selection = {
  file: File;
  previewUrl: string | null;
};

type PreviewState =
  | { kind: "closed" }
  | { kind: "preparing" }
  | { kind: "ready"; selection: Selection | null; error: string; offerManualEntry: boolean }
  | { kind: "uploading"; selection: Selection };

type ReceiptCaptureProps = {
  source: Source;
  // Renders the button that starts the capture. `open` must be called from the tap itself:
  // browsers only open the camera or the file picker from a direct user action.
  children: (open: () => void) => ReactNode;
};

// Takes a photo or picks a file, shows it in a full-screen preview, and uploads it only when the
// user confirms. On success it goes to the new Spend's detail page.
export default function ReceiptCapture({ source, children }: ReceiptCaptureProps) {
  const router = useRouter();
  // Kept in state, not a ref, because `open` is handed to `children` during render.
  const [input, setInput] = useState<HTMLInputElement | null>(null);
  const [state, setState] = useState<PreviewState>({ kind: "closed" });

  const isOpen = state.kind !== "closed";
  const selection = state.kind === "ready" || state.kind === "uploading" ? state.selection : null;
  const previewUrl = selection?.previewUrl;

  // Free the preview's object URL when it is replaced or the preview closes.
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  // The page underneath must not scroll while the preview covers it.
  useEffect(() => {
    if (!isOpen) return;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [isOpen]);

  function open() {
    input?.click();
  }

  function close() {
    setState({ kind: "closed" });
  }

  function showError(error: string, offerManualEntry = false, keep: Selection | null = null) {
    setState({ kind: "ready", selection: keep, error, offerManualEntry });
  }

  async function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const original = e.target.files?.[0];
    // Reset so picking the same file again still fires a change.
    e.target.value = "";
    if (!original) return;

    const isPdf = original.type === "application/pdf";
    if (!isPdf && !original.type.startsWith("image/")) {
      showError("Upload a photo or a PDF.");
      return;
    }

    let file = original;
    if (!isPdf) {
      setState({ kind: "preparing" });
      try {
        file = await compressImage(original);
      } catch {
        showError("We could not read this image. Try a JPEG or PNG photo.");
        return;
      }
    }

    if (file.size > MAX_RECEIPT_SIZE) {
      showError(`The file is ${formatSize(file.size)}. The limit is 5 MB.`);
      return;
    }

    const preview = { file, previewUrl: isPdf ? null : URL.createObjectURL(file) };
    setState({ kind: "ready", selection: preview, error: "", offerManualEntry: false });
  }

  async function upload(current: Selection) {
    setState({ kind: "uploading", selection: current });
    try {
      const created = await createSpendFromReceipt(current.file);
      router.push(`/spends/${created.id}?created=receipt`);
    } catch (error) {
      const kind = error instanceof ReceiptUploadError ? error.kind : "unknown";
      showError(RECEIPT_ERROR_MESSAGES[kind], suggestsManualEntry(kind), current);
    }
  }

  return (
    <>
      <input
        ref={setInput}
        type="file"
        accept={source === "camera" ? "image/*" : "image/*,application/pdf"}
        capture={source === "camera" ? "environment" : undefined}
        onChange={handleInputChange}
        className="hidden"
      />

      {children(open)}

      {isOpen && (
        <div className="fixed inset-0 z-50 flex flex-col bg-background">
          <div className="flex-1 min-h-0 flex items-center justify-center p-4">
            {state.kind === "preparing" ? (
              <div className="text-center space-y-2">
                <div className="animate-spin w-8 h-8 border-2 border-primary border-t-transparent rounded-full mx-auto" />
                <p className="text-sm text-muted-foreground">Preparing image...</p>
              </div>
            ) : selection ? (
              <SelectedFile selection={selection} />
            ) : null}
          </div>

          {state.kind === "ready" && state.error && (
            <div className="mx-4 mb-4 rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
              {state.error}
              {state.offerManualEntry && (
                <>
                  {" "}
                  {/* The manual form is on the page underneath. */}
                  <button type="button" onClick={close} className="font-medium underline">
                    Enter manually
                  </button>
                </>
              )}
            </div>
          )}

          {/* Left = secondary, center = main action, in the floating button's spot. */}
          <div className="grid grid-cols-3 items-center px-4 pb-6">
            <button
              type="button"
              onClick={open}
              disabled={state.kind !== "ready"}
              className="justify-self-start px-4 py-3 rounded-full border border-border text-sm font-medium hover:bg-accent transition disabled:opacity-50"
            >
              {source === "camera" ? "↺ Retake" : "Choose another"}
            </button>
            <button
              type="button"
              onClick={() => selection && upload(selection)}
              disabled={state.kind !== "ready" || !selection}
              className="justify-self-center inline-flex items-center gap-2 h-14 px-6 rounded-full bg-primary text-primary-foreground text-sm font-medium shadow-lg hover:opacity-90 transition disabled:opacity-50"
            >
              {state.kind === "uploading" && (
                <span className="animate-spin w-4 h-4 border-2 border-primary-foreground border-t-transparent rounded-full" />
              )}
              {state.kind === "uploading" ? "Uploading" : "Upload"}
            </button>
            <button
              type="button"
              onClick={close}
              disabled={state.kind === "uploading"}
              className="justify-self-end px-4 py-3 rounded-full border border-border text-sm font-medium hover:bg-accent transition disabled:opacity-50"
            >
              ✕ Cancel
            </button>
          </div>
        </div>
      )}
    </>
  );
}

function SelectedFile({ selection }: { selection: Selection }) {
  const { file, previewUrl } = selection;

  return (
    <div className="flex flex-col items-center gap-3 max-h-full">
      {previewUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- local object URL, nothing for next/image to optimize
        <img src={previewUrl} alt="Receipt preview" className="max-h-[65vh] max-w-full object-contain rounded-lg" />
      ) : (
        <div className="w-16 h-16 rounded-lg bg-muted flex items-center justify-center text-sm font-semibold text-muted-foreground">
          PDF
        </div>
      )}
      <p className="text-xs text-muted-foreground text-center break-all">
        {file.name} · {formatSize(file.size)}
      </p>
    </div>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
