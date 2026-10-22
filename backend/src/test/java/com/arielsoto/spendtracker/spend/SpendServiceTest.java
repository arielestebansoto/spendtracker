package com.arielsoto.spendtracker.spend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.queue.ReceiptQueuePublisher;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.DashboardSummaryResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.UpdateSpendRequest;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class SpendServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Mock
    private SpendRepository spendRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SpendReceiptStorageService storageService;

    @Mock
    private ReceiptQueuePublisher queuePublisher;

    @InjectMocks
    private SpendService spendService;

    private Category category() {
        return Category.builder()
            .id(UUID.randomUUID())
            .name("Comida")
            .build();
    }

    private Spend buildSpend(String receiptKey) {
        UserApp user = UserApp.builder()
            .id(UUID.randomUUID())
            .name("Test User")
            .oauthId("123")
            .build();

        return Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .amount(BigDecimal.TEN)
            .spendDate(LocalDate.now())
            .receiptKey(receiptKey)
            .build();
    }

    @Test
    void manuallyCreatedSpendIsSavedAsProcessed() {
        Category category = category();
        UserApp user = UserApp.builder().id(UUID.randomUUID()).build();
        when(categoryRepository.findById(category.getId()))
            .thenReturn(Optional.of(category));
        when(spendRepository.save(any(Spend.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateSpendResponse response = spendService.create(
            new CreateSpendRequest(
                category.getId(), "Lunch", new BigDecimal("12.50"), START
            ),
            user
        );

        ArgumentCaptor<Spend> saved = ArgumentCaptor.forClass(Spend.class);
        verify(spendRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SpendStatus.PROCESSED);
        assertThat(response.status()).isEqualTo(SpendStatus.PROCESSED);
    }

    @Test
    void spendDetailExposesItsStatus() {
        UUID userId = UUID.randomUUID();
        Spend spend = Spend.builder()
            .id(UUID.randomUUID())
            .category(category())
            .amount(BigDecimal.ZERO)
            .spendDate(START)
            .status(SpendStatus.PENDING)
            .build();
        when(spendRepository.findByIdAndUserId(spend.getId(), userId))
            .thenReturn(Optional.of(spend));

        SpendDetailResponse response =
            spendService.findByIdAndUserId(spend.getId(), userId);

        assertThat(response.status()).isEqualTo(SpendStatus.PENDING);
    }

    @Test
    void dashboardMetricsCountOnlyProcessedSpends() {
        UUID userId = UUID.randomUUID();
        when(spendRepository.sumAmountByUserIdAndStatusAndSpendDateBetween(
            userId, SpendStatus.PROCESSED, START, END
        )).thenReturn(new BigDecimal("30.00"));
        when(spendRepository.countByUserIdAndStatusAndSpendDateBetween(
            userId, SpendStatus.PROCESSED, START, END
        )).thenReturn(3L);
        when(spendRepository.findTopNByUserIdAndStatusAndSpendDateBetween(
            userId, SpendStatus.PROCESSED, START, END, PageRequest.of(0, 5)
        )).thenReturn(new PageImpl<>(List.of()));

        DashboardSummaryResponse summary =
            spendService.getDashboardSummary(userId, START, END, 5);

        assertThat(summary.totalSpent()).isEqualByComparingTo("30.00");
        assertThat(summary.spendCount()).isEqualTo(3);
        assertThat(summary.averageSpend()).isEqualByComparingTo("10.00");
    }

    @Test
    void deleteRemovesStoredReceiptImage() {
        Spend spend = buildSpend("spends/user/file.jpg");
        when(spendRepository.findByIdAndUserId(
            spend.getId(), spend.getUser().getId()
        )).thenReturn(Optional.of(spend));

        spendService.delete(spend.getId(), spend.getUser().getId());

        InOrder order = inOrder(spendRepository, storageService);
        order.verify(spendRepository).delete(spend);
        order.verify(storageService).deleteFile("spends/user/file.jpg");
    }

    @Test
    void deleteWithoutReceiptDoesNotTouchStorage() {
        Spend spend = buildSpend(null);
        when(spendRepository.findByIdAndUserId(
            spend.getId(), spend.getUser().getId()
        )).thenReturn(Optional.of(spend));

        spendService.delete(spend.getId(), spend.getUser().getId());

        verify(spendRepository).delete(spend);
        verify(storageService, never()).deleteFile(any());
    }

    private Spend spendWithStatus(String receiptKey, SpendStatus status) {
        Spend spend = buildSpend(receiptKey);
        spend.setCategory(category());
        spend.setStatus(status);
        return spend;
    }

    private void owns(Spend spend) {
        when(spendRepository.findByIdAndUserId(spend.getId(), spend.getUser().getId()))
            .thenReturn(Optional.of(spend));
    }

    private UpdateSpendRequest updateRequest() {
        return new UpdateSpendRequest(null, "Edited", new BigDecimal("99.00"), START);
    }

    // ---- edit locking ----

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PENDING", "PROCESSING"})
    void editIsRejectedWhileTheSpendIsInProgress(SpendStatus status) {
        Spend spend = spendWithStatus("spends/u/s.jpg", status);
        owns(spend);

        assertThatThrownBy(() -> spendService.update(spend.getId(), updateRequest(), spend.getUser().getId()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409);
        assertThat(spend.getDescription()).isNotEqualTo("Edited");
    }

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PROCESSED", "FAILED"})
    void editIsAllowedOnceTheSpendIsNotInProgress(SpendStatus status) {
        Spend spend = spendWithStatus("spends/u/s.jpg", status);
        owns(spend);
        when(categoryRepository.findByName("Uncategorized")).thenReturn(Optional.of(category()));

        spendService.update(spend.getId(), updateRequest(), spend.getUser().getId());

        assertThat(spend.getDescription()).isEqualTo("Edited");
        assertThat(spend.getAmount()).isEqualByComparingTo("99.00");
    }

    @ParameterizedTest
    @EnumSource(SpendStatus.class)
    void deleteIsAllowedInAnyStatus(SpendStatus status) {
        Spend spend = spendWithStatus(null, status);
        owns(spend);

        spendService.delete(spend.getId(), spend.getUser().getId());

        verify(spendRepository).delete(spend);
    }

    // ---- reclassify ----

    @Test
    void reclassifyMovesTheSpendBackToProcessingAndQueuesACategoryOnlyEnrichment() {
        Spend spend = spendWithStatus("spends/u/s.jpg", SpendStatus.PROCESSED);
        owns(spend);

        SpendDetailResponse response = spendService.reclassify(spend.getId(), spend.getUser().getId());

        assertThat(spend.getStatus()).isEqualTo(SpendStatus.PROCESSING);
        assertThat(response.status()).isEqualTo(SpendStatus.PROCESSING);
        InOrder order = inOrder(spendRepository, queuePublisher);
        order.verify(spendRepository).save(spend);
        order.verify(queuePublisher).publishForCategoryOnlyEnrichment(spend.getId());
    }

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PENDING", "PROCESSING", "FAILED"})
    void reclassifyIsRejectedUnlessTheSpendIsProcessed(SpendStatus status) {
        Spend spend = spendWithStatus("spends/u/s.jpg", status);
        owns(spend);

        assertThatThrownBy(() -> spendService.reclassify(spend.getId(), spend.getUser().getId()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409);
        assertThat(spend.getStatus()).isEqualTo(status);
        verifyNoInteractions(queuePublisher);
    }

    @Test
    void reclassifyIsRejectedForASpendWithoutAReceipt() {
        Spend spend = spendWithStatus(null, SpendStatus.PROCESSED);
        owns(spend);

        assertThatThrownBy(() -> spendService.reclassify(spend.getId(), spend.getUser().getId()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(400);
        verifyNoInteractions(queuePublisher);
    }

    @Test
    void reclassifyOfAnotherUsersSpendIsNotFound() {
        UUID spendId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(spendRepository.findByIdAndUserId(spendId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendService.reclassify(spendId, userId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(404);
    }

    @Test
    void reclassifyWhoseMessageCannotBeQueuedLeavesTheSpendProcessedAndUntouched() {
        Spend spend = spendWithStatus("spends/u/s.jpg", SpendStatus.PROCESSED);
        owns(spend);
        doThrow(new RuntimeException("sqs down")).when(queuePublisher).publishForCategoryOnlyEnrichment(any());

        assertThatThrownBy(() -> spendService.reclassify(spend.getId(), spend.getUser().getId()))
            .hasMessage("sqs down");

        assertThat(spend.getStatus()).isEqualTo(SpendStatus.PROCESSED);
    }
}
