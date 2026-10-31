package com.arielsoto.spendtracker.spend;

/**
 * Why a FAILED Spend cannot be retried. A FAILED Spend without one failed for a reason a Retry can
 * fix (an AWS error, the AI quota, a stuck Spend).
 */
public enum FailureReason {
    // Textract found no text in the Receipt, or rejected it (for example a multi-page PDF). The same
    // file always gives the same result, so a Retry would only spend quota.
    UNREADABLE_RECEIPT
}
