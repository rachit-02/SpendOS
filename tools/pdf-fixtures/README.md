# PDF statement fixtures

Synthetic bank statements (all names, accounts and amounts are fake) used by the PDF import tests in
`backend/src/test/resources/statements`. They are produced by PDF writers other than PDFBox, which
does the parsing, so the tests do not only exercise PDFBox's own output.

| Fixture | Producer | What it exercises |
|---|---|---|
| `hdfc-style.pdf` | Chrome print-to-PDF | account block above the table, headers stacked over two lines, wrapped narrations, header repeated on page 2, summary table after the transactions |
| `sbi-style.pdf` | Chrome | dates wrapped over three lines ("1" / "Aug" / "2026"), separate value date, three pages |
| `icici-style.pdf` | Chrome | serial-number column, narrow neighbouring columns, "(INR )" amount headers |
| `vertically-centred-rows.pdf` | Chrome | same as hdfc-style with vertically centred cells (date on the middle line of a row) |
| `wallet-style.pdf` | Chrome | borderless three columns, no debit/credit columns, direction only in the narration, two-line date cells, and header labels spaced wider than a word space |
| `credit-card-style.pdf` | OpenPDF | one amount column with Dr/Cr suffixes |
| `us-bank-style.pdf` | OpenPDF | MM/DD/YYYY dates, negatives in parentheses |
| `no-transaction-table.pdf` | OpenPDF | a text PDF that is not a statement |
| `truncated.pdf` | OpenPDF, cut short | a damaged file |
| `scanned-image-only.pdf` | Chrome, page rendered as an image | a scanned statement with no text layer |
| `password-protected.pdf` | PDFBox (AES-128) | an encrypted statement |
| `partly-scanned.pdf` | PDFBox merge | one text page plus one image-only page |

## Regenerating

```bash
cd frontend && node ../tools/pdf-fixtures/generate-chrome.cjs && cd ..
java -cp ~/.m2/repository/com/github/librepdf/openpdf/2.0.3/openpdf-2.0.3.jar \
  tools/pdf-fixtures/GenerateOpenPdf.java backend/src/test/resources/statements
# PdfBoxFixtures needs pdfbox, pdfbox-io, fontbox and commons-logging on the classpath (see the file header)
```

Regenerated files can differ byte for byte (timestamps, IDs); the tests assert on content, not bytes.
