package com.arielsoto.spendtracker.storage;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.arielsoto.spendtracker.user.UserApp;

@Service
@ConditionalOnBean(FileStorageService.class)
public class SpendReceiptStorageService {

    private final FileStorageService fileStorageService;

    public SpendReceiptStorageService(
        FileStorageService fileStorageService
    ) {
        this.fileStorageService = fileStorageService;
    }

    public StoredFile store(
        UserApp user,
        MultipartFile file
    ) {

        String extension = getExtension(
            file.getOriginalFilename()
        );

        String key = String.format(
            "spends/%s/%s.%s",
            user.getId(),
            UUID.randomUUID(),
            extension
        );

        return fileStorageService.store(
            file,
            key
        );
    }

    private String getExtension(
        String filename
    ) {
        if (
            filename == null
            || !filename.contains(".")
        ) {
            return "bin";
        }

        return filename.substring(
            filename.lastIndexOf(".") + 1
        );
    }

    public Resource getResource(String key) {
        return fileStorageService.load(key);
    }

    public void deleteAllReceiptsByUser(UserApp user) {
        fileStorageService.deleteDirectory(
            "spends/" + user.getId()
        );
    }
}
