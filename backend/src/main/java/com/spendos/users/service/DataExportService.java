package com.spendos.users.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.audit.service.AuditService;
import com.spendos.common.util.CsvCells;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Download my data" (SECURITY.md, User Data Rights): a ZIP of everything stored about the user,
 * as CSV for tabular data and JSON for the profile and audit trail. Credentials (the password
 * hash) are never included. Every query is scoped to the requesting user.
 */
@Service
public class DataExportService {

    /** Columns never exported. */
    private static final Set<String> EXCLUDED_COLUMNS = Set.of("password_hash", "tokens_valid_after");

    /** File name → user-scoped query. Table names are fixed here, never taken from input. */
    private static final Map<String, String> CSV_FILES = new LinkedHashMap<>();

    static {
        CSV_FILES.put("accounts.csv", "SELECT * FROM accounts WHERE user_id = ? ORDER BY created_at");
        CSV_FILES.put("transactions.csv", """
                SELECT t.*, m.merchant_name, c.category_name, s.subcategory_name
                FROM transactions t
                LEFT JOIN merchants m ON m.id = t.merchant_id
                LEFT JOIN categories c ON c.id = t.category_id
                LEFT JOIN subcategories s ON s.id = t.subcategory_id
                WHERE t.user_id = ? ORDER BY t.transaction_date, t.created_at""");
        CSV_FILES.put("budgets.csv", "SELECT * FROM budgets WHERE user_id = ? ORDER BY created_at");
        CSV_FILES.put("budget_categories.csv", """
                SELECT bc.*, c.category_name FROM budget_categories bc
                JOIN budgets b ON b.id = bc.budget_id
                LEFT JOIN categories c ON c.id = bc.category_id
                WHERE b.user_id = ? ORDER BY b.created_at""");
        CSV_FILES.put("goals.csv", "SELECT * FROM financial_goals WHERE user_id = ? ORDER BY created_at");
        CSV_FILES.put("insights.csv", "SELECT * FROM insights WHERE user_id = ? ORDER BY created_at");
        CSV_FILES.put("recurring_payments.csv", "SELECT * FROM recurring_payments WHERE user_id = ? ORDER BY detected_at");
        CSV_FILES.put("merchant_mappings.csv", """
                SELECT um.*, m.merchant_name AS normalized_merchant_name FROM user_merchant_mappings um
                LEFT JOIN merchants m ON m.id = um.normalized_merchant_id
                WHERE um.user_id = ? ORDER BY um.created_at""");
        CSV_FILES.put("imports.csv", "SELECT * FROM import_jobs WHERE user_id = ? ORDER BY created_at");
        CSV_FILES.put("import_errors.csv", """
                SELECT e.* FROM import_errors e JOIN import_jobs j ON j.id = e.import_job_id
                WHERE j.user_id = ? ORDER BY e.created_at""");
        CSV_FILES.put("monthly_reports.csv", "SELECT * FROM monthly_reports WHERE user_id = ? ORDER BY period_year, period_month");
        CSV_FILES.put("simulations.csv", "SELECT * FROM simulations WHERE user_id = ? ORDER BY created_at");
        CSV_FILES.put("health_score_history.csv",
                "SELECT * FROM financial_health_history WHERE user_id = ? ORDER BY period_month");
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public DataExportService(JdbcTemplate jdbc, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public byte[] export(UUID userId) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("user", firstRow("SELECT * FROM users WHERE id = ?", userId));
            profile.put("preferences", firstRow("SELECT * FROM user_preferences WHERE user_id = ?", userId));
            profile.put("exportedAt", Instant.now().toString());
            writeJson(zip, "user_profile.json", profile);

            for (Map.Entry<String, String> file : CSV_FILES.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                writer.write(0xFEFF); // lets Excel open UTF-8 (₹, names) correctly
                jdbc.query(file.getValue(), (ResultSet rs) -> {
                    writeCsv(rs, writer);
                    return null;
                }, userId);
                writer.flush();
                zip.closeEntry();
            }

            writeJson(zip, "audit_logs.json", rows("""
                    SELECT action, entity_type, entity_id, old_values, new_values, created_at
                    FROM audit_logs WHERE user_id = ? ORDER BY created_at""", userId));
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(README.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        auditService.record(userId, "user", userId, AuditService.EXPORT, null, Map.of("format", "zip"));
        return bytes.toByteArray();
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        zip.closeEntry();
    }

    private static void writeCsv(ResultSet rs, Writer writer) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Integer> columns = new ArrayList<>();
        List<String> header = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getColumnLabel(i);
            if (!EXCLUDED_COLUMNS.contains(name)) {
                columns.add(i);
                header.add(name);
            }
        }
        try {
            // Not closed on purpose: closing would close the ZIP stream underneath.
            @SuppressWarnings("resource")
            CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setHeader(header.toArray(String[]::new)).build());
            while (rs.next()) {
                List<String> values = new ArrayList<>(columns.size());
                for (int column : columns) {
                    values.add(CsvCells.safe(rs.getString(column)));
                }
                printer.printRecord(values);
            }
            printer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Map<String, Object> firstRow(String sql, UUID userId) {
        List<Map<String, Object>> rows = rows(sql, userId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private List<Map<String, Object>> rows(String sql, UUID userId) {
        return jdbc.query(sql, (rs, i) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
                String name = meta.getColumnLabel(c);
                if (!EXCLUDED_COLUMNS.contains(name)) {
                    row.put(name, rs.getString(c));
                }
            }
            return row;
        }, userId);
    }

    private static final String README = """
            SpendOS data export

            user_profile.json     your account details and preferences (no password or tokens)
            accounts.csv          your bank accounts
            transactions.csv      every transaction, including deleted ones (see deleted_at)
            budgets.csv, budget_categories.csv
            goals.csv, insights.csv, recurring_payments.csv
            merchant_mappings.csv your merchant and category corrections
            imports.csv, import_errors.csv  statement imports and rows that were skipped
            monthly_reports.csv, simulations.csv, health_score_history.csv
            audit_logs.json       changes made to your data

            Cells that start with = + - or @ are prefixed with ' so spreadsheets do not run them as formulas.
            """;
}
