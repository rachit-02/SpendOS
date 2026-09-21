import { expect, test, type Page } from '@playwright/test'

/** Opens a navigation destination on any screen size (sidebar on desktop, drawer on mobile). */
async function goTo(page: Page, label: string) {
  const openMenu = page.getByRole('button', { name: 'Open navigation' })
  if (await openMenu.isVisible()) {
    await openMenu.click()
    await page.getByRole('dialog', { name: 'Navigation' }).getByRole('link', { name: label }).click()
  } else {
    await page.getByRole('complementary').getByRole('link', { name: label }).click()
  }
}

/**
 * The transactions list: a table on wide screens, a card list on phones. Both are labelled
 * "Transactions" and only one is visible, so match by name rather than element type.
 */
function transactionList(page: Page) {
  return page.getByRole('table', { name: 'Transactions' })
    .or(page.getByRole('list', { name: 'Transactions' }))
    .filter({ visible: true })
}

test('the app is served with security headers', async ({ request }) => {
  const response = await request.get('/login')
  expect(response.ok()).toBeTruthy()
  const headers = response.headers()
  expect(headers['content-security-policy']).toContain("default-src 'self'")
  expect(headers['x-frame-options']).toBe('DENY')
  expect(headers['x-content-type-options']).toBe('nosniff')

  const api = await request.get('/api/v1/health')
  expect(api.ok()).toBeTruthy()
  expect((await api.json()).data.status).toBe('UP')
})

test('demo: explore the dashboard, transactions, assistant and health, then reset', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /Try the demo/ }).click()

  await expect(page.getByRole('heading', { name: /Welcome, Demo/ })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Demo account' })).toBeVisible()

  await goTo(page, 'Transactions')
  await expect(transactionList(page)).toContainText('₹')

  await goTo(page, 'Assistant')
  await page.getByLabel('Your question').fill('Where did most of my money go?')
  await page.getByRole('button', { name: 'Send' }).click()
  await expect(page.getByRole('log', { name: 'Conversation' })).toContainText('Most of it went to')

  await goTo(page, 'Health')
  await expect(page.getByRole('list', { name: 'Score breakdown' })).toBeVisible()

  await page.getByRole('button', { name: /Reset demo/ }).click()
  await expect(page.getByText('Demo data restored.')).toBeVisible()
})

test('register, sign in, add a transaction and sign out', async ({ page }) => {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`
  const password = 'Tr0pic@lThund3r!-e2e'

  await page.goto('/register')
  await page.getByLabel('Full name').fill('Asha Rao')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: /Create account/ }).click()
  await expect(page.getByRole('heading', { name: /Welcome, Asha/ })).toBeVisible()

  // New users add an account before recording transactions by hand.
  await goTo(page, 'Settings')
  await page.getByRole('button', { name: 'Add account' }).click()
  await page.getByLabel('Account name').fill('Salary account')
  await page.getByRole('button', { name: 'Add account' }).click()
  await expect(page.getByText('Salary account')).toBeVisible()

  await goTo(page, 'Transactions')
  await page.getByRole('button', { name: /Add transaction/ }).click()
  const dialog = page.getByRole('dialog', { name: 'Add transaction' })
  await dialog.getByLabel('Merchant').fill('UPI-NETFLIX')
  await dialog.getByLabel('Amount').fill('649')
  await dialog.getByRole('button', { name: 'Add transaction' }).click()
  // The merchant normalizer turns the raw UPI text into the canonical merchant.
  await expect(transactionList(page)).toContainText('Netflix')

  await page.getByRole('button', { name: /Sign out/ }).click()
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()

  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: /Welcome, Asha/ })).toBeVisible()
})
