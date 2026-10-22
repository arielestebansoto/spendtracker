package com.arielsoto.spendtracker.spend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.receipt.ReceiptProcessingService;
import com.arielsoto.spendtracker.receipt.SpendNotRetryableException;
import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class SpendControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SpendService spendService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private ReceiptProcessingService receiptProcessingService;

    private final UserApp user = UserApp.builder().id(UUID.randomUUID()).name("Test").oauthId("1").build();

    private final MockMultipartFile receipt =
        new MockMultipartFile("receipt", "r.jpg", "image/jpeg", new byte[] {1, 2, 3});

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SpendController(spendService, authenticatedUserService, receiptProcessingService))
            .build();
        when(authenticatedUserService.getCurrentUser(any())).thenReturn(user);
    }

    @Test
    void fromReceiptReturnsThePendingSpendImmediately() throws Exception {
        Spend pending = Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(Category.builder().id(UUID.randomUUID()).name("Uncategorized").build())
            .description("Processing receipt...")
            .amount(BigDecimal.ZERO)
            .spendDate(LocalDate.now())
            .build();
        pending.setStatus(SpendStatus.PENDING);
        when(receiptProcessingService.uploadReceipt(any(), any())).thenReturn(pending);

        mockMvc.perform(multipart("/api/v1/spends/from-receipt").file(receipt))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pending.getId().toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.category").value("Uncategorized"));
    }

    @Test
    void fromReceiptReturns429WhenThereIsNoAiQuota() throws Exception {
        when(receiptProcessingService.uploadReceipt(any(), any()))
            .thenThrow(new AiUsageLimitExceededException("TEXTRACT", "Monthly AI extraction limit reached."));

        mockMvc.perform(multipart("/api/v1/spends/from-receipt").file(receipt))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.resourceType").value("TEXTRACT"));
    }

    @Test
    void retryReturnsTheSpendBackInPending() throws Exception {
        Spend pending = Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(Category.builder().id(UUID.randomUUID()).name("Uncategorized").build())
            .description("Processing receipt...")
            .amount(BigDecimal.ZERO)
            .spendDate(LocalDate.now())
            .build();
        pending.setStatus(SpendStatus.PENDING);
        when(receiptProcessingService.retry(user, pending.getId())).thenReturn(pending);

        mockMvc.perform(post("/api/v1/spends/{id}/retry", pending.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pending.getId().toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void retryReturns409WhenTheSpendIsNotFailed() throws Exception {
        UUID id = UUID.randomUUID();
        when(receiptProcessingService.retry(user, id))
            .thenThrow(new SpendNotRetryableException(SpendStatus.PROCESSED));

        mockMvc.perform(post("/api/v1/spends/{id}/retry", id))
            .andExpect(status().isConflict());
    }

    @Test
    void retryReturns404WhenTheSpendDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(receiptProcessingService.retry(user, id)).thenThrow(new NoSuchElementException());

        mockMvc.perform(post("/api/v1/spends/{id}/retry", id))
            .andExpect(status().isNotFound());
    }
}
