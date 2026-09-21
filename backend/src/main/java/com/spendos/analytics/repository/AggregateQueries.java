package com.spendos.analytics.repository;

import com.spendos.common.util.Money;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregate SQL shared by the dashboard, analytics, budgets, insights, reports and the assistant.
 * Conventions: expense = debit, income = credit; transfers between own accounts are excluded from both.
 * Every query is filtered by user_id first so the (user_id, transaction_date) index is used.
 */
@Repository
public class AggregateQueries {

    public record Totals(BigDecimal income, BigDecimal expense, long incomeCount, long expenseCount) {
        public BigDecimal net() {
            return income.subtract(expense);
        }
    }

    public record CategoryAmount(UUID categoryId, String categoryName, String colorHex, BigDecimal amount, long count) {
    }

    public record SubcategoryAmount(UUID subcategoryId, String subcategoryName, BigDecimal amount, long count) {
    }

    public record MerchantAmount(UUID merchantId, String merchantName, BigDecimal amount, long count) {
    }

    public record MonthTotals(YearMonth month, BigDecimal income, BigDecimal expense) {
        public BigDecimal net() {
            return income.subtract(expense);
        }
    }

    public record MethodAmount(String method, BigDecimal amount, long count) {
    }

    public record DayAmount(LocalDate date, BigDecimal amount) {
    }

    public record MonthAmount(YearMonth month, BigDecimal amount, long count) {
    }

    private final JdbcTemplate jdbc;

