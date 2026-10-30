package com.arielsoto.spendtracker.classifier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(BedrockProperties.class)
@ConditionalOnProperty(prefix = "app.aws.bedrock", name = "enabled", havingValue = "true")
public class BedrockConfig {

    // Caps a whole call, retries included, so Enrichment fits in the claim lease and the queue's visibility timeout.
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        return BedrockRuntimeClient.builder()
            .overrideConfiguration(config -> config.apiCallTimeout(properties.apiCallTimeout()))
            .build();
    }

    @Bean
    public BedrockClassificationService bedrockClassificationService(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockProperties properties,
            Environment environment) {
        return new BedrockClassificationService(bedrockRuntimeClient, properties, environment);
    }
}
