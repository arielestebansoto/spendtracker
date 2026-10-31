package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ReceiptValidatorTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    private static MockMultipartFile file(String contentType, byte[] content) {
        return new MockMultipartFile("receipt", "receipt", contentType, content);
    }

    private static String rejectionOf(MockMultipartFile file) {
        return assertThrows(ReceiptValidationException.class, () -> ReceiptValidator.validate(file))
            .getMessage();
    }

    @Test
    void detectsJpeg() {
        assertEquals(ReceiptFileType.JPEG, ReceiptValidator.validate(file("image/jpeg", JPEG)));
    }

    @Test
    void detectsPng() {
        assertEquals(ReceiptFileType.PNG, ReceiptValidator.validate(file("image/png", PNG)));
    }

    @Test
    void detectsPdf() {
        assertEquals(ReceiptFileType.PDF, ReceiptValidator.validate(file("application/pdf", PDF)));
    }

    @Test
    void typeComesFromTheBytesNotTheDeclaredContentType() {
        assertEquals(ReceiptFileType.JPEG, ReceiptValidator.validate(file("application/octet-stream", JPEG)));
        assertEquals(ReceiptFileType.PNG, ReceiptValidator.validate(file(null, PNG)));
    }

    @Test
    void rejectsContentThatDoesNotMatchTheDeclaredImageType() {
        byte[] text = "<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);

        assertEquals("File type not allowed", rejectionOf(file("image/png", text)));
    }

    @Test
    void rejectsWebpBecauseTextractCannotReadIt() {
        assertEquals("File type not allowed", rejectionOf(file("image/webp", WEBP)));
    }

    @Test
    void rejectsAFileShorterThanAnySignature() {
        assertEquals("File type not allowed", rejectionOf(file("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8})));
    }

    @Test
    void rejectsAnEmptyFile() {
        assertEquals("File is empty", rejectionOf(file("image/jpeg", new byte[0])));
    }

    @Test
    void rejectsAFileOver5Mb() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, content, 0, JPEG.length);

        assertEquals("File size exceeds 5MB limit", rejectionOf(file("image/jpeg", content)));
    }

    @Test
    void acceptsAFileOfExactly5Mb() {
        byte[] content = new byte[5 * 1024 * 1024];
        System.arraycopy(JPEG, 0, content, 0, JPEG.length);

        assertEquals(ReceiptFileType.JPEG, ReceiptValidator.validate(file("image/jpeg", content)));
    }
}
