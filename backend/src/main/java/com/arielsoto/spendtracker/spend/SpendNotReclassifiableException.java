package com.arielsoto.spendtracker.spend;

/** A FAILED Spend has no Extraction result to reclassify; it must be retried instead. */
public class SpendNotReclassifiableException extends RuntimeException {

    public SpendNotReclassifiableException() {
        super("A failed spend must be retried, not reclassified");
    }
}
