package com.swiftlabs.invoicing.security.util;

public class PasswordPolicy {

    private final int minLength;

    public PasswordPolicy(int minLength) {
        this.minLength = minLength;
    }

    public boolean isValid(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        if (password.length() < minLength) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isLowerCase(c)) hasLower = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        return hasUpper && hasLower && hasDigit;
    }

    public String getErrorMessage() {
        return "Le mot de passe doit contenir au moins " + minLength
            + " caractères, une majuscule, une minuscule et un chiffre.";
    }
}
