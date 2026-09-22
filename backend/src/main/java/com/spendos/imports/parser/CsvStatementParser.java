package com.spendos.imports.parser;

import com.spendos.common.exception.ApiException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;

/**
 * Turns an uploaded statement into raw rows: decodes the bytes, detects the delimiter, finds the header
 * row (skipping bank preamble lines) or infers columns from content when there is no header, and maps
 * columns to fields. Structural problems throw {@link ApiException} so the upload fails fast with 400.
 */
public final class CsvStatementParser {

    public static final int MAX_ROWS = 200_000;
    private static final char[] DELIMITERS = {',', ';', '\t', '|'};
    private static final int HEADER_SEARCH_LIMIT = 25;

    private static final Set<String> DATE_HEADERS = Set.of("date", "transactiondate", "txndate", "valuedate",
            "postingdate", "trandate", "transdate", "bookingdate");
    private static final Set<String> DESCRIPTION_HEADERS = Set.of("description", "narration", "particulars", "details",
            "remarks", "merchant", "merchantname", "payee", "name", "transactiondetails", "transactionremarks",
            "transactiondescription", "memo");
    private static final Set<String> AMOUNT_HEADERS = Set.of("amount", "amt", "transactionamount", "txnamount",
            "amountinr", "amountrs", "value");
    private static final Set<String> DEBIT_HEADERS = Set.of("debit", "withdrawal", "withdrawalamt", "withdrawalamount",
            "debitamount", "dr", "withdrawals", "debits", "withdrawalamtinr", "debitinr");
    private static final Set<String> CREDIT_HEADERS = Set.of("credit", "deposit", "depositamt", "depositamount",
            "creditamount", "cr", "deposits", "credits", "depositamtinr", "creditinr");
    private static final Set<String> TYPE_HEADERS = Set.of("type", "transactiontype", "drcr", "crdr", "txntype",
            "debitcredit", "creditdebit", "direction");
    private static final Set<String> REFERENCE_HEADERS = Set.of("reference", "ref", "refno", "referencenumber",
            "referenceno", "chequeno", "chqrefno", "chqno", "utr", "utrno", "transactionid", "txnid", "refnochequeno");
    private static final Set<String> MODE_HEADERS = Set.of("mode", "paymentmethod", "paymentmode", "channel");
    private static final Set<String> CATEGORY_HEADERS = Set.of("category");

