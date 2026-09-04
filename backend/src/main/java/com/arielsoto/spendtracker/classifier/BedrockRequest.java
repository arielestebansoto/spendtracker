package com.arielsoto.spendtracker.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BedrockRequest(
    @JsonProperty("inputText") String inputText
) {}
