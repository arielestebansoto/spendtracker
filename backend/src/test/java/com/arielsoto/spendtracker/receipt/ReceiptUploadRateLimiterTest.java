package com.arielsoto.spendtracker.receipt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class ReceiptUploadRateLimiterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);

    @Mock
    private SpendRepository spendRepository;

    private final UserApp user = UserApp.builder().id(UUID.randomUUID()).name("Test").oauthId("1").build();

    private ReceiptUploadRateLimiter limiter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        limiter = new ReceiptUploadRateLimiter(spendRepository, new ReceiptUploadLimitProperties(5, 30), clock);
    }

    private void uploads(long lastMinute, long lastDay) {
        lenient().when(spendRepository.countByUserIdAndReceiptKeyIsNotNullAndCreatedAtAfter(
            eq(user.getId()), eq(NOW.minusMinutes(1)))).thenReturn(lastMinute);
        lenient().when(spendRepository.countByUserIdAndReceiptKeyIsNotNullAndCreatedAtAfter(
            eq(user.getId()), eq(NOW.minusDays(1)))).thenReturn(lastDay);
    }

    @Test
    void allowsAnUploadUnderBothLimits() {
        uploads(4, 29);

        assertDoesNotThrow(() -> limiter.check(user));
    }

    @Test
    void rejectsWhenTheMinuteLimitIsReached() {
        uploads(5, 5);

        ReceiptUploadRateLimitException ex =
            assertThrows(ReceiptUploadRateLimitException.class, () -> limiter.check(user));
        assertEquals("minute", ex.getWindow());
    }

    @Test
    void rejectsWhenTheDayLimitIsReached() {
        uploads(0, 30);

        ReceiptUploadRateLimitException ex =
            assertThrows(ReceiptUploadRateLimitException.class, () -> limiter.check(user));
        assertEquals("day", ex.getWindow());
    }
}
