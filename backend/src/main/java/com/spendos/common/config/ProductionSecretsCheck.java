package com.spendos.common.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in production with a placeholder secret (SECURITY.md: no hardcoded secrets).
 * Development defaults such as "CHANGE_ME..." are convenient locally but must never reach production.
 */
@Component
public class ProductionSecretsCheck {

    private final String environment;
    private final String jwtSecret;
    private final String databasePassword;

    public ProductionSecretsCheck(@Value("${app.environment:development}") String environment,
                                  @Value("${jwt.secret}") String jwtSecret,
                                  @Value("${spring.datasource.password:}") String databasePassword) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.databasePassword = databasePassword;
    }

    @PostConstruct
    void verify() {
        if (!"production".equalsIgnoreCase(environment)) {
            return;
        }
        if (isPlaceholder(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET is a placeholder; set a random secret of at least 32 bytes");
        }
        if (databasePassword == null || databasePassword.isBlank() || isPlaceholder(databasePassword)) {
            throw new IllegalStateException("DATABASE_PASSWORD must be set to a real password in production");
        }
    }

    static boolean isPlaceholder(String value) {
        return value == null || value.toUpperCase(Locale.ROOT).contains("CHANGE_ME");
    }
}
