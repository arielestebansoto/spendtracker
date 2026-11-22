package com.arielsoto.spendtracker.spend;

/** No Spend with that id belongs to the user. */
public class SpendNotFoundException extends RuntimeException {

    public SpendNotFoundException() {
        super("Spend not found");
    }
}
