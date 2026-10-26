package com.swiftlabs.invoicing.licence;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Licence keys: "SWIFT-" + the licence content + "." + its Ed25519 signature, both Base64 (URL
 * alphabet, no padding). Only the private key (kept by the publisher) can sign; the application
 * holds the public key and checks keys offline.
 */
public final class LicenceCodec {

    static final String PREFIX = "SWIFT-";
    private static final String ALGORITHM = "Ed25519";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private LicenceCodec() {
    }

    public static KeyPair newKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Ed25519 is part of Java 15+", ex);
        }
    }

    public static String encodeKey(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PrivateKey privateKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Not an Ed25519 private key", ex);
        }
    }

    public static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Not an Ed25519 public key", ex);
        }
    }

    public static String sign(Licence licence, PrivateKey privateKey) {
        byte[] payload = licence.payload().getBytes(StandardCharsets.UTF_8);
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload);
            return PREFIX + ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not sign the licence", ex);
        }
    }

    /**
     * The licence carried by a key whose signature matches the public key. Spaces and line breaks
     * (from copying an email) are ignored.
     *
     * @throws InvalidLicenceException for a mistyped, altered or foreign key
     */
    public static Licence verify(String key, PublicKey publicKey) {
        String compact = key == null ? "" : key.replaceAll("\\s", "");
        int dot = compact.indexOf('.');
        if (!compact.startsWith(PREFIX) || dot < 0) {
            throw new InvalidLicenceException("Ce texte n'est pas une clé de licence Swift Invoicing.");
        }
        try {
            byte[] payload = DECODER.decode(compact.substring(PREFIX.length(), dot));
            byte[] signed = DECODER.decode(compact.substring(dot + 1));
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload);
            if (!signature.verify(signed)) {
                throw new InvalidLicenceException("Clé de licence invalide : elle a été modifiée ou ne vient pas de l'éditeur.");
            }
            return Licence.parse(new String(payload, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new InvalidLicenceException("Clé de licence illisible : vérifiez qu'elle a été copiée en entier.");
        }
    }
}
