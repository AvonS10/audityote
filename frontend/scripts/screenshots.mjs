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
  // Add-control mapping panel
  await page.getByRole('button', { name: 'Add control mapping' }).click()
  await page.waitForTimeout(600)
  await shot('finding-detail-add')

  // Edit -> delete confirm dialog
  await page.getByRole('button', { name: 'Edit finding' }).click()
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(300)
  await page.getByRole('button', { name: 'Delete', exact: true }).click()
  await page.waitForTimeout(250)
  await shot('finding-confirm-delete')
} else {
  console.log('SEED_ANALYST_* not set — skipping authenticated screenshots')
}

await browser.close()
