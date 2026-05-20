package com.arielsoto.spendtracker.spend;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.SpendListItemResponse;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/spends")
@RequiredArgsConstructor
public class SpendController {
    
    private final SpendService spendService;
    private final AuthenticatedUserService authenticatedUserService;
    
    @GetMapping("/{id}")
    public SpendDetailResponse findById(
        @PathVariable("id") UUID id,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.findByIdAndUserId(id, user.getId());
    }

    @GetMapping
    public Page<SpendListItemResponse> findAll(
        @PageableDefault(
            size = 20,
            sort = "spendDate",
            direction = Sort.Direction.DESC
        )
        Pageable pageable,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.findAllByUserId(
            user.getId(),
            pageable
        );
     }

    @PostMapping
    public CreateSpendResponse create(
        @RequestBody @Valid CreateSpendRequest request,
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.create(request, user);
    }
}
