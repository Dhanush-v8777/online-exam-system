package com.examscheduler.entity;

import com.examscheduler.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType = NotificationType.GENERAL;

    @Column(name = "read_status", nullable = false)
    private boolean readStatus = false;

    @Column(name = "related_exam_id")
    private Long relatedExamId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Notification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public NotificationType getNotificationType() { return notificationType; }
    public void setNotificationType(NotificationType notificationType) { this.notificationType = notificationType; }
    public NotificationType getType() { return notificationType; }
    public void setType(NotificationType type) { this.notificationType = type; }
    public boolean isReadStatus() { return readStatus; }
    public void setReadStatus(boolean readStatus) { this.readStatus = readStatus; }
    public boolean isRead() { return readStatus; }
    public void setRead(boolean read) { this.readStatus = read; }
    public boolean getReadStatus() { return readStatus; }
    public Long getRelatedExamId() { return relatedExamId; }
    public void setRelatedExamId(Long relatedExamId) { this.relatedExamId = relatedExamId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
