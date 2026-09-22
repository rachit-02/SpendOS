import java.io.File;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/**
 * Fixtures built with PDFBox from the other generated files:
 * - password-protected.pdf: the credit-card fixture encrypted with a user password (AES-128), as banks
 *   do when they email statements;
 * - partly-scanned.pdf: page 1 of hdfc-style.pdf followed by an image-only page, to prove a statement
 *   is never imported partially.
 * Run with the PDFBox jars on the classpath:
 *   java -cp "pdfbox-3.0.8.jar;pdfbox-io-3.0.8.jar;fontbox-3.0.8.jar;commons-logging.jar" \
 *     tools/pdf-fixtures/PdfBoxFixtures.java backend/src/test/resources/statements
 */
public class PdfBoxFixtures {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        try (PDDocument doc = Loader.loadPDF(new File(dir, "credit-card-style.pdf"))) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pass", "secret123", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(new File(dir, "password-protected.pdf"));
        }
        System.out.println("wrote password-protected.pdf");
        try (PDDocument text = Loader.loadPDF(new File(dir, "hdfc-style.pdf"));
             PDDocument scan = Loader.loadPDF(new File(dir, "scanned-image-only.pdf"));
             PDDocument merged = new PDDocument()) {
            merged.importPage(text.getPage(0));
            merged.importPage(scan.getPage(0));
            merged.save(new File(dir, "partly-scanned.pdf"));
        }
        System.out.println("wrote partly-scanned.pdf");
    }
}
