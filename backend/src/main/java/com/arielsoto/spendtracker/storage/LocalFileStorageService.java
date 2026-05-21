package com.arielsoto.spendtracker.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.storage.local.path}")
    private String storagePath;

    @Override
    public StoredFile store(MultipartFile file) {
        try {
            String extension = getExtension(file.getOriginalFilename());

            String filename = UUID.randomUUID() + "." + extension;

            Path target = Paths.get(storagePath, filename);

            Files.createDirectories(target.getParent());

            Files.copy(
                file.getInputStream(),
                target,
                StandardCopyOption.REPLACE_EXISTING 
            );
            
            return new StoredFile(
                filename,
                "/uploads/" + filename,
                file.getContentType(),
                file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }
    
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }

        return filename.substring(
            filename.lastIndexOf(".") + 1
        );
    }
    
}
