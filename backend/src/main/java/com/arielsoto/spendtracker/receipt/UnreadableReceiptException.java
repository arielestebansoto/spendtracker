package com.arielsoto.spendtracker.receipt;

/**
 * Textract cannot read the Receipt: it found no text, or rejected the file. The same file always
 * gives the same result, so the Spend fails without being retried.
 */
public class UnreadableReceiptException extends ReceiptProcessingException {

    public UnreadableReceiptException(String message) {
        super(message);
    }

    public UnreadableReceiptException(String message, Throwable cause) {
        super(message, cause);
    }
}
