import { apiFetch } from "./api";
import { SpendStatus } from "@/app/lib/spendStatus";

export type CreateSpendFromReceiptResponse = {
  id: string;
  categoryId: string;
  category: string;
  description: string;
  amount: number;
  spendDate: string;
  status: SpendStatus;
};

// Same limit as the backend (spring.servlet.multipart.max-file-size) and nginx (docs/nginx.md).
export const MAX_RECEIPT_SIZE = 5 * 1024 * 1024;

export type ReceiptUploadErrorKind =
  | "invalid_file"
  | "too_large"
  | "ai_quota"
  | "rate_limit_minute"
  | "rate_limit_day"
  | "unknown";

export class ReceiptUploadError extends Error {
  constructor(readonly kind: ReceiptUploadErrorKind) {
    super(`Receipt upload failed: ${kind}`);
  }
}

// The extension properties of the backend's Problem Detail (RFC 9457) for a rejected upload.
type ErrorBody = {
  reason?: string;
  window?: string;
};

// Returns right away with a provisional PENDING Spend; the Category is decided later by Enrichment.
export async function createSpendFromReceipt(
  file: File
): Promise<CreateSpendFromReceiptResponse> {
  const formData = new FormData();
  formData.append("receipt", file);

  const response = await apiFetch("/api/v1/spends/from-receipt", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new ReceiptUploadError(errorKind(response.status, await readErrorBody(response)));
  }

  return response.json();
}

function errorKind(status: number, body: ErrorBody | null): ReceiptUploadErrorKind {
  switch (status) {
    case 400:
      return "invalid_file";
    case 413:
      return "too_large";
    case 429:
      if (body?.reason === "ai_quota") return "ai_quota";
      if (body?.reason === "rate_limit" && body.window === "day") return "rate_limit_day";
      // The backend's per-minute limit, or nginx's per-IP limit (an HTML page, no body to read).
      return "rate_limit_minute";
    default:
      return "unknown";
  }
}

// nginx answers 413 and its own 429 with HTML, so the body is not always JSON.
async function readErrorBody(response: Response): Promise<ErrorBody | null> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

export const RECEIPT_ERROR_MESSAGES: Record<ReceiptUploadErrorKind, string> = {
  invalid_file: "This file is not a supported receipt. Upload a JPEG or PNG photo, or a 1-page PDF.",
  too_large: "The file is larger than 5 MB.",
  ai_quota: "You have no AI quota left to read receipts. Enter the details manually instead.",
  rate_limit_minute: "Too many uploads in a short time. Wait a minute and try again.",
  rate_limit_day: "You reached today's receipt upload limit. Try again tomorrow or enter the details manually.",
  unknown: "Failed to upload receipt. Please try again or enter details manually.",
};

// Errors where entering the Spend by hand is the way forward, so the message links to the form.
export function suggestsManualEntry(kind: ReceiptUploadErrorKind) {
  return kind === "ai_quota" || kind === "rate_limit_day" || kind === "unknown";
}
