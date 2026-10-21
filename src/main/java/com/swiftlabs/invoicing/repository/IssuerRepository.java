package com.swiftlabs.invoicing.repository;

import com.swiftlabs.invoicing.domain.Issuer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuerRepository extends JpaRepository<Issuer, Long> {
}
