package com.spendos.categories.engine;

import com.spendos.categories.dto.CategoryResponse;
import com.spendos.categories.service.CategoryService;
import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.domain.UserMerchantMapping;
import com.spendos.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic categorization, applied in priority order:
 * 1. the user's own merchant mapping (their corrections always win),
 * 2. transfers,
 * 3. the canonical merchant's category,
 * 4. keyword rules on the statement text,
 * 5. fallback: credits are Income, everything else Other (low confidence, flagged for review).
 */
@Component
public class CategoryEngine {

    public static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.60");

    public record Categorization(UUID categoryId, UUID subcategoryId, BigDecimal confidence, String source) {
        public boolean needsReview() {
            return confidence.compareTo(REVIEW_THRESHOLD) < 0;
        }
    }

    private record KeywordRule(Pattern pattern, String category, String subcategory, String appliesToType) {
    }

    private static final List<KeywordRule> KEYWORD_RULES = List.of(
            rule("\\bsalary\\b|\\bsal cr\\b|payroll", "Income", "Salary", Transaction.CREDIT),
            rule("\\binterest\\b|\\bint\\.? ?cr", "Income", "Interest", Transaction.CREDIT),
            rule("\\brefund\\b|\\breversal\\b|cashback", "Income", "Refunds", Transaction.CREDIT),
            rule("\\brent\\b", "Bills", "Rent", Transaction.DEBIT),
            rule("electricity|\\bpower\\b|\\bbescom\\b|\\bmseb\\b", "Bills", "Electricity", null),
            rule("broadband|fibernet|internet", "Bills", "Internet", null),
            rule("recharge|postpaid|prepaid|mobile bill", "Bills", "Mobile", null),
            rule("\\bemi\\b|insurance|premium|\\blic\\b|loan", "Bills", null, null),
            rule("\\batm\\b|cash withdrawal|\\bcash wdl\\b|self transfer|\\bown account\\b", "Transfers", null, null),
            rule("petrol|diesel|\\bfuel\\b|filling station", "Transport", "Fuel", null),
            rule("\\bcab\\b|taxi|\\bauto\\b", "Transport", "Cab", null),
            rule("\\bmetro\\b|\\bbus\\b|railway", "Transport", "Public Transport", null),
            rule("restaurant|\\bcafe\\b|\\bcafé\\b|coffee|bakery|\\bdhaba\\b|\\bhotel\\b.*\\bfood\\b", "Food", "Restaurants", null),
            rule("grocer|supermarket|mart\\b|kirana|vegetable|fruits", "Food", "Groceries", null),
            rule("hospital|clinic|pharma|medical|chemist|diagnostic|\\blab\\b", "Healthcare", null, null),
            rule("school|college|university|tuition|course|academy", "Education", null, null),
            rule("flight|airline|airways|\\bhotel\\b|resort|travels?\\b|booking\\.com|airbnb", "Travel", null, null),
            rule("movie|cinema|theatre|theater|concert|gaming", "Entertainment", null, null),
            rule("subscription|membership|\\bgym\\b", "Subscriptions", null, null),
            rule("electronics|mobiles|laptop", "Shopping", "Electronics", null),
            rule("fashion|apparel|clothing|garments|footwear", "Shopping", "Clothing", null));

    private final CategoryService categoryService;
    private volatile Map<String, UUID> categoryIds;
    private volatile Map<String, UUID> subcategoryIds;

    public CategoryEngine(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    private static KeywordRule rule(String regex, String category, String subcategory, String type) {
        return new KeywordRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), category, subcategory, type);
    }

    /**
     * @param rawText      statement description (also used for keyword rules)
     * @param merchant     canonical merchant, if one was resolved
     * @param userMapping  the user's mapping for this raw merchant text, if any
     */
    public Categorization categorize(String rawText, Merchant merchant, UserMerchantMapping userMapping,
                                     String transactionType) {
        ensureLoaded();
        if (userMapping != null && userMapping.getCategoryId() != null) {
            return new Categorization(userMapping.getCategoryId(), null, BigDecimal.ONE,
                    Transaction.SOURCE_MERCHANT_MAPPING);
        }
        if (Transaction.TRANSFER.equals(transactionType)) {
            return new Categorization(categoryIds.get("transfers"), null, new BigDecimal("0.90"), Transaction.SOURCE_RULE);
        }
        if (merchant != null && merchant.getCategoryId() != null) {
            BigDecimal confidence = merchant.getConfidenceScore() != null ? merchant.getConfidenceScore()
                    : new BigDecimal("0.85");
            return new Categorization(merchant.getCategoryId(), merchant.getSubcategoryId(), confidence,
                    Transaction.SOURCE_MERCHANT_MAPPING);
        }
        String text = rawText == null ? "" : rawText.toLowerCase(Locale.ROOT);
        for (KeywordRule rule : KEYWORD_RULES) {
            if ((rule.appliesToType() == null || rule.appliesToType().equals(transactionType))
                    && rule.pattern().matcher(text).find()) {
                UUID categoryId = categoryIds.get(rule.category().toLowerCase(Locale.ROOT));
                UUID subcategoryId = rule.subcategory() == null ? null
                        : subcategoryIds.get(key(rule.category(), rule.subcategory()));
                if (categoryId != null) {
                    return new Categorization(categoryId, subcategoryId, new BigDecimal("0.75"), Transaction.SOURCE_RULE);
                }
            }
        }
        if (Transaction.CREDIT.equals(transactionType)) {
            return new Categorization(categoryIds.get("income"), null, new BigDecimal("0.50"), Transaction.SOURCE_RULE);
        }
        return new Categorization(categoryIds.get("other"), null, new BigDecimal("0.30"), Transaction.SOURCE_RULE);
    }

    private void ensureLoaded() {
        if (categoryIds == null) {
            synchronized (this) {
                if (categoryIds == null) {
                    Map<String, UUID> categories = new HashMap<>();
                    Map<String, UUID> subcategories = new HashMap<>();
                    for (CategoryResponse category : categoryService.listCategories()) {
                        categories.put(category.categoryName().toLowerCase(Locale.ROOT), category.id());
                        for (CategoryResponse.SubcategoryResponse sub : category.subcategories()) {
                            subcategories.put(key(category.categoryName(), sub.subcategoryName()), sub.id());
                        }
                    }
                    subcategoryIds = subcategories;
                    categoryIds = categories;
                }
            }
        }
    }

    private static String key(String category, String subcategory) {
        return (category + "/" + subcategory).toLowerCase(Locale.ROOT);
    }
}
