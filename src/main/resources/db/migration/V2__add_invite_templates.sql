-- ============================
-- Add invite student templates
-- ============================

INSERT INTO notification_templates (name, type, subject, body, variables)
VALUES
    ('invite_student_to_group_email', 'EMAIL', 'Invitation to join group: {{groupName}}',
     '<html>
      <body>
        <h2>Hello {{firstName}}!</h2>
        <p>You have been invited to join the group: <strong>{{groupName}}</strong></p>
        <p>Click the link below to accept the invitation:</p>
        <p><a href="{{joinLink}}">Join {{groupName}}</a></p>
        <p>If you did not expect this invitation, you can ignore this email.</p>
        <p>Best regards,<br>The Platform Team</p>
      </body>
     </html>',
     '{"firstName": "string", "groupName": "string", "joinLink": "string"}'::jsonb),

    ('invite_student_to_group_push', 'PUSH', 'New Group Invitation',
     'You have been invited to join {{groupName}}. Check your dashboard to accept.',
     '{"groupName": "string"}'::jsonb)
    ON CONFLICT (name) DO NOTHING;
