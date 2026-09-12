package com.arielsoto.spendtracker.receipt;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.storage.StoredFile;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final SpendReceiptStorageService storageService;
    private final SpendRepository spendRepository;

    @Transactional
    public ReceiptUploadResponse storeReceipt(
        UserApp user,
        UUID spendId,
        MultipartFile file
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(spendId, user.getId())
            .orElseThrow(() -> new RuntimeException(
                "Spend not found"
            ));

        ReceiptValidator.validate(file);

        StoredFile stored = storageService.store(
            user,
            spendId,
            file
        );

        spend.setReceiptKey(stored.key());
        spend.setReceiptContentType(stored.contentType());
        spendRepository.save(spend);

        return new ReceiptUploadResponse(
            spend.receiptUrl(),
            stored.contentType(),
            stored.size()
        );
    }

    public Resource loadReceipt(
        UserApp user,
        UUID spendId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(spendId, user.getId())
            .orElseThrow(() -> new RuntimeException(
                "Spend not found"
            ));

        if (spend.getReceiptKey() == null) {
            throw new RuntimeException("No receipt found");
        }

        return storageService.getResource(
            spend.getReceiptKey()
        );
    }

    public String getReceiptContentType(
        UserApp user,
        UUID spendId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(spendId, user.getId())
            .orElseThrow(() -> new RuntimeException(
                "Spend not found"
            ));

        return spend.getReceiptContentType();
    }

    @Transactional
    public void deleteReceipt(
        UserApp user,
        UUID spendId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(spendId, user.getId())
            .orElseThrow(() -> new RuntimeException(
                "Spend not found"
            ));

        if (spend.getReceiptKey() != null) {
            storageService.deleteFile(
                spend.getReceiptKey()
            );
            spend.setReceiptKey(null);
            spend.setReceiptContentType(null);
            spendRepository.save(spend);
        }
    }
}
