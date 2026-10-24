package com.swiftlabs.invoicing.security.repository;

import com.swiftlabs.invoicing.security.domain.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    boolean existsByCode(String code);
    Privilege findByCode(String code);
}
