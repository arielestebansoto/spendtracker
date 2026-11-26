package com.arielsoto.spendtracker.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.arielsoto.spendtracker.support.PostgresIntegrationTest;

// Without the dev profile (prod included), the test login does not exist.
@SpringBootTest
@AutoConfigureMockMvc
class DevLoginDisabledIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void devLoginDoesNotLogAnyoneIn() throws Exception {
        mockMvc.perform(get("/dev/login"))
            .andExpect(status().isUnauthorized());
    }
}
