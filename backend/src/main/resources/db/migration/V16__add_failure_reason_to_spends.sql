-- Why a FAILED Spend failed, when that decides whether it can be retried. NULL for failures a Retry can fix.
ALTER TABLE spends
    ADD COLUMN failure_reason VARCHAR(30);
