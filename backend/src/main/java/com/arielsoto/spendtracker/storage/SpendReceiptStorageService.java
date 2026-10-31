package com.arielsoto.spendtracker.storage;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.Resource;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.arielsoto.spendtracker.user.UserApp;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnBean(FileStorageService.class)
@Slf4j
public class SpendReceiptStorageService {

    private final FileStorageService fileStorageService;

    public SpendReceiptStorageService(
        FileStorageService fileStorageService
    ) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Stores the Receipt under spends/{userId}/{spendId}.{extension}. The extension and content type
     * come from the detected file type, never from the client's file name or Content-Type.
     */
    public StoredFile store(
        UserApp user,
        UUID spendId,
        MultipartFile file,
        String extension,
        String contentType
    ) {
        String key = String.format(
            "spends/%s/%s.%s",
            user.getId(),
            spendId,
            extension
        );

        return fileStorageService.store(
            file,
            key,
            contentType
        );
    }

    public Resource getResource(String key) {
        return fileStorageService.load(key);
    }

    public void deleteFile(String key) {
        fileStorageService.deleteFile(key);
    }

    public void deleteAllReceiptsByUser(UserApp user) {
        String prefix = "spends/" + user.getId();
        log.info(
            "delete_all_receipts_by_user userId={} prefix={}",
            user.getId(),
            prefix
        );
        fileStorageService.deleteDirectory(prefix);
    }
}
