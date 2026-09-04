package com.arielsoto.spendtracker.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TextractOcrService {

    private static final Logger log = LoggerFactory.getLogger(TextractOcrService.class);

    private final TextractClient textractClient;

    public TextractOcrService() {
        this.textractClient = TextractClient.create();
    }

    public OcrResult extractText(byte[] imageBytes, String contentType) {
        try {
            SdkBytes imageBytesSdk = SdkBytes.fromByteArray(imageBytes);

            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                .document(Document.builder()
                    .bytes(imageBytesSdk)
                    .build())
                .build();

            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Textract OCR extraction failed", e);
            return new OcrResult("", 0f, List.of());
        }
    }

    private OcrResult parseResponse(DetectDocumentTextResponse response) {
        List<Block> blocks = response.blocks();

        if (blocks.isEmpty()) {
            return new OcrResult("", 0f, List.of());
        }

        String rawText = blocks.stream()
            .filter(b -> b.blockType() == BlockType.LINE)
            .map(Block::text)
            .collect(Collectors.joining("\n"));

        float confidence = blocks.stream()
            .filter(b -> b.blockType() == BlockType.PAGE)
            .map(Block::confidence)
            .findFirst()
            .orElse(0f);

        List<OcrResult.TextBlock> textBlocks = new ArrayList<>();
        for (Block block : blocks) {
            if (block.blockType() == BlockType.LINE) {
                List<OcrResult.Symbol> symbols = block.relationships().stream()
                    .filter(rel -> rel.type() == RelationshipType.CHILD)
                    .flatMap(rel -> rel.ids().stream())
                    .map(childId -> findBlockById(blocks, childId))
                    .filter(java.util.Objects::nonNull)
                    .filter(child -> child.blockType() == BlockType.WORD)
                    .map(word -> new OcrResult.Symbol(word.text(), word.confidence()))
                    .collect(Collectors.toList());

                textBlocks.add(new OcrResult.TextBlock(
                    block.text(),
                    block.confidence(),
                    symbols
                ));
            }
        }

        return new OcrResult(rawText, confidence, textBlocks);
    }

    private Block findBlockById(List<Block> blocks, String id) {
        return blocks.stream()
            .filter(b -> b.id().equals(id))
            .findFirst()
            .orElse(null);
    }
}
