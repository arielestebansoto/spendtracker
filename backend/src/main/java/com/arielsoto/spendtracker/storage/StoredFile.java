package com.arielsoto.spendtracker.storage;

public record StoredFile(
    String key,
    String url,
    String contentType,
    long size
) {
    
}
