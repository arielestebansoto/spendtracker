package com.arielsoto.spendtracker.receipt;

import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

public class ReceiptValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf"
    );

    private static final long MAX_SIZE = 10 * 1024 * 1024;

    public static void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ReceiptValidationException("File is empty");
        }
        if (
            file.getContentType() == null ||
            !ALLOWED_TYPES.contains(file.getContentType())
        ) {
            throw new ReceiptValidationException(
                "File type not allowed"
            );
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ReceiptValidationException(
                "File size exceeds 10MB limit"
            );
        }
    }
}
