-- Receipt metadata table
CREATE TABLE receipt_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spend_id UUID NOT NULL UNIQUE,
    raw_ocr_text TEXT,
    ocr_confidence REAL,
    classified_at TIMESTAMP,
    raw_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_receipt_metadata_spend 
        FOREIGN KEY (spend_id) 
        REFERENCES spends(id) 
        ON DELETE CASCADE
);

-- Spend items table
CREATE TABLE spend_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    spend_id UUID NOT NULL,
    description TEXT NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    
    CONSTRAINT fk_spend_items_spend 
        FOREIGN KEY (spend_id) 
        REFERENCES spends(id) 
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_receipt_metadata_spend_id ON receipt_metadata(spend_id);
CREATE INDEX idx_spend_items_spend_id ON spend_items(spend_id);
CREATE INDEX idx_spend_items_position ON spend_items(spend_id, position);
