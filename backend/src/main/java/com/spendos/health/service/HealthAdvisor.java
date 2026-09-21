package com.spendos.health.service;

import static com.spendos.common.util.MoneyText.money;

import com.spendos.health.dto.HealthMetricsDtos.FactorChange;
import com.spendos.health.dto.HealthMetricsDtos.Recommendation;
import com.spendos.health.service.FinancialHealthService.Snapshot;
import com.spendos.health.service.HealthScoreCalculator.Factor;
import com.spendos.health.service.HealthScoreCalculator.Inputs;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Explains score changes factor by factor and turns weak factors into concrete actions with the
 * user's own amounts. Pure and deterministic, like the calculator it builds on.
 */
public final class HealthAdvisor {

    /** The user's biggest spending category over the score window, as a monthly average. */
    public record TopCategory(String name, BigDecimal monthlyAmount) {
    }

    public record ChangeSummary(String summary, List<FactorChange> changes) {
    }

    private static final BigDecimal HALF_POINT = new BigDecimal("0.5");
    private static final BigDecimal STRONG_SHARE = new BigDecimal("0.9");
    private static final int MAX_RECOMMENDATIONS = 3;

    private HealthAdvisor() {
    }

    public static ChangeSummary explain(Snapshot current, Snapshot previous) {
        Integer now = current.result().score();
        Integer before = previous.result().score();
        Map<String, Factor> old = previous.result().factors().stream()
                .collect(Collectors.toMap(Factor::key, Function.identity()));
        List<FactorChange> changes = new ArrayList<>();
        for (Factor factor : current.result().factors()) {
            Factor was = old.get(factor.key());
            if (was == null) {
                continue;
            }
            String reason = null;
            if (factor.scored() && !was.scored()) {
                reason = factor.label() + " is now included in your score.";
            } else if (!factor.scored() && was.scored()) {
                reason = factor.label() + " could no longer be measured, so it is left out.";
            } else if (factor.scored() && factor.points().subtract(was.points()).abs().compareTo(HALF_POINT) >= 0) {
                reason = metricChange(factor, was);
            }
            if (reason != null) {
                changes.add(new FactorChange(factor.key(), factor.label(), was.scored() ? was.points() : null,
                        factor.scored() ? factor.points() : null,
                        (factor.scored() ? factor.points() : BigDecimal.ZERO).subtract(was.scored() ? was.points() : BigDecimal.ZERO),
                        reason));
            }
        }
        changes.sort(Comparator.comparing((FactorChange c) -> c.change().abs()).reversed());

        String summary;
        if (now == null) {
            summary = "There is not enough data for a score yet.";
        } else if (before == null) {
            summary = "This is your first month with a score: " + now + " out of 100.";
        } else if (now.equals(before)) {
            summary = "Your score stayed at " + now + ".";
        } else {
            summary = "Your score " + (now > before ? "rose" : "fell") + " from " + before + " to " + now;
            List<FactorChange> drivers = changes.stream()
                    .filter(c -> c.change().signum() == Integer.signum(now - before)).limit(2).toList();
            summary += drivers.isEmpty() ? "." : ", mainly because of " + joinLabels(drivers) + ".";
        }
        return new ChangeSummary(summary, changes);
    }

