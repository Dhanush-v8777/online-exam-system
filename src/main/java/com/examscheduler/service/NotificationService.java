package com.examscheduler.service;

import com.examscheduler.entity.Notification;
import com.examscheduler.entity.User;
import com.examscheduler.enums.NotificationType;
import com.examscheduler.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification createNotification(User user, String title, String message, NotificationType type) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(type);
        notification.setReadStatus(false);
        return notificationRepository.save(notification);
    }

    public Notification createNotification(User user, String title, String message, NotificationType type, Long relatedExamId) {
        Notification notification = createNotification(user, title, message, type);
        notification.setRelatedExamId(relatedExamId);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Optional<Notification> findById(Long id) {
        return notificationRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Notification> getByUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecentByUser(Long userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadByUser(Long userId) {
        return notificationRepository.findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadStatusFalse(userId);
    }

    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsRead(notificationId, userId);
    }

    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadForUser(userId);
    }

    public void delete(Long id) {
        notificationRepository.deleteById(id);
    }
}
