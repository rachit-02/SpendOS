package com.spendos.merchants.repository;

import com.spendos.merchants.dto.MerchantDtos.MappingResponse;
import com.spendos.merchants.dto.MerchantDtos.MerchantResponse;
import com.spendos.common.util.Times;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * User-scoped merchant reads. Merchants are global, so a user only ever sees verified (seeded)
 * merchants and merchants that appear in their own transactions, never names created from other
 * users' statements.
 */
@Repository
public class MerchantQueries {

    public record UserMerchant(UUID id, String name, long transactionCount) {
    }

    /** Sort keys accepted by {@link #list}; values are trusted SQL fragments, never user input. */
    public enum Sort {
        NAME("m.merchant_name_lower ASC"),
        TRANSACTIONS("COALESCE(s.cnt, 0) DESC, m.merchant_name_lower ASC"),
        LAST_TRANSACTION("s.last_date DESC NULLS LAST, m.merchant_name_lower ASC");

        private final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    private static final String FROM = """
            FROM merchants m
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS cnt, AVG(t.amount) AS avg_amount, MAX(t.transaction_date) AS last_date
                FROM transactions t
                WHERE t.user_id = ? AND t.merchant_id = m.id AND t.deleted_at IS NULL
            ) s ON TRUE
            LEFT JOIN user_merchant_mappings um
                   ON um.user_id = ? AND um.normalized_merchant_id = m.id
                  AND LOWER(um.raw_merchant_name) = m.merchant_name_lower
            LEFT JOIN categories c ON c.id = COALESCE(um.category_id, m.category_id)
            WHERE m.is_active AND (m.is_verified OR s.cnt > 0)""";

    private final JdbcTemplate jdbc;

    public MerchantQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(UUID userId, String searchText, UUID categoryId) {
        List<Object> args = new ArrayList<>(List.of(userId, userId));
        String where = filters(searchText, categoryId, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public List<MerchantResponse> list(UUID userId, String searchText, UUID categoryId, Sort sort, int limit, long offset) {
        List<Object> args = new ArrayList<>(List.of(userId, userId));
        String where = filters(searchText, categoryId, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(select() + where + " ORDER BY " + sort.orderBy + " LIMIT ? OFFSET ?",
                MerchantQueries::merchant, args.toArray());
    }

    public Optional<MerchantResponse> find(UUID userId, UUID merchantId) {
        return jdbc.query(select() + " AND m.id = ?", MerchantQueries::merchant, userId, userId, merchantId)
                .stream().findFirst();
    }

    /** The user's merchants that are not verified (typically created from raw statement text). */
    public List<UserMerchant> unverifiedMerchants(UUID userId) {
        return jdbc.query("""
                SELECT m.id, m.merchant_name, COUNT(*)
                FROM transactions t JOIN merchants m ON m.id = t.merchant_id
                WHERE t.user_id = ? AND t.deleted_at IS NULL AND NOT m.is_verified
                GROUP BY m.id, m.merchant_name""",
                (rs, i) -> new UserMerchant(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)), userId);
    }

    public long mappingCount(UUID userId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM user_merchant_mappings WHERE user_id = ?", Long.class, userId);
        return total == null ? 0 : total;
    }

    public List<MappingResponse> mappings(UUID userId, int limit, long offset) {
        return jdbc.query("""
                SELECT um.id, um.raw_merchant_name, m.id, m.merchant_name, c.id, c.category_name,
                       (SELECT COUNT(*) FROM transactions t
                        WHERE t.user_id = um.user_id AND t.merchant_id = um.normalized_merchant_id AND t.deleted_at IS NULL),
                       um.created_at, um.updated_at
                FROM user_merchant_mappings um
                JOIN merchants m ON m.id = um.normalized_merchant_id
                LEFT JOIN categories c ON c.id = um.category_id
                WHERE um.user_id = ?
                ORDER BY um.updated_at DESC
                LIMIT ? OFFSET ?""",
                (rs, i) -> new MappingResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getLong(7), null,
                        instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9))),
                userId, limit, offset);
    }

    /** Points the user's matching transactions at the canonical merchant; returns rows changed. */
    public int remapTransactions(UUID userId, String rawName, UUID targetMerchantId) {
        return jdbc.update("""
                UPDATE transactions t
                SET merchant_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE t.user_id = ? AND t.deleted_at IS NULL
                  AND (LOWER(t.raw_description) = ? OR t.merchant_id IN (
                        SELECT id FROM merchants WHERE merchant_name_lower = ?))""",
                targetMerchantId, userId, rawName.toLowerCase(Locale.ROOT), rawName.toLowerCase(Locale.ROOT));
    }

    /**
     * Applies the user's category to their transactions at this merchant. Transactions the user
     * categorised by hand keep their category: an explicit edit always wins.
     */
    public int recategorizeTransactions(UUID userId, UUID merchantId, UUID categoryId) {
        return jdbc.update("""
                UPDATE transactions
                SET category_id = ?, subcategory_id = NULL, categorization_source = 'merchant_mapping',
                    categorization_confidence = 1.00, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND merchant_id = ? AND deleted_at IS NULL
                  AND COALESCE(categorization_source, '') <> 'user'""",
                categoryId, userId, merchantId);
    }

    private static String select() {
        return """
                SELECT m.id, m.merchant_name, c.id, c.category_name, m.logo_url, m.website, m.is_verified,
                       m.confidence_score, um.category_id IS NOT NULL, COALESCE(s.cnt, 0), s.avg_amount, s.last_date
                """ + FROM;
    }

    private static String filters(String searchText, UUID categoryId, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (searchText != null && !searchText.isBlank()) {
            String escaped = searchText.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            where.append(" AND m.merchant_name_lower LIKE ? ESCAPE '\\'");
            args.add("%" + escaped + "%");
        }
        if (categoryId != null) {
            where.append(" AND COALESCE(um.category_id, m.category_id) = ?");
            args.add(categoryId);
        }
        return where.toString();
    }

    private static MerchantResponse merchant(ResultSet rs, int row) throws SQLException {
        Date last = rs.getDate(12);
        return new MerchantResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getBigDecimal(8),
                rs.getBoolean(9), rs.getLong(10),
                rs.getBigDecimal(11) == null ? null : rs.getBigDecimal(11).setScale(2, java.math.RoundingMode.HALF_UP),
                last == null ? null : last.toLocalDate());
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : Times.utc(timestamp.toLocalDateTime());
    }
}
