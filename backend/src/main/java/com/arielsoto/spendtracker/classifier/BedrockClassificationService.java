package com.arielsoto.spendtracker.classifier;

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

@Service
@EnableConfigurationProperties(BedrockProperties.class)
public class BedrockClassificationService {

    private static final Logger log = LoggerFactory.getLogger(BedrockClassificationService.class);

    private final BedrockRuntimeClient bedrockClient;
    private final BedrockProperties properties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public BedrockClassificationService(BedrockProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        this.bedrockClient = BedrockRuntimeClient.create();
        this.objectMapper = new ObjectMapper();
    }

    private boolean isDev() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    public ClassifiedSpend classify(String ocrText) {
        try {
            String prompt = ClassificationPrompt.buildPrompt(ocrText);

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

            ClassifiedSpend result = parseResponse(responseText);

            if (isDev()) {
                log.debug("bedrock_parsed_result amount={} category={} description={} date={} items={}",
                    result.amount(), result.category(), result.description(), result.date(), result.items());
            }

            return result;
        } catch (Exception e) {
            log.error("Bedrock classification failed", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
        }
    }

    private ClassifiedSpend parseResponse(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode output = root.get("output");

            if (output != null && output.has("message")) {
                JsonNode content = output.get("message").get("content");
                if (content.isArray() && content.size() > 0) {
                    String text = content.get(0).get("text").asText();
                    return parseClassificationJson(text);
                }
            }

            return parseClassificationJson(responseText);
        } catch (Exception e) {
            log.error("Failed to parse Bedrock response", e);
            return new ClassifiedSpend(null, null, null, null, List.of());
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
