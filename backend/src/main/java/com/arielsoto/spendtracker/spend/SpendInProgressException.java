package com.arielsoto.spendtracker.spend;

/** The Spend is PENDING or PROCESSING, so the background result would overwrite any change. */
public class SpendInProgressException extends RuntimeException {

    public SpendInProgressException() {
        super("The spend is being processed and cannot be changed yet");
    }
}
