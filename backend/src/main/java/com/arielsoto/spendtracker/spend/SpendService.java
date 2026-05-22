package com.arielsoto.spendtracker.spend;

import java.util.UUID;

import org.springframework.core.io.Resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.SpendListItemResponse;
import com.arielsoto.spendtracker.spend.dto.UpdateSpendRequest;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.storage.StoredFile;
import com.arielsoto.spendtracker.storage.StoredResource;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpendService {

    private final SpendRepository spendRepository;
    private final CategoryRepository categoryRepository;
    private final SpendReceiptStorageService spendReceiptStorageService;

    public SpendDetailResponse findByIdAndUserId(
        UUID id,
        UUID userId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow();

        return new SpendDetailResponse(
            spend.getId(),
            spend.getCategory().getId(),
            spend.getCategory().getName(),
            spend.getDescription(),
            spend.getAmount(),
            spend.getCurrency(),
            spend.getSpendDate(),
            spend.getCreatedAt(),
            spend.receiptUrl()
        );
    }

    public Page<SpendListItemResponse> findAllByUserId(
        UUID userId,
        Pageable pageable
    ) {
        return spendRepository
            .findAllByUserId(userId, pageable)
            .map( s -> {
                return new SpendListItemResponse(
                    s.getId(),
                    s.getCategory().getName(),
                    s.getAmount(),
                    s.getCurrency(),
                    s.getSpendDate(),
                    s.getDescription()
                );
            });
    }

    public CreateSpendResponse create(
        CreateSpendRequest request,
        MultipartFile receipt,
        UserApp user
    ) {

        Category category = categoryRepository
            .findById(request.categoryId())
            .orElseThrow(() -> new RuntimeException(
                "Category not found: " + request.categoryId()
            ));

        String receiptKey = null;
        String contentType = null;
        
        if (receipt != null && !receipt.isEmpty()) {
            StoredFile storedFile = spendReceiptStorageService.store(user, receipt);
            receiptKey = storedFile.key();
            contentType = storedFile.contentType();
        }

        Spend spend = Spend.builder()
            .user(user)
            .category(category)
            .description(request.description())
            .amount(request.amount())
            .currency(request.currency())
            .spendDate(request.spendDate())
            .receiptKey(receiptKey)
            .receiptContentType(contentType)
            .build();

        Spend saved = spendRepository.save(spend);

        return new CreateSpendResponse(
            saved.getId(),
            saved.getCategory().getId(),
            saved.getCategory().getName(),
            saved.getDescription(),
            saved.getAmount(),
            saved.getCurrency(),
            saved.getSpendDate()
        );
    }

    public StoredResource findReceiptResource(
        UUID id,
        UUID userId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new RuntimeException(
                "Spend not found"
            ));

        if (spend.getReceiptKey() == null) {
            throw new RuntimeException(
                "Spend has no receipt"
            );
        }


        Resource resource = spendReceiptStorageService
            .getResource(spend.getReceiptKey());

        return new StoredResource(
            resource,
            spend.getReceiptContentType()
        );
    }

    @Transactional
    public SpendDetailResponse update(
        UUID id,
        UpdateSpendRequest request,
        UUID userId
    ) {

        Spend spend = spendRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new RuntimeException("Spend not found"));

        Category category = categoryRepository
            .findById(request.categoryId())
            .orElseThrow(() -> new RuntimeException("Category not found"));

        spend.setCategory(category);
        spend.setDescription(request.description());
        spend.setAmount(request.amount());
        spend.setCurrency(request.currency());
        spend.setSpendDate(request.spendDate());

        return new SpendDetailResponse(
            spend.getId(),
            spend.getCategory().getId(),
            spend.getCategory().getName(),
            spend.getDescription(),
            spend.getAmount(),
            spend.getCurrency(),
            spend.getSpendDate(),
            spend.getCreatedAt(),
            spend.receiptUrl()
        );
    }

    @Transactional
    public void delete(
        UUID id,
        UUID userId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new RuntimeException("Spend not found"));

        spendRepository.delete(spend);
    }
}