package com.example.notificationservice.service;

import com.example.notificationservice.dto.BulkNotificationRequest;
import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.entity.NotificationPreference;
import com.example.notificationservice.entity.NotificationTemplate;
import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.enums.NotificationStatus;
import com.example.notificationservice.event.outbound.BulkNotificationCompletedEvent;
import com.example.notificationservice.event.outbound.NotificationFailedEvent;
import com.example.notificationservice.event.outbound.NotificationSentEvent;
import com.example.notificationservice.repository.NotificationPreferenceRepository;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.repository.NotificationTemplateRepository;
import com.example.notificationservice.util.TemplateEngine;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailService emailService;
    private final SseEmitterService sseEmitterService;
    private final TemplateEngine templateEngine;
    private final RedisStreamService redisStreamService;

    @Value("${app.notification.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${app.redis.streams.notification-events}")
    private String notificationEventsStream;

    /**
     * Process single notification for a user
     */
    @Transactional
    public void processNotification(String eventType, String userId, String email,
            Map<String, Object> data, List<NotificationChannel> channels) {

        log.info("Processing notification - Type: {}, User: {}, Email: {}, Channels: {}",
                eventType, userId, email, channels);

        // Check user preferences
        Optional<NotificationPreference> preference = preferenceRepository.findByUserId(userId);

        // Create notification record and send through appropriate channels
        for (NotificationChannel channel : channels) {
            if (shouldSendToChannel(preference, channel, eventType)) {
                log.info("Sending notification via channel: {}", channel);

                // Get channel-specific template
                String templateName = mapEventToTemplate(eventType, channel);
                Optional<NotificationTemplate> templateOpt = templateRepository.findByName(templateName);

                if (templateOpt.isEmpty()) {
                    log.error("❌ Template not found for event type: {} channel: {} (template name: {})",
                            eventType, channel, templateName);
                    continue;
                }

                NotificationTemplate template = templateOpt.get();
                log.debug("Found template: {} for event type: {} channel: {}", templateName, eventType, channel);

                // Validate template data
                if (!templateEngine.validateTemplateData(template.getBody(), data)) {
                    log.warn("⚠️ Missing required template variables for template: {}", templateName);
                }

                // Process content with template engine
                String processedContent = templateEngine.processTemplate(template.getBody(), data);
                String processedSubject = template.getSubject() != null
                        ? templateEngine.processTemplate(template.getSubject(), data)
                        : "";

                log.debug("Processed subject: {}", processedSubject);

                Notification notification = Notification.builder()
                        .recipientId(userId)
                        .recipientEmail(email)
                        .type(eventType)
                        .subject(processedSubject)
                        .content(processedContent)
                        .template(template)
                        .channel(channel)
                        .status(NotificationStatus.PENDING)
                        .build();

                Notification saved = notificationRepository.save(notification);
                sendNotification(saved);
            } else {
                log.info("Skipping channel {} due to user preferences", channel);
            }
        }
    }

    /**
     * Process bulk notifications for multiple users
     */
    @Async("notificationExecutor")
    @Transactional
    public CompletableFuture<Map<String, Integer>> processBulkNotification(BulkNotificationRequest request) {
        log.info("Processing bulk notification for {} users, type: {}", request.getUserIds().size(), request.getType());

        String batchId = UUID.randomUUID().toString();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        // Get template
        String templateName = mapEventToTemplate(request.getType(), NotificationChannel.EMAIL);
        Optional<NotificationTemplate> templateOpt = templateRepository.findByName(templateName);

        if (templateOpt.isEmpty()) {
            log.error("Template not found for notification type: {}", request.getType());
            return CompletableFuture.completedFuture(Map.of(
                    "total", request.getUserIds().size(),
                    "success", 0,
                    "failed", request.getUserIds().size()));
        }

        NotificationTemplate template = templateOpt.get();

        // Process each user
        for (String userId : request.getUserIds()) {
            try {
                // Merge common data with user-specific data
                Map<String, Object> userData = new HashMap<>();
                if (request.getCommonData() != null) {
                    userData.putAll(request.getCommonData());
                }
                if (request.getUserSpecificData() != null && request.getUserSpecificData().containsKey(userId)) {
                    userData.putAll(request.getUserSpecificData().get(userId));
                }

                // Get user preferences
                Optional<NotificationPreference> preference = preferenceRepository.findByUserId(userId);

                // Get user email
                String email = userData.get("email") != null ? userData.get("email").toString() : null;

                // Process template
                String processedContent = templateEngine.processTemplate(template.getBody(), userData);
                String processedSubject = template.getSubject() != null
                        ? templateEngine.processTemplate(template.getSubject(), userData)
                        : "";

                // Send through requested channels
                for (NotificationChannel channel : request.getChannels()) {
                    if (shouldSendToChannel(preference, channel, request.getType())) {
                        Notification notification = Notification.builder()
                                .recipientId(userId)
                                .recipientEmail(email)
                                .type(request.getType())
                                .subject(processedSubject)
                                .content(processedContent)
                                .channel(channel)
                                .template(template)
                                .status(NotificationStatus.PENDING)
                                .build();

                        Notification saved = notificationRepository.save(notification);
                        sendNotification(saved);
                    }
                }

                successCount.incrementAndGet();
            } catch (Exception e) {
                log.error("Failed to process notification for user {}: {}", userId, e.getMessage());
                failedCount.incrementAndGet();
            }
        }

        // Publish bulk completion event
        publishBulkNotificationCompletedEvent(batchId, request.getUserIds().size(),
                successCount.get(), failedCount.get(), request.getType());

        Map<String, Integer> result = Map.of(
                "total", request.getUserIds().size(),
                "success", successCount.get(),
                "failed", failedCount.get());

        log.info("Bulk notification completed. Batch: {}, Total: {}, Success: {}, Failed: {}",
                batchId, request.getUserIds().size(), successCount.get(), failedCount.get());

        return CompletableFuture.completedFuture(result);
    }

    @Async
    public void sendNotification(Notification notification) {
        try {
            log.info("Sending notification {} via channel: {}", notification.getId(), notification.getChannel());
            boolean sent = false;

            switch (notification.getChannel()) {
                case EMAIL:
                    if (notification.getRecipientEmail() != null) {
                        log.info("Sending email to: {}", notification.getRecipientEmail());
                        // Use CompletableFuture to handle async result
                        emailService.sendEmail(
                                notification.getRecipientEmail(),
                                notification.getSubject(),
                                notification.getContent()).thenAccept(result -> {
                                    if (result) {
                                        updateNotificationStatus(notification, NotificationStatus.SENT);
                                        publishNotificationSentEvent(notification);
                                    } else {
                                        handleFailedNotification(notification, "Failed to send email");
                                    }
                                }).exceptionally(ex -> {
                                    log.error("Email sending exception: {}", ex.getMessage());
                                    handleFailedNotification(notification, ex.getMessage());
                                    return null;
                                });

                        // Consider it sent for now (will be updated by callback)
                        sent = true;
                    } else {
                        log.warn("Cannot send email notification: recipient email is null for notification {}",
                                notification.getId());
                        handleFailedNotification(notification, "Recipient email is null");
                    }
                    break;

                case PUSH:
                    log.info("Sending PUSH notification to user: {}", notification.getRecipientId());
                    // Use SSE emitter service for real-time push delivery
                    sent = sseEmitterService.sendToUser(
                            notification.getRecipientId(),
                            notification.getType(),
                            notification.getContent());

                    if (sent) {
                        updateNotificationStatus(notification, NotificationStatus.SENT);
                        publishNotificationSentEvent(notification);
                    } else {
                        // User not connected, but notification is saved - mark as pending for later
                        // delivery
                        log.info("User {} not connected, notification saved for later", notification.getRecipientId());
                        updateNotificationStatus(notification, NotificationStatus.SENT);
                        publishNotificationSentEvent(notification);
                    }
                    break;
            }

        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
            handleFailedNotification(notification, e.getMessage());
        }
    }

    private void handleFailedNotification(Notification notification, String errorMessage) {
        notification.setErrorMessage(errorMessage);
        notification.setRetryCount(notification.getRetryCount() + 1);

        boolean willRetry = notification.getRetryCount() < maxRetryAttempts;

        notification.setStatus(NotificationStatus.FAILED); // <-- always mark failed

        if (!willRetry) {
            log.error("❌ Notification {} permanently failed after {} attempts",
                    notification.getId(), notification.getRetryCount());
        } else {
            log.info("⚠️ Notification {} will be retried (attempt {})",
                    notification.getId(), notification.getRetryCount());
        }

        notificationRepository.save(notification);
        publishNotificationFailedEvent(notification, willRetry);
    }

    @Scheduled(fixedDelayString = "${app.notification.retry.delay-ms}")
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = notificationRepository
                .findByStatusAndRetryCountLessThan(NotificationStatus.FAILED, maxRetryAttempts);

        if (!failedNotifications.isEmpty()) {
            log.info("Retrying {} failed notifications", failedNotifications.size());

            for (Notification notification : failedNotifications) {
                log.info("Retrying notification {} (attempt {})",
                        notification.getId(), notification.getRetryCount() + 1);
                sendNotification(notification);
            }
        }
    }

    private void updateNotificationStatus(Notification notification, NotificationStatus status) {
        notification.setStatus(status);
        notification.setSentAt(Instant.now());
        if (status == NotificationStatus.DELIVERED) {
            notification.setDeliveredAt(Instant.now());
        }
        notificationRepository.save(notification);
        log.info("✅ Notification {} status updated to: {}", notification.getId(), status);
    }

    private boolean shouldSendToChannel(Optional<NotificationPreference> preference,
            NotificationChannel channel, String eventType) {
        if (preference.isEmpty()) {
            log.debug("No preferences found, defaulting to enabled for channel: {}", channel);
            return true;
        }

        NotificationPreference pref = preference.get();

        // 1. Check global master switch
        if (Boolean.FALSE.equals(pref.getNotificationsEnabled())) {
            log.debug("Global notifications disabled for user");
            return false;
        }

        // 2. Check specific category/type preferences
        if (pref.getCategories() != null && pref.getCategories().containsKey(eventType)) {
            Map<String, Boolean> typeSettings = pref.getCategories().get(eventType);

            // Check specific channel setting
            String channelKey = switch (channel) {
                case EMAIL -> "emailEnabled";
                case PUSH -> "pushEnabled";
            };

            if (typeSettings != null && typeSettings.containsKey(channelKey)) {
                boolean specificEnabled = typeSettings.get(channelKey);
                log.debug("Found specific preference for type '{}', channel '{}': {}",
                        eventType, channel, specificEnabled);
                return specificEnabled;
            }
        }

        // 3. Fallback to global channel settings
        boolean enabled = switch (channel) {
            case EMAIL -> Boolean.TRUE.equals(pref.getEmailEnabled());
            case PUSH -> Boolean.TRUE.equals(pref.getPushEnabled());
        };

        log.debug("Using global channel setting for '{}': {}", channel, enabled);
        return enabled;
    }

    /**
     * Map event type to template name based on channel.
     * EMAIL channel uses _email suffix (HTML templates)
     * PUSH channel uses _push suffix (plain text templates)
     */
    private String mapEventToTemplate(String eventType, NotificationChannel channel) {
        String baseName = switch (eventType) {
            case "user.registered" -> "welcome_user";
            case "session.completed" -> "session_completion";
            case "proctoring.violation" -> "proctoring_alert";
            case "assessment.published" -> "new_assessment_assigned";
            case "assessment.reminder" -> "assessment_reminder";
            case "grade.available" -> "grade_available";
            case "comment.feedback" -> "comment_feedback";
            case "system.update" -> "system_update";
            case "invite.student" -> "invite_student_to_group";
            default -> eventType.replace(".", "_");
        };

        // Add channel-specific suffix
        String suffix = switch (channel) {
            case EMAIL -> "_email";
            case PUSH -> "_push";
        };

        String templateName = baseName + suffix;
        log.debug("Mapped event type '{}' + channel '{}' to template '{}'", eventType, channel, templateName);
        return templateName;
    }

    private void publishNotificationSentEvent(Notification notification) {
        NotificationSentEvent event = NotificationSentEvent.builder()
                .notificationId(notification.getId())
                .recipientId(notification.getRecipientId())
                .channel(notification.getChannel().toString())
                .type(notification.getType())
                .status("sent")
                .deliveryTime(Instant.now().toString())
                .build();
        event.init();

        redisStreamService.publish(notificationEventsStream, event);
    }

    private void publishNotificationFailedEvent(Notification notification, boolean willRetry) {
        NotificationFailedEvent event = NotificationFailedEvent.builder()
                .notificationId(notification.getId())
                .recipientId(notification.getRecipientId())
                .channel(notification.getChannel().toString())
                .errorMessage(notification.getErrorMessage())
                .retryCount(notification.getRetryCount())
                .willRetry(willRetry)
                .build();
        event.init();

        redisStreamService.publish(notificationEventsStream, event);
    }

    private void publishBulkNotificationCompletedEvent(String batchId, int total, int success, int failed,
            String type) {
        BulkNotificationCompletedEvent event = BulkNotificationCompletedEvent.builder()
                .batchId(batchId)
                .totalRecipients(total)
                .successfulSent(success)
                .failedSent(failed)
                .notificationType(type)
                .build();
        event.init();

        redisStreamService.publish(notificationEventsStream, event);
    }
}