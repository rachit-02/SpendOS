package com.spendos.reports.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.spendos.reports.dto.AutopsyDtos.BudgetLine;
import com.spendos.reports.dto.AutopsyDtos.CategoryDelta;
import com.spendos.reports.dto.AutopsyDtos.CategoryLine;
import com.spendos.reports.dto.AutopsyDtos.MonthlyAutopsy;
import com.spendos.reports.dto.AutopsyDtos.WatchItem;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renders the monthly autopsy as a printable A4 PDF: summary, a category bar chart (drawn with table
 * cells so it needs no image library), changes, merchants, recurring payments, unusual transactions,
 * budgets, the key insight and next month's watch list. Built-in PDF fonts lack the rupee glyph, so
 * amounts are prefixed with the ISO currency code.
 */
@Component
public class AutopsyPdfRenderer {

    private static final Color PRIMARY = new Color(79, 70, 229);
    private static final Color MUTED = new Color(107, 114, 128);
    private static final Color TRACK = new Color(229, 231, 235);

    public byte[] render(MonthlyAutopsy report) {
        NumberFormat number = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        number.setMaximumFractionDigits(0);
        String currency = report.currencyCode();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, PRIMARY);
        Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("SpendOS monthly money autopsy - " + report.period());
            document.open();
            document.add(new Paragraph("Monthly Money Autopsy", title));
            document.add(new Paragraph(report.period() + (report.isComplete() ? "" : " (month in progress)"), body));
            document.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(4);
            summary.setWidthPercentage(100);
            summaryCell(summary, "Income", money(report.income(), currency, number));
            summaryCell(summary, "Expenses", money(report.expenses(), currency, number));
            summaryCell(summary, "Savings", money(report.savings(), currency, number));
            summaryCell(summary, "Savings rate",
                    report.savingsRate().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%");
            document.add(summary);
            if (report.healthScore() != null) {
                document.add(new Paragraph("Financial health score: " + report.healthScore() + " / 100", small));
            }

            section(document, "Where the money went", heading);
            PdfPTable chart = new PdfPTable(new float[] {3, 6, 2});
            chart.setWidthPercentage(100);
            BigDecimal max = report.spendingByCategory().stream().map(CategoryLine::amount)
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
            for (CategoryLine line : report.spendingByCategory()) {
                chart.addCell(plain(line.categoryName(), body));
                chart.addCell(bar(line.amount(), max, parseColor(line.colorHex())));
                chart.addCell(right(money(line.amount(), currency, number), body));
            }
            document.add(chart);

            section(document, "What changed vs last month", heading);
            for (CategoryDelta delta : report.changes().largestIncreases()) {
                document.add(new Paragraph("Up: " + delta.category() + " +" + money(delta.amount(), currency, number)
                        + pct(delta.percentageChange()), body));
            }
            for (CategoryDelta delta : report.changes().largestDecreases()) {
                document.add(new Paragraph("Down: " + delta.category() + " " + money(delta.amount(), currency, number)
                        + pct(delta.percentageChange()), body));
            }
            if (report.changes().largestIncreases().isEmpty() && report.changes().largestDecreases().isEmpty()) {
                document.add(new Paragraph("No change from last month.", body));
            }

            section(document, "Largest merchants", heading);
            List<String[]> merchantRows = report.largestMerchants().stream()
                    .map(m -> new String[] {m.merchantName(), m.count() + " payments", money(m.amount(), currency, number)})
                    .toList();
            document.add(simpleTable(merchantRows, body));

            if (!report.recurringPayments().isEmpty()) {
                section(document, "Recurring payments", heading);
                document.add(simpleTable(report.recurringPayments().stream()
                        .map(r -> new String[] {r.merchantName(), r.frequency(), money(r.amount(), currency, number)})
                        .toList(), body));
            }
            if (!report.unusualTransactions().isEmpty()) {
                section(document, "Unusual transactions", heading);
                report.unusualTransactions().forEach(u -> add(document, "- " + stripSymbol(u.description()), body));
            }
            if (!report.budgetPerformance().isEmpty()) {
                section(document, "Budget performance", heading);
                PdfPTable budgets = new PdfPTable(new float[] {3, 6, 2});
                budgets.setWidthPercentage(100);
                for (Map.Entry<String, BudgetLine> entry : report.budgetPerformance().entrySet()) {
                    BudgetLine line = entry.getValue();
                    budgets.addCell(plain(entry.getKey(), body));
                    budgets.addCell(bar(line.spent().min(line.budget()), line.budget(),
                            line.exceeded() ? new Color(220, 38, 38) : new Color(22, 163, 74)));
                    budgets.addCell(right(line.percentage().setScale(0, RoundingMode.HALF_UP) + "% of "
                            + money(line.budget(), currency, number), body));
                }
                document.add(budgets);
            }

            section(document, "Most important insight", heading);
            add(document, stripSymbol(report.mostImportantInsight()), body);
            section(document, "Suggested action", heading);
            add(document, stripSymbol(report.suggestedAction()), body);

            if (!report.nextMonthWatchlist().isEmpty()) {
                section(document, "Watch next month", heading);
                for (WatchItem item : report.nextMonthWatchlist()) {
                    add(document, "- " + stripSymbol(item.message()), body);
                }
            }
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Generated by SpendOS from your own transactions. This is a planning aid, "
                    + "not professional financial advice.", small));
        } catch (DocumentException exception) {
            throw new IllegalStateException("Could not build the report PDF", exception);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static void section(Document document, String text, Font font) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private static void add(Document document, String text, Font font) throws DocumentException {
        document.add(new Paragraph(text, font));
    }

    private static void summaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(TRACK);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED)));
        cell.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        table.addCell(cell);
    }

    /** A horizontal bar: a nested two-column table whose first column width is the value's share. */
    static PdfPCell bar(BigDecimal value, BigDecimal max, Color color) {
        float share = max.signum() == 0 ? 0f : value.divide(max, 4, RoundingMode.HALF_UP).floatValue();
        share = Math.max(0.01f, Math.min(1f, share));
        PdfPTable bar = new PdfPTable(share >= 0.99f ? new float[] {1f} : new float[] {share, 1f - share});
        PdfPCell filled = new PdfPCell(new Phrase(" "));
        filled.setBackgroundColor(color);
        filled.setBorder(Rectangle.NO_BORDER);
        filled.setFixedHeight(10);
        bar.addCell(filled);
        if (share < 0.99f) {
            PdfPCell empty = new PdfPCell(new Phrase(" "));
            empty.setBackgroundColor(TRACK);
            empty.setBorder(Rectangle.NO_BORDER);
            empty.setFixedHeight(10);
            bar.addCell(empty);
        }
        PdfPCell holder = new PdfPCell(bar);
        holder.setBorder(Rectangle.NO_BORDER);
        holder.setVerticalAlignment(Element.ALIGN_MIDDLE);
        holder.setPaddingTop(4);
        return holder;
    }

    private static PdfPTable simpleTable(List<String[]> rows, Font font) {
        PdfPTable table = new PdfPTable(new float[] {4, 3, 3});
        table.setWidthPercentage(100);
        for (String[] row : rows) {
            table.addCell(plain(row[0], font));
            table.addCell(plain(row[1], font));
            table.addCell(right(row[2], font));
        }
        return table;
    }

    private static PdfPCell plain(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(TRACK);
        cell.setPadding(4);
        return cell;
    }

    private static PdfPCell right(String text, Font font) {
        PdfPCell cell = plain(text, font);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private static String money(BigDecimal amount, String currency, NumberFormat number) {
        String sign = amount.signum() < 0 ? "-" : "";
        return sign + currency + " " + number.format(amount.abs());
    }

    private static String pct(BigDecimal value) {
        return value == null ? " (new)" : " (" + value.setScale(0, RoundingMode.HALF_UP) + "%)";
    }

    /** Insight sentences use the rupee sign; replace it with "INR " for the built-in PDF fonts. */
    static String stripSymbol(String text) {
        return text == null ? "" : text.replace("₹", "INR ").replace("–", "-");
    }

    private static Color parseColor(String hex) {
        try {
            return hex == null ? PRIMARY : Color.decode(hex);
        } catch (NumberFormatException exception) {
            return PRIMARY;
        }
    }
}
