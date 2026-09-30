package com.spendos.imports.parser;

import com.spendos.common.exception.ApiException;
import com.spendos.imports.parser.CsvStatementParser.ParsedFile;
import com.spendos.imports.parser.CsvStatementParser.RawRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Reads the transaction table out of a text-based PDF bank statement and returns the same
 * {@link ParsedFile} as the CSV parser, so validation, duplicate detection, merchant matching and
 * categorization are shared.
 *
 * <p>Works from glyph coordinates rather than extracted text, because text order alone cannot tell a
 * withdrawal from a deposit or a wrapped narration from the next row:
 * <ol>
 *   <li>Every character is taken with its position and grouped into lines by baseline.</li>
 *   <li>The table header is found with the CSV parser's column vocabulary (Date, Narration,
 *       Withdrawal, ...). Headers stacked over two or three lines ("Withdrawal / Amt.") are merged by
 *       horizontal overlap.</li>
 *   <li>Column boundaries are placed in the vertical whitespace ("gutters") between columns, learned
 *       from the data rows, falling back to midpoints between header cells.</li>
 *   <li>A line with a date in the date column starts a transaction. Nearby lines with text only in
 *       text columns are wrapped continuations, attached to whichever row is vertically closer. Other
 *       lines (account details, summaries, footers) are ignored.</li>
 * </ol>
 * Scanned (image-only) PDFs have no text to read and are rejected with an explanation; OCR is not
 * supported.
 */
public final class PdfStatementParser {

    static final int MAX_PAGES = 300;
    /** Pages with fewer characters than this count as having no text layer. */
    static final int MIN_TEXT_CHARS_PER_PAGE = 20;
    private static final long TIME_BUDGET_MS = 30_000;
    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern PAGE_FOOTER = Pattern.compile("(?i)^page\\s*\\d+(\\s*(of|/)\\s*\\d+)?$");
    private static final Pattern BALANCE_ROW = Pattern.compile(
            "(?i)^(opening|closing)\\s+balance|^balance\\s+(b/?f|c/?f|brought|carried)|^(b/?f|c/?f)\\b");
    private static final Logger log = LoggerFactory.getLogger(PdfStatementParser.class);

    /**
     * One character with its position (PDF units, y grows downwards). {@code em} and {@code space} are
     * measured from the glyph itself: the font size declared in the PDF is unreliable (Chrome reports
     * 12pt for text drawn at about 7pt, which hides word and column gaps).
     */
    record Glyph(float x, float width, float y, float em, float space, boolean spaceBefore, String text) {
        float right() {
            return x + width;
        }
    }

    /** Characters sharing a baseline, left to right. */
    record Line(int page, float y, float height, List<Glyph> glyphs) {
        float left() {
            return glyphs.get(0).x();
        }

        String text() {
            return join(glyphs);
        }
    }

    /** A header cell: its text and horizontal extent. */
    record HeaderCell(String text, float left, float right) {
        float center() {
            return (left + right) / 2;
        }
    }

    record Header(List<HeaderCell> cells, float bottom, int lastLineIndex, ColumnMapping mapping) {
    }

    private PdfStatementParser() {
    }

