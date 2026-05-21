package com.arielsoto.spendtracker.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    StoredFile store(
        MultipartFile file,
        String key
    );
}
