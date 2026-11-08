package com.swiftlabs.invoicing.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TimedCacheTest {

    @Test
    void aValueIsComputedOnceThenKeptUntilEvicted() {
        TimedCache<String, Integer> cache = new TimedCache<>(Duration.ofMinutes(1));
        AtomicInteger reads = new AtomicInteger();

        assertThat(cache.get("a", reads::incrementAndGet)).isEqualTo(1);
        assertThat(cache.get("a", reads::incrementAndGet)).isEqualTo(1);
        assertThat(cache.get("b", reads::incrementAndGet)).isEqualTo(2);

        cache.evict("a");
        assertThat(cache.get("a", reads::incrementAndGet)).isEqualTo(3);
        cache.clear();
        assertThat(cache.get("b", reads::incrementAndGet)).isEqualTo(4);
    }

    @Test
    void expiredOrDisabledValuesAreReadAgain() throws InterruptedException {
        TimedCache<String, Integer> shortLived = new TimedCache<>(Duration.ofMillis(20));
        AtomicInteger reads = new AtomicInteger();
        shortLived.get("a", reads::incrementAndGet);
        Thread.sleep(40);
        assertThat(shortLived.get("a", reads::incrementAndGet)).isEqualTo(2);

        TimedCache<String, Integer> disabled = new TimedCache<>(Duration.ZERO);
        disabled.get("a", reads::incrementAndGet);
        assertThat(disabled.get("a", reads::incrementAndGet)).isEqualTo(4);
    }
}
