// Local visual-verification helper: screenshots key screens from the running dev server
// (http://localhost:5173). Not used in CI. Usage:
//   1) backend + `npm run dev` running, with SEED_ANALYST_* exported (from the repo .env)
//   2) npm run screenshots
// Output: frontend/.screenshots/*.png (gitignored).
import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const BASE = process.env.SCREENSHOT_BASE ?? 'http://localhost:5173'
const OUT = new URL('../.screenshots/', import.meta.url)
const EMAIL = process.env.SEED_ANALYST_EMAIL
const PASSWORD = process.env.SEED_ANALYST_PASSWORD

await mkdir(OUT, { recursive: true })

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

async function shot(name) {
  await page.screenshot({ path: new URL(`${name}.png`, OUT).pathname, fullPage: false })
  console.log(`captured ${name}.png`)
}

// Login screen (public)
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await shot('login')

// Session-notice variant
await page.goto(`${BASE}/login?reason=expired`, { waitUntil: 'networkidle' })
await shot('login-expired')

// Authenticated screens — log in through the UI, then capture the AppShell variants
if (EMAIL && PASSWORD) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.fill('input[type="email"]', EMAIL)
  await page.fill('input[type="password"]', PASSWORD)
  await page.click('button[type="submit"]')
  await page.waitForURL(`${BASE}/`, { timeout: 10000 })
  await page.waitForLoadState('networkidle')
  await shot('app-dashboard')

  // Account menu open
  await page.click('button[title="Account"]')
  await page.waitForTimeout(150)
  await shot('app-account-menu')
  await page.keyboard.press('Escape').catch(() => {})
  await page.mouse.click(700, 400)
  await page.waitForTimeout(150)

  // Control Coverage screen (default Sovereign theme)
  await page.goto(`${BASE}/coverage`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(400)
  await shot('coverage')
  // Export dropdown open (CSV / PDF)
  await page.getByRole('button', { name: 'Export', exact: true }).click()
  await page.waitForTimeout(150)
  await shot('coverage-export-menu')
  await page.mouse.click(300, 520)
  await page.waitForTimeout(120)
  await page.fill('input[type="search"]', 'inject')
  await page.waitForTimeout(200)
  await shot('coverage-search')
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(200)

  // Carbon theme
  await page.click('button[title="Switch visual direction"]')
  await page.waitForTimeout(200)
  await shot('app-carbon')

  // Control Catalog screen
  await page.goto(`${BASE}/catalog`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(250)
  await shot('catalog')
  // Search filter
  await page.fill('input[type="search"]', 'crypto')
  await page.waitForTimeout(200)
  await shot('catalog-search')

  // Findings dashboard
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(400)
  await shot('dashboard')
  // Export menu (Findings register + Audit log)
  await page.getByRole('button', { name: 'Export', exact: true }).click()
  await page.waitForTimeout(200)
  await shot('dashboard-export-menu')
  await page.getByRole('button', { name: 'Export', exact: true }).click() // close the menu
  await page.waitForTimeout(150)
  // "Show deleted" view — soft-deleted findings retained read-only
  await page.getByRole('button', { name: 'Deleted' }).click()
  await page.waitForTimeout(600)
  await shot('dashboard-deleted')
  await page.getByRole('button', { name: 'Deleted' }).click()
  await page.waitForTimeout(300)
  // Filter by severity = critical
  await page.selectOption('select >> nth=1', 'critical')
  await page.waitForTimeout(500)
  await shot('dashboard-filtered')

  // New finding form
  await page.goto(`${BASE}/findings/new`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(250)
  await shot('finding-form-new')
  // CVSS entered -> severity derived + score preview
  await page.fill('input[placeholder="0.0"]', '9.8')
  await page.waitForTimeout(200)
  await shot('finding-form-cvss')

  // Finding detail screen (open the first finding)
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(400)
  await page.click('table.cm-findings tbody tr')
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(400)
  await shot('finding-detail')
  // Add-control mapping panel (only if the first finding is editable by this user/state)
  const addBtn = page.getByRole('button', { name: 'Add control mapping' })
  if ((await addBtn.count()) > 0) {
    await addBtn.click()
    await page.waitForTimeout(600)
    await shot('finding-detail-add')
  }

  // Edit -> delete confirm dialog (only if editable)
  const editBtn = page.getByRole('button', { name: 'Edit finding' })
  if ((await editBtn.count()) > 0) {
    await editBtn.click()
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(300)
    await page.getByRole('button', { name: 'Delete', exact: true }).click()
    await page.waitForTimeout(250)
    await shot('finding-confirm-delete')
  }

  // Activity trail on a finding with real audit history (set up out-of-band; id passed via env).
  if (process.env.SCREENSHOT_FINDING_ID) {
    await page.goto(`${BASE}/findings/${process.env.SCREENSHOT_FINDING_ID}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(500)
    await page.evaluate(() => {
      const h = [...document.querySelectorAll('h2')].find((e) => e.textContent === 'Activity')
      h?.scrollIntoView({ block: 'start' })
    })
    await page.waitForTimeout(250)
    await shot('finding-activity')
  }

  // A soft-deleted finding: read-only banner, no actions, trail intact.
  if (process.env.SCREENSHOT_DELETED_FINDING_ID) {
    await page.goto(`${BASE}/findings/${process.env.SCREENSHOT_DELETED_FINDING_ID}`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(500)
    await shot('finding-deleted')
  }
} else {
  console.log('SEED_ANALYST_* not set — skipping authenticated screenshots')
}

// Reviewer view: a submitted finding shows Approve / Return, and the return dialog requires a comment.
const REVIEWER_EMAIL = process.env.SEED_REVIEWER_EMAIL
const REVIEWER_PASSWORD = process.env.SEED_REVIEWER_PASSWORD
if (REVIEWER_EMAIL && REVIEWER_PASSWORD) {
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await page.click('button[title="Account"]')
  await page.waitForTimeout(150)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.waitForURL(/\/login/, { timeout: 10000 })
  await page.fill('input[type="email"]', REVIEWER_EMAIL)
  await page.fill('input[type="password"]', REVIEWER_PASSWORD)
  await page.click('button[type="submit"]')
  await page.waitForURL(`${BASE}/`, { timeout: 10000 })
  await page.waitForLoadState('networkidle')

  // Review Queue (#17): reviewer-only two-pane sign-off screen.
  await page.goto(`${BASE}/reviews`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(500)
  await shot('review-queue')
  // Return-for-changes dialog (comment required) from the sign-off panel.
  const returnBtn = page.getByRole('button', { name: 'Return for changes' })
  if ((await returnBtn.count()) > 0) {
    await returnBtn.click()
    await page.waitForTimeout(250)
    await shot('review-queue-return')
    await page.keyboard.press('Escape').catch(() => {})
    await page.waitForTimeout(150)
  }

  // Filter to submitted findings, open the first one.
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(300)
  await page.selectOption('select >> nth=0', 'submitted')
  await page.waitForTimeout(500)
  const row = page.locator('table.cm-findings tbody tr').first()
  if ((await row.count()) > 0) {
    await row.click()
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(400)
    await shot('finding-detail-reviewer')
    await page.getByRole('button', { name: 'Return for changes' }).click()
    await page.waitForTimeout(250)
    await shot('finding-return-dialog')
  } else {
    console.log('No submitted findings to show the reviewer view')
  }
} else {
  console.log('SEED_REVIEWER_* not set — skipping reviewer screenshots')
}

await browser.close()
