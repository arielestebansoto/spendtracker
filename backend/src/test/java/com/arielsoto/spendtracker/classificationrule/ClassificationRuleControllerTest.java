package com.arielsoto.spendtracker.classificationrule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.arielsoto.spendtracker.classificationrule.dto.RuleResponse;
import com.arielsoto.spendtracker.classificationrule.dto.RulesListResponse;
import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ClassificationRuleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassificationRuleService ruleService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ClassificationRuleController controller =
            new ClassificationRuleController(ruleService, authenticatedUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(authenticatedUserService.getCurrentUser(any()))
            .thenReturn(UserApp.builder().id(userId).name("Test User").build());
    }

    private RuleResponse ruleResponse(UUID id) {
        return new RuleResponse(
            id, "electricity bill", UUID.randomUUID(), "Servicios", null, null
        );
    }

    @Test
    void listReturnsRulesAndMax() throws Exception {
        when(ruleService.list(userId))
            .thenReturn(new RulesListResponse(List.of(ruleResponse(UUID.randomUUID())), 10));

        mockMvc.perform(get("/api/v1/classification-rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRules").value(10))
            .andExpect(jsonPath("$.rules[0].condition").value("electricity bill"));
    }

    @Test
    void createReturns201() throws Exception {
        when(ruleService.create(any(), eq(userId)))
            .thenReturn(ruleResponse(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/classification-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"electricity bill\",\"categoryId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categoryName").value("Servicios"));
    }

    @Test
    void createReturns409WhenLimitReached() throws Exception {
        when(ruleService.create(any(), eq(userId)))
            .thenThrow(new RuleLimitExceededException("limit reached"));

        mockMvc.perform(post("/api/v1/classification-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"electricity bill\",\"categoryId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void createReturns400ForInvalidCategory() throws Exception {
        when(ruleService.create(any(), eq(userId)))
            .thenThrow(new InvalidCategoryException("bad category"));

        mockMvc.perform(post("/api/v1/classification-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"electricity bill\",\"categoryId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(ruleService.update(eq(id), any(), eq(userId)))
            .thenReturn(ruleResponse(id));

        mockMvc.perform(put("/api/v1/classification-rules/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"electricity bill\",\"categoryId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void updateReturns404WhenNotOwned() throws Exception {
        UUID id = UUID.randomUUID();
        when(ruleService.update(eq(id), any(), eq(userId)))
            .thenThrow(new RuleNotFoundException("not found"));

        mockMvc.perform(put("/api/v1/classification-rules/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"condition\":\"electricity bill\",\"categoryId\":\""
                    + UUID.randomUUID() + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/classification-rules/" + id))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenNotOwned() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new RuleNotFoundException("not found"))
            .when(ruleService).delete(id, userId);

        mockMvc.perform(delete("/api/v1/classification-rules/" + id))
            .andExpect(status().isNotFound());
    }
}
