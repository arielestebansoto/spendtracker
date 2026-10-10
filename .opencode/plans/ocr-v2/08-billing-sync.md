# Slice 8: AWS Cost Explorer Billing Sync Job

## Goal
Create a background job that queries AWS Cost Explorer and updates the global usage table.

## Files
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/CostExplorerConfig.java`
- **New:** `backend/src/main/java/com/arielsoto/spendtracker/aiusage/AwsBillingSyncJob.java`

## Dependencies
- Slice 8-bis complete. This job calls `insertIfAbsent` and `setCountersFromBilling` on
  `AiUsageGlobalRepository`; it must not read or mutate the `AiUsageGlobal` entity.

## Details

### CostExplorerConfig.java
```java
@Configuration
public class CostExplorerConfig {
    @Bean
    public CostExplorerClient costExplorerClient() {
        return CostExplorerClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
}
```

### AwsBillingSyncJob.java
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AwsBillingSyncJob {

    private final AiUsageGlobalRepository globalRepo;
    private final CostExplorerClient costExplorerClient;

    @Scheduled(cron = "0 0 */6 * * *")
    public void syncGlobalUsage() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate tomorrow = today.plusDays(1);

        log.info("billing_sync_start month={}", monthStart);

        globalRepo.insertIfAbsent(monthStart);

        try {
            long expensePages = queryTextractUsage(
                "SyncExpensePagesProcessed", monthStart, tomorrow);
            long textPages = queryTextractUsage(
                "SyncTextPagesProcessed", monthStart, tomorrow)
                + queryTextractUsage("AsyncTextPagesProcessed", monthStart, tomorrow);

            long inputTokens = queryBedrockTokens("input-tokens", monthStart, tomorrow);
            long outputTokens = queryBedrockTokens("output-tokens", monthStart, tomorrow);

            globalRepo.setCountersFromBilling(
                monthStart,
                (int) expensePages,
                (int) textPages,
                inputTokens,
                outputTokens);

            log.info("billing_sync_complete month={} expensePages={} textPages={} "
                + "inputTokens={} outputTokens={}",
                monthStart, expensePages, textPages, inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("billing_sync_failed month={}", monthStart, e);
        }
    }

    private long queryTextractUsage(String usageTypeSuffix,
            LocalDate start, LocalDate end) {
        Expression filter = Expression.builder()
            .dimensions(DimensionValues.builder()
                .key(DimensionKey.SERVICE)
                .values("Amazon Textract")
                .build())
            .build();

        GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
            .timePeriod(DateInterval.builder()
                .start(start.toString()).end(end.toString()).build())
            .granularity(Granularity.DAILY)
            .metrics("UsageQuantity")
            .filter(filter)
            .groupBy(GroupDefinition.builder()
                .type(GroupDefinitionType.DIMENSION)
                .key(DimensionKey.USAGE_TYPE)
                .build())
            .build();

        long total = 0;
        for (ResultByTime result : costExplorerClient.getCostAndUsage(request).resultsByTime()) {
            for (Group group : result.groups()) {
                if (group.keys().stream().anyMatch(k -> k.contains(usageTypeSuffix))) {
                    total += new BigDecimal(
                        group.metrics().get("UsageQuantity").amount()).longValue();
                }
            }
        }
        return total;
    }

    private long queryBedrockTokens(String tokenSuffix,
            LocalDate start, LocalDate end) {
        Expression filter = Expression.builder()
            .dimensions(DimensionValues.builder()
                .key(DimensionKey.SERVICE)
                .values("Amazon Bedrock")
                .build())
            .build();

        GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
            .timePeriod(DateInterval.builder()
                .start(start.toString()).end(end.toString()).build())
            .granularity(Granularity.DAILY)
            .metrics("UsageQuantity")
            .filter(filter)
            .groupBy(GroupDefinition.builder()
                .type(GroupDefinitionType.DIMENSION)
                .key(DimensionKey.USAGE_TYPE)
                .build())
            .build();

        long total = 0;
        for (ResultByTime result : costExplorerClient.getCostAndUsage(request).resultsByTime()) {
            for (Group group : result.groups()) {
                if (group.keys().stream().anyMatch(k -> k.endsWith(tokenSuffix))) {
                    total += new BigDecimal(
                        group.metrics().get("UsageQuantity").amount()).longValue();
                }
            }
        }
        return total;
    }
}
```

## Verify
- `./gradlew compileJava` passes
