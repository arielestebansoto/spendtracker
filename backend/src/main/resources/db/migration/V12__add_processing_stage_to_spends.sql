ALTER TABLE spends
    ADD COLUMN processing_stage VARCHAR(20);

-- Until now every PROCESSING Spend was waiting for, or running, Enrichment or Reclassify.
UPDATE spends SET processing_stage = 'ENRICHMENT' WHERE status = 'PROCESSING';
