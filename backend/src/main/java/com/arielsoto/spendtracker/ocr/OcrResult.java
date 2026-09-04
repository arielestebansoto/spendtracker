package com.arielsoto.spendtracker.ocr;

import java.util.List;

public record OcrResult(
    String rawText,
    float confidence,
    List<TextBlock> blocks
) {
    public record TextBlock(
        String text,
        float confidence,
        List<Symbol> symbols
    ) {}

    public record Symbol(
        String text,
        float confidence
    ) {}
}
