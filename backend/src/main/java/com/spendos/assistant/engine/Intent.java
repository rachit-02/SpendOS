package com.spendos.assistant.engine;

/** What a question asks for. Each intent has one deterministic handler in the assistant service. */
public enum Intent {
    SPENDING_CHANGE("spending_change"),
    TOP_CATEGORIES("top_categories"),
    CATEGORY_SPEND("category_spend"),
    MERCHANT_SPEND("merchant_spend"),
    LARGEST_PURCHASES("largest_purchases"),
    SUBSCRIPTIONS("subscriptions"),
    COMPARE_MONTHS("compare_months"),
    MONTH_SUMMARY("month_summary"),
    UNKNOWN("unknown");

    private final String code;

    Intent(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
