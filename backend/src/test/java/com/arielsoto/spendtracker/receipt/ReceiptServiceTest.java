package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.mock.web.MockMultipartFile;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.storage.StoredFile;
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

    private MockMultipartFile buildFile() {
        return new MockMultipartFile(
            "receipt", "receipt.jpg", "image/jpeg",
            new byte[]{1, 2, 3}
        );
    }

    @Test
    void storeReceiptHappyPath() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);
        MockMultipartFile file = buildFile();

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        when(storageService.store(
            eq(user), eq(spend.getId()), any()
        )).thenReturn(new StoredFile(
            "spends/" + user.getId() + "/" + spend.getId() + ".jpg",
            "image/jpeg", 3L
        ));

        when(spendRepository.save(any(Spend.class)))
            .thenReturn(spend);

        ReceiptUploadResponse response = receiptService
            .storeReceipt(user, spend.getId(), file);

        assertNotNull(response);
        assertEquals("image/jpeg", response.contentType());
        assertEquals(3L, response.size());
        assertEquals(
            "/api/v1/spends/" + spend.getId() + "/receipt",
            response.receiptUrl()
        );
        verify(spendRepository).save(spend);
    }

    @Test
    void storeReceiptSpendNotFound() {
        UserApp user = buildUser();
        MockMultipartFile file = buildFile();

        when(spendRepository.findByIdAndUserId(
            any(), eq(user.getId())
        )).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> receiptService.storeReceipt(
                user, UUID.randomUUID(), file
            )
        );
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

    @Test
    void deleteReceiptWithReceipt() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);
        spend.setReceiptKey("spends/user/file.jpg");
        spend.setReceiptContentType("image/jpeg");

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        receiptService.deleteReceipt(user, spend.getId());

        verify(storageService).deleteFile("spends/user/file.jpg");
        assertNull(spend.getReceiptKey());
        assertNull(spend.getReceiptContentType());
        verify(spendRepository).save(spend);
    }

    @Test
    void deleteReceiptWithoutReceiptKey() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        receiptService.deleteReceipt(user, spend.getId());

        verify(storageService, never()).deleteFile(any());
        verify(spendRepository, never()).save(any());
    }
}
