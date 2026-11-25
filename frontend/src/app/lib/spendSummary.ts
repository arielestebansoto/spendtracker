import { apiFetch } from "./api";

// Totals of the current month's processed Spends, computed by the backend.
export type SpendSummary = {
  totalSpent: number;
  spendCount: number;
  averageSpend: number;
};

export async function fetchSpendSummary(): Promise<SpendSummary> {
  // The response also lists a few recent Spends, which this page does not use.
  const res = await apiFetch("/api/v1/spends/summary");
  if (!res.ok) throw new Error("Failed to fetch the spend summary");
  const { totalSpent, spendCount, averageSpend } = await res.json();
  return { totalSpent, spendCount, averageSpend };
}
