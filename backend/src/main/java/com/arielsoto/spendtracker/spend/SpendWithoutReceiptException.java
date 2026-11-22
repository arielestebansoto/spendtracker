package com.arielsoto.spendtracker.spend;

/** Reclassify needs the Receipt text, so a Spend created by hand cannot be reclassified. */
public class SpendWithoutReceiptException extends RuntimeException {

    public SpendWithoutReceiptException() {
        super("Only a spend with a receipt can be reclassified");
    }
}
