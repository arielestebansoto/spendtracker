package com.arielsoto.spendtracker.spend;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.SpendListItemResponse;
import com.arielsoto.spendtracker.spend.dto.UpdateSpendRequest;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpendService {

    private final SpendRepository spendRepository;
    private final CategoryRepository categoryRepository;

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
            spend.getCreatedAt()
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
        UserApp user
    ) {

        Category category = categoryRepository
            .findById(request.categoryId())
            .orElseThrow();

        Spend spend = Spend.builder()
            .user(user)
            .category(category)
            .description(request.description())
            .amount(request.amount())
            .currency(request.currency())
            .spendDate(request.spendDate())
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
            spend.getCreatedAt()
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