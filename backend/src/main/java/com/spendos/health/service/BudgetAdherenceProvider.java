package com.spendos.health.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Supplies the budget-adherence input to the health score (implemented by the budgets module). */
public interface BudgetAdherenceProvider {

    /** Share of budget category limits respected in budgets overlapping the window, and how many were tracked. */
    Adherence adherence(UUID userId, LocalDate start, LocalDate end);

    record Adherence(BigDecimal ratio, int tracked) {
        public static Adherence none() {
            return new Adherence(null, 0);
        }
    }
}
