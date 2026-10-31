package com.arielsoto.spendtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arielsoto.spendtracker.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

// The real CSRF flow of the frontend, without the csrf() post-processor the other tests use:
// after login it calls GET /api/v1/security/csrf, reads the XSRF-TOKEN cookie and sends it back in
// the X-XSRF-TOKEN header of every state-changing request. Logout is the state-changing request here.
@SpringBootTest
@AutoConfigureMockMvc
class CsrfFlowIT extends PostgresIntegrationTest {

    private static final String CSRF_URL = "/api/v1/security/csrf";

    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postWithTheCookieTokenAsHeaderIsAccepted() throws Exception {
        Cookie cookie = fetchCsrf().getResponse().getCookie("XSRF-TOKEN");

        assertThat(cookie).isNotNull();

        mockMvc.perform(post(LOGOUT_URL)
                .with(loggedIn())
                .cookie(cookie)
                .header("X-XSRF-TOKEN", cookie.getValue()))
            .andExpect(status().isOk());
    }

    @Test
    void postWithoutTheHeaderIsForbidden() throws Exception {
        Cookie cookie = fetchCsrf().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post(LOGOUT_URL)
                .with(loggedIn())
                .cookie(cookie))
            .andExpect(status().isForbidden());
    }

    // The token in the response body is masked, so it never matches the cookie when sent as a header.
    @Test
    void postWithTheResponseBodyTokenAsHeaderIsForbidden() throws Exception {
        MvcResult csrf = fetchCsrf();
        Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String bodyToken = JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post(LOGOUT_URL)
                .with(loggedIn())
                .cookie(cookie)
                .header("X-XSRF-TOKEN", bodyToken))
            .andExpect(status().isForbidden());
    }

    private MvcResult fetchCsrf() throws Exception {
        return mockMvc.perform(get(CSRF_URL).with(loggedIn()))
            .andExpect(status().isOk())
            .andReturn();
    }

    private OAuth2LoginRequestPostProcessor loggedIn() {
        return oauth2Login().attributes(attributes -> attributes.put("sub", "google-sub-123"));
    }
}
