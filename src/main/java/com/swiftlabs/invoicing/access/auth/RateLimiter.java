package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.config.SecurityProperties.Limit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window request counter per key (rule + client IP), kept in memory.
 * Enough for a single instance; several instances would need a shared store (e.g. Redis).
 */
@Component
public class RateLimiter {

    /** Above this many tracked keys, expired windows are purged so memory stays bounded. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Result of a request: allowed, or refused with the seconds to wait before retrying. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /** Counts one request for {@code key} and tells whether it is within {@code limit}. */
    public Decision tryAcquire(String key, Limit limit) {
        Instant now = clock.instant();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
        Window window = windows.compute(key, (k, current) ->
            current == null || current.isExpired(now) ? new Window(now.plus(limit.window()), 1) : current.increment());
        if (window.count() <= limit.requests()) {
            return new Decision(true, 0);
        }
        long wait = Math.max(1, Duration.between(now, window.end()).toSeconds());
        return new Decision(false, wait);
    }

    private record Window(Instant end, int count) {

        boolean isExpired(Instant now) {
            return !now.isBefore(end);
        }

        Window increment() {
            return new Window(end, count + 1);
        }
    }
}