    public static List<Recommendation> recommend(Snapshot snapshot, TopCategory top, String currency) {
        Inputs in = snapshot.inputs();
        BigDecimal income = nz(in.averageMonthlyIncome());
        BigDecimal expense = nz(in.averageMonthlyExpense());
        List<Recommendation> recommendations = new ArrayList<>();
        for (Factor factor : snapshot.result().factors()) {
            BigDecimal potential = BigDecimal.valueOf(factor.weight()).subtract(factor.scored() ? factor.points() : BigDecimal.ZERO)
                    .setScale(1, RoundingMode.HALF_UP);
            boolean weak = !factor.scored()
                    || factor.points().compareTo(STRONG_SHARE.multiply(BigDecimal.valueOf(factor.weight()))) < 0;
            if (!weak) {
                continue;
            }
            String action = switch (factor.key()) {
                case "savingsRate" -> {
                    if (!factor.scored()) {
                        yield "Import a statement that includes your salary or other income so your savings rate can be measured.";
                    }
                    BigDecimal cut = expense.subtract(income.multiply(new BigDecimal("0.70")));
                    yield cut.signum() <= 0 ? null
                            : "Spend about " + money(cut, currency) + " less a month to save 30% of your income"
                            + (top == null ? "." : "; " + top.name() + " is your biggest category at about "
                            + money(top.monthlyAmount(), currency) + " a month.");
                }
                case "budgetAdherence" -> factor.scored()
                        ? "Some budgets went over their limit. Set limits close to what you actually spend and act on the alerts."
                        : "Create a monthly budget" + (top == null ? "" : ", starting with " + top.name() + " (about "
                        + money(top.monthlyAmount(), currency) + " a month)") + ".";
                case "spendingVolatility" -> {
                    if (!factor.scored() || in.monthlyExpenses().isEmpty()) {
                        yield null; // only more history helps; nothing to do today
                    }
                    BigDecimal min = in.monthlyExpenses().stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    BigDecimal max = in.monthlyExpenses().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    yield "Your monthly spending ranged from " + money(min, currency) + " to " + money(max, currency)
                            + ". A monthly budget near " + money(expense, currency) + " helps even out the big months.";
                }
                case "recurringBurden" -> {
                    if (!factor.scored()) {
                        yield null;
                    }
                    BigDecimal recurring = nz(in.averageMonthlyRecurring());
                    BigDecimal excess = recurring.subtract(income.multiply(new BigDecimal("0.10")));
                    yield excess.signum() <= 0 ? null
                            : "Recurring payments cost about " + money(recurring, currency) + " a month. Cancelling about "
                            + money(excess, currency) + " a month of subscriptions you rarely use would bring them to 10% of income.";
                }
                case "emergencyBuffer" -> {
                    if (!factor.scored()) {
                        yield "Add opening balances to your accounts so your emergency buffer can be measured.";
                    }
                    BigDecimal target = expense.multiply(BigDecimal.valueOf(6));
                    BigDecimal gap = target.subtract(nz(in.liquidBalance()));
                    yield gap.signum() <= 0 ? null
                            : "Build your savings towards " + money(target, currency) + " (six months of spending); about "
                            + money(gap, currency) + " to go.";
                }
                default -> null;
            };
            if (action != null) {
                recommendations.add(new Recommendation(factor.key(), "Improve " + factor.label().toLowerCase(), action, potential));
            }
        }
        return recommendations.stream()
                .sorted(Comparator.comparing(Recommendation::potentialPoints).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    private static String metricChange(Factor now, Factor was) {
        boolean improved = now.points().compareTo(was.points()) > 0;
        String direction = improved ? "improved" : "worsened";
        if (now.metric() == null || was.metric() == null) {
            return now.label() + " " + direction + ".";
        }
        String from = format(now.key(), was.metric());
        String to = format(now.key(), now.metric());
        return switch (now.key()) {
            case "savingsRate" -> "You saved " + to + " of your income, compared with " + from + " before.";
            case "budgetAdherence" -> to + " of budget limits were respected, compared with " + from + " before.";
            case "spendingVolatility" -> "Monthly spending varied by " + to + " around its average, compared with " + from + ".";
            case "recurringBurden" -> "Recurring payments took " + to + " of income, compared with " + from + ".";
            case "emergencyBuffer" -> "Your balances cover " + to + " of spending, compared with " + from + ".";
            default -> now.label() + " " + direction + ".";
        };
    }

    private static String format(String key, BigDecimal metric) {
        if ("emergencyBuffer".equals(key)) {
            return metric.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " months";
        }
        return metric.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String joinLabels(List<FactorChange> changes) {
        List<String> labels = changes.stream().map(c -> c.label().toLowerCase()).toList();
        return labels.size() == 1 ? labels.get(0) : labels.get(0) + " and " + labels.get(1);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
