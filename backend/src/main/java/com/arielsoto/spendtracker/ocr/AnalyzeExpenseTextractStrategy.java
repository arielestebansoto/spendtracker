package com.arielsoto.spendtracker.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyzeExpenseTextractStrategy implements TextractStrategy {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeExpenseTextractStrategy.class);

    private final TextractClient textractClient;

    public AnalyzeExpenseTextractStrategy() {
        this.textractClient = TextractClient.create();
    }

    @Override
    public String name() {
        return "ANALYZE_EXPENSE";
    }

    @Override
    public OcrResult extractText(byte[] imageBytes, String contentType) {
        SdkBytes imageBytesSdk = SdkBytes.fromByteArray(imageBytes);

        AnalyzeExpenseRequest request = AnalyzeExpenseRequest.builder()
            .document(Document.builder()
                .bytes(imageBytesSdk)
                .build())
            .build();

        AnalyzeExpenseResponse response = textractClient.analyzeExpense(request);
        return parseResponse(response);
    }

    private OcrResult parseResponse(AnalyzeExpenseResponse response) {
        List<ExpenseDocument> documents = response.expenseDocuments();

        if (documents.isEmpty()) {
            return new OcrResult("", 0f, List.of());
        }

        List<OcrResult.TextBlock> allBlocks = new ArrayList<>();
        List<Float> allConfidences = new ArrayList<>();
        StringBuilder rawText = new StringBuilder();

        for (ExpenseDocument doc : documents) {
            for (ExpenseField field : doc.summaryFields()) {
                String label = field.labelDetection() != null
                    ? field.labelDetection().text()
                    : field.type() != null ? field.type().text() : "";
                String value = field.valueDetection() != null
                    ? field.valueDetection().text()
                    : "";

                if (!value.isBlank()) {
                    float conf = field.valueDetection() != null
                        ? field.valueDetection().confidence()
                        : 0f;
                    allConfidences.add(conf);

                    rawText.append(label).append(": ").append(value).append("\n");

                    allBlocks.add(new OcrResult.TextBlock(
                        label + ": " + value,
                        conf,
                        List.of()
                    ));
                }
            }

            rawText.append("---\nLine Items:\n");
            for (LineItemGroup group : doc.lineItemGroups()) {
                for (LineItemFields item : group.lineItems()) {
                    StringBuilder itemText = new StringBuilder();
                    float itemConfidence = 0f;
                    int fieldCount = 0;

                    for (ExpenseField field : item.lineItemExpenseFields()) {
                        String value = field.valueDetection() != null
                            ? field.valueDetection().text()
                            : "";
                        if (!value.isBlank()) {
                            itemText.append(value).append(" ");
                            if (field.valueDetection() != null) {
                                itemConfidence += field.valueDetection().confidence();
                                fieldCount++;
                            }
                        }
                    }

                    String itemStr = itemText.toString().trim();
                    if (!itemStr.isEmpty()) {
                        float avgConf = fieldCount > 0 ? itemConfidence / fieldCount : 0f;
                        allConfidences.add(avgConf);
                        rawText.append("- ").append(itemStr).append("\n");
                        allBlocks.add(new OcrResult.TextBlock(itemStr, avgConf, List.of()));
                    }
                }
            }
        }

        float avgConfidence = allConfidences.isEmpty()
            ? 0f
            : (float) allConfidences.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);

        return new OcrResult(rawText.toString().trim(), avgConfidence, allBlocks);
    }
}
