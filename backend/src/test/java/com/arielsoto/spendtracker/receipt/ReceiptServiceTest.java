package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private SpendReceiptStorageService storageService;

    @Mock
    private SpendRepository spendRepository;

    @InjectMocks
    private ReceiptService receiptService;

    private UserApp buildUser() {
        return UserApp.builder()
            .id(UUID.randomUUID())
            .name("Test User")
            .oauthId("123")
            .build();
    }

    private Spend buildSpend(UserApp user) {
        return Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .amount(BigDecimal.TEN)
            .spendDate(LocalDate.now())
            .build();
    }

    @Test
    void loadReceiptHappyPath() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);
        spend.setReceiptKey("spends/user/file.jpg");

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        Resource resource = new ByteArrayResource(new byte[]{1});
        when(storageService.getResource("spends/user/file.jpg"))
            .thenReturn(resource);

        Resource result = receiptService.loadReceipt(
            user, spend.getId()
        );
        assertEquals(resource, result);
    }

    @Test
    void loadReceiptNoReceiptKey() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        assertThrows(RuntimeException.class,
            () -> receiptService.loadReceipt(user, spend.getId())
        );
    }
}
