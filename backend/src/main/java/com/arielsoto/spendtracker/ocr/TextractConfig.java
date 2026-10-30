package com.arielsoto.spendtracker.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.textract.TextractClient;

import java.time.Duration;

@Configuration
@Profile("!test")
@ConditionalOnProperty(prefix = "app.aws.textract", name = "enabled", havingValue = "true")
public class TextractConfig {

    // Caps a whole call, retries included. Extraction can make two Textract calls (fallback strategy),
    // so both must fit, with the S3 download, in the claim lease and the queue's visibility timeout.
    @Bean
    public TextractClient textractClient(@Value("${app.aws.textract.api-call-timeout}") Duration apiCallTimeout) {
        return TextractClient.builder()
            .overrideConfiguration(config -> config.apiCallTimeout(apiCallTimeout))
            .build();
    }

    @Bean
    public DetectDocumentTextTextractStrategy detectDocumentTextTextractStrategy(
            TextractClient textractClient) {
        return new DetectDocumentTextTextractStrategy(textractClient);
    }

    @Bean
    public AnalyzeExpenseTextractStrategy analyzeExpenseTextractStrategy(
            TextractClient textractClient) {
        return new AnalyzeExpenseTextractStrategy(textractClient);
    }
}
