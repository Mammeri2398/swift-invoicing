package com.swiftlabs.invoicing.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A few values read on every page (the issuer's legal status, the licence status...), kept a short while instead
 * of being read again each time. Whoever changes a value evicts it; the time limit is only a safety net for changes
 * made elsewhere (a restored backup). A zero duration keeps nothing.
 */
public final class TimedCache<K, V> {

    private record Entry<V>(V value, long expiresAt) {
    }

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;

    public TimedCache(Duration ttl) {
        this.ttlNanos = ttl.toNanos();
    }

    /** The value kept for the key, or the one computed now (then kept). Null values are not kept. */
    public V get(K key, Supplier<V> compute) {
        long now = System.nanoTime();
        Entry<V> entry = entries.get(key);
        if (entry != null && now - entry.expiresAt() < 0) {
            return entry.value();
        }
        V value = compute.get();
        if (ttlNanos > 0 && value != null) {
            entries.put(key, new Entry<>(value, now + ttlNanos));
        }
        return value;
    }

    /**
     * Forgets the key now and again once the current transaction commits: a page read in between would otherwise
     * keep the old value until the time limit.
     */
    public void evict(K key) {
        entries.remove(key);
        afterCommit(() -> entries.remove(key));
    }

    /** Forgets everything (now and after the current transaction). */
    public void clear() {
        entries.clear();
        afterCommit(entries::clear);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
    }
}
