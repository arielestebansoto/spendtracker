package com.arielsoto.spendtracker.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import com.arielsoto.spendtracker.support.PostgresIntegrationTest;
import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;

// The controller tests use a standalone MockMvc; this one goes through the real filter chain and the
// application's JSON mapper, which must write the extension properties (`reason`) at the top level.
@SpringBootTest
@AutoConfigureMockMvc
class ApiExceptionHandlerIT extends PostgresIntegrationTest {

    private static final String OAUTH_ID = "api-exception-handler-it";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAppRepository userRepository;

    @Autowired
    private ClientRegistrationRepository clientRegistrations;

    @BeforeEach
    void createUser() {
        if (userRepository.findByOauthProviderAndOauthId(OAuthProvider.GOOGLE, OAUTH_ID).isEmpty()) {
            userRepository.save(UserApp.builder()
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId(OAUTH_ID)
                .name("Problem Detail")
                .createdAt(LocalDateTime.now())
                .build());
        }
    }

    @Test
    void missingSpendIsA404ProblemDetail() throws Exception {
        UUID spendId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/spends/{id}", spendId).with(loggedIn()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.detail").value("Spend not found"))
            .andExpect(jsonPath("$.instance").value("/api/v1/spends/" + spendId));
    }

    @Test
    void invalidReceiptKeepsTheReasonAtTheTopLevel() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("receipt", "r.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/spends/from-receipt").file(empty).with(loggedIn()).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("File is empty"))
            .andExpect(jsonPath("$.reason").value("invalid_file"));
    }

    private OAuth2LoginRequestPostProcessor loggedIn() {
        return oauth2Login()
            .clientRegistration(clientRegistrations.findByRegistrationId("google"))
            .attributes(attributes -> attributes.put("sub", OAUTH_ID));
    }
}
