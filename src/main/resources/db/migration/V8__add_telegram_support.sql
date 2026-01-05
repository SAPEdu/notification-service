-- ============================
-- V8: Add Telegram Support
-- ============================

-- Add Telegram columns to notification_preferences
ALTER TABLE notification_preferences 
    ADD COLUMN IF NOT EXISTS telegram_enabled BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS telegram_linked_at TIMESTAMPTZ;

-- Create index for efficient telegram_chat_id lookups
CREATE INDEX IF NOT EXISTS idx_preferences_telegram_chat_id 
    ON notification_preferences(telegram_chat_id) 
    WHERE telegram_chat_id IS NOT NULL;

-- Add Telegram notification templates (plain text with emoji)
INSERT INTO notification_templates (name, type, subject, body, variables) VALUES

('welcome_user_telegram', 'TELEGRAM', NULL, 
'🎉 Chào mừng {{firstName}} {{lastName}}!

Tài khoản của bạn đã được tạo thành công.
👤 Username: {{username}}

Cảm ơn bạn đã tham gia hệ thống!', 
'{"firstName": "string", "lastName": "string", "username": "string"}'::jsonb),

('new_assessment_assigned_telegram', 'TELEGRAM', NULL,
'📚 *Assessment Mới*

📝 {{assessmentName}}
⏱ Thời gian: {{duration}} phút
📅 Hạn nộp: {{dueDate}}

Chúc bạn làm bài tốt! 💪', 
'{"assessmentName": "string", "duration": "number", "dueDate": "string"}'::jsonb),

('assessment_expiring_telegram', 'TELEGRAM', NULL,
'⏰ *Sắp Hết Hạn!*

📝 {{assessmentName}}
⚠️ Còn {{hoursRemaining}} giờ nữa là hết hạn!

Hãy hoàn thành bài kiểm tra ngay nhé!', 
'{"assessmentName": "string", "hoursRemaining": "number"}'::jsonb),

('attempt_submitted_telegram', 'TELEGRAM', NULL,
'✅ *Đã Nộp Bài*

📝 {{assessmentName}}
📊 Điểm: {{score}}/{{maxScore}}
🎯 Trạng thái: {{status}}

Cảm ơn bạn đã hoàn thành!', 
'{"assessmentName": "string", "score": "number", "maxScore": "number", "status": "string"}'::jsonb),

('attempt_graded_telegram', 'TELEGRAM', NULL,
'🎓 *Kết Quả Chấm Điểm*

📝 {{assessmentName}}
📊 Điểm: {{score}}/{{maxScore}} ({{percentage}}%)
🏆 Kết quả: {{passed}}

Chúc mừng bạn!', 
'{"assessmentName": "string", "score": "number", "maxScore": "number", "percentage": "number", "passed": "string"}'::jsonb),

('proctoring_alert_telegram', 'TELEGRAM', NULL,
'🚨 *Cảnh Báo Vi Phạm*

📍 Session: {{sessionId}}
👤 User: {{username}}
⚠️ Loại: {{violationType}}
🔴 Mức độ: {{severity}}

Vui lòng kiểm tra ngay!', 
'{"sessionId": "string", "username": "string", "violationType": "string", "severity": "string"}'::jsonb)

ON CONFLICT (name) DO UPDATE SET 
    body = EXCLUDED.body, 
    variables = EXCLUDED.variables,
    updated_at = NOW();

-- Add comments
COMMENT ON COLUMN notification_preferences.telegram_enabled IS 'Enable/disable Telegram notifications for user';
COMMENT ON COLUMN notification_preferences.telegram_chat_id IS 'Telegram chat ID linked to this user account';
COMMENT ON COLUMN notification_preferences.telegram_linked_at IS 'Timestamp when Telegram was linked';
