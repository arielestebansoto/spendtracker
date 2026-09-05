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
