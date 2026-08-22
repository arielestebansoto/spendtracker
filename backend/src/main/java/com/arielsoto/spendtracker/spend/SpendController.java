package com.arielsoto.spendtracker.spend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.DashboardSummaryResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.SpendListItemResponse;
import com.arielsoto.spendtracker.spend.dto.UpdateSpendRequest;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/spends")
@RequiredArgsConstructor
public class SpendController {
    
    private final SpendService spendService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(
        @RequestParam(name = "recentLimit", defaultValue = "5") int recentLimit,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        YearMonth now = YearMonth.now();

        return spendService.getDashboardSummary(
            user.getId(),
            now.atDay(1),
            now.atEndOfMonth(),
            recentLimit
        );
    }

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
        @RequestParam(name = "categoryId", required = false) UUID categoryId,
        @RequestParam(name = "description", required = false) String description,
        @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
        @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
        @RequestParam(name = "startDate", required = false) LocalDate startDate,
        @RequestParam(name = "endDate", required = false) LocalDate endDate,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.findByFilters(
            user.getId(), categoryId, description,
            minAmount, maxAmount, startDate, endDate,
            pageable
        );
     }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public CreateSpendResponse create(
        @RequestBody @Valid CreateSpendRequest request,
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.create(
            request,
            user
        );
    }

    @PutMapping("/{id}")
    public SpendDetailResponse update(
        @PathVariable("id") UUID id,
        @RequestBody @Valid UpdateSpendRequest request,
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.update(
            id,
            request,
            user.getId()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable("id") UUID id,
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        spendService.delete(
            id,
            user.getId()
        );
    }
}
