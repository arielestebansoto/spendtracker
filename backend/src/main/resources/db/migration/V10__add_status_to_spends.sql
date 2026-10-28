ALTER TABLE spends
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
    ADD COLUMN status_changed_at TIMESTAMP NOT NULL DEFAULT now();

-- Existing rows keep PROCESSED; new rows must set the status explicitly.
ALTER TABLE spends
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN status_changed_at DROP DEFAULT;

ALTER TABLE receipt_metadata
    DROP COLUMN processing_status;
