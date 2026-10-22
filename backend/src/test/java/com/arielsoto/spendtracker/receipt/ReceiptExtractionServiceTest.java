package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.aiusage.AiUsageLimitExceededException;
import com.arielsoto.spendtracker.aiusage.AiUsageService;
import com.arielsoto.spendtracker.ocr.OcrResult;
import com.arielsoto.spendtracker.ocr.TextractStrategy;
import com.arielsoto.spendtracker.user.UserApp;

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
    void extract_failsWhenNoStrategyReturnsText() {
        when(aiUsageService.validateAndPickStrategy(user)).thenReturn(primary);
        when(primary.name()).thenReturn("ANALYZE_EXPENSE");
        when(primary.extractText(IMAGE, CONTENT_TYPE)).thenReturn(ocr(null));
        when(aiUsageService.pickFallbackStrategy(user, "ANALYZE_EXPENSE")).thenReturn(null);

        assertThrows(
            ReceiptProcessingException.class,
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
