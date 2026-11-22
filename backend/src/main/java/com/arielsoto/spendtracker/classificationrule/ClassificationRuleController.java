package com.arielsoto.spendtracker.classificationrule;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(
        @RequestBody @Valid CreateRuleRequest request,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);
        return ruleService.create(request, user.getId());
    }

    @PutMapping("/{id}")
    public RuleResponse update(
        @PathVariable("id") UUID id,
        @RequestBody @Valid UpdateRuleRequest request,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);
        return ruleService.update(id, request, user.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable("id") UUID id,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);
        ruleService.delete(id, user.getId());
    }
}
