package com.example.notificationservice.repository;

import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientId(Integer recipientId, Pageable pageable);

    List<Notification> findByStatusAndRetryCountLessThan(NotificationStatus status, Integer maxRetry);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.createdAt < :before")
    List<Notification> findOldNotificationsByStatus(@Param("status") NotificationStatus status,
                                                    @Param("before") Instant before);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientId = :userId AND n.status = 'PENDING'")
    long countPendingNotificationsByUserId(@Param("userId") Integer userId);
}