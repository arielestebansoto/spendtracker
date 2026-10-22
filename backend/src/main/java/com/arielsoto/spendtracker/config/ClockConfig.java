package com.arielsoto.spendtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class ClockConfig {

    // System default zone, to match the LocalDateTime.now() used when a Spend changes status.
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
