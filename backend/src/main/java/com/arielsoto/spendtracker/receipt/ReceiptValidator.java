package com.arielsoto.spendtracker.receipt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.springframework.web.multipart.MultipartFile;

public class ReceiptValidator {

    // Matches spring.servlet.multipart.max-file-size, which rejects larger files (413) before this runs.
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    /**
     * Checks the file is not empty, within the size limit, and of an allowed type, and returns that
     * type. The type comes from the file's first bytes; the Content-Type the client declares is ignored.
     */
    public static ReceiptFileType validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ReceiptValidationException("File is empty");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ReceiptValidationException("File size exceeds 5MB limit");
        }
        return ReceiptFileType.detect(readHeader(file))
            .orElseThrow(() -> new ReceiptValidationException("File type not allowed"));
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(ReceiptFileType.HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded receipt", e);
        }
    }
}
