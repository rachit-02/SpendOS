package com.spendos.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSecretsCheckTest {

    private static final String REAL = "k3v9QzP1x8Lm2Rt7Wy4Bn6Hc0Js5Fd2Ga9";

    @Test
    void placeholdersAreRejectedInProductionOnly() {
        assertThatThrownBy(() -> new ProductionSecretsCheck("production", "CHANGE_ME_MINIMUM_32_CHARACTERS_LONG", REAL).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new ProductionSecretsCheck("production", REAL, "").verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DATABASE_PASSWORD");
        assertThatCode(() -> new ProductionSecretsCheck("production", REAL, REAL).verify()).doesNotThrowAnyException();
        assertThatCode(() -> new ProductionSecretsCheck("development", "CHANGE_ME_MINIMUM_32_CHARACTERS_LONG", "").verify())
                .doesNotThrowAnyException();
    }
}