    public static boolean isPdf(byte[] bytes) {
        // The header may follow a few junk bytes (some generators prepend a BOM or whitespace).
        int limit = Math.min(bytes.length - MAGIC.length, 1024);
        for (int start = 0; start <= limit; start++) {
            boolean match = true;
            for (int i = 0; i < MAGIC.length && match; i++) {
                match = bytes[start + i] == MAGIC[i];
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    public static ParsedFile parse(byte[] bytes) {
        PDDocument document;
        try {
            document = Loader.loadPDF(bytes);
        } catch (InvalidPasswordException exception) {
            throw invalid("PDF_PASSWORD_PROTECTED", "This PDF is password-protected. Open it with its password, "
                    + "save or print a copy without a password (or download an unprotected statement from your bank), "
                    + "and upload that copy.");
        } catch (IOException | RuntimeException exception) {
            log.info("Unreadable PDF upload | reason={}", exception.getClass().getSimpleName());
            throw invalid("PDF_UNREADABLE", "This PDF is damaged or incomplete and could not be read. "
                    + "Download the statement again and upload the new copy.");
        }
        try (document) {
            return extractTable(document);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // Stack traces carry no statement content; they are needed to fix layouts we don't handle.
            log.warn("PDF statement could not be parsed", exception);
            throw invalid("PDF_UNREADABLE", "This PDF could not be read. Download the statement again, or export "
                    + "it as CSV from your bank and upload that instead.");
        }
    }

    private static ParsedFile extractTable(PDDocument document) throws IOException {
        int pages = document.getNumberOfPages();
        if (pages == 0) {
            throw invalid("PDF_EMPTY", "This PDF has no pages.");
        }
        if (pages > MAX_PAGES) {
            throw invalid("PDF_TOO_LONG", "PDF statements are limited to " + MAX_PAGES
                    + " pages; download a shorter date range.");
        }
        long started = System.currentTimeMillis();
        List<List<Line>> pageLines = new ArrayList<>();
        List<Integer> imageOnlyPages = new ArrayList<>();
        int textPages = 0;
        int unreadable = 0;
        int total = 0;
        for (int page = 1; page <= pages; page++) {
            if (System.currentTimeMillis() - started > TIME_BUDGET_MS) {
                throw invalid("PDF_TOO_COMPLEX", "This PDF took too long to read. Try a shorter date range.");
            }
            List<Glyph> glyphs = glyphs(document, page);
            int chars = glyphs.size();
            total += chars;
            unreadable += (int) glyphs.stream().filter(g -> isGarbled(g.text())).count();
            if (chars < MIN_TEXT_CHARS_PER_PAGE) {
                if (hasImages(document.getPage(page - 1))) {
                    imageOnlyPages.add(page);
                }
            } else {
                textPages++;
            }
            pageLines.add(lines(page, glyphs));
        }
        if (textPages == 0) {
            if (!imageOnlyPages.isEmpty()) {
                throw invalid("PDF_SCANNED_IMAGE", "This PDF is a scanned image: it contains pictures of the pages "
                        + "but no selectable text. SpendOS can't read scanned statements yet (that needs OCR). "
                        + "Download the statement from your bank's website or app as a PDF or CSV, which contain "
                        + "real text, and upload that instead.");
            }
            throw invalid("PDF_NO_TEXT", "This PDF contains no readable text.");
        }
        if (!imageOnlyPages.isEmpty()) {
            // Never import part of a statement silently.
            throw invalid("PDF_PARTLY_SCANNED", "Pages " + imageOnlyPages + " of this PDF are scanned images without "
                    + "text, so their transactions can't be read and the import would be incomplete. Download the "
                    + "statement again as a text PDF or CSV.");
        }
        if (total > 0 && unreadable > total * 0.3) {
            throw invalid("PDF_UNREADABLE_TEXT", "The text in this PDF is encoded in a way that can't be extracted "
                    + "(it uses fonts without character maps). Export the statement as CSV instead.");
        }
        return new TableBuilder(pageLines).build();
    }

    // ----- text extraction -----------------------------------------------------------------------

    static List<Glyph> glyphs(PDDocument document, int page) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                // Positions arrive with duplicated/overprinted "bold" glyphs already removed.
                for (TextPosition p : positions) {
                    String unicode = p.getUnicode();
                    if (unicode == null || unicode.isBlank()) {
                        spaceNext = true; // an explicit space glyph separates words
                        continue;
                    }
                    // Cap height is roughly 0.65 em across common fonts, including monospaced ones.
                    float em = p.getHeightDir() > 0 ? p.getHeightDir() / 0.65f : Math.max(p.getFontSizeInPt(), 1f);
                    float space = p.getWidthOfSpace();
                    if (!(space >= em * 0.15f && space <= em * 0.7f)) {
                        space = em * 0.28f;
                    }
                    glyphs.add(new Glyph(p.getXDirAdj(), p.getWidthDirAdj(), p.getYDirAdj(), em, space, spaceNext, unicode));
                    spaceNext = false;
                }
            }

            @Override
            protected void writeWordSeparator() {
                spaceNext = true;
            }

            @Override
            protected void writeLineSeparator() {
                spaceNext = false;
            }

            private boolean spaceNext;
        };
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.getText(document);
        return glyphs;
    }

    private static boolean isGarbled(String text) {
        return text.chars().anyMatch(c -> c == 0xFFFD || (c >= 0xE000 && c <= 0xF8FF) || (Character.isISOControl(c)));
    }

