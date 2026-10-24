package com.swiftlabs.invoicing.invoice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceSequenceRepository extends org.springframework.data.jpa.repository.JpaRepository<InvoiceSequence, Integer> {

    InvoiceSequence findByYear(Integer year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.year = :year")
    InvoiceSequence lockByYear(@Param("year") Integer year);
}
