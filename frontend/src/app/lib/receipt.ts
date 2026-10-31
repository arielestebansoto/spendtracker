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
