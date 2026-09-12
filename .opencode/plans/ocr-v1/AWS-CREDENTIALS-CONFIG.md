# AWS Unified Credentials Configuration

## Goal

Make S3, Textract, and Bedrock clients all use the same explicit `StaticCredentialsProvider` instead of relying on the implicit default credential chain. This ensures consistency, clarity, and no surprises when credentials are missing.

---

## Problem

- **S3** (`S3StorageConfig.java`) — uses explicit `StaticCredentialsProvider` from `S3Properties` ✓
- **Textract** (`TextractOcrService.java:22`) — `TextractClient.create()` uses default credential chain ✗
- **Bedrock** (`BedrockClassificationService.java:31`) — `BedrockRuntimeClient.create()` uses default credential chain ✗

---

## Tasks

### 1. Create Shared AWS Properties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/config/AwsProperties.java` (new)

```java
package com.arielsoto.spendtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws")
public record AwsProperties(
    String region,
    String accessKeyId,
    String secretAccessKey
) {}
```

### 2. Create Shared AWS Config

**File**: `backend/src/main/java/com/arielsoto/spendtracker/config/AwsConfig.java` (new)

```java
package com.arielsoto.spendtracker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
class AwsConfig {

    @Bean
    StaticCredentialsProvider awsCredentialsProvider(AwsProperties properties) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(
                properties.accessKeyId(),
                properties.secretAccessKey()
            )
        );
    }

    @Bean
    Region awsRegion(AwsProperties properties) {
        return Region.of(properties.region());
    }
}
```

### 3. Create Textract Config

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/TextractConfig.java` (new)

```java
package com.arielsoto.spendtracker.ocr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
class TextractConfig {

    @Bean
    TextractClient textractClient(StaticCredentialsProvider awsCredentialsProvider, Region awsRegion) {
        return TextractClient.builder()
            .region(awsRegion)
            .credentialsProvider(awsCredentialsProvider)
            .build();
    }
}
```

### 4. Create Bedrock Config

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockConfig.java` (new)

```java
package com.arielsoto.spendtracker.classifier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
class BedrockConfig {

    @Bean
    BedrockRuntimeClient bedrockRuntimeClient(StaticCredentialsProvider awsCredentialsProvider, Region awsRegion) {
        return BedrockRuntimeClient.builder()
            .region(awsRegion)
            .credentialsProvider(awsCredentialsProvider)
            .build();
    }
}
```

### 5. Simplify S3Properties

**File**: `backend/src/main/java/com/arielsoto/spendtracker/storage/S3Properties.java`

Remove `region`, `accessKeyId`, `secretAccessKey` — keep only `bucketName`:

```java
package com.arielsoto.spendtracker.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
    String bucketName
) {}
```

### 6. Refactor S3StorageConfig

**File**: `backend/src/main/java/com/arielsoto/spendtracker/storage/S3StorageConfig.java`

Inject shared credentials instead of building from `S3Properties`:

```java
package com.arielsoto.spendtracker.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@ConditionalOnProperty(
    prefix = "app.storage.s3",
    name = "bucket-name",
    matchIfMissing = false
)
class S3StorageConfig {

    @Bean
    S3Client s3Client(S3Properties properties, StaticCredentialsProvider awsCredentialsProvider, Region awsRegion) {
        return S3Client.builder()
            .region(awsRegion)
            .credentialsProvider(awsCredentialsProvider)
            .build();
    }
}
```

### 7. Refactor TextractOcrService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/ocr/TextractOcrService.java`

Inject `TextractClient` via constructor instead of creating it:

```java
public TextractOcrService(TextractClient textractClient) {
    this.textractClient = textractClient;
}
```

Remove `this.textractClient = TextractClient.create();`

### 8. Refactor BedrockClassificationService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/classifier/BedrockClassificationService.java`

Inject `BedrockRuntimeClient` via constructor instead of creating it:

```java
public BedrockClassificationService(BedrockRuntimeClient bedrockClient, BedrockProperties properties) {
    this.properties = properties;
    this.bedrockClient = bedrockClient;
    this.objectMapper = new ObjectMapper();
}
```

Remove `this.bedrockClient = BedrockRuntimeClient.create();`

### 9. Update application.yml

**File**: `backend/src/main/resources/application.yml`

Add `app.aws` properties and simplify `app.storage.s3`:

```yaml
app:
  aws:
    region: ${AWS_REGION:}
    access-key-id: ${AWS_ACCESS_KEY_ID:}
    secret-access-key: ${AWS_SECRET_ACCESS_KEY:}
  storage:
    s3:
      bucket-name: ${S3_BUCKET_NAME:}
  bedrock:
    model-id: ${BEDROCK_MODEL_ID:amazon.titan-text-lite-v1}
```

Remove `region`, `access-key-id`, `secret-access-key` from `app.storage.s3`.

---

## No Changes Needed

- `docker-compose.yml` — env vars stay the same (`AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
- `.env` — stays the same
- `build.gradle` — no new dependencies needed

---

## Dependency Flow

```
application.yml (env vars: AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
  └─ AwsProperties (config/AwsProperties.java)
       └─ AwsConfig (config/AwsConfig.java)
            ├─ StaticCredentialsProvider bean
            └─ Region bean
                 ├─ TextractConfig → TextractClient bean
                 ├─ BedrockConfig → BedrockRuntimeClient bean
                 └─ S3StorageConfig → S3Client bean
```

---

## Verification

1. Run `./gradlew bootRun` — app should start without errors
2. Verify all three clients are wired: check logs for bean creation
3. Test with missing env vars — app should fail fast at startup (not silently)
4. Run existing tests: `./gradlew test`

---

## Rollback

- Delete new files: `config/AwsProperties.java`, `config/AwsConfig.java`, `ocr/TextractConfig.java`, `classifier/BedrockConfig.java`
- Restore `S3Properties.java` (add back `region`, `accessKeyId`, `secretAccessKey`)
- Restore `S3StorageConfig.java` (revert to building credentials from `S3Properties`)
- Restore `TextractOcrService.java` (revert to `TextractClient.create()`)
- Restore `BedrockClassificationService.java` (revert to `BedrockRuntimeClient.create()`)
- Restore `application.yml` (revert to original `app.storage.s3` block)
