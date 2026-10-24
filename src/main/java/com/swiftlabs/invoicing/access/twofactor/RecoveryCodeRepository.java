package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.user.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    Optional<RecoveryCode> findByUserAndCodeHashAndUsedAtIsNull(User user, String codeHash);

    long countByUserAndUsedAtIsNull(User user);

    void deleteByUser(User user);
}
