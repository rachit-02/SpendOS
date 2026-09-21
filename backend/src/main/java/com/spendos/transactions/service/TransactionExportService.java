package com.spendos.transactions.service;

import com.spendos.audit.service.AuditService;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.dto.TransactionFilter;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.transactions.repository.TransactionSpecifications;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exports the user's transactions as CSV (filtered, or an explicit selection). */
@Service
public class TransactionExportService {

    public static final int MAX_EXPORT_ROWS = 100_000;
    static final String[] HEADER = {"Date", "Type", "Amount", "Currency", "Merchant", "Category", "Subcategory",
            "Description", "Payment method", "Reference", "Recurring"};

    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public TransactionExportService(TransactionRepository transactionRepository, AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public String exportCsv(UUID userId, TransactionFilter filter, Collection<UUID> ids) {
        List<Transaction> rows;
        if (ids != null && !ids.isEmpty()) {
            rows = transactionRepository.findByUserIdAndIdIn(userId, ids).stream()
                    .sorted((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()))
                    .toList();
        } else {
            rows = transactionRepository.findAll(TransactionSpecifications.forUser(userId, filter),
                    org.springframework.data.domain.PageRequest.of(0, MAX_EXPORT_ROWS,
                            Sort.by(Sort.Direction.DESC, "transactionDate", "createdAt"))).getContent();
        }
        auditService.record(userId, "transaction", "bulk", AuditService.EXPORT, null,
                Map.of("rows", rows.size(), "format", "csv"));
        return toCsv(rows);
    }

    static String toCsv(List<Transaction> rows) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADER).build())) {
            for (Transaction t : rows) {
                printer.printRecord(
                        t.getTransactionDate(), t.getTransactionType(), t.getAmount().toPlainString(), t.getCurrencyCode(),
                        safe(t.getMerchant() != null ? t.getMerchant().getMerchantName() : t.getRawDescription()),
                        t.getCategory() != null ? t.getCategory().getCategoryName() : "",
                        t.getSubcategory() != null ? t.getSubcategory().getSubcategoryName() : "",
                        safe(t.getDescription()), nz(t.getPaymentMethod()), safe(t.getExternalReference()),
                        t.isRecurring() ? "yes" : "no");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toString();
    }

    static String safe(String value) {
        return com.spendos.common.util.CsvCells.safe(value);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
