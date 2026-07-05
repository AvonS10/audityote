# Sample reports

Real reports exported from the [AuditYote demo](https://audityote.pasin.dev), over the same 27-finding dataset shown in the main README. Nothing here is a mockup; each file is what the app returns when you click Export. The PDFs are generated with Apache PDFBox, with the charts, gauge, and heatmap drawn directly rather than through a chart library. The CSV follows RFC 4180 with a UTF-8 byte-order mark and a guard against spreadsheet formula injection.

| File | What it is |
|---|---|
| `posture-report.pdf` | The full risk-posture report, eight pages: overall score and band, KPI tiles, a gauge, findings by severity and by status, a severity-by-status risk heatmap, key insights and recommended actions synthesized from the data by fixed rules, and a top-risks table. |
| `findings-register.pdf` | Every active finding with severity, CVSS, effective risk score, status, owner, asset, and mapped controls. |
| `findings-register.csv` | The same register as CSV, ready for a spreadsheet. |
| `control-coverage.pdf` | ISO/IEC 27001:2022 coverage, control by control: how many findings map to each control, the worst severity among them, and whether it is at risk. |

Each report carries a provenance header (generated-at, generated-by, scope) and a page footer. The app also exports an audit-trail report and an admin-only user-management audit, which are not sampled here. All names and findings belong to fictional demo accounts.
