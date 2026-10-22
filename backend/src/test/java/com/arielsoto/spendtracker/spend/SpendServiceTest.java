package com.arielsoto.spendtracker.spend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.DashboardSummaryResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
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

    @ParameterizedTest
    @EnumSource(SpendStatus.class)
    void deleteWorksInAnyStatusIncludingWhileBeingProcessed(SpendStatus status) {
        Spend spend = buildSpend("spends/user/file.jpg");
        spend.setStatus(status);
        when(spendRepository.findByIdAndUserId(
            spend.getId(), spend.getUser().getId()
        )).thenReturn(Optional.of(spend));

        spendService.delete(spend.getId(), spend.getUser().getId());

        verify(spendRepository).delete(spend);
        verify(storageService).deleteFile("spends/user/file.jpg");
    }
}
