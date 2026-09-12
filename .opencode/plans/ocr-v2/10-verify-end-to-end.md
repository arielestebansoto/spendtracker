# Slice 10: End-to-End Verification

## Goal
Verify the complete flow works: upload receipt → limits checked → usage recorded → settings shows usage.

## Steps

1. **Start the stack:**
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
   ```

2. **Upload a receipt:**
   - Go to `/spends/new`
   - Upload a receipt image
   - Verify it processes successfully
   - Check DB: `ai_usage_user` and `ai_usage_global` both have 1 page for ANALYZE_EXPENSE + token counts

3. **Check the /me endpoint:**
   ```bash
   curl -b cookies.txt http://localhost:8080/api/v1/ai-usage/me
   ```
   Should return usage with limits.

4. **Check settings page:**
   - Go to `/settings`
   - Verify "AI Usage (this month)" section shows correct numbers

5. **Test limit enforcement:**
   - Manually set `analyze_expense_pages` to 10 for user in DB
   - Upload another receipt
   - Should fall back to DetectDocumentText (check logs for strategy name)
   - Set both to limits → should return 429 error

6. **Verify billing sync:**
   - Check logs hourly for `billing_sync_start` / `billing_sync_complete`
   - Global table updated from AWS Cost Explorer data

## Checklist
- [ ] Receipt upload works with AnalyzeExpense strategy
- [ ] Falls back to DetectDocumentText when AnalyzeExpense limit hit
- [ ] Returns 429 when both Textract limits exhausted
- [ ] Returns 429 when Bedrock token limits exhausted
- [ ] User usage increments correctly per receipt
- [ ] Global usage increments correctly
- [ ] GET /api/v1/ai-usage/me returns correct data
- [ ] Settings page displays usage numbers
- [ ] Billing sync job runs hourly and updates global table
