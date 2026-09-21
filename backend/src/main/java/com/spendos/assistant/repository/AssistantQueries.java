package com.spendos.assistant.repository;

import com.spendos.assistant.dto.AssistantDtos.RelatedTransaction;
import com.spendos.assistant.engine.QuestionParser.MerchantRef;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only, user-scoped lookups the assistant needs beyond the shared aggregates. */
@Repository
public class AssistantQueries {

    public record Filter(UUID categoryId, UUID subcategoryId, UUID merchantId) {
        public static final Filter NONE = new Filter(null, null, null);
    }

    public record MerchantTotal(BigDecimal amount, long count) {
    }

    private final JdbcTemplate jdbc;

    public AssistantQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Merchants the user has transactions with (used to recognise merchant names in questions). */
    public List<MerchantRef> merchants(UUID userId) {
        return jdbc.query("""
                SELECT DISTINCT m.id, m.merchant_name
                FROM transactions t JOIN merchants m ON m.id = t.merchant_id
                WHERE t.user_id = ? AND t.deleted_at IS NULL""",
                (rs, i) -> new MerchantRef(rs.getObject(1, UUID.class), rs.getString(2)), userId);
    }

    /** The largest debits in a date range, optionally within a category, subcategory or merchant. */
    public List<RelatedTransaction> largestDebits(UUID userId, LocalDate start, LocalDate end, Filter filter, int limit) {
        List<Object> args = new ArrayList<>(List.of(userId, Date.valueOf(start), Date.valueOf(end)));
        StringBuilder clauses = new StringBuilder();
        if (filter.categoryId() != null) {
            clauses.append(" AND t.category_id = ?");
            args.add(filter.categoryId());
        }
        if (filter.subcategoryId() != null) {
            clauses.append(" AND t.subcategory_id = ?");
            args.add(filter.subcategoryId());
        }
        if (filter.merchantId() != null) {
            clauses.append(" AND t.merchant_id = ?");
            args.add(filter.merchantId());
        }
        args.add(limit);
        String sql = """
                SELECT t.id, m.merchant_name, t.description, COALESCE(c.category_name, 'Uncategorized'), t.amount,
                       t.transaction_date
                FROM transactions t
                LEFT JOIN merchants m ON m.id = t.merchant_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ? AND t.transaction_date BETWEEN ? AND ? AND t.transaction_type = 'debit'
                  AND t.deleted_at IS NULL%s
                ORDER BY t.amount DESC, t.transaction_date DESC
                LIMIT ?""".formatted(clauses);
        return jdbc.query(sql,
                (rs, i) -> new RelatedTransaction(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBigDecimal(5), rs.getDate(6).toLocalDate()),
                args.toArray());
    }

    public MerchantTotal merchantTotal(UUID userId, UUID merchantId, LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount), 0), COUNT(*)
                FROM transactions
                WHERE user_id = ? AND merchant_id = ? AND transaction_date BETWEEN ? AND ?
                  AND transaction_type = 'debit' AND deleted_at IS NULL""",
                (rs, i) -> new MerchantTotal(rs.getBigDecimal(1), rs.getLong(2)),
                userId, merchantId, Date.valueOf(start), Date.valueOf(end));
    }
}
