package com.arielsoto.spendtracker.receipt;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final SpendReceiptStorageService storageService;
    private final SpendRepository spendRepository;

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
}
