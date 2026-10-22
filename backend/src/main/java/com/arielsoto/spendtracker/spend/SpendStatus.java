package com.arielsoto.spendtracker.spend;

public enum SpendStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED;

    /** While in progress, a background result is on its way, so the Spend cannot be edited or reclassified. */
    public boolean isInProgress() {
        return this == PENDING || this == PROCESSING;
    }
}
