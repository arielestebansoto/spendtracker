package com.arielsoto.spendtracker.ocr;

public interface TextractStrategy {

    OcrResult extractText(byte[] imageBytes, String contentType);

    String name();
}
