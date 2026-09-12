package com.arielsoto.spendtracker.receipt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ReceiptControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReceiptService receiptService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    private ReceiptController controller;

    @BeforeEach
    void setUp() {
        controller = new ReceiptController(
            receiptService,
            authenticatedUserService
        );
        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .build();
    }

    private UserApp buildUser() {
        return UserApp.builder()
            .id(UUID.randomUUID())
            .name("Test User")
            .oauthId("123")
            .build();
    }

    @Test
    void uploadReturns200WithResponse() throws Exception {
        UserApp user = buildUser();
        UUID spendId = UUID.randomUUID();

        when(authenticatedUserService.getCurrentUser(
            any()
        )).thenReturn(user);

        when(receiptService.storeReceipt(
            eq(user), eq(spendId), any()
        )).thenReturn(new ReceiptUploadResponse(
            "/api/v1/spends/" + spendId + "/receipt",
            "image/jpeg", 1024L
        ));

        MockMultipartFile file = new MockMultipartFile(
            "receipt", "receipt.jpg", "image/jpeg",
            new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart(
                "/api/v1/spends/" + spendId + "/receipt"
            ).file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.size").value(1024));
    }

    @Test
    void getReceiptReturns200WithResource() throws Exception {
        UserApp user = buildUser();
        UUID spendId = UUID.randomUUID();

        when(authenticatedUserService.getCurrentUser(
            any()
        )).thenReturn(user);

        when(receiptService.loadReceipt(user, spendId))
            .thenReturn(new ByteArrayResource(new byte[]{1, 2}));

        when(receiptService.getReceiptContentType(user, spendId))
            .thenReturn("image/png");

        mockMvc.perform(get(
                "/api/v1/spends/" + spendId + "/receipt"
            ))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void deleteReceiptReturns204() throws Exception {
        UserApp user = buildUser();
        UUID spendId = UUID.randomUUID();

        when(authenticatedUserService.getCurrentUser(
            any()
        )).thenReturn(user);

        mockMvc.perform(delete(
                "/api/v1/spends/" + spendId + "/receipt"
            ))
            .andExpect(status().isNoContent());
    }
}
