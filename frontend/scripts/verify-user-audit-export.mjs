// One-off verification for the ADMIN-only user-audit export surface (#user-audit chunk).
// Captures: /users with the Export menu open, and downloads the CSV through the real UI flow
// (click -> blob -> attachment filename). Usage: backend + vite running, then
//   ADMIN_EMAIL=... ADMIN_PASSWORD=... node scripts/verify-user-audit-export.mjs
import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const BASE = process.env.SCREENSHOT_BASE ?? 'http://localhost:5173'
const OUT = new URL('../.screenshots/', import.meta.url)
const EMAIL = process.env.ADMIN_EMAIL
const PASSWORD = process.env.ADMIN_PASSWORD
if (!EMAIL || !PASSWORD) {
  console.error('ADMIN_EMAIL / ADMIN_PASSWORD not set')
  process.exit(1)
}

await mkdir(OUT, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

// Log in through the UI as the admin.
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.fill('input[type="email"]', EMAIL)
await page.fill('input[type="password"]', PASSWORD)
await page.click('button[type="submit"]')
await page.waitForURL(`${BASE}/`, { timeout: 10000 })

// Users screen with the Export menu open.
await page.goto(`${BASE}/users`, { waitUntil: 'networkidle' })
await page.getByRole('button', { name: 'Export', exact: true }).click()
await page.waitForTimeout(180)
await page.screenshot({ path: new URL('users-audit-export-menu.png', OUT).pathname })
console.log('captured users-audit-export-menu.png')

// Download the CSV through the real click path (blob URL -> suggested filename).
const downloadPromise = page.waitForEvent('download', { timeout: 10000 })
await page.getByRole('menuitem', { name: 'Download CSV' }).click()
const download = await downloadPromise
console.log(`downloaded: ${download.suggestedFilename()}`)

await browser.close()
console.log('user-audit export surface verified')
