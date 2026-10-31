package com.arielsoto.spendtracker.receipt;

import lombok.Getter;

@Getter
public class ReceiptUploadRateLimitException extends RuntimeException {

    /** The window whose limit was reached: "minute" or "day". */
    private final String window;

    public ReceiptUploadRateLimitException(String window, String message) {
        super(message);
        this.window = window;
    }
}
