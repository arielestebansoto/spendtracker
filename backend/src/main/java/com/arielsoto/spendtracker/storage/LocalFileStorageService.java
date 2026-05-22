package com.arielsoto.spendtracker.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.storage.local.path}")
    private String storagePath;

    @Override
    public StoredFile store(
        MultipartFile file,
        String key
    ) {
        try {
            Path target = Paths.get(
                storagePath,
                key
            );

            Files.createDirectories(
                target.getParent()
            );

            Files.copy(
                file.getInputStream(),
                target,
                StandardCopyOption.REPLACE_EXISTING
            );

            return new StoredFile(
                key,
                file.getContentType(),
                file.getSize()
            );

        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to store file",
                e
            );
        }
    } 
    
    @Override
    public Resource load(String key) {

        Path path = Paths.get(
            storagePath,
            key
        );

        return new FileSystemResource(path);
    }
}
