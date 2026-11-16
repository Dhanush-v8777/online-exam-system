package com.examscheduler.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Entity
@Table(name = "seating_allocations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"examination_id", "exam_room_id", "seat_number"}, name = "uk_seat_allocation"),
    @UniqueConstraint(columnNames = {"student_id", "examination_id"}, name = "uk_student_exam_seat")
})
public class SeatingAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_room_id", nullable = false)
    private ExamRoom examRoom;

    @NotBlank
    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    private LocalDateTime allocatedAt;

    @PrePersist
    protected void onCreate() { allocatedAt = LocalDateTime.now(); }

    public SeatingAllocation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Examination getExamination() { return examination; }
    public void setExamination(Examination examination) { this.examination = examination; }
    public ExamRoom getExamRoom() { return examRoom; }
    public void setExamRoom(ExamRoom examRoom) { this.examRoom = examRoom; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public LocalDateTime getAllocatedAt() { return allocatedAt; }
}
