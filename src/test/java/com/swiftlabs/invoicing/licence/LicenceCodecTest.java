package com.swiftlabs.invoicing.licence;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenceCodecTest {

    private final KeyPair publisher = LicenceCodec.newKeyPair();
    private final Licence licence = new Licence("LIC-2026-0001", "Société d'Études = Atlas", "000016001234567", 3,
        LocalDate.of(2026, 10, 26), LocalDate.of(2027, 10, 31));

    @Test
    void aSignedKeyGivesBackItsLicence() {
        String key = LicenceCodec.sign(licence, publisher.getPrivate());
        assertThat(key).startsWith("SWIFT-");
        assertThat(LicenceCodec.verify(key, publisher.getPublic())).isEqualTo(licence);
        // Copied from an email over several lines
        String wrapped = key.substring(0, 30) + "\n  " + key.substring(30, 60) + "\r\n" + key.substring(60);
        assertThat(LicenceCodec.verify(wrapped, publisher.getPublic())).isEqualTo(licence);
    }

    @Test
    void alteredForeignOrGarbageKeysAreRefused() {
        String key = LicenceCodec.sign(licence, publisher.getPrivate());
        String moreUsers = LicenceCodec.sign(new Licence("LIC-2026-0001", licence.company(), licence.nif(), 50,
            licence.issued(), licence.expires()), publisher.getPrivate());
        // Payload of the 50-user licence with the signature of the 3-user one
        String spliced = moreUsers.substring(0, moreUsers.indexOf('.')) + key.substring(key.indexOf('.'));
        assertThatThrownBy(() -> LicenceCodec.verify(spliced, publisher.getPublic())).isInstanceOf(InvalidLicenceException.class);

        KeyPair someoneElse = LicenceCodec.newKeyPair();
        assertThatThrownBy(() -> LicenceCodec.verify(LicenceCodec.sign(licence, someoneElse.getPrivate()), publisher.getPublic()))
            .hasMessageContaining("ne vient pas de l'éditeur");
        assertThatThrownBy(() -> LicenceCodec.verify("hello", publisher.getPublic())).hasMessageContaining("n'est pas une clé");
        assertThatThrownBy(() -> LicenceCodec.verify("SWIFT-@@@.###", publisher.getPublic())).hasMessageContaining("illisible");
    }

    @Test
    void keysRoundTripThroughTheirTextForm() {
        assertThat(LicenceCodec.publicKey(LicenceCodec.encodeKey(publisher.getPublic()))).isEqualTo(publisher.getPublic());
        assertThat(LicenceCodec.privateKey(LicenceCodec.encodeKey(publisher.getPrivate()))).isEqualTo(publisher.getPrivate());
    }
}
