# SLICE-02: Receipt Upload Endpoint

## Goal

Expose HTTP endpoints for uploading and retrieving receipt images. Wire to S3 via existing `SpendReceiptStorageService`.

---

## Storage Path Decision

**Pattern**: `spends/{userId}/{spendId}.{ext}`

- Ties receipt directly to the spend entity (no random UUID)
- Key is derivable from path variables — no need to look up `receiptKey` from DB before S3 operations
- Matches the REST URL pattern (`/api/v1/spends/{spendId}/receipt`)
- Enforces single receipt per spend (already enforced by `receiptKey` column)

---

## Tasks

### 2.1 Update SpendReceiptStorageService

- Change key from `spends/{userId}/{randomUUID}.{ext}` to `spends/{userId}/{spendId}.{ext}`
- Add `spendId` parameter to `store()` method
- Add `deleteFile(String key)` to `FileStorageService` interface and `S3StorageService`

### 2.2 Create ReceiptValidator

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptValidator.java` (new)

Static utility: validates file type (jpeg, png, webp, pdf) and size (max 10MB).

### 2.3 Create ReceiptUploadResponse DTO

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptUploadResponse.java` (new)

Record with `receiptUrl`, `contentType`, `size`.

### 2.4 Create ReceiptService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptService.java` (new)

Orchestrates validation, S3 storage, and Spend entity updates. Takes `spendId` to build deterministic key.

### 2.5 Create ReceiptController

**File**: `backend/src/main/java/com/arielsoto/spendtracker/receipt/ReceiptController.java` (new)

- `POST` — upload receipt (multipart)
- `GET` — download receipt (returns resource with content-type)
- `DELETE` — delete receipt (clears S3 + entity fields)

### 2.6 Update Spend.receiptUrl()

Change URL pattern from `/api/v1/spends/{id}/receipt` to remain consistent. No change needed — already correct.

---

## Testing — Unit Tests (no infrastructure needed)

### ReceiptValidatorTest (pure unit test)
- Empty file → ReceiptValidationException
- Null content type → ReceiptValidationException
- Disallowed type (e.g. text/plain) → ReceiptValidationException
- File exceeds 10MB → ReceiptValidationException
- Valid jpeg/png/webp/pdf → passes without exception

### ReceiptServiceTest (@ExtendWith(MockitoExtension.class))
- storeReceipt happy path → validates, stores via S3, updates Spend entity, returns ReceiptUploadResponse
- storeReceipt spend not found → throws RuntimeException
- loadReceipt happy path → returns Resource from S3
- loadReceipt no receipt key → throws RuntimeException
- deleteReceipt with receipt → deletes from S3, clears receiptKey/receiptContentType, saves
- deleteReceipt without receipt key → no-op (no S3 call, no save)

### ReceiptControllerTest (@ExtendWith(MockitoExtension.class) + standalone MockMvc)
- POST /api/v1/spends/{id}/receipt → 200 with receiptUrl/contentType/size
- GET /api/v1/spends/{id}/receipt → 200 with resource + correct Content-Type header
- DELETE /api/v1/spends/{id}/receipt → 204 no content

---

## Test File Implementations

### `backend/src/test/java/com/arielsoto/spendtracker/receipt/ReceiptValidatorTest.java`

```java
package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class ReceiptValidatorTest {

    @Test
    void emptyFileThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        ReceiptValidationException ex = assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
        assertEquals("File is empty", ex.getMessage());
    }

    @Test
    void nullContentTypeThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn(null);

        ReceiptValidationException ex = assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
        assertEquals("File type not allowed", ex.getMessage());
    }

    @Test
    void disallowedTypeThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("text/plain");

        assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
    }

    @Test
    void oversizedFileThrows() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(11L * 1024 * 1024);

        assertThrows(
            ReceiptValidationException.class,
            () -> ReceiptValidator.validate(file)
        );
    }

    @Test
    void validJpegPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }

    @Test
    void validPngPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }

    @Test
    void validWebpPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/webp");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }

    @Test
    void validPdfPasses() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(1024L);

        assertDoesNotThrow(() -> ReceiptValidator.validate(file));
    }
}
```

### `backend/src/test/java/com/arielsoto/spendtracker/receipt/ReceiptServiceTest.java`

