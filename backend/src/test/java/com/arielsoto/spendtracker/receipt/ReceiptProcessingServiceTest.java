package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.queue.ReceiptQueuePublisher;
import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.spend.SpendStatus;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.storage.StoredFile;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ReceiptProcessingServiceTest {

    @Mock
    private SpendRepository spendRepository;

    @Mock
    private ReceiptMetadataRepository receiptMetadataRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ReceiptExtractionService extractionService;

    @Mock
    private AiUsageService aiUsageService;

    @Mock
    private SpendReceiptStorageService storageService;

    @Mock
    private ReceiptQueuePublisher queuePublisher;

    @InjectMocks
    private ReceiptProcessingService service;

    private final UserApp user = UserApp.builder().id(UUID.randomUUID()).name("Test").oauthId("1").build();
    private final Category uncategorized = Category.builder().id(UUID.randomUUID()).name("Uncategorized").build();

    private final MockMultipartFile file =
        new MockMultipartFile("receipt", "r.jpg", "image/jpeg", new byte[] {1, 2, 3});

    @BeforeEach
    void setUp() {
        // The database assigns the id on first save.
        lenient().when(spendRepository.save(any(Spend.class))).thenAnswer(call -> {
            Spend spend = call.getArgument(0);
            if (spend.getId() == null) {
                spend.setId(UUID.randomUUID());
            }
            return spend;
        });
    }

    // ---- upload ----

    @Test
    void upload_createsPendingSpendStoresImageAndPublishesToExtractionQueue() {
        when(categoryRepository.findByName("Uncategorized")).thenReturn(Optional.of(uncategorized));
        when(storageService.store(any(), any(), any()))
            .thenReturn(new StoredFile("spends/u/s.jpg", "image/jpeg", 3));

        Spend spend = service.uploadReceipt(user, file);

        assertEquals(SpendStatus.PENDING, spend.getStatus());
        assertEquals(BigDecimal.ZERO, spend.getAmount());
        assertEquals("spends/u/s.jpg", spend.getReceiptKey());
        assertEquals(uncategorized, spend.getCategory());
        verify(queuePublisher).publishForExtraction(spend.getId());
    }

    @Test
    void upload_withNoQuotaCreatesNothing() {
        doThrow(new AiUsageLimitExceededException("TEXTRACT", "limit"))
            .when(aiUsageService).validateAndPickStrategy(user);

        assertThrows(AiUsageLimitExceededException.class, () -> service.uploadReceipt(user, file));

        verify(spendRepository, never()).save(any());
        verifyNoInteractions(storageService, queuePublisher);
    }

    @Test
    void upload_withInvalidFileCreatesNothing() {
        MockMultipartFile textFile = new MockMultipartFile("receipt", "r.txt", "text/plain", new byte[] {1});

        assertThrows(ReceiptValidationException.class, () -> service.uploadReceipt(user, textFile));

        verify(spendRepository, never()).save(any());
        verifyNoInteractions(storageService, queuePublisher);
    }

    @Test
    void upload_whenPublishFailsLeavesFailedSpendWithItsImage() {
        when(categoryRepository.findByName("Uncategorized")).thenReturn(Optional.of(uncategorized));
        when(storageService.store(any(), any(), any()))
            .thenReturn(new StoredFile("spends/u/s.jpg", "image/jpeg", 3));
        doThrow(new RuntimeException("sqs down")).when(queuePublisher).publishForExtraction(any());

        Spend spend = service.uploadReceipt(user, file);

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        verify(storageService, never()).deleteFile(any());
        verify(spendRepository, never()).delete(any(Spend.class));
    }

    // ---- extraction worker ----

    private static final byte[] IMAGE = new byte[] {1, 2, 3};

    private Spend spendIn(SpendStatus status) {
        Spend spend = Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(uncategorized)
            .description("Processing receipt...")
            .amount(BigDecimal.ZERO)
            .receiptKey("spends/u/s.jpg")
            .receiptContentType("image/jpeg")
            .build();
        spend.setStatus(status);
        lenient().when(spendRepository.findWithUserById(spend.getId())).thenReturn(Optional.of(spend));
        lenient().when(storageService.getResource("spends/u/s.jpg")).thenReturn(new ByteArrayResource(IMAGE));
        return spend;
    }

    private OcrResult ocr(String text) {
        return new OcrResult(text, 98f, List.of());
    }

    @Test
    void extraction_storesRawTextMovesToProcessingAndPublishesToEnrichmentQueue() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(extractionService.extract(user, spend.getId(), IMAGE, "image/jpeg")).thenReturn(ocr("TOTAL 10"));

        service.processExtraction(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        ArgumentCaptor<ReceiptMetadata> saved = ArgumentCaptor.forClass(ReceiptMetadata.class);
        verify(receiptMetadataRepository).save(saved.capture());
        assertEquals("TOTAL 10", saved.getValue().getRawOcrText());
        assertEquals(spend, saved.getValue().getSpend());
        verify(queuePublisher).publishForEnrichment(spend.getId());
    }

    @Test
    void extraction_dropsMessageForMissingSpend() {
        UUID missing = UUID.randomUUID();
        when(spendRepository.findWithUserById(missing)).thenReturn(Optional.empty());

        service.processExtraction(missing, false);

        verifyNoInteractions(extractionService, queuePublisher, receiptMetadataRepository);
    }

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PROCESSING", "PROCESSED", "FAILED"})
    void extraction_dropsDuplicateOrStaleMessageWithoutSideEffects(SpendStatus status) {
        Spend spend = spendIn(status);

        service.processExtraction(spend.getId(), false);

        assertEquals(status, spend.getStatus());
        verifyNoInteractions(extractionService, queuePublisher, receiptMetadataRepository);
        verify(spendRepository, never()).save(any());
    }

    @Test
    void extraction_failureBeforeLastAttemptReturnsSpendToPendingSoRedeliveryRetries() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(extractionService.extract(any(), any(), any(), any()))
            .thenThrow(new ReceiptProcessingException("OCR returned empty text"));

        assertThrows(ReceiptProcessingException.class, () -> service.processExtraction(spend.getId(), false));

        assertEquals(SpendStatus.PENDING, spend.getStatus());
        verifyNoInteractions(queuePublisher);
    }

    @Test
    void extraction_failureOnLastAttemptMarksFailedAndDeletesNothing() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(extractionService.extract(any(), any(), any(), any()))
            .thenThrow(new ReceiptProcessingException("OCR returned empty text"));

        assertThrows(ReceiptProcessingException.class, () -> service.processExtraction(spend.getId(), true));

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        verify(storageService, never()).deleteFile(any());
        verify(spendRepository, never()).delete(any(Spend.class));
        verify(spendRepository, never()).deleteById(any());
        verifyNoInteractions(queuePublisher);
    }

    @Test
    void extraction_withNoQuotaLeftMarksFailedWithoutRetry() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(extractionService.extract(any(), any(), any(), any()))
            .thenThrow(new AiUsageLimitExceededException("TEXTRACT", "limit"));

        service.processExtraction(spend.getId(), false);

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        verifyNoInteractions(queuePublisher);
    }
}
