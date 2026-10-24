package com.swiftlabs.invoicing.access.twofactor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B, SHA-1 key, last 6 of the 8 published digits. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors() {
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(RFC_KEY, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void base32RoundTripsAndMatchesTheStandardAlphabet() {
        assertThat(Totp.base32Encode(RFC_KEY)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32Decode("GEZD GNBV gy3t QOJQ GEZDGNBVGY3TQOJQ")).isEqualTo(RFC_KEY);
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32Encode(Totp.base32Decode(secret))).isEqualTo(secret);
    }

    @Test
    void acceptsOneStepOfClockDriftButNotMore() {
        String secret = Totp.base32Encode(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1111111109);
        long step = Totp.step(now);
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, step + 2), now, null)).isEmpty();
    }

    @Test
    void aCodeIsAcceptedOnceOnly() {
        String secret = Totp.base32Encode(RFC_KEY);
        Instant now = Instant.ofEpochSecond(1111111109);
        String code = Totp.code(RFC_KEY, Totp.step(now));
        OptionalLong first = Totp.verify(secret, code, now, null);
        assertThat(first).isPresent();
        assertThat(Totp.verify(secret, code, now, first.getAsLong())).isEmpty();
        // An older code is refused too once a newer one was used
        assertThat(Totp.verify(secret, Totp.code(RFC_KEY, first.getAsLong() - 1), now, first.getAsLong())).isEmpty();
    }

    @Test
    void malformedCodesAreRefused() {
        String secret = Totp.base32Encode(RFC_KEY);
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.verify(secret, null, now, null)).isEmpty();
        assertThat(Totp.verify(secret, "28708", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "2870821", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "28708a", now, null)).isEmpty();
    }

    @Test
    void uriFollowsTheKeyUriFormat() {
        assertThat(Totp.uri("Swift Invoicing", "karim", "ABCD"))
            .isEqualTo("otpauth://totp/Swift%20Invoicing:karim?secret=ABCD&issuer=Swift%20Invoicing&algorithm=SHA1&digits=6&period=30");
    }
}
