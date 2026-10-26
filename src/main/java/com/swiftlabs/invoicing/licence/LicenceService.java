package com.swiftlabs.invoicing.licence;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.common.MessageProperties;
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
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
            return new LicenceStatus(LicenceStatus.State.NOT_REQUIRED, null, 0);
        }
        LicenceRecord record = record();
        LocalDate today = now(record).toLocalDate();
        if (record.getLicenceKey() == null) {
            LocalDate trialEnd = record.getTrialStartedAt().toLocalDate().plusDays(properties.trialDays());
            long left = ChronoUnit.DAYS.between(today, trialEnd);
            return left >= 0
                ? new LicenceStatus(LicenceStatus.State.TRIAL, null, left, MessageProperties.LICENCE_STATUS_TRIAL, new Object[] {left})
                : new LicenceStatus(LicenceStatus.State.TRIAL_OVER, null, left, MessageProperties.LICENCE_STATUS_TRIAL_OVER, null);
        }
        Licence licence;
        try {
            licence = LicenceCodec.verify(record.getLicenceKey(), publicKey);
        } catch (InvalidLicenceException ex) {
            return new LicenceStatus(LicenceStatus.State.INVALID, null, 0, ex.getCode(), ex.getArgs());
        }
        InvalidLicenceException mismatch = nifMismatch(licence);
        if (mismatch != null) {
            return new LicenceStatus(LicenceStatus.State.INVALID, licence, 0, mismatch.getCode(), mismatch.getArgs());
        }
        long left = ChronoUnit.DAYS.between(today, licence.expires());
        if (left > properties.warningDays()) {
            return new LicenceStatus(LicenceStatus.State.VALID, licence, left);
        }
        if (left >= 0) {
            return new LicenceStatus(LicenceStatus.State.EXPIRING, licence, left, MessageProperties.LICENCE_STATUS_EXPIRING, new Object[] {left});
        }
        long graceLeft = left + properties.graceDays();
        return graceLeft >= 0
            ? new LicenceStatus(LicenceStatus.State.GRACE, licence, left, MessageProperties.LICENCE_STATUS_GRACE,
                new Object[] {licence.expires().format(DATE), graceLeft})
            : new LicenceStatus(LicenceStatus.State.EXPIRED, licence, left, MessageProperties.LICENCE_STATUS_EXPIRED, null);
    }

    /** Checks and saves a key pasted by the customer. */
    @Transactional
    public Licence activate(String key) {
        Licence licence = LicenceCodec.verify(key, publicKey);
        InvalidLicenceException mismatch = nifMismatch(licence);
        if (mismatch != null) {
            throw mismatch;
        }
        LicenceRecord record = record();
        if (licence.expires().isBefore(now(record).toLocalDate())) {
            throw new InvalidLicenceException(MessageProperties.LICENCE_ERROR_EXPIRED, licence.expires().format(DATE));
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
            throw new BusinessException(MessageProperties.LICENCE_ERROR_USER_LIMIT, String.valueOf(maxUsers()));
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
    private InvalidLicenceException nifMismatch(Licence licence) {
        String issuerNif = digits(issuerService.getIssuer().getNif());
        return digits(licence.nif()).equals(issuerNif) ? null
            : new InvalidLicenceException(MessageProperties.LICENCE_ERROR_NIF_MISMATCH, licence.nif(), licence.company(),
                issuerService.getIssuer().getNif());
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
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