    private static boolean hasImages(PDPage page) throws IOException {
        return hasImages(page.getResources(), 0);
    }

    private static boolean hasImages(PDResources resources, int depth) throws IOException {
        if (resources == null || depth > 5) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDImageXObject) {
                return true;
            }
            if (object instanceof PDFormXObject form && hasImages(form.getResources(), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** Groups glyphs into lines by baseline, each sorted left to right. */
    static List<Line> lines(int page, List<Glyph> glyphs) {
        List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingDouble(Glyph::y).thenComparingDouble(Glyph::x));
        List<Line> lines = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        float currentY = Float.NaN;
        for (Glyph glyph : sorted) {
            float tolerance = Math.max(1.5f, glyph.em() * 0.4f);
            if (!current.isEmpty() && Math.abs(glyph.y() - currentY) > tolerance) {
                lines.add(line(page, current));
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentY = glyph.y();
            }
            current.add(glyph);
        }
        if (!current.isEmpty()) {
            lines.add(line(page, current));
        }
        return lines;
    }

    private static Line line(int page, List<Glyph> glyphs) {
        List<Glyph> ordered = new ArrayList<>(glyphs);
        ordered.sort(Comparator.comparingDouble(Glyph::x));
        float y = (float) ordered.stream().mapToDouble(Glyph::y).average().orElse(0);
        float height = (float) ordered.stream().mapToDouble(Glyph::em).max().orElse(8);
        return new Line(page, y, height, ordered);
    }

    /** Joins glyphs into text, inserting a space where the gap is wider than normal letter spacing. */
    static String join(List<Glyph> glyphs) {
        StringBuilder text = new StringBuilder();
        Glyph previous = null;
        for (Glyph glyph : glyphs) {
            if (previous != null && (glyph.spaceBefore() || glyph.x() - previous.right() > glyph.space() * 0.35f)) {
                text.append(' ');
            }
            text.append(glyph.text());
            previous = glyph;
        }
        return text.toString().trim();
    }

    /** Splits a line into phrases wherever the gap is wider than a word space (a likely column break). */
    static List<List<Glyph>> phrases(Line line) {
        List<List<Glyph>> phrases = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        Glyph previous = null;
        for (Glyph glyph : line.glyphs()) {
            if (previous != null && glyph.x() - previous.right() > glyph.space() * 1.5f) {
                phrases.add(current);
                current = new ArrayList<>();
            }
            current.add(glyph);
            previous = glyph;
        }
        if (!current.isEmpty()) {
            phrases.add(current);
        }
        return phrases;
    }

    static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    // ----- table reconstruction ------------------------------------------------------------------

    /** Finds the header on each page, places column boundaries and assembles rows. */
    static final class TableBuilder {

        private final List<List<Line>> pages;

        TableBuilder(List<List<Line>> pages) {
            this.pages = pages;
        }

        ParsedFile build() {
            Header header = null;
            float[] boundaries = null;
            List<RawRow> rows = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                List<Line> lines = pages.get(pageIndex);
                Header pageHeader = findHeader(lines);
                int start = 0;
                if (pageHeader != null) {
                    if (header == null) {
                        header = pageHeader;
                    }
                    start = pageHeader.lastLineIndex() + 1;
                    // Re-measure columns on each page (widths can change), but only when this page's header
                    // has the same columns as the first; otherwise keep the previous page's boundaries.
                    if (pageHeader.cells().size() == header.cells().size()) {
                        boundaries = boundaries(pageHeader.cells(), tableLines(lines.subList(start, lines.size()),
                                pageHeader, midpoints(pageHeader.cells())));
                    }
                } else if (header == null) {
                    continue; // no table on this page yet (cover page, account summary)
                }
                // Pages without a repeated header keep the previous page's columns.
                List<Line> body = lines.subList(start, lines.size());
                for (List<String> cells : assemble(body, header, boundaries)) {
                    if (rows.size() >= CsvStatementParser.MAX_ROWS) {
                        throw invalid("PDF_TOO_LONG", "Statements are limited to " + CsvStatementParser.MAX_ROWS
                                + " transactions; download a shorter date range.");
                    }
                    rows.add(new RawRow(rows.size() + 1, "Page " + (pageIndex + 1) + ": " + String.join(" | ", cells),
                            cells));
                }
            }
            if (header == null) {
                throw invalid("PDF_NO_TABLE", "Couldn't find a transaction table in this PDF. SpendOS looks for a "
                        + "header row with columns such as Date, Description or Narration, and Amount (or "
                        + "Withdrawal and Deposit). If this is a bank statement, try your bank's CSV export instead.");
            }
            if (rows.isEmpty()) {
                throw invalid("PDF_NO_TRANSACTIONS", "Found the transaction table in this PDF but no transactions "
                        + "in it. Check that the statement covers a period with activity.");
            }
            List<String> headerTexts = header.cells().stream().map(HeaderCell::text).toList();
            return new ParsedFile(header.mapping(), rows, ',', true, headerTexts, "pdf");
        }

        /** The first run of 1-3 tightly spaced lines that reads as a statement header. */
        static Header findHeader(List<Line> lines) {
            for (int i = 0; i < lines.size(); i++) {
                Header best = null;
                for (int span = 1; span <= 3 && i + span <= lines.size(); span++) {
                    List<Line> block = lines.subList(i, i + span);
                    if (span > 1 && !tight(block)) {
                        break;
                    }
                    if (span > 1 && block.subList(1, span).stream().anyMatch(TableBuilder::hasValues)) {
                        break; // the next line is already data
                    }
                    List<HeaderCell> cells = merge(cluster(block), lines.subList(i + span, lines.size()));
                    List<String> texts = cells.stream().map(HeaderCell::text).toList();
                    if (cells.size() >= 3 && CsvStatementParser.looksLikeHeader(texts)) {
                        ColumnMapping mapping = CsvStatementParser.mapHeader(texts);
                        if (mapping.isComplete()) {
                            best = new Header(cells, block.get(span - 1).y(), i + span - 1, mapping);
                        }
                    }
                }
                if (best != null) {
                    return best;
                }
            }
            return null;
        }

        private static boolean tight(List<Line> block) {
            for (int i = 1; i < block.size(); i++) {
                Line previous = block.get(i - 1);
                if (block.get(i).y() - previous.y() > previous.height() * 1.8f) {
                    return false;
                }
            }
            return true;
        }

        /** True when a line holds dates or amounts, i.e. it is data rather than header text. */
        private static boolean hasValues(Line line) {
            return phrases(line).stream().map(PdfStatementParser::join)
                    .anyMatch(text -> DateParser.looksLikeDate(text) || AmountParser.parse(text) != null);
        }

        /** Merges the phrases of a header block into cells by horizontal overlap, top line first. */
        static List<HeaderCell> cluster(List<Line> block) {
            record Piece(float left, float right, float y, String text) {
            }
            List<Piece> pieces = new ArrayList<>();
            for (Line line : block) {
                for (List<Glyph> phrase : phrases(line)) {
                    pieces.add(new Piece(phrase.get(0).x(), phrase.get(phrase.size() - 1).right(), line.y(), join(phrase)));
                }
            }
            pieces.sort(Comparator.comparingDouble(Piece::left));
            List<List<Piece>> groups = new ArrayList<>();
            float groupRight = Float.NEGATIVE_INFINITY;
            for (Piece piece : pieces) {
                if (groups.isEmpty() || piece.left() > groupRight + 1f) {
                    groups.add(new ArrayList<>());
                    groupRight = piece.right();
                } else {
                    groupRight = Math.max(groupRight, piece.right());
                }
                groups.get(groups.size() - 1).add(piece);
            }
            List<HeaderCell> cells = new ArrayList<>();
            for (List<Piece> group : groups) {
                group.sort(Comparator.comparingDouble(Piece::y).thenComparingDouble(Piece::left));
                String text = String.join(" ", group.stream().map(Piece::text).toList());
                cells.add(new HeaderCell(text, (float) group.stream().mapToDouble(Piece::left).min().orElse(0),
                        (float) group.stream().mapToDouble(Piece::right).max().orElse(0)));
            }
            return cells;
        }

        /**
         * Merges header cells that no column gutter separates. A label of several words ("Date & time")
         * is clustered into one cell per word when the words are spaced more widely than a space, and each
         * fragment would then become a column whose edge falls inside the rows' own text, cutting
         * "01 Aug, 2026" into "01 Au" | "g," | "2026". Two header cells only start different columns when
         * the rows below them leave a whitespace corridor in between.
         */
        static List<HeaderCell> merge(List<HeaderCell> cells, List<Line> body) {
            if (cells.size() < 2 || body.isEmpty()) {
                return cells;
            }
            float gutter = gutter(body);
            List<HeaderCell> merged = new ArrayList<>();
            HeaderCell pending = cells.get(0);
            for (int i = 1; i < cells.size(); i++) {
                HeaderCell next = cells.get(i);
                if (corridor(pending.right(), next.left(), body, gutter)) {
                    merged.add(pending);
                    pending = next;
                } else {
                    pending = new HeaderCell(pending.text() + " " + next.text(), pending.left(), next.right());
                }
            }
            merged.add(pending);
            return merged;
        }

        /** The narrowest gap that can be a column gutter rather than the space between two words. */
        private static float gutter(List<Line> lines) {
            List<Float> spaces = new ArrayList<>();
            for (Line line : lines) {
                for (Glyph glyph : line.glyphs()) {
                    if (glyph.space() > 0) {
                        spaces.add(glyph.space());
                    }
                }
            }
            if (spaces.isEmpty()) {
                return 1f;
            }
            spaces.sort(Float::compare);
            return spaces.get(spaces.size() / 2);
        }

        /**
         * True when the rows leave a whitespace corridor at least {@code gutter} wide between the two x
         * positions. Measured per line rather than over all text at once: a summary table or a footnote
         * below the transactions sits at its own x positions and would otherwise close a real gutter, so a
         * corridor is allowed to be crossed by a few lines.
         */
        private static boolean corridor(float left, float right, List<Line> lines, float gutter) {
            if (right - left < gutter) {
                return false;
            }
            float step = Math.max(gutter / 4, 0.25f);
            int samples = (int) Math.ceil((right - left) / step);
            int[] crossings = new int[samples + 1];
            for (Line line : lines) {
                for (Glyph glyph : line.glyphs()) {
                    if (glyph.right() <= left || glyph.x() >= right) {
                        continue;
                    }
                    int from = Math.max(0, (int) Math.floor((glyph.x() - left) / step));
                    int to = Math.min(samples, (int) Math.ceil((glyph.right() - left) / step));
                    for (int i = from; i <= to; i++) {
                        crossings[i]++;
                    }
                }
            }
            int tolerated = lines.size() / 10;
            float free = 0;
            for (int i = 0; i <= samples; i++) {
                free = crossings[i] <= tolerated ? free + step : 0;
                if (free >= gutter) {
                    return true;
                }
            }
            return false;
        }

        static float[] midpoints(List<HeaderCell> cells) {
            float[] boundaries = new float[cells.size() - 1];
            for (int i = 0; i < boundaries.length; i++) {
                boundaries[i] = (cells.get(i).right() + cells.get(i + 1).left()) / 2;
            }
            return boundaries;
        }

        /**
         * Places each boundary in the widest whitespace gutter between two header centres, measured
         * over the table's own lines; keeps the midpoint where no gutter exists.
         */
        static float[] boundaries(List<HeaderCell> cells, List<Line> tableLines) {
            float[] boundaries = midpoints(cells);
            if (tableLines.isEmpty()) {
                return boundaries;
            }
            List<float[]> occupied = new ArrayList<>();
            for (Line line : tableLines) {
                for (Glyph glyph : line.glyphs()) {
                    occupied.add(new float[] {glyph.x(), glyph.right()});
                }
            }
            occupied.sort(Comparator.comparingDouble(range -> range[0]));
            List<float[]> merged = new ArrayList<>();
            for (float[] range : occupied) {
                float[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                if (last != null && range[0] <= last[1] + 0.5f) {
                    last[1] = Math.max(last[1], range[1]);
                } else {
                    merged.add(new float[] {range[0], range[1]});
                }
            }
            float gutter = gutter(tableLines);
            for (int i = 0; i < boundaries.length; i++) {
                float from = cells.get(i).center();
                float to = cells.get(i + 1).center();
                // Header labels never cross a column edge, so the true gutter is the one closest to the
                // space between the two labels, not simply the widest (a clipped gap can look wider).
                float regionLeft = Math.min(cells.get(i).right(), cells.get(i + 1).left());
                float regionRight = Math.max(cells.get(i).right(), cells.get(i + 1).left());
                float bestDistance = Float.MAX_VALUE;
                float bestOverlap = -1;
                for (int g = 1; g < merged.size(); g++) {
                    float gapLeft = Math.max(merged.get(g - 1)[1], from);
                    float gapRight = Math.min(merged.get(g)[0], to);
                    // A gap narrower than a gutter is the space between two letters or words, not a column
                    // edge; taking it would cut a cell's text in half.
                    if (gapRight - gapLeft < gutter) {
                        continue;
                    }
                    float overlap = Math.min(gapRight, regionRight) - Math.max(gapLeft, regionLeft);
                    float distance = overlap >= 0 ? 0 : Math.max(regionLeft - gapRight, gapLeft - regionRight);
                    if (distance < bestDistance || (distance == bestDistance && overlap > bestOverlap)) {
                        bestDistance = distance;
                        bestOverlap = overlap;
                        boundaries[i] = (gapLeft + gapRight) / 2;
                    }
                }
            }
            return boundaries;
        }

        /** Lines that belong to the transaction table (rows and their wrapped lines), for gutter measurement. */
        static List<Line> tableLines(List<Line> body, Header header, float[] boundaries) {
            List<Line> lines = new ArrayList<>();
            group(body, header, boundaries).forEach(lines::addAll);
            return lines;
        }

        private static boolean close(Line above, Line below) {
            return below.y() - above.y() <= above.height() * 2.2f;
        }

        /** Splits a line into one text per column by the x position of each glyph. */
        static List<String> cells(Line line, float[] boundaries, int columns) {
            List<List<Glyph>> byColumn = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                byColumn.add(new ArrayList<>());
            }
            for (Glyph glyph : line.glyphs()) {
                float center = glyph.x() + glyph.width() / 2;
                int column = 0;
                while (column < boundaries.length && center > boundaries[column]) {
                    column++;
                }
                byColumn.get(column).add(glyph);
            }
            List<String> cells = new ArrayList<>(columns);
            byColumn.forEach(glyphs -> cells.add(join(glyphs)));
            return cells;
        }

        /** True when a line has text only in description/reference columns (a wrapped continuation). */
        private static boolean textOnly(List<String> cells, ColumnMapping mapping) {
            for (int c = 0; c < cells.size(); c++) {
                if (!cells.get(c).isEmpty() && c != mapping.description() && c != mapping.reference()) {
                    return false;
                }
            }
            return cells.stream().anyMatch(cell -> !cell.isEmpty());
        }

        /** Date text that can begin a date: a number or a month name ("1", "01/08/26", "Aug 5,"). */
        private static final Pattern DATE_START = Pattern.compile(
                "(?i)^(\\d{1,4}\\b|\\d{1,2}[-/.]|(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\b)");

        /**
         * Groups a page's body lines into transactions. A row starts at a line whose date cell begins a
         * date; following lines join it while its date is still incomplete (dates wrapped over several
         * lines, e.g. "1" / "Aug" / "2026") or while they carry only wrapped description/reference text.
         * Wrapped lines between two rows go to whichever row is vertically closer, which also handles
         * tables whose cells are vertically centred. Any other line ends the table section.
         */
        static List<List<Line>> group(List<Line> body, Header header, float[] boundaries) {
            ColumnMapping mapping = header.mapping();
            int columns = header.cells().size();
            List<String> headerTexts = header.cells().stream().map(cell -> cell.text().toLowerCase(Locale.ROOT)).toList();
            List<List<Line>> rows = new ArrayList<>();
            List<Line> current = null;
            String currentDate = "";
            List<Line> pending = new ArrayList<>();

            for (Line line : body) {
                List<String> cells = cells(line, boundaries, columns);
                if (PAGE_FOOTER.matcher(line.text()).matches() || isRepeatedHeader(cells, headerTexts)) {
                    continue;
                }
                String date = cells.get(mapping.date());
                Line previous = current == null ? null : pending.isEmpty() ? current.get(current.size() - 1)
                        : pending.get(pending.size() - 1);
                if (current != null && !date.isEmpty() && !DateParser.looksLikeDate(currentDate)
                        && pending.isEmpty() && close(previous, line)) {
                    current.add(line); // the rest of a wrapped date
                    currentDate = (currentDate + " " + date).trim();
                    continue;
                }
                if (!date.isEmpty() && DATE_START.matcher(date).find()) {
                    List<Line> next = new ArrayList<>();
                    if (current == null && !pending.isEmpty() && close(pending.get(pending.size() - 1), line)) {
                        // Wrapped text directly above a page's first row (vertically centred cells).
                        next.addAll(pending);
                    }
                    if (current != null) {
                        for (Line waiting : pending) {
                            Line last = current.get(current.size() - 1);
                            if (waiting.y() - last.y() <= line.y() - waiting.y()) {
                                current.add(waiting);
                            } else {
                                next.add(waiting);
                            }
                        }
                        rows.add(current);
                    }
                    pending.clear();
                    next.add(line);
                    current = next;
                    currentDate = date;
                    continue;
                }
                if (current != null && date.isEmpty() && textOnly(cells, mapping) && close(previous, line)) {
                    pending.add(line);
                    continue;
                }
                if (current == null && date.isEmpty() && textOnly(cells, mapping)
                        && !BALANCE_ROW.matcher(cells.get(mapping.description())).find()
                        && (pending.isEmpty() || close(pending.get(pending.size() - 1), line))) {
                    pending.add(line); // may belong to the next row if its cells are vertically centred
                    continue;
                }
                if (current == null) {
                    pending.clear();
                }
                if (current != null) {
                    // Not part of the table (summary, notes, footer): close the open row.
                    current.addAll(pending);
                    pending.clear();
                    rows.add(current);
                    current = null;
                    currentDate = "";
                }
            }
            if (current != null) {
                current.addAll(pending);
                rows.add(current);
            }
            return rows;
        }

        /** Turns grouped lines into rows of cells, joining each column's text top to bottom. */
        static List<List<String>> assemble(List<Line> body, Header header, float[] boundaries) {
            ColumnMapping mapping = header.mapping();
            int columns = header.cells().size();
            List<List<String>> rows = new ArrayList<>();
            for (List<Line> group : group(body, header, boundaries)) {
                List<Line> ordered = new ArrayList<>(group);
                ordered.sort(Comparator.comparingDouble(Line::y));
                List<String> row = new ArrayList<>(cells(ordered.get(0), boundaries, columns));
                for (Line line : ordered.subList(1, ordered.size())) {
                    append(row, cells(line, boundaries, columns));
                }
                emit(rows, row, mapping);
            }
            return rows;
        }

        private static boolean isRepeatedHeader(List<String> cells, List<String> headerTexts) {
            long matches = cells.stream().filter(cell -> !cell.isEmpty())
                    .filter(cell -> headerTexts.stream().anyMatch(h -> h.startsWith(cell.toLowerCase(Locale.ROOT))))
                    .count();
            return matches >= 3 && cells.stream().noneMatch(DateParser::looksLikeDate);
        }

        private static void append(List<String> row, List<String> continuation) {
            for (int c = 0; c < row.size(); c++) {
                String extra = continuation.get(c);
                if (!extra.isEmpty()) {
                    String existing = row.get(c);
                    // Wrapped text that broke inside a word (e.g. "LTD-" / "SALARY") is joined without a space.
                    String separator = existing.isEmpty() || existing.endsWith("-") || existing.endsWith("/") ? "" : " ";
                    row.set(c, existing + separator + extra);
                }
            }
        }

        private static void emit(List<List<String>> rows, List<String> row, ColumnMapping mapping) {
            String description = mapping.description() >= 0 ? row.get(mapping.description()) : "";
            if (BALANCE_ROW.matcher(description.trim()).find()) {
                return; // opening/closing balance lines are not transactions
            }
            // A row that never formed a valid date is only dropped when it carries no amount; with an
            // amount it is kept so validation reports it to the user instead of losing it silently.
            if (!DateParser.looksLikeDate(row.get(mapping.date())) && !hasAmount(row, mapping)) {
                return;
            }
            rows.add(row);
        }

        private static boolean hasAmount(List<String> row, ColumnMapping mapping) {
            for (int column : new int[] {mapping.amount(), mapping.debit(), mapping.credit()}) {
                if (column >= 0 && AmountParser.parse(row.get(column)) != null) {
                    return true;
                }
            }
            return false;
        }
    }
}
