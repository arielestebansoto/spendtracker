package com.arielsoto.spendtracker.ocr;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;

import java.util.ArrayList;
import java.util.List;

@Service
public class DetectDocumentTextTextractStrategy implements TextractStrategy {

    private final TextractClient textractClient;

    public DetectDocumentTextTextractStrategy() {
        this.textractClient = TextractClient.create();
    }

    @Override
    public String name() {
        return "DETECT_TEXT";
    }

    @Override
    public OcrResult extractText(byte[] imageBytes, String contentType) {
        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
            .document(Document.builder()
                .bytes(SdkBytes.fromByteArray(imageBytes))
                .build())
            .build();

        DetectDocumentTextResponse response = textractClient.detectDocumentText(request);
        return parseResponse(response);
    }

    private OcrResult parseResponse(DetectDocumentTextResponse response) {
        List<OcrResult.TextBlock> blocks = new ArrayList<>();
        List<Float> confidences = new ArrayList<>();
        StringBuilder rawText = new StringBuilder();

        for (Block block : response.blocks()) {
            if (block.blockType() != BlockType.LINE) {
                continue;
            }

            String text = block.text();
            if (text == null || text.isBlank()) {
                continue;
            }

            rawText.append(text).append("\n");
            confidences.add(block.confidence());
            blocks.add(new OcrResult.TextBlock(text, block.confidence(), List.of()));
        }

        if (blocks.isEmpty()) {
            return new OcrResult("", 0f, List.of());
        }

        float avgConfidence = (float) confidences.stream()
            .mapToDouble(Float::doubleValue)
            .average()
            .orElse(0.0);

        return new OcrResult(rawText.toString().trim(), avgConfidence, blocks);
    }
}
