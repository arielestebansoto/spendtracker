package com.arielsoto.spendtracker.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
@Profile("!test")
@ConditionalOnProperty(prefix = "app.aws.textract", name = "enabled", havingValue = "true")
public class TextractConfig {

    @Bean
    public TextractClient textractClient() {
        return TextractClient.create();
    }

    @Service
    @ConditionalOnBean(TextractClient.class)
    static class DetectDocumentTextTextractStrategyImpl extends DetectDocumentTextTextractStrategy {
        public DetectDocumentTextTextractStrategyImpl(TextractClient textractClient) {
            super(textractClient);
        }
    }

    @Service
    @ConditionalOnBean(TextractClient.class)
    static class AnalyzeExpenseTextractStrategyImpl extends AnalyzeExpenseTextractStrategy {
        public AnalyzeExpenseTextractStrategyImpl(TextractClient textractClient) {
            super(textractClient);
        }
    }
}
