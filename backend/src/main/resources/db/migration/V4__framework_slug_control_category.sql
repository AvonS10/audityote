-- V4 — add a stable framework `slug` (the key the API/UI use: iso27001 / owasp / nist) and a
-- control `category` (the theme group used to organise the catalog screen, PLAN §9).
ALTER TABLE framework ADD COLUMN slug VARCHAR(50);
UPDATE framework SET slug = CASE name
    WHEN 'ISO/IEC 27001' THEN 'iso27001'
    WHEN 'OWASP Top 10'  THEN 'owasp'
    WHEN 'NIST CSF'      THEN 'nist'
    ELSE lower(replace(name, ' ', '-'))
END WHERE slug IS NULL;
ALTER TABLE framework ALTER COLUMN slug SET NOT NULL;
ALTER TABLE framework ADD CONSTRAINT uq_framework_slug UNIQUE (slug);

ALTER TABLE control ADD COLUMN category VARCHAR(100);
