package com.swiftlabs.invoicing.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Where emails go: the server set in Paramètres > Email when there is one (on-premise installs),
 * otherwise the MAIL_* settings of the deployment (spring.mail.*, mail.enabled, mail.from).
 */
@Component
public class MailGateway {

    private static final int TIMEOUT_MS = 10_000;

    private final MailSettingsRepository settingsRepository;
    private final JavaMailSender deploymentSender;
    private final boolean deploymentEnabled;
    private final String deploymentFrom;

    public MailGateway(MailSettingsRepository settingsRepository,
                       @Autowired(required = false) JavaMailSender deploymentSender,
                       @Value("${mail.enabled:false}") boolean deploymentEnabled,
                       @Value("${mail.from:}") String mailFrom,
                       @Value("${spring.mail.username:}") String mailUsername) {
        this.settingsRepository = settingsRepository;
        this.deploymentSender = deploymentSender;
        this.deploymentEnabled = deploymentEnabled && deploymentSender != null;
        // Most SMTP servers only accept their own account as sender
        this.deploymentFrom = !mailFrom.isBlank() ? mailFrom.trim() : mailUsername.trim();
    }

    /** False when no server is configured: pages then say how to set one up. */
    public boolean isEnabled() {
        return settings()
            .map(s -> s.isEnabled() && s.getHost() != null && !s.getHost().isBlank() && !s.sender().isBlank())
            .orElse(deploymentEnabled && !deploymentFrom.isEmpty());
    }

    /** A server is set up, sender address or not (password reset links: the account is the sender). */
    public boolean isServerConfigured() {
        return settings()
            .map(s -> s.isEnabled() && s.getHost() != null && !s.getHost().isBlank())
            .orElse(deploymentEnabled);
    }

    public String fromAddress() {
        return settings().map(MailSettings::sender).orElse(deploymentFrom);
    }

    /** The sender to use right now (settings saved in the application apply without a restart). */
    public JavaMailSender sender() {
        return settings().<JavaMailSender>map(MailGateway::build).orElse(deploymentSender);
    }

    private Optional<MailSettings> settings() {
        return settingsRepository.findById(MailSettings.ID);
    }

    static JavaMailSenderImpl build(MailSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        MailSecurity security = settings.getSecurity() != null ? settings.getSecurity() : MailSecurity.STARTTLS;
        sender.setPort(settings.getPort() != null ? settings.getPort() : switch (security) {
            case STARTTLS -> 587;
            case SSL -> 465;
            case NONE -> 25;
        });
        boolean authenticated = settings.getUsername() != null && !settings.getUsername().isBlank();
        if (authenticated) {
            sender.setUsername(settings.getUsername());
            sender.setPassword(settings.getPassword());
        }
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(authenticated));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MS));
        properties.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MS));
        properties.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MS));
        if (security == MailSecurity.STARTTLS) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        } else if (security == MailSecurity.SSL) {
            properties.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }
}
