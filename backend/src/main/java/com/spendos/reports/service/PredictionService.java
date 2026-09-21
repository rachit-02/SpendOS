package com.spendos.reports.service;

import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.DayAmount;
import com.spendos.analytics.repository.AggregateQueries.MonthTotals;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.budgets.dto.BudgetDtos.BudgetResponse;
import com.spendos.budgets.service.BudgetService;
import com.spendos.common.util.Money;
import com.spendos.goals.service.GoalService;
import com.spendos.recurring.dto.RecurringDtos.RecurringResponse;
import com.spendos.recurring.service.RecurringService;
import com.spendos.reports.dto.PredictionDtos.Affordability;
import com.spendos.reports.dto.PredictionDtos.AffordabilityRequest;
import com.spendos.reports.dto.PredictionDtos.Analysis;
import com.spendos.reports.dto.PredictionDtos.Purchase;
import com.spendos.reports.dto.PredictionDtos.Range;
import com.spendos.reports.dto.PredictionDtos.SpendingPrediction;
import com.spendos.reports.dto.PredictionDtos.Verdict;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Month-end spending forecast and "can I afford this?".
 *
 * <p>Forecast = spending so far + daily rate x remaining days + recurring payments still expected
 * this month. The daily rate blends this month's rate with the last three months' rate (weighted by
 * how much of the month has passed) and excludes recurring payments, which are added separately.
 * The range is +/- one standard deviation of historical daily spending scaled by sqrt(remaining days).
 * With no history and under a week of data the result is flagged as insufficient.
 */
@Service
public class PredictionService {

    static final int HISTORY_MONTHS = 3;
    static final String DISCLAIMER = "This is a planning estimate, not professional financial advice.";
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final AggregateQueries aggregates;
    private final RecurringService recurringService;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;

