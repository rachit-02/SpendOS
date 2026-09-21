package com.spendos.transactions.repository;

import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.dto.TransactionFilter;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the WHERE clause for transaction search. Always constrained to one user. */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> forUser(UUID userId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (filter.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.startDate()));
            }
            if (filter.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.endDate()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.merchantId() != null) {
                predicates.add(cb.equal(root.get("merchantId"), filter.merchantId()));
            }
            if (filter.accountId() != null) {
                predicates.add(cb.equal(root.get("accountId"), filter.accountId()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            if (filter.transactionType() != null) {
                predicates.add(cb.equal(root.get("transactionType"), filter.transactionType()));
            }
            if (filter.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }
            if (filter.recurring() != null) {
                predicates.add(cb.equal(root.get("recurring"), filter.recurring()));
            }
            if (filter.searchText() != null && !filter.searchText().isBlank()) {
                String like = "%" + escapeLike(filter.searchText().trim().toLowerCase(Locale.ROOT)) + "%";
                Join<Object, Object> merchant = root.join("merchant", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), like, '\\'),
                        cb.like(cb.lower(root.get("rawDescription")), like, '\\'),
                        cb.like(merchant.get("merchantNameLower"), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Escapes LIKE wildcards so user text is matched literally. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
