package com.spendos.imports.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/** Zero-based column indexes of the fields found in a statement; -1 when absent. */
public record ColumnMapping(int date, int description, int amount, int debit, int credit, int type, int reference,
                            int paymentMethod, int category) {

    public boolean hasAmount() {
        return amount >= 0 || debit >= 0 || credit >= 0;
    }

    public boolean isComplete() {
        return date >= 0 && description >= 0 && hasAmount();
    }

    public Map<String, Integer> describe() {
        Map<String, Integer> columns = new LinkedHashMap<>();
        columns.put("date", date);
        columns.put("description", description);
        columns.put("amount", amount);
        columns.put("debit", debit);
        columns.put("credit", credit);
        columns.put("type", type);
        columns.put("reference", reference);
        columns.put("paymentMethod", paymentMethod);
        columns.values().removeIf(index -> index < 0);
        return columns;
    }
}
