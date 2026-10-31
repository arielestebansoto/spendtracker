package com.arielsoto.spendtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.arielsoto.spendtracker.loggin.SecurityMdcFilter;
import com.arielsoto.spendtracker.support.PostgresIntegrationTest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Goes through the real logout, so it also checks that LogoutAuditHandler reads the session
// before Spring Security invalidates it.
@SpringBootTest
@AutoConfigureMockMvc
class LogoutAuditIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(SecurityAuditLogger.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
    }

    @Test
    void logoutLogsTheAppUserIdNotTheProviderId() throws Exception {
        UUID appUserId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/auth/logout")
                .with(oauth2Login().attributes(attributes -> attributes.put("sub", "google-sub-123")))
                .with(csrf())
                .sessionAttr(SecurityMdcFilter.APP_USER_ID, appUserId))
            .andExpect(status().isOk());

        assertThat(appender.list).singleElement()
            .extracting(ILoggingEvent::getFormattedMessage)
            .isEqualTo("security_event type=LOGOUT userId=" + appUserId);
    }
}
