-- ============================
-- Migration: Add templates for Assessment Service notification events
-- ============================

-- Assessment Expiring Templates
INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    -- Email version
    ('assessment_expiring_email', 'EMAIL', 'Sắp hết hạn: {{assessmentTitle}}',
     '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background: linear-gradient(135deg, #f39c12, #e74c3c); color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center; }
        .content { background: #f9f9f9; padding: 20px; border-radius: 0 0 8px 8px; }
        .warning { background: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 5px; margin: 15px 0; }
        .btn { display: inline-block; padding: 12px 24px; background: #e74c3c; color: white; text-decoration: none; border-radius: 5px; margin-top: 15px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>⏰ Sắp hết hạn!</h1>
        </div>
        <div class="content">
            <p>Xin chào,</p>
            <div class="warning">
                <strong>Bài kiểm tra "{{assessmentTitle}}"</strong> chỉ còn <strong>{{hoursRemaining}} giờ</strong> nữa là hết hạn!
            </div>
            <p><strong>Hạn nộp:</strong> {{dueDate}}</p>
            <p><strong>Nhóm:</strong> {{groupName}}</p>
            <p>Vui lòng hoàn thành bài kiểm tra trước thời hạn.</p>
            <a href="#" class="btn">Làm bài ngay</a>
        </div>
    </div>
</body>
</html>',
     '{"assessmentTitle": "string", "hoursRemaining": "number", "dueDate": "string", "groupName": "string"}'::jsonb),
    
    -- Push version
    ('assessment_expiring_push', 'PUSH', 'Sắp hết hạn!',
     '⏰ "{{assessmentTitle}}" còn {{hoursRemaining}}h nữa là hết hạn. Hạn: {{dueDate}}',
     '{"assessmentTitle": "string", "hoursRemaining": "number", "dueDate": "string", "groupName": "string"}'::jsonb)

ON CONFLICT (name) DO UPDATE
SET type = EXCLUDED.type,
    subject = EXCLUDED.subject,
    body = EXCLUDED.body,
    variables = EXCLUDED.variables,
    updated_at = NOW();

-- Attempt Started Templates (for teachers)
INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    -- Email version
    ('attempt_started_email', 'EMAIL', 'Student đang làm bài: {{assessmentTitle}}',
     '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background: linear-gradient(135deg, #3498db, #2980b9); color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center; }
        .content { background: #f9f9f9; padding: 20px; border-radius: 0 0 8px 8px; }
        .info-box { background: #e8f4fc; border-left: 4px solid #3498db; padding: 15px; margin: 15px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>▶️ Student đang làm bài</h1>
        </div>
        <div class="content">
            <div class="info-box">
                <p><strong>Student:</strong> {{studentId}}</p>
                <p><strong>Bài kiểm tra:</strong> {{assessmentTitle}}</p>
                <p><strong>Bắt đầu lúc:</strong> {{startedAt}}</p>
                <p><strong>Thời gian làm bài:</strong> {{timeLimit}} phút</p>
            </div>
        </div>
    </div>
</body>
</html>',
     '{"studentId": "string", "assessmentTitle": "string", "startedAt": "string", "timeLimit": "number"}'::jsonb),
    
    -- Push version
    ('attempt_started_push', 'PUSH', 'Student đang làm bài',
     '▶️ {{studentId}} đã bắt đầu "{{assessmentTitle}}"',
     '{"studentId": "string", "assessmentTitle": "string", "startedAt": "string", "timeLimit": "number"}'::jsonb)

ON CONFLICT (name) DO UPDATE
SET type = EXCLUDED.type,
    subject = EXCLUDED.subject,
    body = EXCLUDED.body,
    variables = EXCLUDED.variables,
    updated_at = NOW();

-- Attempt Submitted Templates (for teachers)
INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    -- Email version
    ('attempt_submitted_email', 'EMAIL', 'Đã nộp bài: {{assessmentTitle}}',
     '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background: linear-gradient(135deg, #27ae60, #2ecc71); color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center; }
        .content { background: #f9f9f9; padding: 20px; border-radius: 0 0 8px 8px; }
        .info-box { background: #e8f8f0; border-left: 4px solid #27ae60; padding: 15px; margin: 15px 0; }
        .pending-badge { background: #f39c12; color: white; padding: 5px 10px; border-radius: 4px; font-size: 12px; }
        .btn { display: inline-block; padding: 12px 24px; background: #27ae60; color: white; text-decoration: none; border-radius: 5px; margin-top: 15px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📤 Đã nộp bài</h1>
        </div>
        <div class="content">
            <div class="info-box">
                <p><strong>Student:</strong> {{studentId}}</p>
                <p><strong>Bài kiểm tra:</strong> {{assessmentTitle}}</p>
                <p><strong>Nộp lúc:</strong> {{submittedAt}}</p>
                <p><strong>Điểm tạm:</strong> {{score}}/{{maxScore}}</p>
            </div>
            {{#if isPendingGrade}}
            <p><span class="pending-badge">⚠️ Cần chấm thủ công</span></p>
            <a href="#" class="btn">Chấm bài ngay</a>
            {{/if}}
        </div>
    </div>
</body>
</html>',
     '{"studentId": "string", "assessmentTitle": "string", "submittedAt": "string", "score": "number", "maxScore": "number", "isPendingGrade": "boolean"}'::jsonb),
    
    -- Push version
    ('attempt_submitted_push', 'PUSH', 'Đã nộp bài',
     '📤 {{studentId}} đã nộp "{{assessmentTitle}}". Điểm: {{score}}/{{maxScore}}',
     '{"studentId": "string", "assessmentTitle": "string", "submittedAt": "string", "score": "number", "maxScore": "number", "isPendingGrade": "boolean"}'::jsonb)

ON CONFLICT (name) DO UPDATE
SET type = EXCLUDED.type,
    subject = EXCLUDED.subject,
    body = EXCLUDED.body,
    variables = EXCLUDED.variables,
    updated_at = NOW();

-- Attempt Graded Templates (for students)
INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    -- Email version
    ('attempt_graded_email', 'EMAIL', 'Đã chấm điểm: {{assessmentTitle}}',
     '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background: linear-gradient(135deg, #9b59b6, #8e44ad); color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center; }
        .content { background: #f9f9f9; padding: 20px; border-radius: 0 0 8px 8px; }
        .score-box { text-align: center; padding: 30px; }
        .score { font-size: 48px; font-weight: bold; color: #27ae60; }
        .percentage { font-size: 24px; color: #666; }
        .passed { color: #27ae60; }
        .failed { color: #e74c3c; }
        .btn { display: inline-block; padding: 12px 24px; background: #9b59b6; color: white; text-decoration: none; border-radius: 5px; margin-top: 15px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📝 Đã chấm điểm</h1>
        </div>
        <div class="content">
            <h2>{{assessmentTitle}}</h2>
            <div class="score-box">
                <div class="score">{{score}}/{{maxScore}}</div>
                <div class="percentage">({{percentage}}%)</div>
                {{#if passed}}
                <p class="passed">✅ Đạt</p>
                {{else}}
                <p class="failed">❌ Chưa đạt</p>
                {{/if}}
            </div>
            <p><strong>Chấm lúc:</strong> {{gradedAt}}</p>
            <a href="#" class="btn">Xem chi tiết</a>
        </div>
    </div>
</body>
</html>',
     '{"assessmentTitle": "string", "score": "number", "maxScore": "number", "percentage": "number", "passed": "boolean", "gradedAt": "string"}'::jsonb),
    
    -- Push version
    ('attempt_graded_push', 'PUSH', 'Đã chấm điểm',
     '📝 "{{assessmentTitle}}": {{score}}/{{maxScore}} ({{percentage}}%)',
     '{"assessmentTitle": "string", "score": "number", "maxScore": "number", "percentage": "number", "passed": "boolean", "gradedAt": "string"}'::jsonb)

ON CONFLICT (name) DO UPDATE
SET type = EXCLUDED.type,
    subject = EXCLUDED.subject,
    body = EXCLUDED.body,
    variables = EXCLUDED.variables,
    updated_at = NOW();
