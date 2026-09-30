/*
 * Generates synthetic bank-statement PDFs with Chrome's print-to-PDF, the same engine many
 * net-banking portals use for "Download as PDF". All names, accounts and amounts are fake.
 *
 * Run from the frontend directory (it provides Playwright's Chromium):
 *   cd frontend && node ../tools/pdf-fixtures/generate-chrome.cjs
 * Output: backend/src/test/resources/statements/*.pdf
 */
const path = require('path')
const fs = require('fs')
const { chromium } = require(require.resolve('@playwright/test', { paths: [process.cwd()] }))

const OUT = path.resolve(__dirname, '../../backend/src/test/resources/statements')
fs.mkdirSync(OUT, { recursive: true })

const inr = (n) => n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

// One shared, realistic transaction list: [day, month, narration, ref, debit, credit]
const TXNS = [
  [1, 8, 'NEFT CR-ACME TECHNOLOGIES PVT LTD-SALARY FOR JULY 2026-ACMEPAY0012', 'NEFT0012345', 0, 85000],
  [2, 8, 'UPI/422345678901/PRESTIGE RESIDENCY RENT/rent@okhdfcbank/Monthly rent August', 'UPI422345678', 22000, 0],
  [3, 8, 'UPI/422345678902/ZOMATO/zomato@hdfcbank/Food order', 'UPI422345679', 486, 0],
  [4, 8, 'POS 512345XXXXXX1234 BIGBASKET BANGALORE', 'POS000123', 2340.5, 0],
  [5, 8, 'NETFLIX.COM SI CHARGE MANDATE ID NFLX77781', 'SI0098765', 649, 0],
  [6, 8, 'UPI/422345678903/UBER INDIA/uber@icici/Ride to airport', 'UPI422345680', 312, 0],
  [8, 8, 'BESCOM ELECTRICITY BILL PAYMENT CONSUMER NO 1234567890', 'BBPS123456', 1845, 0],
  [9, 8, 'UPI/422345678904/SWIGGY/swiggy@icici/Order', 'UPI422345681', 398, 0],
  [10, 8, 'ACT FIBERNET BROADBAND AUTOPAY', 'SI0098766', 999, 0],
  [12, 8, 'IOCL PETROL PUMP KORAMANGALA BANGALORE', 'POS000124', 2500, 0],
  [14, 8, 'UPI/422345678905/SHARMA KIRANA STORE/sharmakirana@ybl', 'UPI422345682', 640, 0],
  [15, 8, 'SPOTIFY INDIA SI', 'SI0098767', 119, 0],
  [17, 8, 'AMAZON PAY INDIA PRIVATE LIMITED ORDER 402-1234567-7654321', 'UPI422345683', 1899, 0],
  [19, 8, 'UPI/422345678906/ZOMATO/zomato@hdfcbank/Food order', 'UPI422345684', 552, 0],
  [21, 8, 'ATM CASH WITHDRAWAL MG ROAD BANGALORE CARD XX1234', 'ATM000125', 3000, 0],
  [23, 8, 'UPI/422345678907/RAJU TEA STALL/raju@paytm', 'UPI422345685', 60, 0],
  [26, 8, 'UPI/422345678908/UBER INDIA/uber@icici', 'UPI422345686', 284, 0],
  [29, 8, 'BOOKMYSHOW MOVIE TICKETS', 'UPI422345687', 720, 0],
  [1, 9, 'NEFT CR-ACME TECHNOLOGIES PVT LTD-SALARY FOR AUGUST 2026-ACMEPAY0013', 'NEFT0012346', 0, 85000],
  [2, 9, 'UPI/422345678909/PRESTIGE RESIDENCY RENT/rent@okhdfcbank/Monthly rent September', 'UPI422345688', 22000, 0],
  [3, 9, 'UPI/422345678910/ZOMATO/zomato@hdfcbank/Food order', 'UPI422345689', 612, 0],
  [4, 9, 'UPI/422345678911/SWIGGY/swiggy@icici/Order', 'UPI422345690', 455, 0],
  [5, 9, 'NETFLIX.COM SI CHARGE MANDATE ID NFLX77781', 'SI0098768', 649, 0],
  [5, 9, 'POS 512345XXXXXX1234 BIGBASKET BANGALORE', 'POS000126', 2785, 0],
  [7, 9, 'UPI/422345678912/ZOMATO/zomato@hdfcbank/Food order', 'UPI422345691', 734, 0],
  [8, 9, 'BESCOM ELECTRICITY BILL PAYMENT CONSUMER NO 1234567890', 'BBPS123457', 2110, 0],
  [9, 9, 'UPI/422345678913/UBER INDIA/uber@icici', 'UPI422345692', 356, 0],
  [10, 9, 'ACT FIBERNET BROADBAND AUTOPAY', 'SI0098769', 999, 0],
  [11, 9, 'UPI/422345678914/SWIGGY/swiggy@icici/Order', 'UPI422345693', 520, 0],
  [12, 9, 'UPI/422345678915/ZOMATO/zomato@hdfcbank/Food order', 'UPI422345694', 689, 0],
  [14, 9, 'CROMA ELECTRONICS LAPTOP PURCHASE INVOICE CRM-88213', 'POS000127', 45999, 0],
  [15, 9, 'SPOTIFY INDIA SI', 'SI0098770', 119, 0],
  [16, 9, 'UPI/422345678916/SHARMA KIRANA STORE/sharmakirana@ybl', 'UPI422345695', 710, 0],
  [17, 9, 'UPI/422345678917/ZOMATO/zomato@hdfcbank/Food order', 'UPI422345696', 498, 0],
  [18, 9, 'IOCL PETROL PUMP KORAMANGALA BANGALORE', 'POS000128', 2600, 0],
  [19, 9, 'UPI/422345678918/SWIGGY/swiggy@icici/Order', 'UPI422345697', 577, 0],
  [20, 9, 'INT.PD SB ACCOUNT INTEREST FOR QUARTER', 'INT000129', 0, 1210],
]
const OPENING = 120000

