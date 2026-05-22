package com.arielsoto.spendtracker.spend;

import java.util.UUID;

import org.springframework.core.io.Resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.spend.dto.CreateSpendRequest;
import com.arielsoto.spendtracker.spend.dto.CreateSpendResponse;
import com.arielsoto.spendtracker.spend.dto.SpendDetailResponse;
import com.arielsoto.spendtracker.spend.dto.SpendListItemResponse;
import com.arielsoto.spendtracker.spend.dto.UpdateSpendRequest;
import com.arielsoto.spendtracker.storage.StoredResource;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreateSpendResponse create(
        @RequestPart("data") @Valid CreateSpendRequest request,
        @RequestPart(value = "receipt", required = false)
        MultipartFile receipt,
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return spendService.create(
            request,
            receipt, 
            user
        );
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<Resource> receipt(
        @PathVariable("id") UUID id,
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        StoredResource storedResource =
            spendService.findReceiptResource(
                id,
                user.getId()
            );


        return ResponseEntity.ok()
            .contentType(
                MediaType.parseMediaType(
                    storedResource.contentType()
                )
            )
            .body(storedResource.resource());
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
