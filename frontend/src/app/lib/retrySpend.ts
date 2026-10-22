import { apiFetch } from "@/app/lib/api";

// Puts a FAILED Spend back in the pipeline. Returns true when the backend accepted it.
export async function retrySpend(spendId: string) {
  try {
    const response = await apiFetch(`/api/v1/spends/${spendId}/retry`, { method: "POST" });
    return response.ok;
  } catch {
    return false;
  }
}
