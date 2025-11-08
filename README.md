# Notification Service - Implementation Guide

## 📋 Summary of Changes

Your notification service has been updated with the following improvements:

### ✅ Completed Updates

1. **Authentication Simplified**
    - ✓ No login flow needed - JWT verification only
    - ✓ Casdoor handles authentication externally
    - ✓ Your app validates tokens via JWK endpoint

2. **UUID Implementation**
    - ✓ All entity IDs changed from Integer to UUID
    - ✓ Better scalability for distributed systems
    - ✓ Non-sequential, secure IDs

3. **Template Type Enum**
    - ✓ Strong typing: EMAIL, SSE, PUSH, SMS
    - ✓ Compile-time validation
    - ✓ No more string typos

4. **Dynamic Template System**
    - ✓ Placeholder syntax: `{{variableName}}`
    - ✓ Template validation
    - ✓ User-specific customization

5. **Template Caching**
    - ✓ Caffeine cache integration
    - ✓ 1-hour TTL, max 100 templates
    - ✓ Automatic invalidation on updates

6. **SSE Explained**
    - ✓ Comprehensive documentation
    - ✓ Real-time push notifications
    - ✓ Automatic reconnection

7. **Bulk Notifications**
    - ✓ Send to multiple users with one API call
    - ✓ User-specific data merging
    - ✓ Async processing

---

## 📁 File Structure

```
/outputs/
├── README.md                           # This file
├── DOCUMENTATION.md                    # Complete documentation
├── API_EXAMPLES.md                     # API usage examples
│
├── enums/
│   └── TemplateType.java              # Template type enum
│
├── entity/
│   ├── Notification.java              # Updated with UUID
│   ├── NotificationTemplate.java      # Updated with UUID & enum
│   └── NotificationPreference.java    # Updated with UUID
│
├── dto/
│   ├── NotificationDto.java           # Updated with UUID
│   ├── TemplateDto.java               # Updated with UUID & enum
│   ├── PreferenceDto.java             # Updated with UUID
│   └── BulkNotificationRequest.java   # New: Bulk API request
│
├── repository/
│   ├── NotificationRepository.java          # Updated with UUID
│   ├── NotificationTemplateRepository.java  # Updated with UUID
│   └── NotificationPreferenceRepository.java # Updated with UUID
│
├── service/
│   ├── NotificationService.java       # Updated with bulk support
│   └── TemplateService.java           # Updated with caching
│
├── event/outbound/
│   ├── NotificationSentEvent.java     # Updated with UUID
│   └── NotificationFailedEvent.java   # Updated with UUID
│
├── controller/
│   └── NotificationController.java    # New: Bulk API endpoint
│
├── config/
│   └── CacheConfig.java               # New: Caching configuration
│
├── migration/
│   └── V2__migrate_to_uuid.sql        # Database migration
│
└── pom-dependencies.xml               # Required dependencies
```

---

## 🚀 Implementation Steps

### Step 1: Add Dependencies

Add to your `pom.xml`:

```xml
<!-- Caffeine Cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Spring Boot Cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### Step 2: Replace Files

Replace the following files in your project:

**Enums:**
- `src/main/java/com/example/notificationservice/enums/TemplateType.java` ✨ NEW

**Entities:**
- `src/main/java/com/example/notificationservice/entity/Notification.java`
- `src/main/java/com/example/notificationservice/entity/NotificationTemplate.java`
- `src/main/java/com/example/notificationservice/entity/NotificationPreference.java`

**DTOs:**
- `src/main/java/com/example/notificationservice/dto/NotificationDto.java`
- `src/main/java/com/example/notificationservice/dto/TemplateDto.java`
- `src/main/java/com/example/notificationservice/dto/PreferenceDto.java`
- `src/main/java/com/example/notificationservice/dto/BulkNotificationRequest.java` ✨ NEW

**Repositories:**
- `src/main/java/com/example/notificationservice/repository/NotificationRepository.java`
- `src/main/java/com/example/notificationservice/repository/NotificationTemplateRepository.java`
- `src/main/java/com/example/notificationservice/repository/NotificationPreferenceRepository.java`

**Services:**
- `src/main/java/com/example/notificationservice/service/NotificationService.java`
- `src/main/java/com/example/notificationservice/service/TemplateService.java`

**Events:**
- `src/main/java/com/example/notificationservice/event/outbound/NotificationSentEvent.java`
- `src/main/java/com/example/notificationservice/event/outbound/NotificationFailedEvent.java`

**Controllers:**
- `src/main/java/com/example/notificationservice/controller/NotificationController.java` ✨ NEW

**Config:**
- `src/main/java/com/example/notificationservice/config/CacheConfig.java` ✨ NEW

### Step 3: Database Migration

**⚠️ IMPORTANT: This will DROP existing data!**

If you have production data, you need to:
1. Export data first
2. Transform IDs to UUIDs
3. Re-import data

For fresh development environment:

```bash
# Place migration file
cp V2__migrate_to_uuid.sql src/main/resources/db/migration/

