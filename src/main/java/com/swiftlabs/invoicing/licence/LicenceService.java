package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.issuer.IssuerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The licence of an on-premise install: a 30-day trial from the first start, then a key signed
 * by the publisher for one company (its NIF), a number of users and an expiry date. Once the
 * licence (or trial) is over, the application turns read-only (see LicenceFilter).
 */
@Service
public class LicenceService {

    private static final Logger log = LoggerFactory.getLogger(LicenceService.class);
    private static final Duration SEEN_PRECISION = Duration.ofHours(1);

    private final LicenceRecordRepository repository;
    private final IssuerService issuerService;
    private final LicenceProperties properties;
    private final Clock clock;
    private final PublicKey publicKey;

    public LicenceService(LicenceRecordRepository repository, IssuerService issuerService, LicenceProperties properties, Clock clock) {
        this.repository = repository;
        this.issuerService = issuerService;
        this.properties = properties;
        this.clock = clock;
        this.publicKey = properties.required() ? LicenceCodec.publicKey(publicKeyText(properties)) : null;
    }

    public boolean isRequired() {
        return properties.required();
    }

    @Transactional
    public LicenceStatus status() {
        if (!properties.required()) {
            return new LicenceStatus(LicenceStatus.State.NOT_REQUIRED, null, 0, null);
        }
        LicenceRecord record = record();
        LocalDate today = now(record).toLocalDate();
        if (record.getLicenceKey() == null) {
            LocalDate trialEnd = record.getTrialStartedAt().toLocalDate().plusDays(properties.trialDays());
            long left = ChronoUnit.DAYS.between(today, trialEnd);
            return left >= 0
                ? new LicenceStatus(LicenceStatus.State.TRIAL, null, left, "Version d'essai : " + days(left) + " restant(s).")
                : new LicenceStatus(LicenceStatus.State.TRIAL_OVER, null, left,
                    "La période d'essai est terminée : l'application est en lecture seule. Activez votre licence.");
        }
        Licence licence;
        try {
            licence = LicenceCodec.verify(record.getLicenceKey(), publicKey);
        } catch (InvalidLicenceException ex) {
            return new LicenceStatus(LicenceStatus.State.INVALID, null, 0, ex.getMessage());
        }
        String mismatch = nifMismatch(licence);
        if (mismatch != null) {
            return new LicenceStatus(LicenceStatus.State.INVALID, licence, 0, mismatch);
        }
        long left = ChronoUnit.DAYS.between(today, licence.expires());
        if (left > properties.warningDays()) {
            return new LicenceStatus(LicenceStatus.State.VALID, licence, left, null);
        }
        if (left >= 0) {
            return new LicenceStatus(LicenceStatus.State.EXPIRING, licence, left,
                "Votre licence expire dans " + days(left) + " : pensez à la renouveler.");
        }
        long graceLeft = left + properties.graceDays();
        return graceLeft >= 0
            ? new LicenceStatus(LicenceStatus.State.GRACE, licence, left, "Votre licence a expiré le "
                + licence.expires().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " : l'application passera en lecture seule dans " + days(graceLeft) + ".")
            : new LicenceStatus(LicenceStatus.State.EXPIRED, licence, left,
                "Votre licence a expiré : l'application est en lecture seule. Renouvelez-la pour continuer.");
    }

    /** Checks and saves a key pasted by the customer. */
    @Transactional
    public Licence activate(String key) {
        Licence licence = LicenceCodec.verify(key, publicKey);
        String mismatch = nifMismatch(licence);
        if (mismatch != null) {
            throw new InvalidLicenceException(mismatch);
        }
        LicenceRecord record = record();
        if (licence.expires().isBefore(now(record).toLocalDate())) {
            throw new InvalidLicenceException("Cette clé a expiré le " + licence.expires() + " : demandez une clé de renouvellement.");
        }
        record.activate(key.replaceAll("\\s", ""), LocalDateTime.now(clock));
        log.info("Licence {} activated for {} ({} users, until {})", licence.id(), licence.company(), licence.users(), licence.expires());
        return licence;
    }

    /** Active user accounts allowed: the licence's, or the trial's. */
    @Transactional
    public int maxUsers() {
        LicenceStatus status = status();
        return status.licence() != null ? status.licence().users() : properties.trialUsers();
    }

    /** Refuses one more active account beyond what the licence allows (no limit when licensing is off). */
    @Transactional
    public void checkUserLimit(long activeUsersAfterChange) {
        if (properties.required() && activeUsersAfterChange > maxUsers()) {
            throw new BusinessException("Votre licence permet " + maxUsers() + " utilisateur(s) actif(s) : désactivez un compte "
                + "ou demandez une licence pour plus d'utilisateurs.");
        }
    }

    private LicenceRecord record() {
        return repository.findById(LicenceRecord.ID).orElseGet(() -> repository.save(new LicenceRecord(LocalDateTime.now(clock))));
    }

    /** The current time, never before the latest one seen (a PC clock moved back does not extend anything). */
    private LocalDateTime now(LicenceRecord record) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(record.getLastSeenAt())) {
            return record.getLastSeenAt();
        }
        if (Duration.between(record.getLastSeenAt(), now).compareTo(SEEN_PRECISION) > 0) {
            record.seen(now);
        }
        return now;
    }

    /** A licence is for one company: its NIF must be the one of the issuer profile. */
    private String nifMismatch(Licence licence) {
        String issuerNif = digits(issuerService.getIssuer().getNif());
        return digits(licence.nif()).equals(issuerNif) ? null
            : "Cette licence est établie pour le NIF " + licence.nif() + " (" + licence.company() + "), alors que le profil émetteur indique "
                + issuerService.getIssuer().getNif() + ".";
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String days(long count) {
        return count + (count > 1 ? " jours" : " jour");
    }

    private static String publicKeyText(LicenceProperties properties) {
        if (properties.publicKey() != null && !properties.publicKey().isBlank()) {
            return properties.publicKey();
        }
        try {
            return new ClassPathResource("licence/public-key.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Licensing is on but the publisher's public key (licence/public-key.txt) is missing", ex);
        }
    }
}
