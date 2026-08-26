-- Background listening: media playing with the screen off. A separate measure
-- from screen time, so a separate column rather than a wider foreground_ms.
ALTER TABLE usage_hours ADD COLUMN background_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usage_days  ADD COLUMN background_ms BIGINT NOT NULL DEFAULT 0;

-- Not "zero minutes" — "this device could not observe it". An iPhone, or an
-- Android phone whose family declined the notification grant. Every reader has
-- to keep the two apart, so the flag travels with the hour.
ALTER TABLE device_hours ADD COLUMN background_measured BOOLEAN NOT NULL DEFAULT false;
