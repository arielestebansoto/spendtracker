package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.spend.ProcessingStage;
import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.spend.SpendStatus;

@ExtendWith(MockitoExtension.class)
class StuckSpendCleanupServiceTest {

    private static final Duration THRESHOLD = Duration.ofMinutes(5);
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 12, 0);

    @Mock
    private SpendRepository spendRepository;

    private StuckSpendCleanupService service;

    private final List<Spend> storedSpends = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new StuckSpendCleanupService(spendRepository, clock, THRESHOLD);

        // Fakes the query: Spends in one of the statuses whose status change is before the cutoff.
        lenient().when(spendRepository.findAllByStatusInAndStatusChangedAtBefore(any(), any()))
            .thenAnswer(call -> {
                Collection<SpendStatus> statuses = call.getArgument(0);
                LocalDateTime cutoff = call.getArgument(1);
                return storedSpends.stream()
                    .filter(spend -> statuses.contains(spend.getStatus()))
                    .filter(spend -> spend.getStatusChangedAt().isBefore(cutoff))
                    .toList();
            });

        // Fakes the conditional update on the stored Spends, so the tests see its effect.
        lenient().when(spendRepository.failIfStuck(any(), any(), any()))
            .thenAnswer(call -> {
                UUID id = call.getArgument(0);
                LocalDateTime cutoff = call.getArgument(1);
                Spend spend = storedSpends.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
                boolean inProgress = spend.getStatus() == SpendStatus.PENDING || spend.getStatus() == SpendStatus.PROCESSING;
                if (!inProgress || !spend.getStatusChangedAt().isBefore(cutoff)) {
                    return 0;
                }
                spend.setStatus(SpendStatus.FAILED);
                spend.setClaimToken(null);
                return 1;
            });
    }

    private Spend spendIn(SpendStatus status, Duration age) {
        Spend spend = Spend.builder().id(UUID.randomUUID()).status(status).build();
        spend.setStatusChangedAt(NOW.minus(age));
        storedSpends.add(spend);
        return spend;
    }

    @Test
    void failsPendingAndProcessingSpendsPastTheThreshold() {
        Spend pending = spendIn(SpendStatus.PENDING, Duration.ofMinutes(6));
        Spend processing = spendIn(SpendStatus.PROCESSING, Duration.ofHours(2));

        service.failStuckSpends();

        assertEquals(SpendStatus.FAILED, pending.getStatus());
        assertEquals(SpendStatus.FAILED, processing.getStatus());
    }

    @Test
    void failsEachSpendWithTheCutoffAndTheCurrentTime() {
        Spend pending = spendIn(SpendStatus.PENDING, Duration.ofMinutes(6));

        service.failStuckSpends();

        verify(spendRepository).failIfStuck(pending.getId(), NOW.minus(THRESHOLD), NOW);
    }

    @ParameterizedTest
    @EnumSource(ProcessingStage.class)
    void failsAStuckProcessingSpendInAnyStageAndClearsItsStage(ProcessingStage stage) {
        Spend processing = spendIn(SpendStatus.PROCESSING, Duration.ofHours(2));
        processing.setProcessingStage(stage);
        processing.setClaimToken(UUID.randomUUID());

        service.failStuckSpends();

        assertEquals(SpendStatus.FAILED, processing.getStatus());
        assertNull(processing.getProcessingStage());
        assertNull(processing.getClaimToken());
    }

    @Test
    void leavesSpendsWithinTheThresholdUntouched() {
        Spend pending = spendIn(SpendStatus.PENDING, Duration.ofMinutes(4));
        Spend processing = spendIn(SpendStatus.PROCESSING, Duration.ofMinutes(1));

        service.failStuckSpends();

        assertEquals(SpendStatus.PENDING, pending.getStatus());
        assertEquals(SpendStatus.PROCESSING, processing.getStatus());
        verify(spendRepository, never()).failIfStuck(any(), any(), any());
    }

    @Test
    void leavesProcessedAndFailedSpendsUntouchedEvenWhenOld() {
        Spend processed = spendIn(SpendStatus.PROCESSED, Duration.ofDays(30));
        Spend failed = spendIn(SpendStatus.FAILED, Duration.ofDays(30));

        service.failStuckSpends();

        assertEquals(SpendStatus.PROCESSED, processed.getStatus());
        assertEquals(SpendStatus.FAILED, failed.getStatus());
        assertEquals(NOW.minusDays(30), failed.getStatusChangedAt());
    }

    @Test
    void keepsGoingWhenAWorkerFinishedASpendAfterItWasFound() {
        Spend finishedMeanwhile = spendIn(SpendStatus.PROCESSING, Duration.ofMinutes(10));
        Spend stuck = spendIn(SpendStatus.PENDING, Duration.ofMinutes(10));
        // The conditional update finds the Spend already PROCESSED and changes nothing.
        doReturn(0).when(spendRepository).failIfStuck(eq(finishedMeanwhile.getId()), any(), any());

        service.failStuckSpends();

        assertEquals(SpendStatus.PROCESSING, finishedMeanwhile.getStatus());
        assertEquals(SpendStatus.FAILED, stuck.getStatus());
    }
}
