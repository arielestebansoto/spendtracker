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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.ClassifiedSpendResult;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.queue.ReceiptQueuePublisher;
import com.arielsoto.spendtracker.queue.SpendMessage;
import com.arielsoto.spendtracker.spend.ProcessingStage;
import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendItem;
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
    private ReceiptEnrichmentService enrichmentService;

    @Mock
    private AiUsageService aiUsageService;

    @Mock
    private SpendReceiptStorageService storageService;

    @Mock
    private ReceiptQueuePublisher queuePublisher;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private ReceiptProcessingService service;

    private final UserApp user = UserApp.builder().id(UUID.randomUUID()).name("Test").oauthId("1").build();
    private final Category uncategorized = Category.builder().id(UUID.randomUUID()).name("Uncategorized").build();

    private final MockMultipartFile file =
        new MockMultipartFile("receipt", "r.jpg", "image/jpeg", new byte[] {1, 2, 3});

    @BeforeEach
    void setUp() {
        service = new ReceiptProcessingService(
            spendRepository, receiptMetadataRepository, categoryRepository, extractionService,
            enrichmentService, aiUsageService, storageService, queuePublisher, clock
        );
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

    private static final LocalDate PROVISIONAL_DATE = LocalDate.of(2026, 10, 22);
    private static final byte[] IMAGE = new byte[] {1, 2, 3};

    private Spend spendIn(SpendStatus status) {
        Spend spend = Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(uncategorized)
            .description("Processing receipt...")
            .amount(BigDecimal.ZERO)
            .spendDate(PROVISIONAL_DATE)
            .receiptKey("spends/u/s.jpg")
            .receiptContentType("image/jpeg")
            .build();
        spend.setStatus(status);
        lenient().when(spendRepository.findWithUserById(spend.getId())).thenReturn(Optional.of(spend));
        lenient().when(storageService.getResource("spends/u/s.jpg")).thenReturn(new ByteArrayResource(IMAGE));
        return spend;
    }

    // The conditional UPDATE that claims a Spend for Extraction succeeds unless a test says another worker won.
    @BeforeEach
    void claimsSucceedByDefault() {
        lenient().when(spendRepository.claimForExtraction(any())).thenReturn(1);
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
        assertEquals(NOW, saved.getValue().getCreatedAt());
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

    @Test
    void extraction_resumesASpendLeftProcessingInExtractionByAWorkerThatDied() {
        Spend spend = spendIn(SpendStatus.PROCESSING);
        spend.setProcessingStage(ProcessingStage.EXTRACTION);
        when(extractionService.extract(user, spend.getId(), IMAGE, "image/jpeg")).thenReturn(ocr("TOTAL 10"));

        service.processExtraction(spend.getId(), false);

        verify(queuePublisher).publishForEnrichment(spend.getId());
    }

    @Test
    void extraction_dropsTheMessageWhenAnotherWorkerClaimedTheSpendFirst() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(spendRepository.claimForExtraction(spend.getId())).thenReturn(0);

        service.processExtraction(spend.getId(), false);

        verifyNoInteractions(extractionService, queuePublisher, receiptMetadataRepository);
        verify(spendRepository, never()).save(any());
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

    // ---- enrichment worker ----

    private final Category comida = Category.builder().id(UUID.randomUUID()).name("Comida").build();

    private Spend spendAwaitingEnrichment(SpendStatus status) {
        Spend spend = spendIn(status);
        spend.setReceiptMetadata(ReceiptMetadata.builder().spend(spend).rawOcrText("TOTAL 10").build());
        inEnrichmentStageIfProcessing(spend);
        return spend;
    }

    private ClassifiedSpendResult classification(String category, LocalDate date) {
        ClassifiedSpend classified = new ClassifiedSpend(
            new BigDecimal("10.50"),
            category,
            "Supermarket",
            date,
            List.of(
                new ClassifiedSpend.ClassifiedItem("Milk", new BigDecimal("4.50")),
                new ClassifiedSpend.ClassifiedItem("Bread", new BigDecimal("6.00"))
            )
        );
        return new ClassifiedSpendResult(classified, 100, 20, Map.of("output", "raw"));
    }

    @Test
    void enrichment_storesExtractedDataAndRawResponseAndMarksProcessed() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Comida")).thenReturn(Optional.of(comida));
        when(enrichmentService.enrich(user, "TOTAL 10"))
            .thenReturn(classification("Comida", LocalDate.of(2026, 1, 5)));

        service.processEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        assertEquals(new BigDecimal("10.50"), spend.getAmount());
        assertEquals("Supermarket", spend.getDescription());
        assertEquals(LocalDate.of(2026, 1, 5), spend.getSpendDate());
        assertEquals(comida, spend.getCategory());
        assertEquals(List.of("Milk", "Bread"), spend.getItems().stream().map(SpendItem::getDescription).toList());
        assertEquals(List.of(0, 1), spend.getItems().stream().map(SpendItem::getPosition).toList());
        assertEquals(Map.of("output", "raw"), spend.getReceiptMetadata().getRawResponse());
        assertEquals(NOW, spend.getReceiptMetadata().getClassifiedAt());
        assertEquals(NOW, spend.getItems().get(0).getCreatedAt());
        verify(spendRepository).save(spend);
    }

    @Test
    void enrichment_keepsTheProvisionalDateWhenTheReceiptHasNone() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Comida")).thenReturn(Optional.of(comida));
        when(enrichmentService.enrich(any(), any())).thenReturn(classification("Comida", null));

        service.processEnrichment(spend.getId(), false);

        assertEquals(PROVISIONAL_DATE, spend.getSpendDate());
        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
    }

    @Test
    void enrichment_fallsBackToUncategorizedWhenTheModelReturnsAnUnknownCategory() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Invented")).thenReturn(Optional.empty());
        when(categoryRepository.findByName("Uncategorized")).thenReturn(Optional.of(uncategorized));
        when(enrichmentService.enrich(any(), any())).thenReturn(classification("Invented", null));

        service.processEnrichment(spend.getId(), false);

        assertEquals(uncategorized, spend.getCategory());
        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
    }

    @Test
    void enrichment_dropsMessageForMissingSpend() {
        UUID missing = UUID.randomUUID();
        when(spendRepository.findWithUserById(missing)).thenReturn(Optional.empty());

        service.processEnrichment(missing, false);

        verifyNoInteractions(enrichmentService, queuePublisher);
        verify(spendRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PENDING", "PROCESSED", "FAILED"})
    void enrichment_dropsDuplicateOrStaleMessageWithoutSideEffects(SpendStatus status) {
        Spend spend = spendAwaitingEnrichment(status);

        service.processEnrichment(spend.getId(), false);

        assertEquals(status, spend.getStatus());
        assertEquals(BigDecimal.ZERO, spend.getAmount());
        verifyNoInteractions(enrichmentService);
        verify(spendRepository, never()).save(any());
    }

    @Test
    void enrichment_dropsMessageWhileTheSpendIsStillInExtraction() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        spend.setProcessingStage(ProcessingStage.EXTRACTION);

        service.processEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        verifyNoInteractions(enrichmentService);
        verify(spendRepository, never()).save(any());
    }

    @Test
    void enrichment_clearsTheStageWhenTheSpendIsProcessed() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Comida")).thenReturn(Optional.of(comida));
        when(enrichmentService.enrich(user, "TOTAL 10"))
            .thenReturn(classification("Comida", LocalDate.of(2026, 1, 5)));

        service.processEnrichment(spend.getId(), false);

        assertEquals(null, spend.getProcessingStage());
    }

    // Saving the Spend after the raw text is stored must not orphan the new metadata: the relation
    // removes orphans, so a Spend that does not point at its metadata would delete it on save.
    @Test
    void extraction_linksTheNewMetadataToTheSpendSoSavingTheSpendDoesNotDeleteIt() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(extractionService.extract(user, spend.getId(), IMAGE, "image/jpeg")).thenReturn(ocr("TOTAL 10"));

        service.processExtraction(spend.getId(), false);

        ArgumentCaptor<ReceiptMetadata> saved = ArgumentCaptor.forClass(ReceiptMetadata.class);
        verify(receiptMetadataRepository).save(saved.capture());
        assertEquals(saved.getValue(), spend.getReceiptMetadata());
    }

    @Test
    void extraction_leavesTheSpendInTheEnrichmentStageWhenItQueuesEnrichment() {
        Spend spend = spendIn(SpendStatus.PENDING);
        when(extractionService.extract(user, spend.getId(), IMAGE, "image/jpeg")).thenReturn(ocr("TOTAL 10"));

        service.processExtraction(spend.getId(), false);

        assertEquals(ProcessingStage.ENRICHMENT, spend.getProcessingStage());
    }

    @Test
    void enrichment_failureBeforeLastAttemptLeavesSpendProcessingSoRedeliveryRetries() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(enrichmentService.enrich(any(), any()))
            .thenThrow(new ReceiptProcessingException("no amount"));

        assertThrows(ReceiptProcessingException.class, () -> service.processEnrichment(spend.getId(), false));

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        assertEquals(BigDecimal.ZERO, spend.getAmount());
    }

    @Test
    void enrichment_failureOnLastAttemptMarksFailedAndDeletesNothing() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(enrichmentService.enrich(any(), any()))
            .thenThrow(new ReceiptProcessingException("no amount"));

        assertThrows(ReceiptProcessingException.class, () -> service.processEnrichment(spend.getId(), true));

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        verify(spendRepository).save(spend);
        verify(storageService, never()).deleteFile(any());
        verify(spendRepository, never()).delete(any(Spend.class));
        verify(spendRepository, never()).deleteById(any());
    }

    @Test
    void enrichment_withNoQuotaLeftMarksFailedWithoutRetry() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(enrichmentService.enrich(any(), any()))
            .thenThrow(new AiUsageLimitExceededException("BEDROCK", "limit"));

        service.processEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        assertEquals(BigDecimal.ZERO, spend.getAmount());
    }

    @Test
    void enrichment_failsWhenTheRawTextWasNeverStored() {
        Spend spend = spendIn(SpendStatus.PROCESSING);
        spend.setProcessingStage(ProcessingStage.ENRICHMENT);

        assertThrows(ReceiptProcessingException.class, () -> service.processEnrichment(spend.getId(), true));

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        verifyNoInteractions(enrichmentService);
    }

    // ---- retry ----

    private Spend ownedSpendIn(SpendStatus status) {
        Spend spend = spendIn(status);
        lenient().when(spendRepository.findByIdAndUserId(spend.getId(), user.getId()))
            .thenReturn(Optional.of(spend));
        return spend;
    }

    @Test
    void retry_movesFailedSpendBackToPendingAndPublishesToExtractionQueue() {
        Spend spend = ownedSpendIn(SpendStatus.FAILED);

        Spend retried = service.retry(user, spend.getId());

        assertEquals(SpendStatus.PENDING, retried.getStatus());
        verify(spendRepository).save(spend);
        verify(queuePublisher).publishForExtraction(spend.getId());
    }

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PENDING", "PROCESSING", "PROCESSED"})
    void retry_rejectsSpendsThatAreNotFailedWithoutSideEffects(SpendStatus status) {
        Spend spend = ownedSpendIn(status);

        assertThrows(SpendNotRetryableException.class, () -> service.retry(user, spend.getId()));

        assertEquals(status, spend.getStatus());
        verify(spendRepository, never()).save(any());
        verifyNoInteractions(queuePublisher);
    }

    @Test
    void retry_ofAnotherUsersOrMissingSpendFindsNothing() {
        UUID spendId = UUID.randomUUID();
        when(spendRepository.findByIdAndUserId(spendId, user.getId())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.retry(user, spendId));

        verifyNoInteractions(queuePublisher);
    }

    @Test
    void retry_whenPublishFailsLeavesTheSpendFailedAndReportsTheError() {
        Spend spend = ownedSpendIn(SpendStatus.FAILED);
        doThrow(new RuntimeException("sqs down")).when(queuePublisher).publishForExtraction(any());

        assertThrows(RuntimeException.class, () -> service.retry(user, spend.getId()));

        assertEquals(SpendStatus.FAILED, spend.getStatus());
        verify(storageService, never()).deleteFile(any());
    }

    @Test
    void retriedSpendRunsThroughExtractionAgain() {
        Spend spend = ownedSpendIn(SpendStatus.FAILED);
        when(extractionService.extract(user, spend.getId(), IMAGE, "image/jpeg")).thenReturn(ocr("TOTAL 10"));

        service.retry(user, spend.getId());
        service.processExtraction(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        verify(queuePublisher).publishForEnrichment(spend.getId());
    }

    // ---- category-only enrichment (Reclassify) ----

    private static final LocalDate USER_DATE = LocalDate.of(2026, 3, 9);

    // A processed Spend the user has corrected, locked in PROCESSING while the reclassification runs.
    private Spend spendBeingReclassified(SpendStatus status) {
        Spend spend = spendIn(status);
        spend.setCategory(comida);
        spend.setDescription("Edited by the user");
        spend.setAmount(new BigDecimal("25.00"));
        spend.setSpendDate(USER_DATE);
        spend.getItems().add(SpendItem.builder().spend(spend).description("Milk").amount(new BigDecimal("4.50")).position(0).build());
        spend.setReceiptMetadata(ReceiptMetadata.builder()
            .spend(spend).rawOcrText("TOTAL 10").rawResponse(Map.of("output", "original"))
            .classifiedAt(LocalDateTime.of(2026, 3, 1, 10, 0)).build());
        inEnrichmentStageIfProcessing(spend);
        return spend;
    }

    private void inEnrichmentStageIfProcessing(Spend spend) {
        if (spend.getStatus() == SpendStatus.PROCESSING) {
            spend.setProcessingStage(ProcessingStage.ENRICHMENT);
        }
    }

    @Test
    void categoryOnly_updatesJustTheCategoryFromTheCurrentDescriptionItemsAndRawText() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        Category transporte = Category.builder().id(UUID.randomUUID()).name("Transporte").build();
        when(categoryRepository.findByName("Transporte")).thenReturn(Optional.of(transporte));
        when(enrichmentService.reclassify(user, "Edited by the user", spend.getItems(), "TOTAL 10"))
            .thenReturn("Transporte");

        service.processCategoryOnlyEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        assertEquals(transporte, spend.getCategory());
        assertEquals(new BigDecimal("25.00"), spend.getAmount());
        assertEquals("Edited by the user", spend.getDescription());
        assertEquals(USER_DATE, spend.getSpendDate());
        assertEquals(List.of("Milk"), spend.getItems().stream().map(SpendItem::getDescription).toList());
        assertEquals(Map.of("output", "original"), spend.getReceiptMetadata().getRawResponse());
        assertEquals(NOW, spend.getReceiptMetadata().getClassifiedAt());
        verify(enrichmentService, never()).enrich(any(), any());
        verify(spendRepository).save(spend);
    }

    @Test
    void categoryOnly_fallsBackToUncategorizedWhenTheModelReturnsAnUnknownCategory() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Invented")).thenReturn(Optional.empty());
        when(categoryRepository.findByName("Uncategorized")).thenReturn(Optional.of(uncategorized));
        when(enrichmentService.reclassify(any(), any(), any(), any())).thenReturn("Invented");

        service.processCategoryOnlyEnrichment(spend.getId(), false);

        assertEquals(uncategorized, spend.getCategory());
        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
    }

    @Test
    void categoryOnly_dropsMessageForMissingSpend() {
        UUID missing = UUID.randomUUID();
        when(spendRepository.findWithUserById(missing)).thenReturn(Optional.empty());

        service.processCategoryOnlyEnrichment(missing, false);

        verifyNoInteractions(enrichmentService);
        verify(spendRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = SpendStatus.class, names = {"PENDING", "PROCESSED", "FAILED"})
    void categoryOnly_dropsDuplicateOrStaleMessageWithoutSideEffects(SpendStatus status) {
        Spend spend = spendBeingReclassified(status);

        service.processCategoryOnlyEnrichment(spend.getId(), false);

        assertEquals(status, spend.getStatus());
        assertEquals(comida, spend.getCategory());
        verifyNoInteractions(enrichmentService);
        verify(spendRepository, never()).save(any());
    }

    @Test
    void enrichment_dropsStaleDuplicateWhenTheSpendIsAlreadyEnrichedAndBeingReclassified() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);

        service.processEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        assertEquals(new BigDecimal("25.00"), spend.getAmount());
        assertEquals("Edited by the user", spend.getDescription());
        assertEquals(List.of("Milk"), spend.getItems().stream().map(SpendItem::getDescription).toList());
        verifyNoInteractions(enrichmentService);
        verify(spendRepository, never()).save(any());
    }

    @Test
    void categoryOnly_dropsStaleMessageWhileARetriedSpendAwaitsFullEnrichment() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);

        service.processCategoryOnlyEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        assertEquals(uncategorized, spend.getCategory());
        verifyNoInteractions(enrichmentService);
        verify(spendRepository, never()).save(any());
    }

    @Test
    void retriedSpendThatWasEnrichedBeforeIsEnrichedAgainInFull() {
        Spend spend = spendBeingReclassified(SpendStatus.FAILED);
        when(spendRepository.findByIdAndUserId(spend.getId(), user.getId())).thenReturn(Optional.of(spend));
        when(receiptMetadataRepository.findBySpendId(spend.getId())).thenReturn(Optional.of(spend.getReceiptMetadata()));
        when(extractionService.extract(user, spend.getId(), IMAGE, "image/jpeg")).thenReturn(ocr("TOTAL 10"));
        when(categoryRepository.findByName("Comida")).thenReturn(Optional.of(comida));
        when(enrichmentService.enrich(user, "TOTAL 10")).thenReturn(classification("Comida", null));

        service.retry(user, spend.getId());
        service.processExtraction(spend.getId(), false);
        service.processEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        assertEquals(new BigDecimal("10.50"), spend.getAmount());
        assertEquals("Supermarket", spend.getDescription());
    }

    @Test
    void categoryOnly_failureBeforeLastAttemptLeavesSpendProcessingSoRedeliveryRetries() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        when(enrichmentService.reclassify(any(), any(), any(), any()))
            .thenThrow(new ReceiptProcessingException("no category"));

        assertThrows(ReceiptProcessingException.class, () -> service.processCategoryOnlyEnrichment(spend.getId(), false));

        assertEquals(SpendStatus.PROCESSING, spend.getStatus());
        assertEquals(comida, spend.getCategory());
    }

    @Test
    void categoryOnly_failureOnLastAttemptRestoresProcessedAndKeepsTheUsersData() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        when(enrichmentService.reclassify(any(), any(), any(), any()))
            .thenThrow(new ReceiptProcessingException("no category"));

        assertThrows(ReceiptProcessingException.class, () -> service.processCategoryOnlyEnrichment(spend.getId(), true));

        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        assertEquals(comida, spend.getCategory());
        assertEquals(new BigDecimal("25.00"), spend.getAmount());
        assertEquals("Edited by the user", spend.getDescription());
        verify(spendRepository).save(spend);
        verify(spendRepository, never()).delete(any(Spend.class));
    }

    @Test
    void categoryOnly_withNoQuotaLeftRestoresProcessedWithoutRetry() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        when(enrichmentService.reclassify(any(), any(), any(), any()))
            .thenThrow(new AiUsageLimitExceededException("BEDROCK", "limit"));

        service.processCategoryOnlyEnrichment(spend.getId(), false);

        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        assertEquals(comida, spend.getCategory());
    }

    @Test
    void categoryOnly_withoutStoredRawTextRestoresProcessedOnTheLastAttempt() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        spend.getReceiptMetadata().setRawOcrText(null);

        assertThrows(ReceiptProcessingException.class, () -> service.processCategoryOnlyEnrichment(spend.getId(), true));

        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        verifyNoInteractions(enrichmentService);
    }

    // ---- enrichment queue messages ----

    @Test
    void enrichmentMessage_withJustASpendIdRunsFullEnrichment() {
        Spend spend = spendAwaitingEnrichment(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Comida")).thenReturn(Optional.of(comida));
        when(enrichmentService.enrich(user, "TOTAL 10")).thenReturn(classification("Comida", null));

        service.processEnrichmentMessage(new SpendMessage(spend.getId(), false), false);

        assertEquals(new BigDecimal("10.50"), spend.getAmount());
        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
    }

    @Test
    void enrichmentMessage_markedCategoryOnlyRunsCategoryOnlyEnrichment() {
        Spend spend = spendBeingReclassified(SpendStatus.PROCESSING);
        when(categoryRepository.findByName("Comida")).thenReturn(Optional.of(comida));
        when(enrichmentService.reclassify(any(), any(), any(), any())).thenReturn("Comida");

        service.processEnrichmentMessage(new SpendMessage(spend.getId(), true), false);

        assertEquals(new BigDecimal("25.00"), spend.getAmount());
        assertEquals(SpendStatus.PROCESSED, spend.getStatus());
        verify(enrichmentService, never()).enrich(any(), any());
    }
}
