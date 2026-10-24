package com.swiftlabs.invoicing.security.service;

import com.swiftlabs.invoicing.security.domain.PasswordResetToken;
import com.swiftlabs.invoicing.security.domain.User;
import com.swiftlabs.invoicing.security.repository.PasswordResetTokenRepository;
import com.swiftlabs.invoicing.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String baseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                @Autowired(required = false) JavaMailSender mailSender,
                                @Value("${mail.enabled:false}") boolean mailEnabled,
                                @Value("${app.base-url:http://localhost:7070}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.baseUrl = baseUrl;
    }

    public void createResetTokenForUser(User user) {
        tokenRepository.deleteByUser(user);
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(60));
        tokenRepository.save(resetToken);

        String resetUrl = baseUrl + "/reset-password?token=" + token;
        if (mailEnabled) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("Réinitialisation de votre mot de passe - Swift Invoicing");
            message.setText("Bonjour " + user.getUsername() + ",\n\n"
                + "Cliquez sur le lien suivant pour réinitialiser votre mot de passe :\n"
                + resetUrl + "\n\n"
                + "Ce lien expire dans 60 minutes.\n\n"
                + "Cordialement,\nSwift Invoicing");
            try {
                mailSender.send(message);
            } catch (Exception ex) {
                System.err.println("Erreur envoi email: " + ex.getMessage());
            }
        } else {
            System.out.println("=== LIEN DE RÉINITIALISATION (mode console) ===");
            System.out.println("Utilisateur: " + user.getUsername() + " (" + user.getEmail() + ")");
            System.out.println("Lien: " + resetUrl);
            System.out.println("==========================================");
        }
    }

    public Optional<PasswordResetToken> findValidToken(String token) {
        Optional<PasswordResetToken> resetToken = tokenRepository.findByToken(token);
        if (resetToken.isPresent() && resetToken.get().getExpiryDate().isAfter(LocalDateTime.now())) {
            return resetToken;
        }
        return Optional.empty();
    }

    @Transactional
    public void useToken(String token) {
        tokenRepository.deleteByToken(token);
    }
}