# Flyway will automatically run it on next startup
```

Or run manually:
```bash
psql -U your_user -d your_database -f V2__migrate_to_uuid.sql
```

### Step 4: Update application.yml (if needed)

```yaml
spring:
  # Cache configuration
  cache:
    type: caffeine
    cache-names: templates

  # Security (already configured)
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://your-casdoor:8000/api/.well-known/jwks
```

### Step 5: Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

---

## 🧪 Testing Guide

### 1. Test Authentication

```bash
# Should work with valid JWT
curl -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:8080/api/v1/auth/test/me

# Should return 401 without token
curl http://localhost:8080/api/v1/auth/test/me
```

### 2. Test Template CRUD

```bash
# Create template (Admin)
curl -X POST http://localhost:8080/api/v1/templates \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test_template",
    "type": "EMAIL",
    "subject": "Hello {{username}}",
    "body": "Welcome {{username}}!",
    "variables": {"username": "string"}
  }'

# Get template (first call → DB, second call → cache)
curl -H "Authorization: Bearer ADMIN_TOKEN" \
     http://localhost:8080/api/v1/templates/test_template
```

### 3. Test SSE Connection

**Browser Console:**
```javascript
const es = new EventSource('/api/v1/sse/connect');
es.addEventListener('connect', (e) => console.log('Connected:', e.data));
es.addEventListener('notification', (e) => console.log('Notification:', e.data));
```

**cURL:**
```bash
curl -N -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:8080/api/v1/sse/connect
```

### 4. Test Bulk Notifications

```bash
curl -X POST http://localhost:8080/api/v1/notifications/bulk \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userIds": [123, 124, 125],
    "type": "assessment.published",
    "channels": ["EMAIL", "SSE"],
    "commonData": {
      "assessmentName": "Test Assessment",
      "duration": 60
    },
    "userSpecificData": {
      "123": {"username": "User1", "email": "user1@example.com"},
      "124": {"username": "User2", "email": "user2@example.com"},
      "125": {"username": "User3", "email": "user3@example.com"}
    }
  }'
```

---

## 📚 Key Concepts

### 1. Authentication (No Login Flow)

```
┌─────────┐       Login       ┌─────────┐
│  User   │ ───────────────> │ Casdoor │
└─────────┘                   └─────────┘
     │                             │
     │  ← JWT Token ←───────────── │
     │
     │  API Request + JWT Token
     ↓
┌──────────────────────┐
│  Your Application    │
│  (Validates JWT)     │
└──────────────────────┘
```

### 2. Template Processing

```
Template: "Hello {{username}}, your score is {{score}}"
Data:     {username: "John", score: 95}
Result:   "Hello John, your score is 95"
```

### 3. Template Caching Flow

```
First Request:
  GET /templates/welcome_user
  → Check Cache (MISS)
  → Query Database
  → Store in Cache
  → Return Template

Second Request:
  GET /templates/welcome_user
  → Check Cache (HIT)
  → Return from Cache (fast!)

After Update:
  PUT /templates/welcome_user
  → Invalidate Cache
  → Next GET will refresh from DB
```

### 4. SSE Communication

```
Client                          Server
  │                                │
  ├─── GET /sse/connect ──────────>│
  │                                │
  │<─── event: connect ─────────── │
  │     data: {connected: true}    │
  │                                │
  │<─── event: heartbeat ──────────│ (every 30s)
  │                                │
  │<─── event: notification ───────│ (when available)
  │     data: {type: "alert",...}  │
  │                                │
```

### 5. Bulk Notification Flow

```
Admin Request:
  POST /notifications/bulk
  {userIds: [1,2,3], type: "alert", ...}
  
Server Processing (Async):
  For each userId:
    1. Fetch template
    2. Merge commonData + userSpecificData
    3. Process template
    4. Check user preferences
    5. Send via channels (EMAIL, SSE)
  
  Track: success/failed counts
  
Response:
  {total: 3, success: 3, failed: 0}
```

---

## 🎯 Usage Examples

### Example 1: Create Custom Template

```bash
curl -X POST http://localhost:8080/api/v1/templates \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "exam_reminder",
    "type": "EMAIL",
    "subject": "Reminder: {{examName}} in {{hoursLeft}} hours",
    "body": "<html><body><h2>Hi {{studentName}},</h2><p>Your exam <strong>{{examName}}</strong> starts in {{hoursLeft}} hours.</p><p>Good luck!</p></body></html>",
    "variables": {
      "studentName": "string",
      "examName": "string",
      "hoursLeft": "number"
    }
  }'
