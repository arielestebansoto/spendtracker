package com.arielsoto.spendtracker.receipt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The file types accepted as a Receipt, recognized by their first bytes (magic bytes) rather than
 * the Content-Type the client declares. Limited to formats Textract's synchronous API reads;
 * WebP and TIFF are left out (Textract does not read WebP).
 */
public enum ReceiptFileType {

    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", "pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII));

    /** Bytes to read from the start of a file to recognize any of the types. */
    public static final int HEADER_LENGTH = 8;

    private final String contentType;
    private final String extension;
    private final byte[] signature;

    ReceiptFileType(String contentType, String extension, byte[] signature) {
        this.contentType = contentType;
        this.extension = extension;
        this.signature = signature;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** The type whose signature starts the given header, or empty if none does. */
    public static Optional<ReceiptFileType> detect(byte[] header) {
        return Arrays.stream(values())
            .filter(type -> type.matches(header))
            .findFirst();
    }

    private boolean matches(byte[] header) {
        return header.length >= signature.length
            && Arrays.equals(header, 0, signature.length, signature, 0, signature.length);
    }
}
