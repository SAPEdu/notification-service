-- Migration V6: Change user_id and recipient_id from INTEGER to VARCHAR to support UUIDs

-- 1. Alter notifications table
ALTER TABLE notifications ALTER COLUMN recipient_id TYPE VARCHAR(50);

-- 2. Alter notification_preferences table
ALTER TABLE notification_preferences ALTER COLUMN user_id TYPE VARCHAR(50);
