package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class ReceiptValidatorTest {

    @Test
    void emptyFileThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        ReceiptValidationException ex = assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
        assertEquals("File is empty", ex.getMessage());
    }

    @Test
    void nullContentTypeThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(null);

        ReceiptValidationException ex = assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
        assertEquals("File type not allowed", ex.getMessage());
    }

    @Test
    void disallowedTypeThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("text/plain");

        assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
    }

    @Test
    void oversizedFileThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(11L * 1024 * 1024);

        assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
    }

    @Test
    void validJpegPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }

    @Test
    void validPngPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }

    @Test
    void validWebpPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/webp");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }

    @Test
    void validPdfPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }
}
