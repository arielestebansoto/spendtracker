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

export class ReceiptQuotaError extends Error {}

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

  if (response.status === 429) {
    throw new ReceiptQuotaError("AI quota exceeded");
  }

  if (!response.ok) {
    throw new Error("Failed to create spend from receipt");
  }

  return response.json();
}
