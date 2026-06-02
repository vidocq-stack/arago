-- Phase 2: OpenGraph preview for URL pins (arago-spec §4.4). Best-effort, nullable — fetched
-- server-side at pin creation (SSRF-hardened) and stored alongside the pin.
ALTER TABLE pins ADD COLUMN preview_title       VARCHAR(500);
ALTER TABLE pins ADD COLUMN preview_image       VARCHAR(2000);
ALTER TABLE pins ADD COLUMN preview_description  VARCHAR(2000);
