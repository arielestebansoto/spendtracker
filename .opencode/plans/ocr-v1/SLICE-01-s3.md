# SLICE-01: S3 Storage

## Goal

Replace local filesystem storage with AWS S3. Remove `LocalFileStorageService` and `StaticResourceConfig`.

---

## Tasks

### 1.1 Add AWS SDK Dependency

**File**: `backend/build.gradle`

```gradle
dependencies {
    // ... existing dependencies
    implementation 'software.amazon.awssdk:s3'
}
```

### 1.2 Create S3 Properties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/storage/S3Properties.java` (new)

```java
package com.arielsoto.spendtracker.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
    String bucketName,
    String region,
    String accessKeyId,
    String secretAccessKey
) {}
```

### 1.3 Create S3StorageService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/storage/S3StorageService.java` (new)

```java
package com.arielsoto.spendtracker.storage;

// Implements FileStorageService interface
// Uses S3Client to putObject, getObject, deleteObject
// Generates presigned URLs for GET /receipt endpoint
```

**Key methods**:
- `store(MultipartFile file, String key)` → upload to S3, return `StoredFile`
- `load(String key)` → return `StoredResource` with presigned URL
- `deleteDirectory(String directory)` → list and delete all objects with prefix
- `deleteFile(String key)` → delete single object

### 1.4 Update Application Configuration

**File**: `backend/src/main/resources/application.yml`

```yaml
app:
  storage:
    s3:
      bucket-name: ${S3_BUCKET_NAME}
      region: ${AWS_REGION}
      access-key-id: ${AWS_ACCESS_KEY_ID}
      secret-access-key: ${AWS_SECRET_ACCESS_KEY}
```

### 1.5 Update Docker Compose

**File**: `docker-compose.yml`

Add to backend environment:
```yaml
environment:
  # ... existing env vars
  AWS_REGION: ${AWS_REGION}
  AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
  AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}
  S3_BUCKET_NAME: ${S3_BUCKET_NAME}
```

### 1.6 Delete Local Storage Files

- Delete `backend/src/main/java/com/arielsoto/spendtracker/storage/LocalFileStorageService.java`
- Delete `backend/src/main/java/com/arielsoto/spendtracker/storage/StaticResourceConfig.java`

### 1.7 Update SpendReceiptStorageService

The existing `SpendReceiptStorageService` already depends on `FileStorageService` interface, so it will automatically use `S3StorageService` once the local implementation is removed.

---

## Testing

1. Unit test: Mock S3Client, verify `store()` calls `putObject` with correct key
2. Integration test: Use LocalStack or real S3 bucket
3. Manual test: Upload a file via curl, verify it appears in S3 bucket

---

## Rollback

- Revert `build.gradle` changes
- Restore `LocalFileStorageService.java` and `StaticResourceConfig.java`
- Remove S3 env vars from docker-compose
