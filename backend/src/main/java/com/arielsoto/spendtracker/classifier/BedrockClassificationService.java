package com.arielsoto.spendtracker.classifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@EnableConfigurationProperties(BedrockProperties.class)
public class BedrockClassificationService {

    private static final Logger log = LoggerFactory.getLogger(BedrockClassificationService.class);

    private final BedrockRuntimeClient bedrockClient;
    private final BedrockProperties properties;
    private final ObjectMapper objectMapper;

    public BedrockClassificationService(BedrockProperties properties) {
        this.properties = properties;
        this.bedrockClient = BedrockRuntimeClient.create();
        this.objectMapper = new ObjectMapper();
    }

    public ClassifiedSpend classify(String ocrText) {
        try {
            String prompt = ClassificationPrompt.buildPrompt(ocrText);

            String requestBody = objectMapper.writeValueAsString(new BedrockRequest(prompt));

            InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(properties.modelId())
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseText = response.body().asUtf8String();

            return parseResponse(responseText);
        } catch (Exception e) {
            log.error("Bedrock classification failed", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }

    private ClassifiedSpend parseResponse(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode results = root.get("results");

            if (results != null && results.isArray() && results.size() > 0) {
                String text = results.get(0).get("outputText").asText();
                return parseClassificationJson(text);
            }

            return parseClassificationJson(responseText);
        } catch (Exception e) {
            log.error("Failed to parse Bedrock response", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }

    private ClassifiedSpend parseClassificationJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            BigDecimal amount = root.has("amount") && !root.get("amount").isNull()
                ? root.get("amount").decimalValue() : null;

            String category = root.has("category") && !root.get("category").isNull()
                ? root.get("category").asText() : null;

            String description = root.has("description") && !root.get("description").isNull()
                ? root.get("description").asText() : null;

            LocalDate date = root.has("date") && !root.get("date").isNull()
                ? LocalDate.parse(root.get("date").asText()) : null;

            List<ClassifiedSpend.ClassifiedItem> items = new ArrayList<>();
            if (root.has("items") && root.get("items").isArray()) {
                for (JsonNode itemNode : root.get("items")) {
                    items.add(new ClassifiedSpend.ClassifiedItem(
                        itemNode.get("description").asText(),
                        itemNode.get("amount").decimalValue()
                    ));
                }
            }

            return new ClassifiedSpend(amount, category, description, date, items);
        } catch (Exception e) {
            log.error("Failed to parse classification JSON", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }
}
