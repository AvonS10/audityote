# Compliance catalog (seed data)

One JSON file per framework. The startup `DataSeeder` reads these and idempotently upserts the
frameworks and their controls into the database — frameworks by `slug`, controls by `(slug, code)`.
The catalog is **seeded and read-only**: there is no runtime editor. Frameworks change roughly yearly,
so the lifecycle is "edit a file here, then redeploy".

## Files

| File | Framework | Controls |
|------|-----------|----------|
| `iso27001.json` | ISO/IEC 27001:2022 — Annex A | 93 (A.5 Organizational, A.6 People, A.7 Physical, A.8 Technological) |
| `owasp.json`    | OWASP Top 10:2025 | 10 |
| `nist.json`     | NIST CSF 2.0 — categories | 22 (GV, ID, PR, DE, RS, RC) |

## File shape

```json
{
  "slug": "iso27001",
  "name": "ISO/IEC 27001",
  "version": "2022",
  "controls": [
    { "code": "A.5.1", "title": "Policies for information security", "category": "Organizational", "description": "…" }
  ]
}
```

`category` is optional (OWASP uses `null`); for ISO it is the Annex A theme, for NIST the function name.

## Rules when editing

- **Only add; never rename or renumber an existing `code`.** Findings map to controls by `(slug, code)`,
  so changing a code in use orphans live mappings. Titles/descriptions/categories *may* be edited — the
  seeder syncs them on the next boot.
- **Control codes must be unique across the whole catalog**, not just within a framework. The AI
  suggestion grounding keys on the code alone, so a shared code would let one control shadow another.
  `DataSeeder` enforces this and fails fast at boot if violated.

## Adding a framework

1. Add `<slug>.json` in this directory (same shape as above).
2. Add its path to `CATALOG_RESOURCES` in `DataSeeder` (this also sets display order).

No other code change is needed — the DTO/mapper and catalog API are framework-agnostic.
