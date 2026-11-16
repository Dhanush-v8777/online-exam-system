package com.examscheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "invigilations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"faculty_id", "examination_id"}, name = "uk_faculty_exam_invigilation")
})
public class Invigilation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column(name = "assignment_note")
    private String assignmentNote;

    @Column(name = "allocation_score")
    private Double allocationScore;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = false;

    @PrePersist
    protected void onCreate() { assignedAt = LocalDateTime.now(); }

    public Invigilation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Faculty getFaculty() { return faculty; }
    public void setFaculty(Faculty faculty) { this.faculty = faculty; }
    public Examination getExamination() { return examination; }
    public void setExamination(Examination examination) { this.examination = examination; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public String getAssignmentNote() { return assignmentNote; }
    public void setAssignmentNote(String assignmentNote) { this.assignmentNote = assignmentNote; }
    public Double getAllocationScore() { return allocationScore; }
    public void setAllocationScore(Double allocationScore) { this.allocationScore = allocationScore; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    /**
     * Automatic dynamic determination of invigilation duty status:
     * - COMPLETED: if examination date and time have passed.
     * - IN_PROGRESS: if current time is within examination schedule.
     * - ASSIGNED: if upcoming.
     */
    public String getDutyStatus() {
        if (examination == null || examination.getExamDate() == null || examination.getStartTime() == null) {
            return "ASSIGNED";
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalDate examDate = examination.getExamDate();
        java.time.LocalTime startTime = examination.getStartTime();
        java.time.LocalTime endTime = examination.getEndTime();
        if (endTime == null) {
            int duration = examination.getDurationMinutes() > 0 ? examination.getDurationMinutes() : 180;
            endTime = startTime.plusMinutes(duration);
        }

        if (today.isAfter(examDate) || (today.isEqual(examDate) && now.isAfter(endTime))) {
            return "COMPLETED";
        } else if (today.isEqual(examDate) && !now.isBefore(startTime) && !now.isAfter(endTime)) {
            return "IN_PROGRESS";
        } else {
            return "ASSIGNED";
        }
    }
}
