package com.swiftlabs.invoicing.access.auth;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.access.user.UserService;
import com.swiftlabs.invoicing.common.Endpoints;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.Messages;
import com.swiftlabs.invoicing.company.Company;
import com.swiftlabs.invoicing.company.CompanyAccess;
import com.swiftlabs.invoicing.company.CompanyAccessService;
import com.swiftlabs.invoicing.company.CompanySchemas;
import com.swiftlabs.invoicing.company.CompanyWork;
import com.swiftlabs.invoicing.config.SecurityProperties;
import com.swiftlabs.invoicing.mail.MailGateway;
import com.swiftlabs.invoicing.setup.NetworkAddresses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
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
    private final MailGateway mailGateway;
    private final CompanyAccessService accessService;
    private final CompanyWork companyWork;
    private final Messages messages;
    private final String baseUrl;
    private final NetworkAddresses networkAddresses;
    private final boolean logResetLinks;
    private final Duration validity;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                UserService userService,
                                SessionService sessionService,
                                SecurityProperties properties,
                                Clock clock,
                                MailGateway mailGateway,
                                CompanyAccessService accessService,
                                CompanyWork companyWork,
                                Messages messages,
                                NetworkAddresses networkAddresses,
                                @Value("${app.base-url:}") String baseUrl,
                                @Value("${app.security.log-reset-links:false}") boolean logResetLinks) {
        this.logResetLinks = logResetLinks;
        this.networkAddresses = networkAddresses;
        this.messages = messages;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.sessionService = sessionService;
        this.validity = properties.resetTokenValidity();
        this.clock = clock;
        this.mailGateway = mailGateway;
        this.accessService = accessService;
        this.companyWork = companyWork;
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
     * @throws com.swiftlabs.invoicing.common.BusinessException if the password breaks the password policy
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
        sendLink(user, baseUrl() + Endpoints.RESET_PASSWORD + "?token=" + token);
    }

    /**
     * The application's address for the link: app.base-url when set (a public server), otherwise the
     * address the user typed to reach it (on-premise: the PC's name on the local network).
     */
    private String baseUrl() {
        if (!baseUrl.isBlank()) {
            return baseUrl;
        }
        String contextPath = ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath();
        String origin = ServletUriComponentsBuilder.fromCurrentContextPath().replacePath(null).toUriString();
        return linkOrigin(origin, networkAddresses) + (contextPath != null ? contextPath : "");
    }

    /**
     * The address typed is taken only when it is one of this computer's own: the Host header comes from the
     * browser, so anyone on the network could otherwise make an administrator's link point at their own machine
     * and receive its token.
     */
    static String linkOrigin(String requestedOrigin, NetworkAddresses networkAddresses) {
        return networkAddresses.isOwn(requestedOrigin) ? requestedOrigin : networkAddresses.preferredUrl();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    /** The email settings of one company: whether they can send, from which address, with which server. */
    private record Transport(boolean configured, String from, JavaMailSender sender) {
    }

    /**
     * Each company has its own email settings: the link is sent with those of the company the user worked in last
     * (else their first company, else the first company of the installation).
     */
    private Transport transportFor(User user) {
        List<CompanyAccess> accesses = accessService.accessesOf(user.getId());
        String schema = accesses.stream().map(CompanyAccess::getCompany)
            .filter(company -> company.getId().equals(user.getLastCompanyId())).findFirst()
            .or(() -> accesses.stream().map(CompanyAccess::getCompany).findFirst())
            .map(Company::getSchemaName)
            .orElse(CompanySchemas.FIRST);
        return companyWork.inCompany(schema, () -> mailGateway.isServerConfigured()
            ? new Transport(true, mailGateway.fromAddress(), mailGateway.sender())
            : new Transport(false, null, null));
    }

    private void sendLink(User user, String resetUrl) {
        Transport transport = transportFor(user);
        if (!transport.configured()) {
            // The link gives access to the account: written to the log in development only (app.security.log-reset-links)
            if (logResetLinks) {
                log.warn("Mail disabled - password reset link for '{}': {}", user.getUsername(), resetUrl);
            } else {
                log.warn("Mail disabled - no password reset link sent to '{}': set up the email server", user.getUsername());
            }
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (!transport.from().isBlank()) {
            message.setFrom(transport.from());
        }
        message.setTo(user.getEmail());
        // In the user's own language, whatever the language of the page that asked for it
        message.setSubject(messages.get(user.getLanguage(), MessageProperties.AUTH_RESET_MAIL_SUBJECT));
        message.setText(messages.get(user.getLanguage(), MessageProperties.AUTH_RESET_MAIL_BODY, user.getUsername(), resetUrl, validity.toMinutes()));
        try {
            transport.sender().send(message);
        } catch (Exception ex) {
            log.error("Could not send the password reset email to user '{}'", user.getUsername(), ex);
        }
    }
}
