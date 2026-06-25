-- V3 — add a display name to users (needed by GET /api/auth/me and the account menu, PLAN §7.11).
-- Added nullable, backfilled for any existing rows from the email local-part, then made NOT NULL.
ALTER TABLE app_user ADD COLUMN name VARCHAR(255);
UPDATE app_user SET name = INITCAP(SPLIT_PART(email, '@', 1)) WHERE name IS NULL;
ALTER TABLE app_user ALTER COLUMN name SET NOT NULL;
