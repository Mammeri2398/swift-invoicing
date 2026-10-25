package com.swiftlabs.invoicing.mail;

import com.swiftlabs.invoicing.common.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Paramètres > Email: the SMTP server, and a test message to check it. */
@Service
@Transactional(readOnly = true)
public class MailSettingsService {

    private static final Logger log = LoggerFactory.getLogger(MailSettingsService.class);

    private final MailSettingsRepository repository;
    private final MailGateway gateway;

    public MailSettingsService(MailSettingsRepository repository, MailGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    /** The saved settings, or empty ones (nothing saved yet). */
    public MailSettings current() {
        return repository.findById(MailSettings.ID).orElseGet(MailSettings::new);
    }

    @Transactional
    public MailSettings save(MailSettingsForm form) {
        if (form.isEnabled() && (form.getHost() == null || form.getHost().isBlank())) {
            throw new BusinessException("host", "Indiquez le serveur d'envoi (SMTP).");
        }
        MailSettings settings = current();
        settings.setEnabled(form.isEnabled());
        settings.setHost(blankToNull(form.getHost()));
        settings.setPort(form.getPort());
        settings.setSecurity(form.getSecurity());
        settings.setUsername(blankToNull(form.getUsername()));
        // The password is never shown on the page: an empty field keeps it, unless the account is removed
        if (form.getPassword() != null && !form.getPassword().isEmpty()) {
            settings.setPassword(form.getPassword());
        } else if (settings.getUsername() == null) {
            settings.setPassword(null);
        }
        settings.setFromAddress(blankToNull(form.getFromAddress()));
        if (form.isEnabled() && settings.sender().isBlank()) {
            throw new BusinessException("fromAddress", "Indiquez l'adresse d'expédition (ou un compte, qui sert d'expéditeur).");
        }
        return repository.save(settings);
    }

    /** Sends a short message to {@code to} with the saved settings; the error message says what went wrong. */
    public void sendTest(String to) {
        if (!gateway.isEnabled()) {
            throw new BusinessException("Enregistrez d'abord un serveur d'envoi activé.");
        }
        if (to == null || to.isBlank()) {
            throw new BusinessException("Votre compte n'a pas d'adresse email : renseignez-la dans Mon compte.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(gateway.fromAddress());
        message.setTo(to);
        message.setSubject("Swift Invoicing : test d'envoi");
        message.setText("Bonjour,\n\nCe message confirme que l'envoi d'emails fonctionne.\n\nSwift Invoicing");
        try {
            gateway.sender().send(message);
        } catch (MailException ex) {
            log.warn("Test email to {} failed", to, ex);
            Throwable cause = ex.getMostSpecificCause();
            throw new BusinessException("L'envoi a échoué : " + (cause.getMessage() != null ? cause.getMessage().strip() : cause.getClass().getSimpleName())
                + ". Vérifiez le serveur, le port, la sécurité et le compte.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
