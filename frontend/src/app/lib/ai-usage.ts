import { apiFetch } from "./api";

export type AiUsage = {
  analyzeExpenseUsed: number;
  analyzeExpenseLimit: number;
  detectTextUsed: number;
  detectTextLimit: number;
  bedrockInputUsed: number;
  bedrockInputLimit: number;
  bedrockOutputUsed: number;
  bedrockOutputLimit: number;
};

export async function fetchAiUsage(): Promise<AiUsage> {
  const response = await apiFetch("/api/v1/ai-usage/me");

  if (!response.ok) {
    throw new Error("Failed to fetch AI usage");
  }

  return response.json();
}