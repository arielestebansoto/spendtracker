-- When the Spend's data last changed, by a user edit or by Enrichment. Status changes are in status_changed_at.
ALTER TABLE spends
    ADD COLUMN updated_at TIMESTAMP;

-- Existing rows have not been edited since they were created, as far as we know.
UPDATE spends SET updated_at = created_at;

ALTER TABLE spends
    ALTER COLUMN updated_at SET NOT NULL;
