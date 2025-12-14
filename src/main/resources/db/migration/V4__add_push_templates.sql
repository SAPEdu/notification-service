-- ============================
-- Migration: Add PUSH-specific templates (plain text format for UI inbox)
-- ============================

-- PUSH templates for notification inbox (plain text format)
INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    -- Welcome user - PUSH version
    ('welcome_user_push', 'PUSH', 'Welcome to the Platform!',
     'Welcome {{firstName}}! Your account has been created successfully. Username: {{username}}',
     '{"firstName": "string", "lastName": "string", "username": "string", "email": "string"}'::jsonb),

    -- Session completion - PUSH version
    ('session_completion_push', 'PUSH', 'Assessment Completed',
     'Congratulations {{username}}! You completed "{{assessmentName}}" with a score of {{score}}. Status: {{status}}',
     '{"username": "string", "assessmentName": "string", "completionTime": "string", "score": "number", "status": "string"}'::jsonb),

    -- Proctoring alert - PUSH version
    ('proctoring_alert_push', 'PUSH', 'Proctoring Violation Detected',
     'Proctoring violation detected for session {{sessionId}}. User: {{username}}, Type: {{violationType}}, Severity: {{severity}}',
     '{"username": "string", "sessionId": "string", "violationType": "string", "severity": "string", "timestamp": "string"}'::jsonb),

    -- New assessment assigned - PUSH version
    ('new_assessment_assigned_push', 'PUSH', 'New Assessment: {{assessmentName}}',
     'You have been assigned a new assessment: "{{assessmentName}}". Duration: {{duration}} minutes. Due: {{dueDate}}',
     '{"username": "string", "assessmentName": "string", "duration": "number", "dueDate": "string"}'::jsonb),

    -- Invite student to group - PUSH version (already exists in V2 but ensuring consistency)
    ('invite_student_to_group_push_v2', 'PUSH', 'Group Invitation',
     'You have been invited to join the group "{{groupName}}". Check your dashboard to accept the invitation.',
     '{"firstName": "string", "groupName": "string", "joinLink": "string"}'::jsonb),

    -- Assessment reminder - PUSH version
    ('assessment_reminder_push', 'PUSH', 'Assessment Reminder',
     'Reminder: Your assessment "{{assessmentName}}" is due on {{dueDate}}. Please complete it before the deadline.',
     '{"username": "string", "assessmentName": "string", "dueDate": "string"}'::jsonb),

    -- Grade available - PUSH version
    ('grade_available_push', 'PUSH', 'Grade Available',
     'Your grade for "{{assessmentName}}" is now available. Score: {{score}}',
     '{"username": "string", "assessmentName": "string", "score": "number"}'::jsonb),

    -- Comment/feedback - PUSH version
    ('comment_feedback_push', 'PUSH', 'New Comment on Your Work',
     '{{teacherName}} left a comment on your submission for "{{assessmentName}}": "{{commentPreview}}"',
     '{"username": "string", "teacherName": "string", "assessmentName": "string", "commentPreview": "string"}'::jsonb),

    -- System update - PUSH version
    ('system_update_push', 'PUSH', 'System Update',
     '{{title}}: {{message}}',
     '{"title": "string", "message": "string"}'::jsonb)

ON CONFLICT (name) DO UPDATE
SET type = EXCLUDED.type,
    subject = EXCLUDED.subject,
    body = EXCLUDED.body,
    variables = EXCLUDED.variables,
    updated_at = NOW();

-- Update existing templates to have _email suffix for clarity
UPDATE notification_templates SET name = 'welcome_user_email' WHERE name = 'welcome_user';
UPDATE notification_templates SET name = 'session_completion_email' WHERE name = 'session_completion';
UPDATE notification_templates SET name = 'proctoring_alert_email' WHERE name = 'proctoring_alert';
UPDATE notification_templates SET name = 'new_assessment_assigned_email' WHERE name = 'new_assessment_assigned';
UPDATE notification_templates SET name = 'invite_student_to_group_email_v2' WHERE name = 'invite_student_to_group_email';
