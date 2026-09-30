package com.arielsoto.spendtracker.aiusage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

@Configuration
@EnableScheduling
class CostExplorerConfig {

    // Cost Explorer is a global service; us-east-1 is its canonical endpoint and
    // the only region available on the standard pricing tier.
    @Bean
    CostExplorerClient costExplorerClient() {
        return CostExplorerClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
}
