package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SessionService sessionService;
    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String baseUrl;
    private final Duration validity;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                UserService userService,
                                SessionService sessionService,
                                SecurityProperties properties,
                                Clock clock,
                                @Autowired(required = false) JavaMailSender mailSender,
                                @Value("${mail.enabled:false}") boolean mailEnabled,
                                @Value("${app.base-url:http://localhost:7070}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.sessionService = sessionService;
        this.validity = properties.resetTokenValidity();
        this.clock = clock;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.baseUrl = baseUrl;
    }

    /**
     * Sends a reset link if the email belongs to an active user. Does nothing otherwise, so callers
     * give the same answer either way and do not reveal which emails exist.
     */
    public void requestReset(String email) {
        userRepository.findByEmail(email)
            .filter(User::isEnabled)
            .ifPresent(this::createResetToken);
    }

    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return findValid(token).isPresent();
    }

    /**
     * Changes the password, consumes every pending link of the user and ends their sessions.
     *
     * @throws InvalidResetTokenException if the token is unknown, used or expired
     * @throws IllegalArgumentException if the password breaks the password policy
     */
    public void resetPassword(String token, String rawPassword) {
        PasswordResetToken resetToken = findValid(token).orElseThrow(InvalidResetTokenException::new);
        User user = resetToken.getUser();
        userService.changePassword(user.getId(), rawPassword);
        tokenRepository.deleteByUser(user);
        sessionService.expireSessions(user.getUsername(), null);
    }

    private Optional<PasswordResetToken> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return tokenRepository.findByTokenHash(hash(token)).filter(t -> !t.isExpired(now));
    }

    private void createResetToken(User user) {
        // Only one pending link per user
        tokenRepository.deleteByUser(user);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new PasswordResetToken(hash(token), user, LocalDateTime.now(clock).plus(validity)));
        sendLink(user, baseUrl + "/reset-password?token=" + token);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    private void sendLink(User user, String resetUrl) {
        if (!mailEnabled) {
            // Development only: the link gives access to the account, never enable this in production
            log.warn("Mail disabled - password reset link for '{}': {}", user.getUsername(), resetUrl);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Réinitialisation de votre mot de passe - Swift Invoicing");
        message.setText("Bonjour " + user.getUsername() + ",\n\n"
            + "Cliquez sur le lien suivant pour réinitialiser votre mot de passe :\n"
            + resetUrl + "\n\n"
            + "Ce lien expire dans " + validity.toMinutes() + " minutes et ne peut servir qu'une fois.\n"
            + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.\n\n"
            + "Cordialement,\nSwift Invoicing");
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Could not send the password reset email to user '{}'", user.getUsername(), ex);
        }
    }
}
