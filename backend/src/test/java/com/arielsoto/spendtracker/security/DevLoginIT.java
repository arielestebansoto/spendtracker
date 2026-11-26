package com.arielsoto.spendtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.arielsoto.spendtracker.support.PostgresIntegrationTest;
import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserAppRepository;

// With the dev profile, GET /dev/login logs in a fixed test user without Google or GitHub.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevLoginIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAppRepository userAppRepository;

    @Test
    void loginAuthenticatesTheSessionAndRedirectsToTheFrontend() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/v1/spends").session(session))
            .andExpect(status().isOk());
    }

    @Test
    void loginReusesTheTestUser() throws Exception {
        login();
        UUID firstId = testUserId();

        login();

        assertThat(testUserId()).isEqualTo(firstId);
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mockMvc.perform(get(DevLoginController.PATH))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("http://localhost:3000"))
            .andReturn()
            .getRequest()
            .getSession(false);
    }

    private UUID testUserId() {
        return userAppRepository
            .findByOauthProviderAndOauthId(OAuthProvider.GITHUB, DevLoginController.OAUTH_ID)
            .orElseThrow()
            .getId();
    }
}
