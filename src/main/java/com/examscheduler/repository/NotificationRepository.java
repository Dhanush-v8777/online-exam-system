package com.examscheduler.repository;

import com.examscheduler.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadStatusFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.user.id = :userId")
    void markAllAsReadForUser(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.id = :id AND n.user.id = :userId")
    void markAsRead(Long id, Long userId);
}
