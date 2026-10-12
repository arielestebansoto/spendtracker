package com.arielsoto.spendtracker.classificationrule;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.classificationrule.dto.CreateRuleRequest;
import com.arielsoto.spendtracker.classificationrule.dto.RuleResponse;
import com.arielsoto.spendtracker.classificationrule.dto.RulesListResponse;
import com.arielsoto.spendtracker.classificationrule.dto.UpdateRuleRequest;
import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/classification-rules")
@RequiredArgsConstructor
public class ClassificationRuleController {

    private final ClassificationRuleService ruleService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public RulesListResponse list(OAuth2AuthenticationToken authentication) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);
        return ruleService.list(user.getId());
    }

    @PostMapping
    public ResponseEntity<?> create(
        @RequestBody @Valid CreateRuleRequest request,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);

        try {
            RuleResponse created = ruleService.create(request, user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (RuleLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        } catch (InvalidCategoryException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
        @PathVariable("id") UUID id,
        @RequestBody @Valid UpdateRuleRequest request,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);

        try {
            return ResponseEntity.ok(ruleService.update(id, request, user.getId()));
        } catch (RuleNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (InvalidCategoryException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
        @PathVariable("id") UUID id,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);

        try {
            ruleService.delete(id, user.getId());
            return ResponseEntity.noContent().build();
        } catch (RuleNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