    public AggregateQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(UUID userId, LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'credit'), 0),
                       COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'debit'), 0),
                       COUNT(*) FILTER (WHERE transaction_type = 'credit'),
                       COUNT(*) FILTER (WHERE transaction_type = 'debit')
                FROM transactions
                WHERE user_id = ? AND transaction_date BETWEEN ? AND ? AND deleted_at IS NULL""",
                (rs, i) -> new Totals(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getLong(3), rs.getLong(4)),
                userId, Date.valueOf(start), Date.valueOf(end));
    }

    /** Spending per category, largest first. */
    public List<CategoryAmount> spendingByCategory(UUID userId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT c.id, COALESCE(c.category_name, 'Uncategorized'), c.color_hex, SUM(t.amount), COUNT(*)
                FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ? AND t.transaction_date BETWEEN ? AND ? AND t.transaction_type = 'debit'
                  AND t.deleted_at IS NULL
                GROUP BY c.id, c.category_name, c.color_hex
                ORDER BY SUM(t.amount) DESC""",
                (rs, i) -> new CategoryAmount(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getLong(5)),
                userId, Date.valueOf(start), Date.valueOf(end));
    }

    public List<SubcategoryAmount> spendingBySubcategory(UUID userId, UUID categoryId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT s.id, COALESCE(s.subcategory_name, 'Other'), SUM(t.amount), COUNT(*)
                FROM transactions t LEFT JOIN subcategories s ON s.id = t.subcategory_id
                WHERE t.user_id = ? AND t.category_id = ? AND t.transaction_date BETWEEN ? AND ?
                  AND t.transaction_type = 'debit' AND t.deleted_at IS NULL
                GROUP BY s.id, s.subcategory_name
                ORDER BY SUM(t.amount) DESC""",
                (rs, i) -> new SubcategoryAmount(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getLong(4)),
                userId, categoryId, Date.valueOf(start), Date.valueOf(end));
    }

    /** Merchants by total spend; {@code categoryId} optionally restricts to one category. */
    public List<MerchantAmount> topMerchants(UUID userId, LocalDate start, LocalDate end, UUID categoryId, int limit) {
        List<Object> args = new ArrayList<>(List.of(userId, Date.valueOf(start), Date.valueOf(end)));
        String categoryClause = "";
        if (categoryId != null) {
            categoryClause = "AND t.category_id = ?";
            args.add(categoryId);
        }
        args.add(limit);
        String sql = """
                SELECT m.id, COALESCE(m.merchant_name, 'Unknown'), SUM(t.amount), COUNT(*)
                FROM transactions t LEFT JOIN merchants m ON m.id = t.merchant_id
                WHERE t.user_id = ? AND t.transaction_date BETWEEN ? AND ? AND t.transaction_type = 'debit'
                  AND t.deleted_at IS NULL %s
                GROUP BY m.id, m.merchant_name
                ORDER BY SUM(t.amount) DESC
                LIMIT ?""".formatted(categoryClause);
        return jdbc.query(sql,
                (rs, i) -> new MerchantAmount(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getLong(4)),
                args.toArray());
    }

    /** Income and expense per calendar month, including months with no activity (as zero). */
    public List<MonthTotals> monthlyTotals(UUID userId, YearMonth from, YearMonth to) {
        Map<YearMonth, MonthTotals> byMonth = new TreeMap<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            byMonth.put(month, new MonthTotals(month, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        jdbc.query("""
                SELECT EXTRACT(YEAR FROM transaction_date)::int, EXTRACT(MONTH FROM transaction_date)::int,
                       COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'credit'), 0),
                       COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'debit'), 0)
                FROM transactions
                WHERE user_id = ? AND transaction_date BETWEEN ? AND ? AND deleted_at IS NULL
                GROUP BY 1, 2""",
                rs -> {
                    YearMonth month = YearMonth.of(rs.getInt(1), rs.getInt(2));
                    byMonth.put(month, new MonthTotals(month, rs.getBigDecimal(3), rs.getBigDecimal(4)));
                },
                userId, Date.valueOf(from.atDay(1)), Date.valueOf(to.atEndOfMonth()));
        return new ArrayList<>(byMonth.values());
    }

    /** One category's monthly spend, including zero months. */
    public Map<YearMonth, BigDecimal> monthlyCategorySpend(UUID userId, UUID categoryId, YearMonth from, YearMonth to) {
        Map<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            byMonth.put(month, BigDecimal.ZERO);
        }
        jdbc.query("""
                SELECT EXTRACT(YEAR FROM transaction_date)::int, EXTRACT(MONTH FROM transaction_date)::int, SUM(amount)
                FROM transactions
                WHERE user_id = ? AND category_id = ? AND transaction_type = 'debit'
                  AND transaction_date BETWEEN ? AND ? AND deleted_at IS NULL
                GROUP BY 1, 2""",
                rs -> {
                    byMonth.put(YearMonth.of(rs.getInt(1), rs.getInt(2)), rs.getBigDecimal(3));
                },
                userId, categoryId, Date.valueOf(from.atDay(1)), Date.valueOf(to.atEndOfMonth()));
        return byMonth;
    }

    /** One category's monthly spend and transaction count, zero-filled. */
    public List<MonthAmount> monthlyCategorySeries(UUID userId, UUID categoryId, YearMonth from, YearMonth to) {
        Map<YearMonth, MonthAmount> byMonth = new TreeMap<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            byMonth.put(month, new MonthAmount(month, BigDecimal.ZERO, 0));
        }
        jdbc.query("""
                SELECT EXTRACT(YEAR FROM transaction_date)::int, EXTRACT(MONTH FROM transaction_date)::int,
                       SUM(amount), COUNT(*)
                FROM transactions
                WHERE user_id = ? AND category_id = ? AND transaction_type = 'debit'
                  AND transaction_date BETWEEN ? AND ? AND deleted_at IS NULL
                GROUP BY 1, 2""",
                rs -> {
                    YearMonth month = YearMonth.of(rs.getInt(1), rs.getInt(2));
                    byMonth.put(month, new MonthAmount(month, rs.getBigDecimal(3), rs.getLong(4)));
                },
                userId, categoryId, Date.valueOf(from.atDay(1)), Date.valueOf(to.atEndOfMonth()));
        return new ArrayList<>(byMonth.values());
    }

    public List<MethodAmount> spendingByPaymentMethod(UUID userId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT COALESCE(payment_method, 'other'), SUM(amount), COUNT(*)
                FROM transactions
                WHERE user_id = ? AND transaction_date BETWEEN ? AND ? AND transaction_type = 'debit'
                  AND deleted_at IS NULL
                GROUP BY 1 ORDER BY 2 DESC""",
                (rs, i) -> new MethodAmount(rs.getString(1), rs.getBigDecimal(2), rs.getLong(3)),
                userId, Date.valueOf(start), Date.valueOf(end));
    }

    /** Income grouped by category (e.g. Salary, Refunds) for the "income by source" breakdown. */
    public List<CategoryAmount> incomeBySource(UUID userId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT COALESCE(s.id, c.id), COALESCE(s.subcategory_name, c.category_name, 'Other income'),
                       c.color_hex, SUM(t.amount), COUNT(*)
                FROM transactions t
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN subcategories s ON s.id = t.subcategory_id
                WHERE t.user_id = ? AND t.transaction_date BETWEEN ? AND ? AND t.transaction_type = 'credit'
                  AND t.deleted_at IS NULL
                GROUP BY 1, 2, 3 ORDER BY 4 DESC""",
                (rs, i) -> new CategoryAmount(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBigDecimal(4), rs.getLong(5)),
                userId, Date.valueOf(start), Date.valueOf(end));
    }

    /** Daily spending, zero-filled, for prediction and volatility. */
    public List<DayAmount> dailySpending(UUID userId, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            byDay.put(day, BigDecimal.ZERO);
        }
        jdbc.query("""
                SELECT transaction_date, SUM(amount) FROM transactions
                WHERE user_id = ? AND transaction_date BETWEEN ? AND ? AND transaction_type = 'debit'
                  AND deleted_at IS NULL
                GROUP BY transaction_date""",
                rs -> {
                    byDay.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2));
                },
                userId, Date.valueOf(start), Date.valueOf(end));
        return byDay.entrySet().stream().map(e -> new DayAmount(e.getKey(), e.getValue())).toList();
    }

    /** Spending on transactions the user or the recurring detector flagged as recurring. */
    public BigDecimal recurringSpend(UUID userId, LocalDate start, LocalDate end) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM transactions
                WHERE user_id = ? AND transaction_date BETWEEN ? AND ? AND transaction_type = 'debit'
                  AND is_recurring = TRUE AND deleted_at IS NULL""",
                BigDecimal.class, userId, Date.valueOf(start), Date.valueOf(end));
        return Money.nz(value);
    }

    /** Opening balances of active accounts plus all-time net flow; null when the user has no accounts. */
    public BigDecimal estimatedBalance(UUID userId) {
        return jdbc.queryForObject("""
                SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM accounts WHERE user_id = ?) THEN NULL ELSE
                    (SELECT COALESCE(SUM(opening_balance), 0) FROM accounts WHERE user_id = ? AND is_active)
                  + (SELECT COALESCE(SUM(CASE WHEN transaction_type = 'credit' THEN amount
                                              WHEN transaction_type = 'debit' THEN -amount ELSE 0 END), 0)
                     FROM transactions WHERE user_id = ? AND deleted_at IS NULL) END""",
                BigDecimal.class, userId, userId, userId);
    }

    public LocalDate firstTransactionDate(UUID userId) {
        Date date = jdbc.queryForObject(
                "SELECT MIN(transaction_date) FROM transactions WHERE user_id = ? AND deleted_at IS NULL",
                Date.class, userId);
        return date == null ? null : date.toLocalDate();
    }
}
