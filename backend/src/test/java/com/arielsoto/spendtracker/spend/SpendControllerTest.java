package com.arielsoto.spendtracker.spend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.receipt.ReceiptProcessingService;
import com.arielsoto.spendtracker.receipt.ReceiptUploadRateLimitException;
import com.arielsoto.spendtracker.receipt.ReceiptValidationException;
import com.arielsoto.spendtracker.receipt.SpendNotRetryableException;
import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.spend.dto.DashboardSummaryResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.web.ApiExceptionHandler;

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
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        // Lenient: a request rejected by @Valid never reaches the controller, so never asks for the user.
        lenient().when(authenticatedUserService.getCurrentUser(any())).thenReturn(user);
    }

    @Test
    void summaryPassesTheRequestedMonthThrough() throws Exception {
        when(spendService.getDashboardSummary(user.getId(), YearMonth.of(2026, 10), 5))
            .thenReturn(new DashboardSummaryResponse(new BigDecimal("30.00"), 3, new BigDecimal("10.00"), List.of()));

        mockMvc.perform(get("/api/v1/spends/summary").param("month", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSpent").value(30.00))
            .andExpect(jsonPath("$.spendCount").value(3));
    }

    @Test
    void summaryWithoutAMonthLeavesItToTheService() throws Exception {
        when(spendService.getDashboardSummary(eq(user.getId()), isNull(), eq(5)))
            .thenReturn(new DashboardSummaryResponse(BigDecimal.ZERO, 0, BigDecimal.ZERO, List.of()));

        mockMvc.perform(get("/api/v1/spends/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spendCount").value(0));
    }

    @Test
    void summaryReturns400ForAnInvalidMonth() throws Exception {
        mockMvc.perform(get("/api/v1/spends/summary").param("month", "2026-13"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(spendService);
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
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.detail").value("Monthly AI extraction limit reached."))
            .andExpect(jsonPath("$.reason").value("ai_quota"))
            .andExpect(jsonPath("$.resourceType").value("TEXTRACT"));
    }

    @Test
    void fromReceiptReturns429WhenTheUploadLimitIsReached() throws Exception {
        when(receiptProcessingService.uploadReceipt(any(), any()))
            .thenThrow(new ReceiptUploadRateLimitException("minute", "Too many receipt uploads. Try again in a minute."));

        mockMvc.perform(multipart("/api/v1/spends/from-receipt").file(receipt))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Too many receipt uploads. Try again in a minute."))
            .andExpect(jsonPath("$.reason").value("rate_limit"))
            .andExpect(jsonPath("$.window").value("minute"));
    }

    @Test
    void fromReceiptReturns400WhenTheFileIsInvalid() throws Exception {
        when(receiptProcessingService.uploadReceipt(any(), any()))
            .thenThrow(new ReceiptValidationException("File type not allowed"));

        mockMvc.perform(multipart("/api/v1/spends/from-receipt").file(receipt))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.reason").value("invalid_file"))
            .andExpect(jsonPath("$.detail").value("File type not allowed"));
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
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Only FAILED spends can be retried, but the spend is PROCESSED"));
    }

    @Test
    void retryReturns404WhenTheSpendDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(receiptProcessingService.retry(user, id)).thenThrow(new SpendNotFoundException());

        mockMvc.perform(post("/api/v1/spends/{id}/retry", id))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.detail").value("Spend not found"));
    }

    @Test
    void reclassifyReturnsTheSpendNowProcessing() throws Exception {
        UUID spendId = UUID.randomUUID();
        when(spendService.reclassify(spendId, user.getId())).thenReturn(new SpendDetailResponse(
            spendId, UUID.randomUUID(), "Comida", "Lunch", BigDecimal.TEN, LocalDate.now(),
            null, null, "/api/v1/spends/" + spendId + "/receipt", SpendStatus.PROCESSING, null
        ));

        mockMvc.perform(post("/api/v1/spends/{id}/reclassify", spendId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(spendId.toString()))
            .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void reclassifyReturns409WhenTheSpendIsInProgress() throws Exception {
        UUID spendId = UUID.randomUUID();
        when(spendService.reclassify(spendId, user.getId()))
            .thenThrow(new SpendInProgressException());

        mockMvc.perform(post("/api/v1/spends/{id}/reclassify", spendId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("The spend is being processed and cannot be changed yet"));
    }

    @Test
    void updateReturns409WhenTheSpendIsInProgress() throws Exception {
        UUID spendId = UUID.randomUUID();
        when(spendService.update(any(), any(), any()))
            .thenThrow(new SpendInProgressException());

        mockMvc.perform(put("/api/v1/spends/{id}", spendId)
                .contentType("application/json")
                .content("{\"description\":\"x\",\"amount\":10,\"spendDate\":\"2026-10-01\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void reclassifyReturns400WhenTheSpendHasNoReceipt() throws Exception {
        UUID spendId = UUID.randomUUID();
        when(spendService.reclassify(spendId, user.getId())).thenThrow(new SpendWithoutReceiptException());

        mockMvc.perform(post("/api/v1/spends/{id}/reclassify", spendId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Only a spend with a receipt can be reclassified"));
    }

    @Test
    void findByIdReturns404WhenTheSpendDoesNotExist() throws Exception {
        UUID spendId = UUID.randomUUID();
        when(spendService.findByIdAndUserId(spendId, user.getId())).thenThrow(new SpendNotFoundException());

        mockMvc.perform(get("/api/v1/spends/{id}", spendId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Spend not found"));
    }

    @Test
    void createReturns400WithTheInvalidFields() throws Exception {
        mockMvc.perform(post("/api/v1/spends")
                .contentType("application/json")
                .content("{\"description\":\"x\",\"amount\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[?(@.field == 'amount')].message").isNotEmpty())
            .andExpect(jsonPath("$.errors[?(@.field == 'spendDate')].message").isNotEmpty());
    }

    @Test
    void unexpectedErrorsReturn500WithoutTheExceptionMessage() throws Exception {
        UUID spendId = UUID.randomUUID();
        when(spendService.findByIdAndUserId(spendId, user.getId()))
            .thenThrow(new IllegalStateException("bucket arn:aws:s3:::secret not reachable"));

        mockMvc.perform(get("/api/v1/spends/{id}", spendId))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
