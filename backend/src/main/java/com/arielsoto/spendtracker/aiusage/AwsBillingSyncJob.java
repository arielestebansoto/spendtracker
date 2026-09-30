package com.arielsoto.spendtracker.aiusage;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.DimensionValues;
import software.amazon.awssdk.services.costexplorer.model.Expression;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsBillingSyncJob {

    private static final String USAGE_QUANTITY = "UsageQuantity";
    private static final String TEXTRACT = "Amazon Textract";
    private static final String BEDROCK = "Amazon Bedrock";

    // Textract bills these per operation; the app calls AnalyzeExpense and
    // DetectDocumentText, both synchronously. There is no async Textract usage.
    private static final String SYNC_EXPENSE_PAGES = "SyncExpensePagesProcessed";
    private static final String SYNC_TEXT_PAGES = "SyncTextPagesProcessed";

    // Bedrock bills per model, e.g. "USE1-NovaMicro-input-tokens".
    private static final String INPUT_TOKENS = "-input-tokens";
    private static final String OUTPUT_TOKENS = "-output-tokens";

    private final AiUsageGlobalRepository globalRepository;
    private final CostExplorerClient costExplorerClient;

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void syncGlobalUsage() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate tomorrow = today.plusDays(1);

        log.info("billing_sync_start month={}", monthStart);

        globalRepository.insertIfAbsent(monthStart);

        try {
            Map<String, Long> textract = usageByType(TEXTRACT, monthStart, tomorrow);
            Map<String, Long> bedrock = usageByType(BEDROCK, monthStart, tomorrow);

            long expensePages = sumMatching(textract, key -> key.contains(SYNC_EXPENSE_PAGES));
            long textPages = sumMatching(textract, key -> key.contains(SYNC_TEXT_PAGES));
            long inputTokens = sumMatching(bedrock, key -> key.endsWith(INPUT_TOKENS));
            long outputTokens = sumMatching(bedrock, key -> key.endsWith(OUTPUT_TOKENS));

            // setCountersFromBilling writes absolutes, so writing 0 for a metric we
            // failed to recognise would silently disable that limit for the month.
            // Only bail when a service clearly has usage and none of it was matched;
            // an empty result legitimately means no usage yet this month.
            boolean textractUnmatched = !textract.isEmpty() && expensePages == 0 && textPages == 0;
            boolean bedrockUnmatched = !bedrock.isEmpty() && inputTokens == 0 && outputTokens == 0;

            if (textractUnmatched || bedrockUnmatched) {
                log.error("billing_sync_skipped month={} reason=unrecognized_usage_types "
                        + "textract={} bedrock={}",
                    monthStart, textract.keySet(), bedrock.keySet());
                return;
            }

            globalRepository.setCountersFromBilling(
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

    private Map<String, Long> usageByType(String service, LocalDate start, LocalDate end) {
        GetCostAndUsageRequest request = GetCostAndUsageRequest.builder()
            .timePeriod(DateInterval.builder()
                .start(start.toString()).end(end.toString()).build())
            .granularity(Granularity.DAILY)
            .metrics(USAGE_QUANTITY)
            .filter(Expression.builder()
                .dimensions(DimensionValues.builder()
                    .key(Dimension.SERVICE)
                    .values(service)
                    .build())
                .build())
            .groupBy(GroupDefinition.builder()
                .type(GroupDefinitionType.DIMENSION)
                .key(Dimension.USAGE_TYPE.toString())
                .build())
            .build();

        Map<String, Long> quantityByUsageType = new HashMap<>();
        for (ResultByTime result : costExplorerClient.getCostAndUsage(request).resultsByTime()) {
            for (Group group : result.groups()) {
                long amount = new BigDecimal(
                    group.metrics().get(USAGE_QUANTITY).amount()).longValue();
                for (String usageType : group.keys()) {
                    quantityByUsageType.merge(usageType, amount, Long::sum);
                }
            }
        }
        return quantityByUsageType;
    }

    private long sumMatching(Map<String, Long> quantityByUsageType,
            Predicate<String> matchesUsageType) {
        return quantityByUsageType.entrySet().stream()
            .filter(entry -> matchesUsageType.test(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
    }
}
