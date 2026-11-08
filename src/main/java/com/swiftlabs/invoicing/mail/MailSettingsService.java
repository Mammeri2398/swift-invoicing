package com.swiftlabs.invoicing.mail;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Paramètres > Email: the SMTP server, and a test message to check it. */
@Service
@Transactional(readOnly = true)
public class MailSettingsService {

    private static final Logger log = LoggerFactory.getLogger(MailSettingsService.class);

    private final MailSettingsRepository repository;
    private final MailGateway gateway;
    private final Messages messages;

    public MailSettingsService(MailSettingsRepository repository, MailGateway gateway, Messages messages) {
        this.repository = repository;
        this.gateway = gateway;
        this.messages = messages;
    }

    /** The saved settings, or empty ones (nothing saved yet). */
    public MailSettings current() {
        return repository.findById(MailSettings.ID).orElseGet(MailSettings::new);
    }

    @Transactional
    public MailSettings save(MailSettingsForm form) {
        if (form.isEnabled() && (form.getHost() == null || form.getHost().isBlank())) {
            throw BusinessException.onField("host", MessageProperties.MAIL_ERROR_HOST_REQUIRED);
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
            throw BusinessException.onField("fromAddress", MessageProperties.MAIL_ERROR_FROM_REQUIRED);
        }
        return repository.save(settings);
    }

    /**
     * Sends a short message to {@code to} (the logged-in user, in their language) with the saved settings;
     * the error message says what went wrong.
     */
    public void sendTest(String to) {
        if (!gateway.isEnabled()) {
            throw new BusinessException(MessageProperties.MAIL_ERROR_NOT_ENABLED);
        }
        if (to == null || to.isBlank()) {
            throw new BusinessException(MessageProperties.MAIL_ERROR_NO_ADDRESS);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(gateway.fromAddress());
        message.setTo(to);
        message.setSubject(messages.get(MessageProperties.MAIL_TEST_SUBJECT));
        message.setText(messages.get(MessageProperties.MAIL_TEST_BODY));
        try {
            gateway.sender().send(message);
        } catch (MailException ex) {
            log.warn("Test email to {} failed", to, ex);
            // The server's own answer (wrong password, refused address...) helps; a network error does not, and its
            // text would tell which hosts and ports of the network answer: it stays in the log
            if (isNetworkFailure(ex)) {
                throw new BusinessException(MessageProperties.MAIL_ERROR_UNREACHABLE);
            }
            Throwable cause = ex.getMostSpecificCause();
            throw new BusinessException(MessageProperties.MAIL_ERROR_SEND_FAILED,
                cause.getMessage() != null ? cause.getMessage().strip() : cause.getClass().getSimpleName());
        }
    }

    private static boolean isNetworkFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketException || cause instanceof SocketTimeoutException
                || cause instanceof UnknownHostException || cause instanceof SSLException) {
                return true;
            }
        }
        return false;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
