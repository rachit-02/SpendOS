package com.spendos.analytics.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryBreakdown;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryChange;
import com.spendos.analytics.dto.AnalyticsDtos.MethodBreakdown;
import com.spendos.analytics.dto.AnalyticsDtos.MonthlyAnalytics;
import com.spendos.analytics.dto.AnalyticsDtos.SourceAmount;
import com.spendos.audit.service.AuditService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monthly analytics as a downloadable CSV or PDF report. */
@Service
public class AnalyticsExportService {

    private final AnalyticsService analyticsService;
    private final AuditService auditService;

    public AnalyticsExportService(AnalyticsService analyticsService, AuditService auditService) {
        this.analyticsService = analyticsService;
        this.auditService = auditService;
    }

    @Transactional
    public byte[] monthlyCsv(UUID userId, Integer month, Integer year) {
        MonthlyAnalytics data = analyticsService.monthly(userId, month, year);
        auditService.record(userId, "analytics", data.period().year() + "-" + data.period().monthNumber(),
                AuditService.EXPORT, null, Map.of("format", "csv"));
        StringWriter out = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            csv.printRecord("SpendOS monthly analytics", data.period().month() + " " + data.period().year());
            csv.printRecord("Currency", data.currencyCode());
            csv.println();
            csv.printRecord("Summary", "Amount");
            csv.printRecord("Income", plain(data.income().total()));
            csv.printRecord("Expenses", plain(data.expenses().total()));
            csv.printRecord("Savings", plain(data.savings()));
            csv.printRecord("Savings rate", data.savingsRate().multiply(BigDecimal.valueOf(100)).setScale(1) + "%");
            csv.println();
            csv.printRecord("Spending by category", "Amount", "Percentage", "Transactions");
            for (CategoryBreakdown c : data.expenses().byCategory()) {
                csv.printRecord(c.categoryName(), plain(c.amount()), c.percentage(), c.count());
            }
            csv.println();
            csv.printRecord("Spending by payment method", "Amount", "Percentage", "Transactions");
            for (MethodBreakdown m : data.expenses().byPaymentMethod()) {
                csv.printRecord(m.method(), plain(m.amount()), m.percentage(), m.count());
            }
            csv.println();
            csv.printRecord("Income by source", "Amount", "Percentage");
            for (SourceAmount s : data.income().bySource()) {
                csv.printRecord(s.source(), plain(s.amount()), s.percentage());
            }
            csv.println();
            csv.printRecord("Change vs previous month", "Previous", "Current", "Change", "Change %");
            for (CategoryChange c : data.previousMonthComparison().categoryChanges()) {
                csv.printRecord(c.categoryName(), plain(c.previous()), plain(c.current()), plain(c.change()),
                        c.changePercentage() == null ? "new" : c.changePercentage());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public byte[] monthlyPdf(UUID userId, Integer month, Integer year) {
        MonthlyAnalytics data = analyticsService.monthly(userId, month, year);
        auditService.record(userId, "analytics", data.period().year() + "-" + data.period().monthNumber(),
                AuditService.EXPORT, null, Map.of("format", "pdf"));
        NumberFormat money = moneyFormat(data.currencyCode());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("SpendOS monthly analytics");
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            document.add(new Paragraph("Monthly analytics - " + data.period().month() + " " + data.period().year(), title));
            document.add(new Paragraph(" "));
            document.add(table(heading, List.of("Summary", "Amount"), List.of(
                    List.of("Income", money.format(data.income().total())),
                    List.of("Expenses", money.format(data.expenses().total())),
                    List.of("Savings", money.format(data.savings())),
                    List.of("Savings rate", data.savingsRate().multiply(BigDecimal.valueOf(100)).setScale(1) + "%"))));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Spending by category", heading));
            document.add(table(heading, List.of("Category", "Amount", "Share"), data.expenses().byCategory().stream()
                    .map(c -> List.of(c.categoryName(), money.format(c.amount()), c.percentage() + "%")).toList()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Change vs previous month", heading));
            document.add(table(heading, List.of("Category", "Previous", "Current", "Change"),
                    data.previousMonthComparison().categoryChanges().stream()
                            .map(c -> List.of(c.categoryName(), money.format(c.previous()), money.format(c.current()),
                                    c.changePercentage() == null ? "new" : c.changePercentage() + "%"))
                            .toList()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Generated by SpendOS. Figures are calculated from your imported transactions.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8)));
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not build PDF", exception);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    static PdfPTable table(Font headerFont, List<String> headers, List<List<String>> rows) {
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setGrayFill(0.92f);
            table.addCell(cell);
        }
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                PdfPCell cell = new PdfPCell(new Phrase(row.get(i)));
                if (i > 0) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                table.addCell(cell);
            }
        }
        return table;
    }

    /** Built-in PDF fonts lack the rupee glyph, so amounts use the ISO code (e.g. "INR 42,300.00"). */
    static NumberFormat moneyFormat(String currency) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return new NumberFormat() {
            @Override
            public StringBuffer format(double number, StringBuffer buffer, java.text.FieldPosition position) {
                return buffer.append(currency).append(' ').append(format.format(number));
            }

            @Override
            public StringBuffer format(long number, StringBuffer buffer, java.text.FieldPosition position) {
                return buffer.append(currency).append(' ').append(format.format(number));
            }

            @Override
            public Number parse(String source, java.text.ParsePosition position) {
                return format.parse(source, position);
            }
        };
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
