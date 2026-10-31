package com.arielsoto.spendtracker.loggin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.arielsoto.spendtracker.support.PostgresIntegrationTest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Goes through the real filter chain, so it also checks that SecurityMdcFilter runs after
// Spring Security and wraps RequestLoggingFilter.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityMdcFilterIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final Logger requestLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

    // A logging event reads the MDC lazily, so it is captured here before the filters clear it
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void attachAppender() {
        appender.start();
        requestLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        requestLogger.detachAppender(appender);
    }

    @Test
    void httpRequestLogCarriesTheAppUserIdNotTheProviderId() throws Exception {
        UUID appUserId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/security/csrf")
                .with(oauth2Login().attributes(attributes -> attributes.put("sub", "google-sub-123")))
                .sessionAttr(SecurityMdcFilter.APP_USER_ID, appUserId))
            .andExpect(status().isOk());

        assertThat(appender.list).singleElement()
            .extracting(event -> event.getMDCPropertyMap().get(SecurityMdcFilter.USER_ID))
            .isEqualTo(appUserId.toString());
    }

    @Test
    void anonymousRequestLogHasNoUserId() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(appender.list).singleElement()
            .extracting(event -> event.getMDCPropertyMap())
            .satisfies(mdc -> assertThat(mdc).doesNotContainKey(SecurityMdcFilter.USER_ID));
    }
}
