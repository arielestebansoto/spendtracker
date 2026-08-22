package com.arielsoto.spendtracker.spend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.DashboardSummaryResponse;
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
                    s.getSpendDate(),
                    s.getDescription()
                );
            });
    }

    public CreateSpendResponse create(
        CreateSpendRequest request,
        UserApp user
    ) {

        Category category = resolveCategory(request.categoryId());

        Spend spend = Spend.builder()
            .user(user)
            .category(category)
            .description(request.description())
            .amount(request.amount())
            .spendDate(request.spendDate())
            .build();

        Spend saved = spendRepository.save(spend);

        return new CreateSpendResponse(
            saved.getId(),
            saved.getCategory().getId(),
            saved.getCategory().getName(),
            saved.getDescription(),
            saved.getAmount(),
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

        Category category = resolveCategory(request.categoryId());

        spend.setCategory(category);
        spend.setDescription(request.description());
        spend.setAmount(request.amount());
        spend.setSpendDate(request.spendDate());

        return new SpendDetailResponse(
            spend.getId(),
            spend.getCategory().getId(),
            spend.getCategory().getName(),
            spend.getDescription(),
            spend.getAmount(),
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

    @Transactional
    public void deleteAllByUser(UserApp user) {
        List<Spend> spends = spendRepository.findAllByUserId(
            user.getId()
        );

        spendRepository.deleteAll(spends);
    }

    public DashboardSummaryResponse getDashboardSummary(
        UUID userId,
        LocalDate startDate,
        LocalDate endDate,
        int recentLimit
    ) {
        BigDecimal total = spendRepository
            .sumAmountByUserIdAndSpendDateBetween(userId, startDate, endDate);

        long count = spendRepository
            .countByUserIdAndSpendDateBetween(userId, startDate, endDate);

        BigDecimal average = count > 0
            ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<SpendListItemResponse> recentSpends = spendRepository
            .findTopNByUserIdAndSpendDateBetween(
                userId, startDate, endDate,
                PageRequest.of(0, recentLimit)
            )
            .map(s -> new SpendListItemResponse(
                s.getId(),
                s.getCategory().getName(),
                s.getAmount(),
                s.getSpendDate(),
                s.getDescription()
            ))
            .getContent();

        return new DashboardSummaryResponse(
            total, count, average, recentSpends
        );
    }

    private Category resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            return categoryRepository.findByName("Uncategorized")
                .orElseThrow(() -> new RuntimeException(
                    "Default 'Uncategorized' category not found"
                ));
        }
        return categoryRepository.findById(categoryId)
            .orElseThrow(() -> new RuntimeException(
                "Category not found: " + categoryId
            ));
    }
}