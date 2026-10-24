package com.swiftlabs.invoicing.access.privilege;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    boolean existsByCode(String code);
    Privilege findByCode(String code);
}
