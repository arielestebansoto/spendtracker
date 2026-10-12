package com.arielsoto.spendtracker.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
@Profile("!test")
@ConditionalOnProperty(prefix = "app.aws.textract", name = "enabled", havingValue = "true")
public class TextractConfig {

    @Bean
    public TextractClient textractClient() {
        return TextractClient.create();
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
