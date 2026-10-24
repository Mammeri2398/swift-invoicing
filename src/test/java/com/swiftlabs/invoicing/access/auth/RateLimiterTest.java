package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.config.SecurityProperties.Limit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    /** A clock the test moves forward by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final RateLimiter limiter = new RateLimiter(clock);
    private final Limit threePerMinute = new Limit(3, Duration.ofMinutes(1));

    @Test
    void allowsTheLimitThenRefusesWithTheTimeLeft() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("login:1.2.3.4", threePerMinute).allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));
        RateLimiter.Decision refused = limiter.tryAcquire("login:1.2.3.4", threePerMinute);
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void aNewWindowStartsOnceTheCurrentOneIsOver() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("login:1.2.3.4", threePerMinute);
        }
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("login:1.2.3.4", threePerMinute).allowed()).isTrue();
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("login:1.2.3.4", threePerMinute);
        }
        assertThat(limiter.tryAcquire("login:1.2.3.4", threePerMinute).allowed()).isFalse();
        assertThat(limiter.tryAcquire("login:5.6.7.8", threePerMinute).allowed()).isTrue();
        assertThat(limiter.tryAcquire("lookup:1.2.3.4", threePerMinute).allowed()).isTrue();
    }
}
