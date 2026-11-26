import { apiFetch } from "./api";

// Totals of one month's processed Spends, computed by the backend.
export type SpendSummary = {
  totalSpent: number;
  spendCount: number;
  averageSpend: number;
};

// `month` is yyyy-MM, e.g. 2026-10.
export async function fetchSpendSummary(month: string): Promise<SpendSummary> {
  // The response also lists a few recent Spends, which this page does not use.
  const res = await apiFetch(`/api/v1/spends/summary?${new URLSearchParams({ month })}`);
  if (!res.ok) throw new Error("Failed to fetch the spend summary");
  const { totalSpent, spendCount, averageSpend } = await res.json();
  return { totalSpent, spendCount, averageSpend };
}