```

### Example 2: Send Bulk Exam Reminder

```bash
curl -X POST http://localhost:8080/api/v1/notifications/bulk \
  -H "Authorization: Bearer ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userIds": [101, 102, 103],
    "type": "exam_reminder",
    "channels": ["EMAIL", "SSE"],
    "commonData": {
      "examName": "Java Certification",
      "hoursLeft": 2
    },
    "userSpecificData": {
      "101": {"studentName": "Alice", "email": "alice@example.com"},
      "102": {"studentName": "Bob", "email": "bob@example.com"},
      "103": {"studentName": "Carol", "email": "carol@example.com"}
    }
  }'
```

### Example 3: Real-time Notifications (SSE)

**HTML + JavaScript:**
```html
<!DOCTYPE html>
<html>
<head>
    <title>Real-time Notifications</title>
</head>
<body>
    <h1>Notification Center</h1>
    <div id="notifications"></div>

    <script>
        const token = 'YOUR_JWT_TOKEN';
        const eventSource = new EventSource('/api/v1/sse/connect', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        eventSource.addEventListener('notification', (event) => {
            const notification = JSON.parse(event.data);
            
            // Display notification
            const div = document.createElement('div');
            div.className = 'notification';
            div.innerHTML = `
                <strong>${notification.type}</strong><br>
                ${notification.content}<br>
                <small>${new Date(notification.timestamp).toLocaleString()}</small>
            `;
            document.getElementById('notifications').prepend(div);

            // Browser notification
            if (Notification.permission === 'granted') {
                new Notification('New Alert', {
                    body: notification.content
                });
            }
        });
    </script>
</body>
</html>
```

---

## 🔧 Troubleshooting

### Issue: Cache not working

**Check:**
```java
// Verify @EnableCaching is present
@Configuration
@EnableCaching
public class CacheConfig { ... }

// Verify annotations on service methods
@Cacheable(value = "templates", key = "#name")
public Optional<TemplateDto> getTemplateByName(String name) { ... }
```

### Issue: SSE connection drops

**Solution:**
- Heartbeat mechanism already implemented (every 30s)
- Browser automatically reconnects
- Check firewall/proxy timeout settings

### Issue: Bulk notifications slow

**Optimize:**
1. Increase thread pool size in `AsyncConfig`:
   ```java
   executor.setCorePoolSize(10); // from 5
   executor.setMaxPoolSize(20);  // from 10
   ```

2. Batch database operations
3. Use parallel streams for processing

### Issue: Template variables not replaced

**Debug:**
```java
// Check variable names match exactly
Template: "Hello {{username}}"  
Data: {"username": "John"}  ✅ Works

Template: "Hello {{username}}"
Data: {"userName": "John"}   ❌ Case mismatch!

// Enable debug logging
logging.level.com.example.notificationservice.util.TemplateEngine=DEBUG
```

---

## 📖 Documentation Files

1. **DOCUMENTATION.md** - Complete technical documentation
    - Authentication flow
    - UUID implementation
    - Template system details
    - Caching mechanism
    - SSE architecture
    - Bulk notifications

2. **API_EXAMPLES.md** - API usage examples
    - Request/response samples
    - cURL commands
    - JavaScript client code
    - Testing tips

3. **README.md** - This file
    - Implementation guide
    - Quick start
    - File structure
    - Testing guide

---

## 🎓 Learning Resources

### Understanding SSE
- SSE vs WebSocket comparison
- Real-time communication patterns
- Browser EventSource API

### Understanding Caching
- Caffeine cache features
- Cache strategies (TTL, size limits)
- Cache invalidation patterns

### Understanding Templates
- Template engine design
- Placeholder syntax
- Variable validation

---

## ✅ Checklist

Before deploying:

- [ ] Dependencies added to pom.xml
- [ ] All files replaced in project
- [ ] Database migration executed
- [ ] application.yml updated
- [ ] Application builds successfully
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Template CRUD tested
- [ ] SSE connection tested
- [ ] Bulk notification tested
- [ ] Cache hit/miss verified
- [ ] Authentication working
- [ ] Documentation reviewed

---

## 🆘 Support

If you encounter issues:

1. Check logs: `tail -f logs/application.log`
2. Verify JWT token is valid
3. Check database connection
4. Review error responses
5. Test with Postman/cURL
6. Check Kafka connectivity
7. Verify email settings

---

## 🚀 Next Steps

Consider adding:

1. **WebSocket Support** - For bidirectional communication
2. **Push Notifications** - Mobile app integration
3. **SMS Service** - Third-party SMS provider
4. **Notification History** - Archive old notifications
5. **Analytics Dashboard** - Track notification metrics
6. **A/B Testing** - Test different templates
7. **Scheduled Notifications** - Send at specific times

---

## 📝 Notes

- UUIDs use PostgreSQL's `uuid_generate_v4()`
- Cache uses Caffeine (high-performance Java cache)
- SSE timeout: 24 hours (configurable)
- Bulk processing is async (non-blocking)
- Templates support HTML and plain text
- All admin endpoints require ROLE_ADMIN

---

**Happy Coding! 🎉**

If you have questions, refer to DOCUMENTATION.md for detailed explanations or API_EXAMPLES.md for practical examples.