```java
package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.storage.SpendReceiptStorageService;
import com.arielsoto.spendtracker.storage.StoredFile;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private SpendReceiptStorageService storageService;

    @Mock
    private SpendRepository spendRepository;

    @InjectMocks
    private ReceiptService receiptService;

    private UserApp buildUser() {
        return UserApp.builder()
            .id(UUID.randomUUID())
            .name("Test User")
            .oauthId("123")
            .build();
    }

    private Spend buildSpend(UserApp user) {
        return Spend.builder()
            .id(UUID.randomUUID())
            .user(user)
            .amount(BigDecimal.TEN)
            .spendDate(LocalDate.now())
            .build();
    }

    private MockMultipartFile buildFile() {
        return new MockMultipartFile(
            "receipt", "receipt.jpg", "image/jpeg",
            new byte[]{1, 2, 3}
        );
    }

    @Test
    void storeReceiptHappyPath() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);
        MockMultipartFile file = buildFile();

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        when(storageService.store(
            eq(user), eq(spend.getId()), any()
        )).thenReturn(new StoredFile(
            "spends/" + user.getId() + "/" + spend.getId() + ".jpg",
            "image/jpeg", 3L
        ));

        when(spendRepository.save(any(Spend.class)))
            .thenReturn(spend);

        ReceiptUploadResponse response = receiptService
            .storeReceipt(user, spend.getId(), file);

        assertNotNull(response);
        assertEquals("image/jpeg", response.contentType());
        assertEquals(3L, response.size());
        assertEquals(
            "/api/v1/spends/" + spend.getId() + "/receipt",
            response.receiptUrl()
        );
        verify(spendRepository).save(spend);
    }

    @Test
    void storeReceiptSpendNotFound() {
        UserApp user = buildUser();
        MockMultipartFile file = buildFile();

        when(spendRepository.findByIdAndUserId(
            any(), eq(user.getId())
        )).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> receiptService.storeReceipt(
                user, UUID.randomUUID(), file
            )
        );
    }

    @Test
    void loadReceiptHappyPath() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);
        spend.setReceiptKey("spends/user/file.jpg");

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        Resource resource = new ByteArrayResource(new byte[]{1});
        when(storageService.getResource("spends/user/file.jpg"))
            .thenReturn(resource);

        Resource result = receiptService.loadReceipt(
            user, spend.getId()
        );
        assertEquals(resource, result);
    }

    @Test
    void loadReceiptNoReceiptKey() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        assertThrows(RuntimeException.class,
            () -> receiptService.loadReceipt(user, spend.getId())
        );
    }

    @Test
    void deleteReceiptWithReceipt() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);
        spend.setReceiptKey("spends/user/file.jpg");
        spend.setReceiptContentType("image/jpeg");

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        receiptService.deleteReceipt(user, spend.getId());

        verify(storageService).deleteFile("spends/user/file.jpg");
        assertNull(spend.getReceiptKey());
        assertNull(spend.getReceiptContentType());
        verify(spendRepository).save(spend);
    }

    @Test
    void deleteReceiptWithoutReceiptKey() {
        UserApp user = buildUser();
        Spend spend = buildSpend(user);

        when(spendRepository.findByIdAndUserId(
            spend.getId(), user.getId()
        )).thenReturn(Optional.of(spend));

        receiptService.deleteReceipt(user, spend.getId());

        verify(storageService, never()).deleteFile(any());
        verify(spendRepository, never()).save(any());
    }
}
```

### `backend/src/test/java/com/arielsoto/spendtracker/receipt/ReceiptControllerTest.java`

Uses `@ExtendWith(MockitoExtension.class)` + standalone `MockMvc` (no Spring context needed).

```java
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
            receiptService, authenticatedUserService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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

        when(authenticatedUserService.getCurrentUser(any()))
            .thenReturn(user);
        when(receiptService.storeReceipt(eq(user), eq(spendId), any()))
            .thenReturn(new ReceiptUploadResponse(
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

        when(authenticatedUserService.getCurrentUser(any()))
            .thenReturn(user);
        when(receiptService.loadReceipt(user, spendId))
            .thenReturn(new ByteArrayResource(new byte[]{1, 2}));
        when(receiptService.getReceiptContentType(user, spendId))
            .thenReturn("image/png");

        mockMvc.perform(get("/api/v1/spends/" + spendId + "/receipt"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void deleteReceiptReturns204() throws Exception {
        UserApp user = buildUser();
        UUID spendId = UUID.randomUUID();

        when(authenticatedUserService.getCurrentUser(any()))
            .thenReturn(user);

        mockMvc.perform(delete("/api/v1/spends/" + spendId + "/receipt"))
            .andExpect(status().isNoContent());
    }
}
```

---

## Rollback

- Remove `ReceiptController.java`, `ReceiptService.java`, `ReceiptValidator.java`, `ReceiptUploadResponse.java`, `ReceiptValidationException.java`
- Remove test files: `ReceiptValidatorTest.java`, `ReceiptServiceTest.java`, `ReceiptControllerTest.java`
- Revert `SpendReceiptStorageService`, `FileStorageService`, `S3StorageService` changes
- No database changes to revert
