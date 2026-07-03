// One-off verification for the posture-report export surfaces (#posture-report chunk B).
// Captures: /posture with the Export menu open, the dashboard Export menu (3 groups),
// downloads the PDF through the real UI flow, and renders pages via pdf.js (an independent
// renderer) to confirm the Times headings are clean outside PDFBox's own rasterizer.
// Usage: backend + vite running, .env sourced → node scripts/verify-posture-export.mjs
import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'

const BASE = process.env.SCREENSHOT_BASE ?? 'http://localhost:5173'
const OUT = new URL('../.screenshots/', import.meta.url)
const EMAIL = process.env.SEED_ANALYST_EMAIL
const PASSWORD = process.env.SEED_ANALYST_PASSWORD
if (!EMAIL || !PASSWORD) {
  console.error('SEED_ANALYST_EMAIL / SEED_ANALYST_PASSWORD not set — source .env first')
  process.exit(1)
}

await mkdir(OUT, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

async function shot(name) {
  await page.screenshot({ path: new URL(`${name}.png`, OUT).pathname, fullPage: false })
  console.log(`captured ${name}.png`)
}

// Log in through the UI.
await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
await page.fill('input[type="email"]', EMAIL)
await page.fill('input[type="password"]', PASSWORD)
await page.click('button[type="submit"]')
await page.waitForURL(`${BASE}/`, { timeout: 10000 })
await page.waitForLoadState('networkidle')

// Dashboard Export menu — now 3 groups (Findings register / Audit log / Posture report).
await page.getByRole('button', { name: 'Export', exact: true }).click()
await page.waitForTimeout(180)
await shot('dashboard-export-menu-3-groups')
await page.keyboard.press('Escape').catch(() => {})
await page.mouse.click(700, 500)

// Risk posture screen with its new Export menu open.
await page.goto(`${BASE}/posture`, { waitUntil: 'networkidle' })
await page.waitForTimeout(400)
await page.getByRole('button', { name: 'Export', exact: true }).click()
await page.waitForTimeout(180)
await shot('posture-export-menu')

// Download the PDF through the real UI flow (menu item → blob download).
const downloadPromise = page.waitForEvent('download', { timeout: 20000 })
await page.getByRole('menuitem', { name: 'Download PDF' }).click()
const download = await downloadPromise
const pdfPath = new URL('posture-report-downloaded.pdf', OUT).pathname
await download.saveAs(pdfPath)
console.log(`downloaded ${await download.suggestedFilename()} -> ${pdfPath}`)

// Independent-render check: pdf.js (the renderer Firefox uses), pages 1 and 3.
const pdfBase64 = Buffer.from(await (await import('node:fs/promises')).readFile(pdfPath)).toString('base64')
await page.setViewportSize({ width: 1000, height: 1400 })
await page.setContent(`<!doctype html><html><body style="margin:0;background:#888">
  <canvas id="c" style="display:block;margin:0 auto"></canvas>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
  <script>
    window.renderPage = async (b64, pageNo) => {
      pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js'
      const raw = atob(b64); const bytes = new Uint8Array(raw.length)
      for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
      const doc = await pdfjsLib.getDocument({ data: bytes }).promise
      const p = await doc.getPage(pageNo)
      const viewport = p.getViewport({ scale: 1.6 })
      const canvas = document.getElementById('c')
      canvas.width = viewport.width; canvas.height = viewport.height
      await p.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
      return doc.numPages
    }
  </script></body></html>`, { waitUntil: 'networkidle' })
for (const pageNo of [1, 3]) {
  const pages = await page.evaluate(([b64, n]) => window.renderPage(b64, n), [pdfBase64, pageNo])
  await page.waitForTimeout(250)
  await shot(`posture-pdf-pdfjs-page-${pageNo}`)
  if (pageNo === 1) console.log(`pdf.js reports ${pages} pages`)
}

await browser.close()
console.log('verify-posture-export: done')
