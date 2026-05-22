package com.arielsoto.spendtracker.storage;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.Resource;

public interface FileStorageService {
    StoredFile store(
        MultipartFile file,
        String key
    );

    Resource load(String key);
}
