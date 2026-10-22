package com.arielsoto.spendtracker.classifier;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.classificationrule.ClassificationRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BedrockClassificationService {

    private static final Logger log = LoggerFactory.getLogger(BedrockClassificationService.class);

    private final BedrockRuntimeClient bedrockClient;
    private final BedrockProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public BedrockClassificationService(BedrockRuntimeClient bedrockClient,
                                      BedrockProperties properties,
                                      Environment environment) {
        this.bedrockClient = bedrockClient;
        this.properties = properties;
        this.environment = environment;
        this.objectMapper = new ObjectMapper();
    }

    private boolean isDev() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    public ClassifiedSpendResult classify(
        String ocrText,
        List<Category> categories,
        List<ClassificationRule> rules
    ) {
        try {
            String prompt = ClassificationPrompt.buildPrompt(ocrText, categories, rules);

            if (isDev()) {
                log.debug("bedrock_request_prompt modelId={}\n{}", properties.modelId(), prompt);
            }

            BedrockRequest novaRequest = new BedrockRequest(
                List.of(new BedrockRequest.Message("user",
                    List.of(new BedrockRequest.ContentBlock(prompt)))),
                new BedrockRequest.InferenceConfig(2048, 0.0)
            );

            String requestBody = objectMapper.writeValueAsString(novaRequest);

            InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(properties.modelId())
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String responseText = response.body().asUtf8String();

            if (isDev()) {
                log.debug("bedrock_response_raw\n{}", responseText);
            }

            ClassifiedSpendResult result = parseResponse(responseText);
            ClassifiedSpend classified = result.classified();

            if (isDev()) {
                log.debug("bedrock_parsed_result amount={} category={} description={} date={} items={} inputTokens={} outputTokens={}",
                    classified.amount(), classified.category(), classified.description(), classified.date(), classified.items(),
                    result.inputTokens(), result.outputTokens());
            }

            return result;
        } catch (Exception e) {
            log.error("Bedrock classification failed", e);
            return new ClassifiedSpendResult(new ClassifiedSpend(null, null, null, null, List.of()), 0, 0, null);
        }
    }

    private ClassifiedSpendResult parseResponse(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            Map<String, Object> rawResponse = objectMapper.convertValue(root, new TypeReference<>() {});

            long inputTokens = 0;
            long outputTokens = 0;
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                inputTokens = usage.has("inputTokens") ? usage.get("inputTokens").asLong() : 0;
                outputTokens = usage.has("outputTokens") ? usage.get("outputTokens").asLong() : 0;
            }

            JsonNode output = root.get("output");

            if (output != null && output.has("message")) {
                JsonNode content = output.get("message").get("content");
                if (content.isArray() && !content.isEmpty()) {
                    String text = content.get(0).get("text").asText();
                    return new ClassifiedSpendResult(parseClassificationJson(text), inputTokens, outputTokens, rawResponse);
                }
            }

            return new ClassifiedSpendResult(parseClassificationJson(responseText), inputTokens, outputTokens, rawResponse);
        } catch (Exception e) {
            log.error("Failed to parse Bedrock response", e);
            return new ClassifiedSpendResult(new ClassifiedSpend(null, null, null, null, List.of()), 0, 0, null);
        }
    }

    private ClassifiedSpend parseClassificationJson(String json) {
        try {
            JsonNode root;
            try {
                root = objectMapper.readTree(json);
            } catch (Exception e) {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                if (start == -1 || end == -1 || end <= start) {
                    log.error("No JSON object found in response: {}", json);
                    return new ClassifiedSpend(null, null, null, null, List.of());
                }
                root = objectMapper.readTree(json.substring(start, end + 1));
            }

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
