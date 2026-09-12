package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendItem;
import java.util.List;

public record SpendProcessingResult(
    Spend spend,
    List<SpendItem> items,
    ProcessingStatus status,
    String errorMessage
) {
    public enum ProcessingStatus {
        SUCCESS,
        OCR_FAILED,
        CLASSIFICATION_FAILED,
        PARTIAL_SUCCESS
    }
}
