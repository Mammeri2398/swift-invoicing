package com.swiftlabs.invoicing.security.repository;

import com.swiftlabs.invoicing.security.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    boolean existsByName(String name);
    Profile findByName(String name);
}
