package com.arielsoto.spendtracker.receipt;

public record ReceiptUploadResponse(
    String receiptUrl,
    String contentType,
    long size
) {}
