package com.arielsoto.spendtracker.spend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.queue.ReceiptQueuePublisher;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.DashboardSummaryResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.SpendListItemResponse;
import com.arielsoto.spendtracker.spend.dto.UpdateSpendRequest;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpendService {

    private final SpendRepository spendRepository;
    private final CategoryRepository categoryRepository;
    private final SpendReceiptStorageService storageService;
    private final ReceiptQueuePublisher queuePublisher;
    private final Clock clock;

    public SpendDetailResponse findByIdAndUserId(
        UUID id,
        UUID userId
    ) {
        Spend spend = spendRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow();

        return toDetailResponse(spend);
    }

    public Page<SpendListItemResponse> findAllByUserId(
        UUID userId,
        Pageable pageable
    ) {
        return spendRepository
            .findAllByUserId(userId, pageable)
            .map(this::toListItemResponse);
    }

    public Page<SpendListItemResponse> findByFilters(
        UUID userId,
        UUID categoryId,
        String description,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDate startDate,
        LocalDate endDate,
        Pageable pageable
    ) {
        var spec = SpendSpecification.withFilters(
            userId, categoryId, description,
            minAmount, maxAmount, startDate, endDate
        );

        return spendRepository
            .findAll(spec, pageable)
            .map(this::toListItemResponse);
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
            .status(SpendStatus.PROCESSED)
            .build();

        Spend saved = spendRepository.save(spend);

        return new CreateSpendResponse(
            saved.getId(),
            saved.getCategory().getId(),
            saved.getCategory().getName(),
            saved.getDescription(),
            saved.getAmount(),
            saved.getSpendDate(),
            saved.getStatus()
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

        rejectIfInProgress(spend);

        Category category = resolveCategory(request.categoryId());

        spend.setCategory(category);
        spend.setDescription(request.description());
        spend.setAmount(request.amount());
        spend.setSpendDate(request.spendDate());
        spend.setUpdatedAt(LocalDateTime.now(clock));

        return toDetailResponse(spend);
    }

    /**
     * Reclassify: recalculates only the Category of a Spend with a Receipt. The Spend goes back to
     * PROCESSING until the category-only Enrichment finishes, which locks it against edits so the
     * background result cannot overwrite them.
     */
    public SpendDetailResponse reclassify(UUID id, UUID userId) {
        Spend spend = spendRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spend not found"));

        if (spend.getReceiptKey() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Only a spend with a receipt can be reclassified"
            );
        }
        rejectIfInProgress(spend);
        if (spend.getStatus() == SpendStatus.FAILED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "A failed spend must be retried, not reclassified"
            );
        }

        spend.setStatus(SpendStatus.PROCESSING);
        spend.setProcessingStage(ProcessingStage.ENRICHMENT);
        spend.setClaimToken(null);
        spendRepository.save(spend);
        try {
            queuePublisher.publishForCategoryOnlyEnrichment(spend.getId());
        } catch (RuntimeException e) {
            spend.setStatus(SpendStatus.PROCESSED);
            spendRepository.save(spend);
            throw e;
        }

        return toDetailResponse(spend);
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

        if (spend.getReceiptKey() != null) {
            storageService.deleteFile(spend.getReceiptKey());
        }
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
            .sumAmountByUserIdAndStatusAndSpendDateBetween(
                userId, SpendStatus.PROCESSED, startDate, endDate
            );

        long count = spendRepository
            .countByUserIdAndStatusAndSpendDateBetween(
                userId, SpendStatus.PROCESSED, startDate, endDate
            );

        BigDecimal average = count > 0
            ? total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<SpendListItemResponse> recentSpends = spendRepository
            .findTopNByUserIdAndStatusAndSpendDateBetween(
                userId, SpendStatus.PROCESSED, startDate, endDate,
                PageRequest.of(0, recentLimit)
            )
            .map(this::toListItemResponse)
            .getContent();

        return new DashboardSummaryResponse(
            total, count, average, recentSpends
        );
    }

    private SpendDetailResponse toDetailResponse(Spend spend) {
        return new SpendDetailResponse(
            spend.getId(),
            spend.getCategory().getId(),
            spend.getCategory().getName(),
            spend.getDescription(),
            spend.getAmount(),
            spend.getSpendDate(),
            spend.getCreatedAt(),
            spend.getUpdatedAt(),
            spend.receiptUrl(),
            spend.getStatus()
        );
    }

    private SpendListItemResponse toListItemResponse(Spend spend) {
        return new SpendListItemResponse(
            spend.getId(),
            spend.getCategory().getName(),
            spend.getAmount(),
            spend.getSpendDate(),
            spend.getDescription(),
            spend.getStatus()
        );
    }

    private void rejectIfInProgress(Spend spend) {
        if (spend.getStatus().isInProgress()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "The spend is being processed and cannot be changed yet"
            );
        }
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