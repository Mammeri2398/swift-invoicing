package com.swiftlabs.invoicing.security.repository;

import com.swiftlabs.invoicing.security.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile p LEFT JOIN FETCH p.privileges WHERE u.username = ?1")
    Optional<User> findByUsernameWithPrivileges(String username);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.profile p JOIN p.privileges pr "
        + "WHERE u.enabled = true AND pr.code = ?1 AND p.id <> ?2")
    boolean existsEnabledUserWithPrivilegeOutsideProfile(String privilegeCode, Long profileId);
}
