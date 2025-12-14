-- ============================
-- Migration: User Notification APIs and Preferences Redesign
-- ============================

-- 1) Add is_read column to notifications table
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_read BOOLEAN DEFAULT FALSE;

-- 2) Create index for is_read queries (unread count, filtering)
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);

-- 3) Composite index for efficient user notification queries
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read ON notifications(recipient_id, is_read);

-- 4) Add notifications_enabled global toggle to preferences
ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN DEFAULT TRUE;

-- 5) Rename categories to notification_type_settings for clarity
-- The JSONB structure will now store: { "type_name": { "enabled": bool, "emailEnabled": bool, "pushEnabled": bool } }
-- No column rename needed since we keep using 'categories' but with new structure

-- 6) Drop sse_enabled column (consolidating SSE into PUSH)
ALTER TABLE notification_preferences DROP COLUMN IF EXISTS sse_enabled;

-- 7) Update SSE templates to PUSH
UPDATE notification_templates SET type = 'PUSH' WHERE type = 'SSE';

-- 8) Update SSE channel in existing notifications to PUSH
UPDATE notifications SET channel = 'PUSH' WHERE channel = 'SSE';

-- 9) Update comments
COMMENT ON COLUMN notifications.is_read IS 'Whether the notification has been read by the recipient';
COMMENT ON COLUMN notifications.channel IS 'Notification channel: EMAIL or PUSH';
COMMENT ON COLUMN notification_preferences.notifications_enabled IS 'Global toggle to enable/disable all notifications';
