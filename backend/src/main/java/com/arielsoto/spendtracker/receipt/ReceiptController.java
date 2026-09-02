package com.arielsoto.spendtracker.receipt;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/spends/{spendId}/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptUploadResponse upload(
        @PathVariable UUID spendId,
        @RequestParam("receipt") MultipartFile file,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return receiptService.storeReceipt(
            user,
            spendId,
            file
        );
    }

    @GetMapping
    public ResponseEntity<Resource> getReceipt(
        @PathVariable UUID spendId,
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
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment"
            )
            .body(resource);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteReceipt(
        @PathVariable UUID spendId,
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        receiptService.deleteReceipt(user, spendId);

        return ResponseEntity.noContent().build();
    }
}
