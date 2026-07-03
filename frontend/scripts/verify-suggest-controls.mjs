// Local visual/functional verification for the AI "Suggest controls" panel (PLAN §7.12). Not used in
// CI. Requires: backend running with AI_SUGGESTIONS_ENABLED=true + ANTHROPIC_API_KEY, and `npm run dev`.
// Usage:  SEED_ANALYST_PASSWORD=… FINDING_ID=14 node scripts/verify-suggest-controls.mjs
// Output: frontend/.screenshots/ai-*.png (gitignored). Drives a real Claude call, then accepts one.
import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const BASE = process.env.SCREENSHOT_BASE ?? 'http://localhost:5173'
const OUT = new URL('../.screenshots/', import.meta.url)
const EMAIL = process.env.SEED_ANALYST_EMAIL ?? 'analyst@controlmap.local'
const PASSWORD = process.env.SEED_ANALYST_PASSWORD ?? '15101rock'
const FINDING = process.env.FINDING_ID ?? '14'

await mkdir(OUT, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const shot = (n) =>
  page.screenshot({ path: new URL(`${n}.png`, OUT).pathname, fullPage: true }).then(() => console.log('captured', n))

// Log in through the UI.
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.fill('input[type="email"]', EMAIL)
await page.fill('input[type="password"]', PASSWORD)
await page.click('button[type="submit"]')
await page.waitForURL(`${BASE}/`, { timeout: 10000 })

// Open an editable finding I own.
await page.goto(`${BASE}/findings/${FINDING}`, { waitUntil: 'networkidle' })
await page.getByRole('heading', { name: 'Control mapping' }).scrollIntoViewIfNeeded()

// Trigger suggestions (real Claude call).
await page.getByRole('button', { name: 'Suggest controls' }).click()
await page.waitForTimeout(500)
await shot('ai-1-loading') // Skeleton rows while the call runs

await page.getByRole('button', { name: 'Accept', exact: true }).first().waitFor({ timeout: 30000 })
await page.getByText('Suggested controls').scrollIntoViewIfNeeded()
await shot('ai-2-suggestions') // FrameworkTag + confidence chip + rationale + Accept/Dismiss

// Accept the top suggestion.
await page.getByRole('button', { name: 'Accept', exact: true }).first().click()
await page.waitForTimeout(1800) // accept round-trip + toast + finding refresh
await page.getByRole('heading', { name: 'Control mapping' }).scrollIntoViewIfNeeded()
await shot('ai-3-accepted') // the control is now in the mapped list; the row is gone

await browser.close()
console.log('done')
