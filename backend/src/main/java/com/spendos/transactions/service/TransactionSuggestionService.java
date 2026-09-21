package com.spendos.transactions.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search-box suggestions: the user's own frequently used merchants and categories. */
@Service
public class TransactionSuggestionService {

    public record MerchantSuggestion(UUID merchantId, String merchantName, long count) {
    }

    public record CategorySuggestion(UUID categoryId, String categoryName, long count) {
    }

    public record Suggestions(List<MerchantSuggestion> merchants, List<CategorySuggestion> categories,
                              List<String> searchTerms) {
    }

    private final JdbcTemplate jdbc;

    public TransactionSuggestionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Suggestions suggest(UUID userId, String query, int limit) {
        String like = query == null || query.isBlank() ? "%"
                : "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        LocalDate since = LocalDate.now().minusDays(180);
        List<MerchantSuggestion> merchants = jdbc.query("""
                        SELECT m.id, m.merchant_name, COUNT(*) AS uses, MAX(t.transaction_date) AS last_used
                        FROM transactions t JOIN merchants m ON m.id = t.merchant_id
                        WHERE t.user_id = ? AND t.transaction_date >= ? AND m.merchant_name_lower LIKE ?
                        GROUP BY m.id, m.merchant_name
                        ORDER BY uses DESC, last_used DESC
                        LIMIT ?""",
                (rs, i) -> new MerchantSuggestion(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
                userId, since, like, limit);
        List<CategorySuggestion> categories = jdbc.query("""
                        SELECT c.id, c.category_name, COUNT(*) AS uses
                        FROM transactions t JOIN categories c ON c.id = t.category_id
                        WHERE t.user_id = ? AND t.transaction_date >= ? AND LOWER(c.category_name) LIKE ?
                        GROUP BY c.id, c.category_name
                        ORDER BY uses DESC
                        LIMIT 5""",
                (rs, i) -> new CategorySuggestion(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
                userId, since, like);
        List<String> terms = merchants.stream().map(MerchantSuggestion::merchantName).limit(3).toList();
        return new Suggestions(merchants, categories, terms);
    }
}
