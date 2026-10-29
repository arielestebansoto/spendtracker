package com.arielsoto.spendtracker.spend;

/**
 * Which pipeline stage a PROCESSING Spend is in. Internal: users only see PROCESSING. It lets a
 * redelivered message tell an interrupted stage of its own from the work of the other stage.
 */
public enum ProcessingStage {
    EXTRACTION,
    ENRICHMENT
}
