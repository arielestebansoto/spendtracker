import { apiFetch } from "./api";

export type DashboardSummary = {
  totalSpent: number;
  spendCount: number;
  averageSpend: number;
  recentSpends: {
    id: string;
    category: string;
    description: string;
    amount: number;
    spendDate: string;
  }[];
};

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const res = await apiFetch("/api/v1/spends/summary?recentLimit=5");
  if (!res.ok) throw new Error("Failed to fetch dashboard");
  return res.json();
}
