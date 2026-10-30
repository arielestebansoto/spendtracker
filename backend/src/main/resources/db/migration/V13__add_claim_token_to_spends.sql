-- Fencing token of the worker that last claimed the Spend for Extraction (null until a claim).
ALTER TABLE spends
    ADD COLUMN claim_token UUID;