    /** A data row: its 1-based line number in the file, the raw text, and the cell values. */
    public record RawRow(int rowNumber, String rawLine, List<String> cells) {
        public String cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index).trim() : null;
        }
    }

    /** {@code format} is "csv" or "pdf"; {@code delimiter} is only meaningful for CSV. */
    public record ParsedFile(ColumnMapping mapping, List<RawRow> rows, char delimiter, boolean hasHeader,
                             List<String> header, String format) {
    }

    private CsvStatementParser() {
    }

    public static ParsedFile parse(byte[] bytes) {
        String content = decode(bytes);
        if (content.isBlank()) {
            throw invalid("The file is empty");
        }
        char delimiter = detectDelimiter(content);
        List<CSVRecord> records = readRecords(content, delimiter);
        if (records.isEmpty()) {
            throw invalid("No rows found in the file");
        }

        int headerIndex = findHeader(records);
        ColumnMapping mapping;
        List<String> header;
        int firstDataIndex;
        if (headerIndex >= 0) {
            header = values(records.get(headerIndex));
            mapping = mapHeader(header);
            firstDataIndex = headerIndex + 1;
        } else {
            header = List.of();
            mapping = inferColumns(records);
            firstDataIndex = 0;
        }
        if (!mapping.isComplete()) {
            throw invalid("Could not find date, description and amount columns. "
                    + "Make sure the file has a header row such as: Date, Description, Amount");
        }

        List<RawRow> rows = new ArrayList<>();
        for (int i = firstDataIndex; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            List<String> cells = values(record);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(new RawRow((int) record.getRecordNumber(), String.join(String.valueOf(delimiter), cells), cells));
            if (rows.size() > MAX_ROWS) {
                throw invalid("Files are limited to " + MAX_ROWS + " rows; split the statement into smaller files");
            }
        }
        if (rows.isEmpty()) {
            throw invalid("The file has a header but no transaction rows");
        }
        return new ParsedFile(mapping, rows, delimiter, headerIndex >= 0, header, "csv");
    }

    /** UTF-8 (with or without BOM) first; falls back to Windows-1252, common for bank exports. */
    static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException exception) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** Picks the delimiter that splits the first lines into the most consistent, largest column count. */
    static char detectDelimiter(String content) {
        List<String> lines = content.lines().filter(line -> !line.isBlank()).limit(20).toList();
        char best = ',';
        double bestScore = -1;
        for (char delimiter : DELIMITERS) {
            int[] counts = lines.stream().mapToInt(line -> countOutsideQuotes(line, delimiter)).toArray();
            if (counts.length == 0) {
                continue;
            }
            int max = Arrays.stream(counts).max().orElse(0);
            if (max == 0) {
                continue;
            }
            long consistent = Arrays.stream(counts).filter(count -> count == max).count();
            double score = max * ((double) consistent / counts.length);
            if (score > bestScore) {
                bestScore = score;
                best = delimiter;
            }
        }
        return best;
    }

    private static int countOutsideQuotes(String line, char delimiter) {
        int count = 0;
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                count++;
            }
        }
        return count;
    }

    private static List<CSVRecord> readRecords(String content, char delimiter) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .build();
        try (CSVParser parser = CSVParser.parse(new StringReader(content), format)) {
            return parser.getRecords();
        } catch (IOException | java.io.UncheckedIOException | IllegalStateException exception) {
            throw invalid("The file is not valid CSV: " + rootMessage(exception));
        }
    }

    private static int findHeader(List<CSVRecord> records) {
        for (int i = 0; i < Math.min(HEADER_SEARCH_LIMIT, records.size()); i++) {
            if (looksLikeHeader(values(records.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /** A statement header row: names a date and an amount column, and holds no date values. Shared with PDF import. */
    static boolean looksLikeHeader(List<String> cells) {
        List<String> normalized = cells.stream().map(CsvStatementParser::normalize).toList();
        boolean hasDate = normalized.stream().anyMatch(h -> DATE_HEADERS.contains(h) || h.endsWith("date"));
        boolean hasAmount = normalized.stream().anyMatch(h -> AMOUNT_HEADERS.contains(h)
                || DEBIT_HEADERS.contains(h) || CREDIT_HEADERS.contains(h) || h.contains("amount")
                || h.startsWith("withdrawal") || h.startsWith("deposit"));
        boolean anyDateValue = cells.stream().anyMatch(DateParser::looksLikeDate);
        return hasDate && hasAmount && !anyDateValue;
    }

    static ColumnMapping mapHeader(List<String> header) {
        List<String> normalized = header.stream().map(CsvStatementParser::normalize).toList();
        // Prefer the transaction date over value/posting dates when a statement has several.
        int date = findPreferred(normalized, DATE_PREFERENCE);
        if (date < 0) {
            date = findMatching(normalized, h -> h.endsWith("date") && !h.contains("value"));
        }
        if (date < 0) {
            date = findMatching(normalized, h -> h.contains("date"));
        }
        int description = find(normalized, DESCRIPTION_HEADERS);
        if (description < 0) {
            description = findMatching(normalized, h -> h.contains("narration") || h.contains("description")
                    || h.contains("particular") || h.contains("detail") || h.contains("merchant"));
        }
        int debit = find(normalized, DEBIT_HEADERS);
        if (debit < 0) {
            debit = findMatching(normalized, h -> h.contains("withdrawal") || h.startsWith("debit"));
        }
        int credit = find(normalized, CREDIT_HEADERS);
        if (credit < 0) {
            credit = findMatching(normalized, h -> h.contains("deposit") || h.startsWith("credit"));
        }
        int amount = find(normalized, AMOUNT_HEADERS);
        if (amount < 0 && (debit < 0 || credit < 0)) {
            amount = findMatching(normalized, h -> h.contains("amount") && !h.contains("balance"));
        }
        return new ColumnMapping(date, description, amount, debit, credit,
                find(normalized, TYPE_HEADERS), find(normalized, REFERENCE_HEADERS), find(normalized, MODE_HEADERS),
                find(normalized, CATEGORY_HEADERS));
    }

    /** Headerless files: the date column parses as dates, amounts parse as numbers, the widest text is the description. */
    static ColumnMapping inferColumns(List<CSVRecord> records) {
        List<CSVRecord> sample = records.subList(0, Math.min(20, records.size()));
        int columns = sample.stream().mapToInt(CSVRecord::size).max().orElse(0);
        int date = -1;
        int description = -1;
        List<Integer> numeric = new ArrayList<>();
        double widestText = 0;
        for (int column = 0; column < columns; column++) {
            int dates = 0;
            int numbers = 0;
            int filled = 0;
            double length = 0;
            for (CSVRecord record : sample) {
                String value = column < record.size() ? record.get(column).trim() : "";
                if (value.isEmpty()) {
                    continue;
                }
                filled++;
                length += value.length();
                if (DateParser.looksLikeDate(value)) {
                    dates++;
                } else if (AmountParser.parse(value) != null) {
                    numbers++;
                }
            }
            if (filled == 0) {
                continue;
            }
            if (date < 0 && dates >= filled * 0.8) {
                date = column;
            } else if (numbers >= filled * 0.8) {
                numeric.add(column);
            } else if (length / filled > widestText) {
                widestText = length / filled;
                description = column;
            }
        }
        int amount = numeric.isEmpty() ? -1 : numeric.get(0);
        return new ColumnMapping(date, description, amount, -1, -1, -1, -1, -1, -1);
    }

    private static final List<String> DATE_PREFERENCE = List.of("transactiondate", "txndate", "trandate",
            "transdate", "date", "postingdate", "bookingdate", "valuedate");

    private static int findPreferred(List<String> normalized, List<String> preferred) {
        for (String name : preferred) {
            int index = normalized.indexOf(name);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    private static int find(List<String> normalized, Set<String> candidates) {
        for (int i = 0; i < normalized.size(); i++) {
            if (candidates.contains(normalized.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatching(List<String> normalized, java.util.function.Predicate<String> predicate) {
        for (int i = 0; i < normalized.size(); i++) {
            if (predicate.test(normalized.get(i))) {
                return i;
            }
        }
        return -1;
    }

    static String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static List<String> values(CSVRecord record) {
        List<String> values = new ArrayList<>(record.size());
        record.forEach(value -> values.add(value == null ? "" : value));
        return values;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", message);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
