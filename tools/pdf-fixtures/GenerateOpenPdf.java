import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Synthetic statement PDFs produced with OpenPDF (the iText 4 lineage many bank statement
 * generators are built on). All data is fake. Run with the OpenPDF jar on the classpath:
 *   java -cp openpdf-2.0.3.jar tools/pdf-fixtures/GenerateOpenPdf.java backend/src/test/resources/statements
 */
public class GenerateOpenPdf {

    static final Font BODY = new Font(Font.HELVETICA, 8);
    static final Font BOLD = new Font(Font.HELVETICA, 8, Font.BOLD);

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        creditCard(out.resolve("credit-card-style.pdf"), null);
        usBank(out.resolve("us-bank-style.pdf"));
        noTable(out.resolve("no-transaction-table.pdf"));
        // password-protected.pdf is made by EncryptWithPdfBox.java (OpenPDF encryption needs BouncyCastle).
        // A real PDF cut off half way, like an interrupted download.
        byte[] whole = Files.readAllBytes(out.resolve("credit-card-style.pdf"));
        Files.write(out.resolve("truncated.pdf"), Arrays.copyOf(whole, whole.length / 3));
        System.out.println("wrote OpenPDF fixtures to " + out);
    }

    static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(3);
        return cell;
    }

    /** Credit-card statement: one amount column with Dr/Cr suffixes and a reward points column. */
    static void creditCard(Path path, String password) throws Exception {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(path.toFile()));
        if (password != null) {
            writer.setEncryption(password.getBytes(), "owner-pass".getBytes(), PdfWriter.ALLOW_PRINTING,
                    PdfWriter.STANDARD_ENCRYPTION_128);
        }
        doc.open();
        doc.add(new Paragraph("EXAMPLE CARDS - Credit Card Statement", BOLD));
        doc.add(new Paragraph("Card Number: 4XXX XXXX XXXX 9876   Statement Date: 20/09/2026   Payment Due Date: 10/10/2026", BODY));
        doc.add(new Paragraph("Total Amount Due: 52,187.00   Minimum Amount Due: 2,610.00", BODY));
        doc.add(new Paragraph(" ", BODY));
        PdfPTable table = new PdfPTable(new float[] {1.3f, 5f, 1.2f, 1.8f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : new String[] {"Date", "Transaction Details", "Reward Points", "Amount (in Rs.)"}) {
            table.addCell(cell(h, BOLD, Element.ALIGN_CENTER));
        }
        String[][] rows = {
            {"22/08/2026", "PAYMENT RECEIVED - THANK YOU", "0", "18,450.00 Cr"},
            {"23/08/2026", "SWIGGY BANGALORE IN", "4", "412.00 Dr"},
            {"25/08/2026", "AMAZON PAY INDIA PRIVATE LIMITED BANGALORE IN", "30", "3,049.00 Dr"},
            {"28/08/2026", "UBER INDIA SYSTEMS PVT LTD GURGAON IN", "2", "276.00 Dr"},
            {"02/09/2026", "MAKEMYTRIP INDIA PVT LTD NEW DELHI IN", "120", "12,480.00 Dr"},
            {"05/09/2026", "NETFLIX.COM MUMBAI IN", "6", "649.00 Dr"},
            {"09/09/2026", "ZOMATO LTD GURGAON IN", "5", "538.00 Dr"},
            {"12/09/2026", "CROMA ELECTRONICS BANGALORE IN", "350", "34,990.00 Dr"},
            {"15/09/2026", "REFUND - AMAZON PAY INDIA", "0", "1,299.00 Cr"},
            {"18/09/2026", "INDIAN OIL PETROL BANGALORE IN", "22", "2,100.00 Dr"},
        };
        for (String[] row : rows) {
            table.addCell(cell(row[0], BODY, Element.ALIGN_LEFT));
            table.addCell(cell(row[1], BODY, Element.ALIGN_LEFT));
            table.addCell(cell(row[2], BODY, Element.ALIGN_RIGHT));
            table.addCell(cell(row[3], BODY, Element.ALIGN_RIGHT));
        }
        doc.add(table);
        doc.add(new Paragraph("Reward points earned this month: 539. Please pay by the due date to avoid late charges.", BODY));
        doc.close();
    }

    /** US-style checking statement: MM/DD/YYYY dates, one signed amount column, negatives in parentheses. */
    static void usBank(Path path) throws Exception {
        Document doc = new Document(PageSize.LETTER, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, new FileOutputStream(path.toFile()));
        doc.open();
        doc.add(new Paragraph("Example Federal Bank - Everyday Checking", BOLD));
        doc.add(new Paragraph("Account ending in 4421   Statement period: 08/01/2026 - 08/31/2026", BODY));
        doc.add(new Paragraph(" ", BODY));
        PdfPTable table = new PdfPTable(new float[] {1.4f, 5f, 1.6f, 1.6f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : new String[] {"Posting Date", "Description", "Amount", "Balance"}) {
            table.addCell(cell(h, BOLD, Element.ALIGN_CENTER));
        }
        String[][] rows = {
            {"08/01/2026", "DIRECT DEP ACME CORP PAYROLL", "4,250.00", "6,250.00"},
            {"08/03/2026", "CHECKCARD 0803 STARBUCKS STORE 1234 SEATTLE WA", "(6.45)", "6,243.55"},
            {"08/05/2026", "ONLINE PMT CITY ELECTRIC UTILITY", "(128.90)", "6,114.65"},
            {"08/12/2026", "NETFLIX.COM LOS GATOS CA", "(15.49)", "6,099.16"},
            {"08/15/2026", "UBER TRIP HELP.UBER.COM", "(23.10)", "6,076.06"},
            {"08/20/2026", "AMAZON MKTPLACE PMTS AMZN.COM/BILL WA", "(64.99)", "6,011.07"},
            {"08/28/2026", "ATM WITHDRAWAL 1ST AVE", "(200.00)", "5,811.07"},
        };
        for (String[] row : rows) {
            table.addCell(cell(row[0], BODY, Element.ALIGN_LEFT));
            table.addCell(cell(row[1], BODY, Element.ALIGN_LEFT));
            table.addCell(cell(row[2], BODY, Element.ALIGN_RIGHT));
            table.addCell(cell(row[3], BODY, Element.ALIGN_RIGHT));
        }
        doc.add(table);
        doc.close();
    }

    /** A real text PDF that is not a statement (e.g. a bank letter): must be rejected clearly. */
    static void noTable(Path path) throws Exception {
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, new FileOutputStream(path.toFile()));
        doc.open();
        doc.add(new Paragraph("EXAMPLE BANK LTD", BOLD));
        doc.add(new Paragraph("Dear Customer, we are pleased to inform you that your debit card will be renewed on "
                + "15/10/2026. Your new card will be dispatched to your registered address. No action is required.", BODY));
        doc.close();
    }
}
