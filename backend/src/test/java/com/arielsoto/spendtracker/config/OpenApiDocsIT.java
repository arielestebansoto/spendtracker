package com.arielsoto.spendtracker.config;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.arielsoto.spendtracker.support.PostgresIntegrationTest;

// The dev profile serves the OpenAPI spec and Swagger UI (application-dev.yml).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiDocsIT extends PostgresIntegrationTest {

    private static final String SPEC_URL = "/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void specDescribesTheSecuritySchemes() throws Exception {
        mockMvc.perform(get(SPEC_URL).with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("SpendTracker API"))
            .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"))
            .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.name").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.security[0].sessionCookie").exists())
            .andExpect(jsonPath("$.security[0].csrfHeader").exists());
    }

    @Test
    void specDescribesTheErrorResponses() throws Exception {
        mockMvc.perform(get(SPEC_URL).with(oauth2Login()))
            .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.status").exists())
            .andExpect(jsonPath("$.paths['/api/v1/spends/{id}'].get.responses['401']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/spends/{id}'].get.responses['403']").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/v1/spends/{id}'].delete.responses['403']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/spends/{id}'].get.responses.default.content['application/problem+json']").exists());
    }

    @Test
    void specDescribesTheRequestParameters() throws Exception {
        mockMvc.perform(get(SPEC_URL).with(oauth2Login()))
            .andExpect(jsonPath("$.paths['/api/v1/spends'].get.parameters[*].name").value(hasItems("page", "size", "sort")))
            .andExpect(jsonPath("$.paths['/api/v1/spends'].get.parameters[*].name").value(not(hasItems("pageable"))))
            .andExpect(jsonPath("$.paths['/api/v1/security/csrf'].get.parameters").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/v1/spends/from-receipt'].post.requestBody.content['multipart/form-data'].schema.properties.receipt.format").value("binary"));
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html").with(oauth2Login()))
            .andExpect(status().isOk());
    }

    @Test
    void specRequiresLogin() throws Exception {
        mockMvc.perform(get(SPEC_URL))
            .andExpect(status().isUnauthorized());
    }
}
