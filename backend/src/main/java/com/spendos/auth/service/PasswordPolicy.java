package com.spendos.auth.service;

import com.spendos.common.exception.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Password rules from SECURITY.md: at least 12 characters, upper + lower + digit + symbol, not a
 * common password or pattern, and not containing the email's local part.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    // Base words of the most common leaked passwords; a password built around one of them is rejected
    // even when padded with digits and symbols to satisfy the character-class rules.
    private static final Set<String> COMMON_BASES = Set.of(
            "password", "passw0rd", "qwerty", "letmein", "welcome", "admin", "iloveyou", "monkey", "dragon",
            "football", "baseball", "abc123", "123456", "12345678", "111111", "sunshine", "princess",
            "trustno1", "master", "superman", "changeme", "spendos");

    public void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw weak("Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean symbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if (!(upper && lower && digit && symbol)) {
            throw weak("Password must include uppercase, lowercase, number, and symbol characters");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        String lettersOnly = normalized.replaceAll("[^a-z0-9]", "");
        for (String base : COMMON_BASES) {
            if (lettersOnly.startsWith(base) || lettersOnly.endsWith(base) || lettersOnly.equals(base)) {
                throw weak("Password is too common");
            }
        }
        if (email != null && email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (localPart.length() >= 3 && normalized.contains(localPart)) {
                throw weak("Password must not contain your email name");
            }
        }
        if (normalized.chars().distinct().count() < 5) {
            throw weak("Password has too few distinct characters");
        }
    }

    private static ApiException weak(String message) {
        return ApiException.badRequest("WEAK_PASSWORD", message);
    }
}
