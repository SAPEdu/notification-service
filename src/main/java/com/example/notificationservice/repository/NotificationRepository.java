package com.example.notificationservice.repository;

import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

        Page<Notification> findByRecipientId(String recipientId, Pageable pageable);

        List<Notification> findByStatusAndRetryCountLessThan(NotificationStatus status, Integer maxRetry);

        @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.createdAt < :before")
        List<Notification> findOldNotificationsByStatus(@Param("status") NotificationStatus status,
                        @Param("before") Instant before);

        @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientId = :userId AND n.status = 'PENDING'")
        long countPendingNotificationsByUserId(@Param("userId") String userId);

        // ===================== User Notification APIs =====================

        /**
         * Find PUSH notifications for user's notification inbox (ordered by created
         * time desc)
         */
        Page<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(
                        String recipientId, NotificationChannel channel, Pageable pageable);

        /**
         * Count unread PUSH notifications for a user
         */
        @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientId = :userId AND n.isRead = false AND n.channel = 'PUSH'")
        long countUnreadByUserId(@Param("userId") String userId);

        /**
         * Mark all unread PUSH notifications as read for a user
         */
        @Modifying
        @Query("UPDATE Notification n SET n.isRead = true, n.updatedAt = CURRENT_TIMESTAMP WHERE n.recipientId = :userId AND n.isRead = false AND n.channel = 'PUSH'")
        int markAllAsReadByUserId(@Param("userId") String userId);

        /**
         * Find a specific notification by id and recipient (for security)
         */
        Optional<Notification> findByIdAndRecipientId(UUID id, String recipientId);

        /**
         * Delete a notification by id and recipient (for security)
         */
        void deleteByIdAndRecipientId(UUID id, String recipientId);

        /**
         * Check if notification exists for user
         */
        boolean existsByIdAndRecipientId(UUID id, String recipientId);
}