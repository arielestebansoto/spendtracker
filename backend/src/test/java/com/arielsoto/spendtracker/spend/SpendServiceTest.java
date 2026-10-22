package com.arielsoto.spendtracker.spend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class SpendServiceTest {

    @Mock
    private SpendRepository spendRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SpendReceiptStorageService storageService;

    @InjectMocks
    private SpendService spendService;

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
}
