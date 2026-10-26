package com.swiftlabs.invoicing.access.twofactor;

import com.swiftlabs.invoicing.access.user.User;
import com.swiftlabs.invoicing.access.user.UserRepository;
import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
import com.swiftlabs.invoicing.common.ResourceNotFoundException;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/** Two-factor login: setting it up, checking codes at login, recovery codes, and resets by an administrator. */
@Service
@Transactional
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    static final int RECOVERY_CODE_COUNT = 10;
    /** No 0/o, 1/l/i: easy to copy by hand. 8 characters of 31 is about 40 bits each. */
    private static final String RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    /** Outcome of the second login step. */
    public enum Check { ACCEPTED, RECOVERY_CODE_USED, REJECTED, LOCKED }

    /** What the setup page shows: the secret (for manual entry) and the QR code that carries it. */
    public record Setup(String secret, String qrCodeDataUri) {

        /** The secret in groups of 4, easier to type by hand. */
        public String groupedSecret() {
            return secret.replaceAll("(.{4})(?!$)", "$1 ");
        }
    }

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final SecurityProperties.Lockout lockout;
    private final String issuer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(UserRepository userRepository, RecoveryCodeRepository recoveryCodeRepository,
                            SecurityProperties properties, Clock clock) {
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.lockout = properties.lockout();
        this.issuer = properties.twoFactor().issuer();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId) {
        return user(userId).isTotpEnabled();
    }

    @Transactional(readOnly = true)
    public long remainingRecoveryCodes(Long userId) {
        return recoveryCodeRepository.countByUserAndUsedAtIsNull(user(userId));
    }

    /** The QR code for a new secret; nothing is stored until the user confirms with a code. */
    public Setup setup(String username, String secret) {
        return new Setup(secret, QrCode.svgDataUri(Totp.uri(issuer, username, secret)));
    }

    /**
     * Turns two-factor login on (or moves it to a new phone) once the user has typed a code from the
     * app, which proves the secret was scanned. Returns the new recovery codes, shown once.
     */
    public List<String> enable(Long userId, String secret, String code) {
        User user = user(userId);
        OptionalLong step = Totp.verify(secret, normalize(code), clock.instant(), null);
        if (step.isEmpty()) {
            throw BusinessException.onField("code", MessageProperties.TWO_FACTOR_ERROR_WRONG_SETUP_CODE);
        }
        user.enableTotp(secret, step.getAsLong());
        log.info("Two-factor login enabled for '{}'", user.getUsername());
        return newRecoveryCodes(user);
    }

    /** New recovery codes; the previous ones stop working. Requires a current code from the app. */
    public List<String> regenerateRecoveryCodes(Long userId, String code) {
        User user = user(userId);
        requireAppCode(user, code);
        log.info("Recovery codes regenerated for '{}'", user.getUsername());
        return newRecoveryCodes(user);
    }

    /** Turned off by the user, with a current code from the app. */
    public void disable(Long userId, String code) {
        User user = user(userId);
        requireAppCode(user, code);
        turnOff(user);
        log.info("Two-factor login disabled by '{}'", user.getUsername());
    }

    /** Turned off by an administrator (lost phone and recovery codes); the user sets it up again. */
    public void reset(Long userId) {
        User user = user(userId);
        turnOff(user);
        log.warn("Two-factor login reset for '{}' by an administrator", user.getUsername());
    }

    /**
     * Second login step: a code from the app or a recovery code. Wrong codes count as failed
     * logins, so the account is locked after the same number of attempts as for passwords.
     */
    public Check verifyLogin(Long userId, String code) {
        User user = user(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!user.isTotpEnabled() || user.isLocked(now)) {
            return Check.LOCKED;
        }
        String typed = normalize(code);
        OptionalLong step = Totp.verify(user.getTotpSecret(), typed, clock.instant(), user.getTotpLastStep());
        if (step.isPresent()) {
            user.acceptTotpStep(step.getAsLong());
            user.recordSuccessfulLogin(now);
            log.info("Login succeeded for '{}' (two-factor code)", user.getUsername());
            return Check.ACCEPTED;
        }
        var recovery = recoveryCodeRepository.findByUserAndCodeHashAndUsedAtIsNull(user, hash(typed));
        if (recovery.isPresent()) {
            recovery.get().use(now);
            user.recordSuccessfulLogin(now);
            log.warn("Login succeeded for '{}' with a recovery code", user.getUsername());
            return Check.RECOVERY_CODE_USED;
        }
        user.recordFailedLogin(lockout.maxAttempts(), lockout.duration(), now);
        log.warn("Wrong two-factor code for '{}'", user.getUsername());
        return user.isLocked(now) ? Check.LOCKED : Check.REJECTED;
    }

    private void requireAppCode(User user, String code) {
        if (!user.isTotpEnabled()) {
            throw new BusinessException(MessageProperties.TWO_FACTOR_ERROR_NOT_ENABLED);
        }
        OptionalLong step = Totp.verify(user.getTotpSecret(), normalize(code), clock.instant(), user.getTotpLastStep());
        if (step.isEmpty()) {
            throw BusinessException.onField("code", MessageProperties.TWO_FACTOR_ERROR_WRONG_CODE);
        }
        user.acceptTotpStep(step.getAsLong());
    }

    private void turnOff(User user) {
        user.disableTotp();
        recoveryCodeRepository.deleteByUser(user);
    }

    private List<String> newRecoveryCodes(User user) {
        recoveryCodeRepository.deleteByUser(user);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int c = 0; c < 8; c++) {
                code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(code.substring(0, 4) + "-" + code.substring(4));
            recoveryCodeRepository.save(new RecoveryCode(user, hash(code.toString())));
        }
        return codes;
    }

    /** Spaces and dashes are ignored, and recovery codes are not case-sensitive. */
    private static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException(MessageProperties.USER_ERROR_NOT_FOUND));
    }
}
