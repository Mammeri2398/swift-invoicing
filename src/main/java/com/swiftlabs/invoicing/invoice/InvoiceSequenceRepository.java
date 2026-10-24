package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Per-year invoice counters. Numbers are allocated with {@link #createIfAbsent} then
 * {@link #lockByYear}, inside the issuing transaction (see InvoiceService#issue).
 */
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Integer> {

    /**
     * Creates the year's counter if it does not exist, atomically: when two transactions race,
     * the second waits for the first and then inserts nothing, instead of failing on the key.
     */
    @Modifying
    @Query(value = "INSERT INTO invoice_sequence (invoice_year, last_value) VALUES (:year, 0) ON CONFLICT DO NOTHING",
        nativeQuery = true)
    void createIfAbsent(@Param("year") int year);

    /**
     * Locks the year's counter (SELECT ... FOR UPDATE) until the end of the transaction.
     * Must be the first read of that row in the transaction, so the value is read after the lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.year = :year")
    InvoiceSequence lockByYear(@Param("year") int year);
}
