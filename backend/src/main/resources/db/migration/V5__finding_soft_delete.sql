-- V5: soft-delete for findings. A finding's audit trail must survive its deletion (the app is an
-- audit tool), so DELETE marks deleted_at instead of removing the row (which would cascade-wipe the
-- finding_control_mapping and audit_log children). NULL deleted_at = active; non-null = deleted.
ALTER TABLE finding ADD COLUMN deleted_at TIMESTAMPTZ;

-- Most reads filter `deleted_at IS NULL`; index it to keep the active-findings scan cheap.
CREATE INDEX ix_finding_deleted_at ON finding (deleted_at);
