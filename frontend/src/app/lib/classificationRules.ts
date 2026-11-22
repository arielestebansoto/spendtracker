import { apiFetch } from "./api";

export type ClassificationRule = {
  id: string;
  condition: string;
  categoryId: string;
  categoryName: string;
  createdAt: string;
  updatedAt: string;
};

export type RulesListResponse = {
  rules: ClassificationRule[];
  maxRules: number;
};

// Backend errors are RFC 9457 Problem Details; `detail` is the message to show.
async function readError(response: Response, fallback: string) {
  try {
    const body = await response.json();
    if (body && typeof body.detail === "string") return body.detail;
  } catch {
    // ignore non-JSON error bodies
  }
  return fallback;
}

export async function listRules(): Promise<RulesListResponse> {
  const response = await apiFetch("/api/v1/classification-rules");
  if (!response.ok) throw new Error("Failed to load rules");
  return response.json();
}

export async function createRule(
  condition: string,
  categoryId: string
): Promise<ClassificationRule> {
  const response = await apiFetch("/api/v1/classification-rules", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ condition, categoryId }),
  });
  if (!response.ok) {
    throw new Error(await readError(response, "Failed to create rule"));
  }
  return response.json();
}

export async function updateRule(
  id: string,
  condition: string,
  categoryId: string
): Promise<ClassificationRule> {
  const response = await apiFetch(`/api/v1/classification-rules/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ condition, categoryId }),
  });
  if (!response.ok) {
    throw new Error(await readError(response, "Failed to update rule"));
  }
  return response.json();
}

export async function deleteRule(id: string): Promise<void> {
  const response = await apiFetch(`/api/v1/classification-rules/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(await readError(response, "Failed to delete rule"));
  }
}