function withBalances() {
  let balance = OPENING
  return TXNS.map(([d, m, narration, ref, debit, credit]) => {
    balance = balance - debit + credit
    return { d, m, narration, ref, debit, credit, balance }
  })
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const pad = (n) => String(n).padStart(2, '0')

const BASE_CSS = `
  body { font-family: Arial, Helvetica, sans-serif; font-size: 9pt; color: #111; }
  table { border-collapse: collapse; width: 100%; }
  thead { display: table-header-group; }
  th, td { border: 0.5pt solid #999; padding: 3pt 4pt; vertical-align: top; }
  th { background: #e8eef7; }
  .num { text-align: right; white-space: nowrap; }
  .block { margin-bottom: 10pt; }
`

function hdfc() {
  const rows = withBalances().map((t) => `<tr>
    <td>${pad(t.d)}/${pad(t.m)}/26</td>
    <td style="width:190pt">${t.narration}</td>
    <td>${t.ref}</td>
    <td>${pad(t.d)}/${pad(t.m)}/26</td>
    <td class="num">${t.debit ? inr(t.debit) : ''}</td>
    <td class="num">${t.credit ? inr(t.credit) : ''}</td>
    <td class="num">${inr(t.balance)}</td></tr>`).join('')
  const debits = TXNS.reduce((s, t) => s + t[4], 0)
  const credits = TXNS.reduce((s, t) => s + t[5], 0)
  return `<style>${BASE_CSS}</style>
  <div class="block"><b>EXAMPLE BANK LTD</b><br>MR TEST CUSTOMER<br>12, FAKE STREET, BANGALORE 560001<br>
  Account No : 5010XXXXXX1234 &nbsp; A/C Open Date : 01/01/2020 &nbsp; Currency : INR<br>
  Statement From : 01/08/2026 To : 20/09/2026</div>
  <table><thead><tr><th>Date</th><th>Narration</th><th>Chq./Ref.No.</th><th>Value Dt</th>
  <th>Withdrawal Amt.</th><th>Deposit Amt.</th><th>Closing Balance</th></tr></thead><tbody>${rows}</tbody></table>
  <div class="block" style="margin-top:12pt"><b>STATEMENT SUMMARY :-</b>
  <table><tr><th>Opening Balance</th><th>Dr Count</th><th>Cr Count</th><th>Debits</th><th>Credits</th><th>Closing Bal</th></tr>
  <tr><td class="num">${inr(OPENING)}</td><td class="num">34</td><td class="num">3</td><td class="num">${inr(debits)}</td>
  <td class="num">${inr(credits)}</td><td class="num">${inr(OPENING - debits + credits)}</td></tr></table>
  Generated On: 21/09/2026 &nbsp; This is a computer generated statement and does not require signature.</div>`
}

function sbi() {
  const rows = withBalances().map((t) => `<tr>
    <td>${t.d} ${MONTHS[t.m - 1]} 2026</td><td>${t.d} ${MONTHS[t.m - 1]} 2026</td>
    <td style="width:200pt">${t.credit ? 'BY TRANSFER-' : 'TO TRANSFER-'}${t.narration}</td>
    <td>${t.ref}</td>
    <td class="num">${t.debit ? inr(t.debit) : ''}</td>
    <td class="num">${t.credit ? inr(t.credit) : ''}</td>
    <td class="num">${inr(t.balance)}</td></tr>`).join('')
  return `<style>${BASE_CSS}</style>
  <div class="block">Account Name : Mr. Test Customer<br>Address : 34 Sample Road, Mumbai<br>
  Account Number : 00000031234567890<br>Branch : SAMPLE BRANCH &nbsp; IFS Code : EXMP0001234<br>
  Balance as on 1 Aug 2026 : ${inr(OPENING)}<br>Account Statement from 1 Aug 2026 to 20 Sep 2026</div>
  <table><thead><tr><th>Txn Date</th><th>Value<br>Date</th><th>Description</th><th>Ref No./Cheque<br>No.</th>
  <th>Debit</th><th>Credit</th><th>Balance</th></tr></thead><tbody>${rows}</tbody></table>
  <p>**This is a computer generated statement.</p>`
}

function icici() {
  const rows = withBalances().map((t, i) => `<tr>
    <td>${i + 1}</td><td>${pad(t.d)}/${pad(t.m)}/2026</td><td>${pad(t.d)}/${pad(t.m)}/2026</td><td>${t.ref.startsWith('NEFT') ? '' : '-'}</td>
    <td style="width:180pt">${t.narration}</td>
    <td class="num">${t.debit ? inr(t.debit) : '0.00'}</td>
    <td class="num">${t.credit ? inr(t.credit) : '0.00'}</td>
    <td class="num">${inr(t.balance)}</td></tr>`).join('')
  return `<style>${BASE_CSS}</style>
  <div class="block"><b>Statement of Transactions in Savings Account Number: 000401234567 for the period
  August 01, 2026 - September 20, 2026</b></div>
  <table><thead><tr><th>S No.</th><th>Value Date</th><th>Transaction Date</th><th>Cheque Number</th>
  <th>Transaction Remarks</th><th>Withdrawal Amount (INR )</th><th>Deposit Amount (INR )</th><th>Balance (INR )</th>
  </tr></thead><tbody>${rows}</tbody></table>
  <p>Legends Used in Account Statement: INF - Internet Fund Transfer, BIL - Bill Payment.</p>`
}

// Wallet statement (Google Pay style): borderless three-column table, no debit/credit columns, the
// direction of the money only in the narration, a date cell split over two lines, and header labels
// spaced more widely than a word space, which is what splits "Date & time" into separate columns.
function wallet() {
  const rows = TXNS.slice(0, 26).map((t, i) => {
    const credit = t[5] > 0
    const who = credit ? 'Received from' : 'Paid to'
    const name = t[2].split('/').filter((s) => /[a-z]/i.test(s)).pop().slice(0, 42)
    return `<tr>
      <td class="dt"><div class="d">${pad(t[0])} ${MONTHS[t[1] - 1]}, 2026</div><div class="t">0${(i % 9) + 1}:0${(i % 5) + 1} PM</div></td>
      <td class="det"><div class="h">${who} ${name}</div><div class="s">UPI Transaction ID: 6213119067${10 + i}</div>
        <div class="s">${credit ? 'Paid to' : 'Paid by'} Bank of Baroda 9384</div></td>
      <td class="amt">&#8377;${inr(credit ? t[5] : t[4])}</td></tr>`
  }).join('')
  return `<style>
    body { font-family: Arial, Helvetica, sans-serif; font-size: 9pt; color: #202124; }
    table { border-collapse: collapse; width: 100%; }
    thead { display: table-header-group; }
    th { background: #f5f5f5; color: #5f6368; font-weight: normal; text-align: left; border: 0;
         padding: 8pt 6pt; word-spacing: 5pt; }
    th.r { text-align: right; }
    td { border: 0; border-bottom: 0.5pt solid #dadce0; padding: 10pt 6pt; vertical-align: top; }
    .dt { width: 92pt; } .d { font-weight: bold; } .t { color: #5f6368; margin-top: 3pt; }
    .h { font-weight: bold; margin-bottom: 3pt; } .s { color: #5f6368; margin-top: 2pt; }
    .amt { text-align: right; font-weight: bold; white-space: nowrap; }
    .summary { background: #f4f6fc; padding: 12pt; display: flex; gap: 40pt; margin-bottom: 16pt; }
  </style>
  <div style="font-size:15pt;color:#5f6368">Wallet Pay</div>
  <div>9000000000,<br>test.customer@example.com</div>
  <div class="summary"><div>Transaction statement period<br>01 August 2026 - 20 September 2026</div>
    <div>Sent<br>&#8377;1,00,000</div><div>Received<br>&#8377;1,70,000</div></div>
  <table><thead><tr><th>Date &amp; time</th><th>Transaction details</th><th class="r">Amount</th></tr></thead>
  <tbody>${rows}</tbody></table>
  <p style="color:#5f6368;font-size:7pt">Note: this statement reflects payments made on the app.</p>`
}

// Image-only PDF: the table is rendered to a PNG first, so the PDF has no text layer (like a scan).
async function scanned(browser) {
  const page = await browser.newPage({ viewport: { width: 900, height: 1200 } })
  await page.setContent(hdfc())
  const png = await page.screenshot({ fullPage: false })
  await page.setContent(`<img src="data:image/png;base64,${png.toString('base64')}" style="width:100%">`)
  await page.pdf({ path: path.join(OUT, 'scanned-image-only.pdf'), format: 'A4' })
  await page.close()
}

;(async () => {
  const browser = await chromium.launch()
  // Same layout as hdfc-style, but cells are vertically centred: in multi-line rows the date sits on the
  // middle line, with wrapped narration both above and below it.
  const centred = hdfc().replace('vertical-align: top', 'vertical-align: middle')
  for (const [name, html] of [['hdfc-style.pdf', hdfc()], ['sbi-style.pdf', sbi()], ['icici-style.pdf', icici()],
    ['vertically-centred-rows.pdf', centred], ['wallet-style.pdf', wallet()]]) {
    const page = await browser.newPage()
    await page.setContent(html)
    await page.pdf({ path: path.join(OUT, name), format: 'A4', margin: { top: '12mm', bottom: '12mm', left: '10mm', right: '10mm' },
      displayHeaderFooter: true, headerTemplate: '<span></span>',
      footerTemplate: '<div style="font-size:7pt;width:100%;text-align:center">Page <span class="pageNumber"></span> of <span class="totalPages"></span></div>' })
    await page.close()
    console.log('wrote', name)
  }
  await scanned(browser)
  console.log('wrote scanned-image-only.pdf')
  await browser.close()
})()
