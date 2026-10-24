package com.swiftlabs.invoicing.security.repository;

import com.swiftlabs.invoicing.security.domain.PasswordResetToken;
import com.swiftlabs.invoicing.security.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
    void deleteByToken(String token);
}
