package com.arielsoto.spendtracker.storage;

public record StoredFile(
    String key,
    String contentType,
    long size
) {
    
}
