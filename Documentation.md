# Notification Service - Documentation

## Table of Contents
1. [Authentication](#authentication)
2. [UUID Implementation](#uuid-implementation)
3. [Template System](#template-system)
4. [Template Caching](#template-caching)
5. [SSE (Server-Sent Events)](#sse-server-sent-events)
6. [Bulk Notifications](#bulk-notifications)

---

## 1. Authentication

### Current Implementation
Your application uses **JWT token verification only** - there's no login endpoint in this service.

**Authentication Flow:**
```
1. User logs in via Casdoor (external OAuth2 provider)
2. Casdoor issues JWT access token
3. User includes token in requests: Authorization: Bearer <token>
4. Your Spring Security validates the JWT using Casdoor's JWK endpoint
5. CasdoorAuthenticationContext extracts user info from validated JWT
```

**Key Components:**
- `SecurityConfig`: Configures JWT validation
- `CasdoorAuthenticationContext`: Extracts user ID, roles, email from JWT claims
- No login/logout endpoints in this service

**Configuration (application.yml):**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://your-casdoor:8000/api/.well-known/jwks
```

---

## 2. UUID Implementation

### Changes Made
All entity IDs changed from `Integer` to `UUID` for better scalability and distributed systems support.

**Benefits:**
- No sequential ID collision in distributed environments
- Better security (IDs not predictable)
- Can generate IDs client-side if needed

**Updated Entities:**
- `NotificationTemplate.id` → UUID
- `Notification.id` → UUID
- `NotificationPreference.id` → UUID

**Migration:**
Use `V2__migrate_to_uuid.sql` to migrate existing database.

---

## 3. Template System

### Overview
Templates use **placeholder syntax**: `{{variableName}}` for dynamic content.

### Template Type Enum
```java
public enum TemplateType {
    EMAIL,    // Email notifications
    SSE,      // Real-time browser notifications
    PUSH,     // Mobile push notifications
    SMS       // SMS notifications
}
```

### How Templates Work

#### 1. Template Definition
```json
{
  "name": "welcome_user",
  "type": "EMAIL",
  "subject": "Welcome {{firstName}}!",
  "body": "<h1>Hello {{firstName}} {{lastName}}!</h1><p>Your username: {{username}}</p>",
  "variables": {
    "firstName": "string",
    "lastName": "string", 
    "username": "string"
  }
}
```

#### 2. Template Processing
The `TemplateEngine` class processes templates:

```java
// Input template
String template = "Hello {{username}}, your score is {{score}}";

// Input data
Map<String, Object> data = Map.of(
    "username", "John",
    "score", 95
);

// Output
String result = templateEngine.processTemplate(template, data);
// Result: "Hello John, your score is 95"
```

#### 3. Pattern Matching
- Uses regex: `\\{\\{(\\w+)\\}\\}`
- Matches: `{{variableName}}`
- Extracts variable name and replaces with actual value
- Keeps placeholder if no value found

### Creating Templates

**API Endpoint:**
```http
POST /api/v1/templates
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "name": "custom_alert",
  "type": "SSE",
  "subject": "Alert for {{username}}",
  "body": "Dear {{username}}, {{message}}",
  "variables": {
    "username": "string",
    "message": "string"
  }
}
```

### Using Templates in Notifications

**Single User:**
```java
Map<String, Object> data = new HashMap<>();
data.put("username", "John");
data.put("message", "Your session expires in 5 minutes");

notificationService.processNotification(
    "custom_alert",           // template name
    userId,                   // recipient
    email,                    // email address
    data,                     // template data
    List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
);
```

**Multiple Users (Bulk):**
```http
POST /api/v1/notifications/bulk
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "userIds": [123, 124, 125],
  "type": "custom_alert",
  "channels": ["EMAIL", "SSE"],
  "commonData": {
    "message": "System maintenance scheduled"
  },
  "userSpecificData": {
    "123": {"username": "John", "email": "john@example.com"},
    "124": {"username": "Jane", "email": "jane@example.com"},
    "125": {"username": "Bob", "email": "bob@example.com"}
  }
}
```

---

## 4. Template Caching

### Configuration
Templates are cached using **Caffeine Cache** for performance.

**Cache Settings:**
- Cache name: `templates`
- Max size: 100 templates
- TTL: 1 hour
- Statistics enabled

**How It Works:**
```java
@Cacheable(value = "templates", key = "#name")
public Optional<TemplateDto> getTemplateByName(String name) {
    // First call → database query → cached
    // Subsequent calls → served from cache
}

@CacheEvict(value = "templates", key = "#name")
public TemplateDto updateTemplate(String name, TemplateDto dto) {
    // Invalidates cache on update
}
```

**Benefits:**
- Faster template retrieval (no DB query)
- Reduced database load
- Automatic eviction on updates

**Dependency (pom.xml):**
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

---

## 5. SSE (Server-Sent Events)

### What is SSE?

**Server-Sent Events** is a technology that allows servers to push real-time updates to clients over HTTP.

**Key Characteristics:**
- **Unidirectional**: Server → Client only (not bidirectional like WebSocket)
- **HTTP-based**: Uses standard HTTP connection
- **Automatic reconnection**: Browser automatically reconnects if connection drops
- **Event-based**: Server sends named events that client listens to

### SSE vs WebSocket

| Feature | SSE | WebSocket |
|---------|-----|-----------|
| Direction | Server → Client | Bidirectional |
| Protocol | HTTP | ws:// or wss:// |
| Reconnection | Automatic | Manual |
| Complexity | Simple | Complex |
| Use Case | Real-time updates, notifications | Chat, gaming, collaboration |

### How SSE Works in Your Application

#### Architecture
```
┌─────────┐         HTTP GET          ┌──────────────────┐
│ Client  │ ────────────────────────> │  Spring Boot     │
│ Browser │                            │  SseEmitter      │
└─────────┘                            └──────────────────┘
     ↑                                          │
     │                                          │
     │        Server-Sent Events                │
     │     (text/event-stream)                  │
     │                                          │
     └──────────────────────────────────────────┘
```

#### 1. Client Connects
```javascript
// JavaScript client code
const eventSource = new EventSource('/api/v1/sse/connect', {
  headers: {
    'Authorization': 'Bearer YOUR_JWT_TOKEN'
  }
});

// Listen for different event types
eventSource.addEventListener('notification', (event) => {
  const data = JSON.parse(event.data);
  console.log('New notification:', data);
  showNotification(data.content);
});

eventSource.addEventListener('connect', (event) => {
  console.log('Connected:', event.data);
});

eventSource.addEventListener('heartbeat', (event) => {
  console.log('Connection alive');
});

// Handle errors
eventSource.onerror = (error) => {
  console.error('SSE error:', error);
};
```

#### 2. Server Creates Emitter
```java
@GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter connect() {
    Integer userId = authContext.getCurrentUserId().orElse(null);
    
    // Create emitter with 24-hour timeout
    SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L);
    
    // Store emitter mapped to userId
    userEmitters.put("user_" + userId, emitter);
    
    // Set up cleanup callbacks
    emitter.onCompletion(() -> removeUserEmitter(userId));
    emitter.onTimeout(() -> removeUserEmitter(userId));
    emitter.onError((ex) -> removeUserEmitter(userId));
    
    // Send initial connection event
    emitter.send(SseEmitter.event()
        .name("connect")
        .data(Map.of("message", "Connected", "userId", userId)));
    
    return emitter;
}
```

#### 3. Server Sends Events
```java
// Send notification to specific user
public boolean sendToUser(Integer userId, String eventName, Object data) {
    SseEmitter emitter = userEmitters.get("user_" + userId);
    
    if (emitter != null) {
        try {
            emitter.send(SseEmitter.event()
                .id(String.valueOf(System.currentTimeMillis()))
                .name(eventName)
                .data(data));
            return true;
        } catch (IOException e) {
            // Connection broken, remove emitter
            removeUserEmitter(userId);
            return false;
        }
    }
    return false;
}
```

#### 4. Event Format
SSE events sent over the wire look like this:
```
id: 1234567890
event: notification
data: {"type":"proctoring.violation","content":"Alert message","timestamp":"2025-10-25T10:30:00Z"}

id: 1234567891
event: heartbeat
data: {"type":"heartbeat","timestamp":"2025-10-25T10:30:30Z"}
```

### SSE Features in Your Application

#### 1. User-Specific Connections
Each user gets their own SSE connection:
```java
// User connects
GET /api/v1/sse/connect
Authorization: Bearer <token>

// User ID extracted from JWT
// Emitter stored as: userEmitters.put("user_123", emitter)
```

#### 2. Topic Subscriptions
Users can subscribe to broadcast topics:
```java
// Subscribe to "proctoring" topic
GET /api/v1/sse/subscribe/proctoring
Authorization: Bearer <token>

// Multiple users subscribe to same topic
// Broadcast message goes to all subscribers
```

#### 3. Heartbeat Mechanism
Keeps connections alive:
```java
@Scheduled(fixedDelay = 30000) // Every 30 seconds
public void sendHeartbeat() {
    userEmitters.forEach((key, emitter) -> {
        try {
            emitter.send(SseEmitter.event()
                .name("heartbeat")
                .comment("keep-alive"));
        } catch (IOException e) {
            // Remove dead connection
            removeUserEmitter(key);
        }
    });
}
```

#### 4. Automatic Cleanup
Connections are automatically cleaned up on:
- Timeout (24 hours)
- Client disconnect
- Error
- Manual disconnect

### SSE Connection Lifecycle

```
1. CLIENT CONNECTS
   ↓
2. SERVER CREATES EMITTER
   ↓
3. SEND INITIAL "connect" EVENT
   ↓
4. STORE EMITTER IN MAP (userEmitters)
   ↓
5. NOTIFICATIONS SENT AS EVENTS
   ↓
6. HEARTBEATS EVERY 30s
   ↓
7. ON DISCONNECT/TIMEOUT/ERROR
   ↓
8. REMOVE FROM MAP
   ↓
9. CONNECTION CLOSED
```

### Testing SSE

**Using cURL:**
```bash
curl -N -H "Authorization: Bearer YOUR_TOKEN" \
     http://localhost:8080/api/v1/sse/connect
```

**Using JavaScript:**
```javascript
const eventSource = new EventSource('/api/v1/sse/connect');
eventSource.onmessage = (e) => console.log(e.data);
```

**Admin: Send Test Notification:**
```bash
curl -X POST "http://localhost:8080/api/v1/sse/test/send-to-user?targetUserId=123&message=Test" \
     -H "Authorization: Bearer ADMIN_TOKEN"
```

### SSE Use Cases in Your Application

1. **Real-time Proctoring Alerts**: Notify proctors immediately when violations occur
2. **Assessment Notifications**: Inform users when new assessments are assigned
3. **Session Updates**: Real-time updates during exam sessions
4. **System Broadcasts**: Maintenance notifications to all connected users

### SSE Advantages

✅ **Simple**: Just HTTP GET, no special protocol
✅ **Automatic Reconnection**: Browser handles reconnection
✅ **Efficient**: Single connection for multiple events
✅ **Scalable**: ConcurrentHashMap handles concurrent access
✅ **Firewall-Friendly**: Uses standard HTTP/HTTPS

### SSE Limitations

❌ **One-way**: Server → Client only (use WebSocket for bidirectional)
❌ **HTTP/1.1**: Limited concurrent connections per domain (6-8 typically)
❌ **No Binary**: Text-based only (JSON works great though)

---

## 6. Bulk Notifications

### Endpoint
```http
POST /api/v1/notifications/bulk
Authorization: Bearer <admin_token>
Content-Type: application/json
```

### Request Body
```json
{
  "userIds": [123, 124, 125],
  "type": "assessment.published",
  "channels": ["EMAIL", "SSE"],
  "commonData": {
    "assessmentName": "Spring Boot Advanced",
    "duration": 120,
    "dueDate": "2025-11-01"
  },
  "userSpecificData": {
    "123": {
      "username": "John",
      "email": "john@example.com"
    },
    "124": {
      "username": "Jane",
      "email": "jane@example.com"
    },
    "125": {
      "username": "Bob",
      "email": "bob@example.com"
    }
  }
}
```

### Response
```json
{
  "message": "Bulk notification processing completed",
  "requestedBy": "admin",
  "statistics": {
    "total": 3,
    "success": 3,
    "failed": 0
  },
  "timestamp": 1234567890
}
```

### How It Works

1. **Admin sends bulk request** with array of userIds
2. **Service fetches template** based on notification type
3. **For each user:**
    - Merge commonData with user-specific data
    - Process template with merged data
    - Check user preferences
    - Send via requested channels
4. **Track statistics** (success/failed counts)
5. **Publish completion event** to Kafka

### Data Merging Strategy
```java
// Common data applies to all users
commonData = {"assessmentName": "Test", "duration": 60}

// User-specific overrides/adds to common
userSpecificData[123] = {"username": "John", "email": "john@example.com"}

// Final data for user 123
finalData = {
    "assessmentName": "Test",  // from common
    "duration": 60,             // from common
    "username": "John",         // from user-specific
    "email": "john@example.com" // from user-specific
}
```

### Async Processing
Bulk notifications are processed asynchronously:
```java
@Async("notificationExecutor")
public CompletableFuture<Map<String, Integer>> processBulkNotification(...)
```

Benefits:
- Non-blocking API response
- Parallel processing
- Better scalability

---

## Example Use Cases

### 1. Welcome Email on Registration
```java
// Kafka listener receives user.registered event
Map<String, Object> data = Map.of(
    "firstName", "John",
    "lastName", "Doe",
    "username", "johndoe",
    "email", "john@example.com"
);

notificationService.processNotification(
    "user.registered",
    userId,
    email,
    data,
    List.of(NotificationChannel.EMAIL)
);
```

### 2. Real-time Proctoring Alert
```java
// Multiple proctors notified via SSE
for (Integer proctorId : proctorIds) {
    Map<String, Object> data = Map.of(
        "username", "John",
        "sessionId", "SESSION-123",
        "violationType", "MULTIPLE_FACES",
        "severity", "HIGH",
        "timestamp", Instant.now().toString()
    );
    
    notificationService.processNotification(
        "proctoring.violation",
        proctorId,
        null,  // No email needed for SSE
        data,
        List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
    );
}
```

### 3. Bulk Assessment Assignment
```http
POST /api/v1/notifications/bulk

{
  "userIds": [101, 102, 103, 104, 105],
  "type": "assessment.published",
  "channels": ["EMAIL", "SSE"],
  "commonData": {
    "assessmentName": "Java Fundamentals",
    "duration": 90,
    "dueDate": "2025-11-15"
  },
  "userSpecificData": {
    "101": {"username": "Alice", "email": "alice@example.com"},
    "102": {"username": "Bob", "email": "bob@example.com"},
    "103": {"username": "Carol", "email": "carol@example.com"},
    "104": {"username": "Dave", "email": "dave@example.com"},
    "105": {"username": "Eve", "email": "eve@example.com"}
  }
}
```

---

## Configuration Requirements

### application.yml
```yaml
spring:
  # JWT validation
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://your-casdoor:8000/api/.well-known/jwks

  # Cache configuration
  cache:
    type: caffeine
    cache-names: templates

# Notification settings
app:
  notification:
    email:
      from: noreply@yourapp.com
      from-name: "Your App"
    retry:
      max-attempts: 3
      delay-ms: 60000  # 1 minute
```

### Dependencies (pom.xml)
```xml
<!-- Caching -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- UUID extension for PostgreSQL -->
<!-- Enabled in migration script -->
```

---

## Summary

### ✅ Completed Features

1. **Authentication**: JWT verification only (no login endpoint)
2. **UUID IDs**: All entities use UUID instead of Integer
3. **Template Type Enum**: Strong typing for template types
4. **Dynamic Templates**: {{placeholder}} syntax with TemplateEngine
5. **Template Caching**: Caffeine cache for performance
6. **SSE**: Real-time notifications to connected users
7. **Bulk Notifications**: Send to multiple users with one API call

### 🚀 Next Steps

1. Run migration: `V2__migrate_to_uuid.sql`
2. Add Caffeine dependency
3. Test SSE connections
4. Test bulk notification API
5. Monitor cache hit rates
6. Consider WebSocket for bidirectional communication if needed

---

## Questions?

Check the code comments or refer to:
- `TemplateEngine.java` - Template processing logic
- `SseEmitterService.java` - SSE implementation
- `NotificationService.java` - Main notification logic
- `CacheConfig.java` - Caching configuration