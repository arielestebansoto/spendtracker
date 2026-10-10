# Slice 10: End-to-End Verification

## Goal
Verify the complete flow works: upload receipt → limits checked → usage recorded → settings shows usage.

## Dependencies
- Slice 9 complete (settings page renders the usage section).
- Slice 13 complete — the `ce:GetCostAndUsage` IAM grant must exist or step 6 cannot pass.
- Step 6 additionally depends on a real AWS account with Textract and Bedrock usage in the
  current month. It cannot be verified against a fresh account: with no usage yet, a correct
  sync writes zeros and looks identical to a broken matcher.

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
   - Wait for the next `0 0 */6 * * *` cron tick (00:00, 06:00, 12:00, 18:00 server time)
   - Check logs for `billing_sync_start` / `billing_sync_complete`
   - Global table updated from AWS Cost Explorer data
   - If the log says `billing_sync_skipped`, the usage-type matchers are wrong and the
     observed AWS codes are in the log line — see slice 11
   - If the log says `billing_sync_failed`, the IAM grant for `ce:GetCostAndUsage` is missing
     — see slice 13

## Checklist
- [ ] Receipt upload works with AnalyzeExpense strategy
- [ ] Falls back to DetectDocumentText when AnalyzeExpense limit hit
- [ ] Returns 429 when both Textract limits exhausted
- [ ] Returns 429 when Bedrock token limits exhausted
- [ ] User usage increments correctly per receipt
- [ ] Global usage increments correctly
- [ ] GET /api/v1/ai-usage/me returns correct data
- [ ] Settings page displays usage numbers
- [ ] Billing sync job runs on its 6-hourly cron and updates the global table
