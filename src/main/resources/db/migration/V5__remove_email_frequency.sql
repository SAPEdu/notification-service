-- ============================
-- Migration: Remove email_frequency from notification_preferences
-- Description: The email_frequency column is unused and being removed from the codebase.
-- ============================

ALTER TABLE notification_preferences
DROP COLUMN IF EXISTS email_frequency;
