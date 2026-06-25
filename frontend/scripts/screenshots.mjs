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

  // Carbon theme
  await page.click('button[title="Switch visual direction"]')
  await page.waitForTimeout(200)
  await shot('app-carbon')
} else {
  console.log('SEED_ANALYST_* not set — skipping authenticated screenshots')
}

await browser.close()
