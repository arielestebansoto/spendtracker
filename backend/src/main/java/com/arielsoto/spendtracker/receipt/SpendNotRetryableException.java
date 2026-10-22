package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.spend.SpendStatus;

public class SpendNotRetryableException extends RuntimeException {

    public SpendNotRetryableException(SpendStatus status) {
        super("Only FAILED spends can be retried, but the spend is " + status);
    }
}
