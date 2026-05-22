package com.arielsoto.spendtracker.storage;

import org.springframework.core.io.Resource;

public record StoredResource(
    Resource resource,
    String contentType
) {
    
}