    public PredictionService(AggregateQueries aggregates, RecurringService recurringService, BudgetService budgetService,
                             GoalService goalService, UserPreferencesService preferencesService, PeriodResolver periods) {
        this.aggregates = aggregates;
        this.recurringService = recurringService;
        this.budgetService = budgetService;
        this.goalService = goalService;
        this.preferencesService = preferencesService;
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public SpendingPrediction predict(UUID userId, Integer month, Integer year) {
        YearMonth period = periods.resolve(userId, month, year);
        LocalDate today = periods.today(userId);
        String currency = preferencesService.currencyFor(userId);
        List<MonthTotals> history = activeHistory(userId, period);
        BigDecimal monthlyAverage = average(history.stream().map(MonthTotals::expense).toList());
        YearMonth previous = period.minusMonths(1);
        BigDecimal previousTotal = aggregates.totals(userId, previous.atDay(1), previous.atEndOfMonth()).expense();

        if (!period.equals(YearMonth.from(today))) {
            // A finished month needs no forecast: report the actual total.
            BigDecimal actual = aggregates.totals(userId, period.atDay(1), period.atEndOfMonth()).expense();
            return new SpendingPrediction(period.atEndOfMonth(), period.getMonthValue(), period.getYear(), Money.scale(actual),
                    period.lengthOfMonth(), 0, new Range(Money.scale(actual), Money.scale(actual), Money.scale(actual)),
                    BigDecimal.ONE.setScale(2), "The month is complete; this is the actual total.", true,
                    "Actual spending for a completed month", BigDecimal.ZERO.setScale(2),
                    Money.scale(actual.divide(BigDecimal.valueOf(period.lengthOfMonth()), MC)), Money.scale(previousTotal),
                    Money.scale(monthlyAverage), currency);
        }

        LocalDate start = period.atDay(1);
        int daysElapsed = today.getDayOfMonth();
        int daysInMonth = period.lengthOfMonth();
        int remaining = daysInMonth - daysElapsed;
        BigDecimal spent = aggregates.totals(userId, start, today).expense();
        BigDecimal recurringSoFar = aggregates.recurringSpend(userId, start, today);
        BigDecimal currentRate = spent.subtract(recurringSoFar).max(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(daysElapsed), MC);

        BigDecimal historicalRate = null;
        if (!history.isEmpty()) {
            BigDecimal variable = BigDecimal.ZERO;
            int days = 0;
            for (MonthTotals past : history) {
                variable = variable.add(past.expense().subtract(aggregates.recurringSpend(userId,
                        past.month().atDay(1), past.month().atEndOfMonth())).max(BigDecimal.ZERO));
                days += past.month().lengthOfMonth();
            }
            historicalRate = variable.divide(BigDecimal.valueOf(days), MC);
        }
        BigDecimal weight = BigDecimal.valueOf(daysElapsed).divide(BigDecimal.valueOf(daysInMonth), MC);
        BigDecimal rate = historicalRate == null ? currentRate
                : currentRate.multiply(weight).add(historicalRate.multiply(BigDecimal.ONE.subtract(weight)));

        BigDecimal upcomingRecurring = recurringService.upcoming(userId, remaining).stream()
                .filter(r -> !r.nextExpectedDate().isAfter(period.atEndOfMonth()))
                .map(RecurringResponse::typicalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal median = spent.add(rate.multiply(BigDecimal.valueOf(remaining))).add(upcomingRecurring);

        List<DayAmount> daily = aggregates.dailySpending(userId, today.minusDays(90), today.minusDays(1));
        BigDecimal dailySd = standardDeviation(daily.stream().map(DayAmount::amount).toList());
        BigDecimal spread = dailySd.multiply(BigDecimal.valueOf(Math.sqrt(remaining)), MC);
        BigDecimal min = median.subtract(spread).max(spent.add(upcomingRecurring));
        BigDecimal max = median.add(spread);

        boolean sufficient = !history.isEmpty() || daysElapsed >= 7;
        BigDecimal confidence = switch (history.size()) {
            case 0 -> new BigDecimal("0.30");
            case 1 -> new BigDecimal("0.50");
            case 2 -> new BigDecimal("0.65");
            default -> new BigDecimal("0.75");
        };
        confidence = confidence.add(weight.multiply(new BigDecimal("0.20"))).min(new BigDecimal("0.95"))
                .setScale(2, RoundingMode.HALF_UP);
        String reason = !sufficient ? "Not enough historical data for a reliable prediction"
                : history.isEmpty() ? "Based only on " + daysElapsed + " days of this month; no earlier months to compare"
                : "Based on " + history.size() + " month" + (history.size() == 1 ? "" : "s")
                + " of history and " + daysElapsed + " days of this month";

        return new SpendingPrediction(today, period.getMonthValue(), period.getYear(), Money.scale(spent), daysElapsed,
                remaining, new Range(Money.scale(min), Money.scale(max), Money.scale(median)), confidence, reason,
                sufficient,
                "Average daily spend (this month blended with your last three months, excluding recurring payments) "
                        + "extrapolated to month end, plus recurring payments still expected this month",
                Money.scale(upcomingRecurring), Money.scale(rate), Money.scale(previousTotal), Money.scale(monthlyAverage),
                currency);
    }

    @Transactional(readOnly = true)
    public Affordability affordability(UUID userId, AffordabilityRequest request) {
        LocalDate today = periods.today(userId);
        YearMonth month = YearMonth.from(today);
        String currency = preferencesService.currencyFor(userId);
        SpendingPrediction prediction = predict(userId, null, null);

        BigDecimal incomeSoFar = aggregates.totals(userId, month.atDay(1), today).income();
        BigDecimal averageIncome = average(activeHistory(userId, month).stream().map(MonthTotals::income).toList());
        BigDecimal expectedIncome = incomeSoFar.max(averageIncome);

        BigDecimal projected = prediction.projectedMonthEnd().median();
        BigDecimal remainingExpenses = projected.subtract(prediction.currentSpending())
                .subtract(prediction.upcomingRecurring()).max(BigDecimal.ZERO);
        BigDecimal withoutPurchase = expectedIncome.subtract(projected);
        BigDecimal amount = request.purchaseAmount();
        BigDecimal withPurchase = withoutPurchase.subtract(amount);
        BigDecimal goals = goalService.monthlyContributionsNeeded(userId);

        BigDecimal remainingBudget = null;
        for (BudgetResponse budget : budgetService.list(userId, true)) {
            if (budget.categories().isEmpty() && !today.isBefore(budget.startDate()) && !today.isAfter(budget.endDate())) {
                remainingBudget = (remainingBudget == null ? BigDecimal.ZERO : remainingBudget).add(budget.remainingAmount());
            }
        }

        List<String> considerations = new ArrayList<>();
        considerations.add("Expected income this month: " + money(expectedIncome, currency));
        considerations.add("Projected spending this month without the purchase: " + money(projected, currency));
        if (prediction.upcomingRecurring().signum() > 0) {
            considerations.add("Includes " + money(prediction.upcomingRecurring(), currency)
                    + " of recurring payments still due this month");
        }
        if (goals.signum() > 0) {
            considerations.add("Your savings goals need about " + money(goals, currency) + " a month");
        }
        if (remainingBudget != null) {
            considerations.add("Remaining overall budget this month: " + money(remainingBudget, currency));
        }
        if (!prediction.sufficientData()) {
            considerations.add("There is little spending history yet, so this estimate is rough");
        }

        String confidence;
        boolean affordable;
        String description = request.purchaseDescription() == null || request.purchaseDescription().isBlank()
                ? "this purchase" : "the " + request.purchaseDescription().trim();
        String explanation;
        if (expectedIncome.signum() == 0) {
            affordable = false;
            confidence = "low";
            explanation = "No income is recorded yet, so SpendOS cannot tell whether " + description
                    + " fits your plan. Import a statement that includes your income.";
        } else if (withPurchase.compareTo(goals) >= 0
                && (remainingBudget == null || remainingBudget.compareTo(amount) >= 0)) {
            affordable = true;
            confidence = prediction.sufficientData() ? "high" : "medium";
            explanation = "Based on your current plan, " + description + " is likely affordable. It would reduce your "
                    + "projected savings this month from " + money(withoutPurchase, currency) + " to about "
                    + money(withPurchase, currency) + ".";
        } else if (withPurchase.signum() >= 0) {
            affordable = true;
            confidence = "medium";
            explanation = "You could pay for " + description + " without spending more than you earn, but it would "
                    + (remainingBudget != null && remainingBudget.compareTo(amount) < 0 ? "take you over budget"
                    : "leave less than your savings goals need this month") + ". Projected savings would drop to "
                    + money(withPurchase, currency) + ".";
        } else {
            affordable = false;
            confidence = "high";
            explanation = description.substring(0, 1).toUpperCase(Locale.ROOT) + description.substring(1)
                    + " would mean spending about " + money(withPurchase.negate(), currency)
                    + " more than you expect to earn this month. Consider waiting or saving up first.";
        }

        Analysis analysis = new Analysis(prediction.currentSpending(), Money.scale(expectedIncome),
                prediction.upcomingRecurring(), Money.scale(remainingExpenses), Money.scale(withPurchase),
                Money.scale(withoutPurchase), Money.scale(withPurchase), Money.scale(amount.negate()), Money.scale(goals),
                remainingBudget == null ? null : Money.scale(remainingBudget));
        return new Affordability(new Purchase(Money.scale(amount), request.purchaseDescription()),
                new Verdict(affordable, confidence), analysis, considerations, explanation, DISCLAIMER);
    }

    /** Up to three complete months before {@code month}, skipping months before the user's data begins. */
    private List<MonthTotals> activeHistory(UUID userId, YearMonth month) {
        List<MonthTotals> months = aggregates.monthlyTotals(userId, month.minusMonths(HISTORY_MONTHS), month.minusMonths(1));
        int first = 0;
        while (first < months.size() && months.get(first).income().signum() == 0
                && months.get(first).expense().signum() == 0) {
            first++;
        }
        return months.subList(first, months.size());
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), MC);
    }

    static BigDecimal standardDeviation(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = average(values);
        return values.stream().map(v -> v.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC).sqrt(MC);
    }

    static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance("INR".equals(currency) ? new Locale("en", "IN") : Locale.US);
        try {
            format.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException ignored) {
            // locale default
        }
        format.setMaximumFractionDigits(0);
        return format.format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
