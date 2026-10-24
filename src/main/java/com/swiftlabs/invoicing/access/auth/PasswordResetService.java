package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.access.user.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_VALIDITY_MINUTES = 60;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                UserService userService,
                                @Autowired(required = false) JavaMailSender mailSender,
                                @Value("${mail.enabled:false}") boolean mailEnabled,
                                @Value("${app.base-url:http://localhost:7070}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.baseUrl = baseUrl;
    }

    /**
     * Sends a reset link if the email belongs to a user. Does nothing otherwise, so callers
     * give the same answer either way and do not reveal which emails exist.
     */
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(this::createResetToken);
    }

    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return tokenRepository.findByToken(token)
            .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
            .isPresent();
    }

    /**
     * Changes the password and consumes the token in one transaction.
     *
     * @throws InvalidResetTokenException if the token is unknown or expired
     * @throws IllegalArgumentException if the password breaks the password policy
     */
    public void resetPassword(String token, String rawPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
            .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
            .orElseThrow(InvalidResetTokenException::new);
        userService.changePassword(resetToken.getUser().getId(), rawPassword);
        tokenRepository.delete(resetToken);
    }

    private void createResetToken(User user) {
        tokenRepository.deleteByUser(user);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES));
        tokenRepository.save(resetToken);
        sendLink(user, baseUrl + "/reset-password?token=" + resetToken.getToken());
    }

    private void sendLink(User user, String resetUrl) {
        if (!mailEnabled) {
            log.info("Mail disabled - password reset link for {} ({}): {}", user.getUsername(), user.getEmail(), resetUrl);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("Réinitialisation de votre mot de passe - Swift Invoicing");
        message.setText("Bonjour " + user.getUsername() + ",\n\n"
            + "Cliquez sur le lien suivant pour réinitialiser votre mot de passe :\n"
            + resetUrl + "\n\n"
            + "Ce lien expire dans " + TOKEN_VALIDITY_MINUTES + " minutes.\n\n"
            + "Cordialement,\nSwift Invoicing");
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Could not send the password reset email to {}", user.getEmail(), ex);
        }
    }
}
