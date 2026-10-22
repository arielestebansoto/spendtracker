package com.arielsoto.spendtracker.receipt;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/spends/{spendId}/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ResponseEntity<Resource> getReceipt(
        @PathVariable("spendId") UUID spendId,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        Resource resource = receiptService.loadReceipt(
            user,
            spendId
        );

        String contentType = receiptService
            .getReceiptContentType(user, spendId);

        return ResponseEntity.ok()
            .contentType(
                MediaType.parseMediaType(
                    contentType != null
                        ? contentType
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE
                )
            )
            .body(resource);
    }
}
