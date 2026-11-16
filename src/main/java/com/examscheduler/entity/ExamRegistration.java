package com.examscheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_registrations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "examination_id"}, name = "uk_student_exam")
})
public class ExamRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "present")
    private Boolean present;

    @PrePersist
    protected void onCreate() { registeredAt = LocalDateTime.now(); }

    public ExamRegistration() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Examination getExamination() { return examination; }
    public void setExamination(Examination examination) { this.examination = examination; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public Boolean getPresent() { return present; }
    public void setPresent(Boolean present) { this.present = present; }
}
