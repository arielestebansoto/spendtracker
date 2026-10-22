package com.arielsoto.spendtracker.config;

import com.arielsoto.spendtracker.classifier.BedrockClassificationService;
import com.arielsoto.spendtracker.classifier.ClassifiedSpend;
import com.arielsoto.spendtracker.classifier.ClassifiedSpendResult;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
@Profile("test")
public class TestBedrockConfig {

    @Bean
    @Primary
    public BedrockClassificationService bedrockClassificationService() {
        BedrockClassificationService mock = Mockito.mock(BedrockClassificationService.class);
        ClassifiedSpend classified = new ClassifiedSpend(
                new BigDecimal("10.00"),
                "Uncategorized",
                "Test receipt",
                LocalDate.now(),
                List.of()
        );
        Mockito.when(mock.classify(Mockito.anyString(), Mockito.anyList(), Mockito.anyList()))
                .thenReturn(new ClassifiedSpendResult(classified, 0, 0, null));
        return mock;
    }
}
