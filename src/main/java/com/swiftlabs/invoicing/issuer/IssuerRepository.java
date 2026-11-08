package com.swiftlabs.invoicing.issuer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IssuerRepository extends JpaRepository<Issuer, Long> {

    /** The legal status and the TVA mention alone, without the logo. */
    @Query("SELECT new com.swiftlabs.invoicing.issuer.IssuerProfile(i.companyType, i.vatMention) FROM Issuer i WHERE i.id = ?1")
    Optional<IssuerProfile> findProfileById(Long id);
}
