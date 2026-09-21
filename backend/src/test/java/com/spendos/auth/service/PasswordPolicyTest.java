package com.spendos.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendos.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"Tr0pic@lThund3r!", "C0ff33#M0rning$", "BlueSky@2026!"})
    void acceptsDocumentedValidExamples(String password) {
        assertThatCode(() -> policy.validate(password, "someone@example.com")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123", "Abc123", "admin123", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!!", "NoSymbols12345"})
    void rejectsShortOrMissingCharacterClasses(String password) {
        assertThatThrownBy(() -> policy.validate(password, "someone@example.com"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("WEAK_PASSWORD");
    }

    @Test
    void rejectsCommonPasswordEvenWhenPadded() {
        assertThatThrownBy(() -> policy.validate("Password123!!", "someone@example.com"))
                .hasMessage("Password is too common");
    }

    @Test
    void rejectsPasswordContainingEmailName() {
        assertThatThrownBy(() -> policy.validate("Xy#1johnsmith2026", "johnsmith@example.com"))
                .hasMessage("Password must not contain your email name");
    }

    @Test
    void rejectsLowVarietyPasswords() {
        assertThatThrownBy(() -> policy.validate("Aa1!Aa1!Aa1!Aa1!", "someone@example.com"))
                .hasMessage("Password has too few distinct characters");
    }
}
