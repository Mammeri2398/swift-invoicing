package com.swiftlabs.invoicing.access.user;

import com.swiftlabs.invoicing.common.BusinessException;
import com.swiftlabs.invoicing.config.SecurityProperties;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Rules for passwords chosen by users, and generation of temporary passwords that satisfy them. */
@Component
public class PasswordPolicy {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final int TEMPORARY_LENGTH = 16;

    private final int minLength;
    private final int maxLength;
    private final SecureRandom random = new SecureRandom();

    public PasswordPolicy(SecurityProperties properties) {
        this.minLength = properties.password().minLength();
        this.maxLength = properties.password().maxLength();
    }

    /** @throws BusinessException with a message for the user when the password is not acceptable */
    public void validate(String password, String username) {
        if (password == null || password.length() < minLength || password.length() > maxLength
            || !password.chars().anyMatch(Character::isUpperCase)
            || !password.chars().anyMatch(Character::isLowerCase)
            || !password.chars().anyMatch(Character::isDigit)) {
            throw new BusinessException(describe());
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw new BusinessException("Le mot de passe ne doit pas contenir le nom d'utilisateur.");
        }
    }

    public String describe() {
        return "Le mot de passe doit contenir entre " + minLength + " et " + maxLength
            + " caractères, dont une majuscule, une minuscule et un chiffre.";
    }

    /** Random password meeting the rules, without ambiguous characters (0/O, 1/l/I). */
    public String generateTemporary() {
        String all = UPPER + LOWER + DIGITS;
        char[] chars = new char[Math.max(TEMPORARY_LENGTH, minLength)];
        chars[0] = UPPER.charAt(random.nextInt(UPPER.length()));
        chars[1] = LOWER.charAt(random.nextInt(LOWER.length()));
        chars[2] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        for (int i = 3; i < chars.length; i++) {
            chars[i] = all.charAt(random.nextInt(all.length()));
        }
        // Shuffle so the required classes are not always in front
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
