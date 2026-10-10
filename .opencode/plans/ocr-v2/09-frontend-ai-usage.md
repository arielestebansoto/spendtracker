# Slice 9: Frontend — AI Usage Display in Settings

## Goal
Show the user their AI usage and limits on the settings page.

## Files
- **New:** `frontend/src/app/lib/ai-usage.ts`
- **Modify:** `frontend/src/app/settings/page.tsx`

## Details

### ai-usage.ts
```typescript
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
  if (!response.ok) throw new Error("Failed to fetch AI usage");
  return response.json();
}
```

### settings/page.tsx additions
Add to the existing settings page (after the account info section):

```tsx
// Add to imports
import { fetchAiUsage, AiUsage } from "../lib/ai-usage";

// Add state
const [aiUsage, setAiUsage] = useState<AiUsage | null>(null);

// Add useEffect to fetch
useEffect(() => {
  fetchAiUsage().then(setAiUsage).catch(console.error);
}, []);

// Add UI section (simple text numbers format)
{aiUsage && (
  <div className="mt-8 border border-border rounded-lg p-6">
    <h2 className="text-lg font-semibold mb-4">AI Usage (this month)</h2>
    <div className="space-y-2 text-sm">
      <p>Expense extraction: {aiUsage.analyzeExpenseUsed} of {aiUsage.analyzeExpenseLimit} pages used</p>
      <p>Text extraction: {aiUsage.detectTextUsed} of {aiUsage.detectTextLimit} pages used</p>
      <p>AI input tokens: {aiUsage.bedrockInputUsed.toLocaleString()} of {aiUsage.bedrockInputLimit.toLocaleString()} used</p>
      <p>AI output tokens: {aiUsage.bedrockOutputUsed.toLocaleString()} of {aiUsage.bedrockOutputLimit.toLocaleString()} used</p>
    </div>
  </div>
)}
```

## Verify
- `pnpm build` passes
