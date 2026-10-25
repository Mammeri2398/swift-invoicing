package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-year credit note counters: same allocation as InvoiceSequenceRepository. */
public interface CreditNoteSequenceRepository extends JpaRepository<CreditNoteSequence, Integer> {

    @Modifying
    @Query(value = "INSERT INTO credit_note_sequence (credit_note_year, last_value) VALUES (:year, 0) ON CONFLICT DO NOTHING",
        nativeQuery = true)
    void createIfAbsent(@Param("year") int year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CreditNoteSequence s WHERE s.year = :year")
    CreditNoteSequence lockByYear(@Param("year") int year);
}
