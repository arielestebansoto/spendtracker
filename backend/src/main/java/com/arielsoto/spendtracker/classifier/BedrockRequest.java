package com.arielsoto.spendtracker.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BedrockRequest(
    List<Message> messages,
    InferenceConfig inferenceConfig
) {
    public record Message(String role, List<ContentBlock> content) {}
    public record ContentBlock(String text) {}
    public record InferenceConfig(
        @JsonProperty("maxTokens") int maxTokens,
        double temperature
    ) {}
}
