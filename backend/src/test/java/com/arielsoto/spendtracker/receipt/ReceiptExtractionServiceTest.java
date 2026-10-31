package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.ocr.TextractStrategy;
import com.arielsoto.spendtracker.user.UserApp;

import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.TextractException;
import software.amazon.awssdk.services.textract.model.ThrottlingException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

@ExtendWith(MockitoExtension.class)
class ReceiptExtractionServiceTest {

    private static final byte[] IMAGE = new byte[] {1, 2, 3};
    private static final String CONTENT_TYPE = "image/jpeg";

    @Mock
    private AiUsageService aiUsageService;

    @Mock
    private TextractStrategy primary;

    @Mock
    private TextractStrategy fallback;

    @InjectMocks
    private ReceiptExtractionService extractionService;

    private final UserApp user = UserApp.builder().id(UUID.randomUUID()).name("Test").oauthId("1").build();

    private OcrResult ocr(String text) {
        return new OcrResult(text, 99f, List.of());
    }

    @Test
    void extract_returnsRawTextAndRecordsUsage() {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.name()).thenReturn("ANALYZE_EXPENSE");
        when(primary.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr("TOTAL 10"));

        OcrResult result = extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE);

        assertEquals("TOTAL 10", result.rawText());
        verify(aiUsageService).recordTextractUsage(user, "ANALYZE_EXPENSE");
        verify(aiUsageService, never()).pickFallbackStrategy(any(), any());
    }

    @Test
    void extract_usesFallbackWhenPrimaryReturnsNoText() {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.name()).thenReturn("ANALYZE_EXPENSE");
        when(primary.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr(" "));
        when(aiUsageService.pickFallbackStrategy(user, "ANALYZE_EXPENSE")).thenReturn(fallback);
        when(fallback.name()).thenReturn("DETECT_TEXT");
        when(fallback.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr("TOTAL 20"));

        OcrResult result = extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE);

        assertEquals("TOTAL 20", result.rawText());
        verify(aiUsageService).recordTextractUsage(user, "ANALYZE_EXPENSE");
        verify(aiUsageService).recordTextractUsage(user, "DETECT_TEXT");
    }

    @Test
    void extract_reportsAnUnreadableReceiptWhenNoStrategyReturnsText() {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.name()).thenReturn("ANALYZE_EXPENSE");
        when(primary.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr(null));
        when(aiUsageService.pickFallbackStrategy(user, "ANALYZE_EXPENSE")).thenReturn(null);

        assertThrows(
            UnreadableReceiptException.class,
            () -> extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE)
        );
    }

    @Test
    void extract_reportsAnUnreadableReceiptWhenTheFallbackReturnsNoTextEither() {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.name()).thenReturn("ANALYZE_EXPENSE");
        when(primary.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr(""));
        when(aiUsageService.pickFallbackStrategy(user, "ANALYZE_EXPENSE")).thenReturn(fallback);
        when(fallback.name()).thenReturn("DETECT_TEXT");
        when(fallback.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr(""));

        assertThrows(
            UnreadableReceiptException.class,
            () -> extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE)
        );
        // Textract bills every page it reads, even when it finds no text.
        verify(aiUsageService).recordTextractUsage(user, "ANALYZE_EXPENSE");
        verify(aiUsageService).recordTextractUsage(user, "DETECT_TEXT");
    }

    // A multi-page PDF is rejected by the synchronous API with UnsupportedDocumentException.
    @ParameterizedTest
    @MethodSource("documentsTextractCannotRead")
    void extract_reportsAnUnreadableReceiptWhenTextractRejectsTheDocument(TextractException rejection) {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.extractText(IMAGE, CONTENT_TYPE)).thenThrow(rejection);

        assertThrows(
            UnreadableReceiptException.class,
            () -> extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE)
        );
        // Textract does not bill a document it rejects, and the fallback would reject it too.
        verify(aiUsageService, never()).recordTextractUsage(any(), any());
        verify(aiUsageService, never()).pickFallbackStrategy(any(), any());
    }

    static Stream<TextractException> documentsTextractCannotRead() {
        return Stream.of(
            UnsupportedDocumentException.builder().message("unsupported").build(),
            BadDocumentException.builder().message("bad").build()
        );
    }

    @Test
    void extract_propagatesOtherTextractErrorsSoTheyAreRetried() {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.extractText(IMAGE, CONTENT_TYPE))
            .thenThrow(ThrottlingException.builder().message("slow down").build());

        assertThrows(
            ThrottlingException.class,
            () -> extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE)
        );
    }

    @Test
    void extract_propagatesQuotaExceeded() {
        when(aiUsageService.validateAndPickStrategy(user))
            .thenThrow(new AiUsageLimitExceededException("TEXTRACT", "limit"));

        assertThrows(
            AiUsageLimitExceededException.class,
            () -> extractionService.extract(user, UUID.randomUUID(), IMAGE, CONTENT_TYPE)
        );
    }
}
