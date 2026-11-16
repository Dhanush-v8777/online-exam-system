package com.examscheduler.entity;

import com.examscheduler.enums.ExamStatus;
import com.examscheduler.enums.ExamType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "examinations")
public class Examination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Examination name is required")
    @Column(name = "exam_name", nullable = false)
    private String examName;

    @NotNull(message = "Exam date is required")
    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Min(value = 30, message = "Duration must be at least 30 minutes")
    @Max(value = 480, message = "Duration cannot exceed 480 minutes")
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false)
    private ExamType examType = ExamType.END_SEMESTER_EXAMINATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExamStatus status = ExamStatus.SCHEDULED;

    @Min(value = 0)
    @Column(name = "registered_students", nullable = false)
    private int registeredStudents = 0;

    @Min(value = 1)
    @Max(value = 8)
    @Column(name = "semester", nullable = false)
    private int semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_room_id")
    private ExamRoom examRoom;

    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL)
    private List<ExamRegistration> registrations = new ArrayList<>();

    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL)
    private List<Invigilation> invigilations = new ArrayList<>();

    @OneToMany(mappedBy = "examination")
    private List<SeatingAllocation> seatingAllocations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (startTime != null && durationMinutes > 0) {
            endTime = startTime.plusMinutes(durationMinutes);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (startTime != null && durationMinutes > 0) {
            endTime = startTime.plusMinutes(durationMinutes);
        }
    }

    public Examination() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExamName() { return examName; }
    public String getName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }
    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() {
        if (endTime != null) return endTime;
        if (startTime != null && durationMinutes > 0) {
            return startTime.plusMinutes(durationMinutes);
        }
        return null;
    }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public ExamType getExamType() { return examType; }
    public void setExamType(ExamType examType) { this.examType = examType; }
    public ExamStatus getStatus() { return status; }
    public void setStatus(ExamStatus status) { this.status = status; }
    public int getRegisteredStudents() { return registeredStudents; }
    public int getEnrolledStudents() { return registeredStudents; }
    public void setRegisteredStudents(int registeredStudents) { this.registeredStudents = registeredStudents; }
    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public ExamRoom getExamRoom() { return examRoom; }
    public ExamRoom getRoom() { return examRoom; }
    public void setExamRoom(ExamRoom examRoom) { this.examRoom = examRoom; }
    public List<ExamRegistration> getRegistrations() { return registrations; }
    public void setRegistrations(List<ExamRegistration> registrations) { this.registrations = registrations; }
    public List<Invigilation> getInvigilations() { return invigilations; }
    public void setInvigilations(List<Invigilation> invigilations) { this.invigilations = invigilations; }
    public List<SeatingAllocation> getSeatingAllocations() { return seatingAllocations; }
    public void setSeatingAllocations(List<SeatingAllocation> seatingAllocations) { this.seatingAllocations = seatingAllocations; }

    /**
     * Display examination name/type cleanly (e.g. "Internal Examination")
     */
    public String getExaminationDisplayName() {
        if (examType != null) {
            return examType.getDisplayName();
        }
        if (examName != null && !examName.isBlank()) {
            return examName;
        }
        return "End-Semester Examination";
    }

    /**
     * Required Invigilator count:
     * 1 per hall, or 2 if hall capacity > 70 or students > 40.
     */
    public int getRequiredInvigilatorCount() {
        int req = 1;
        if ((examRoom != null && examRoom.getCapacity() > 70) || registeredStudents > 40) {
            req = 2;
        }
        return req;
    }

    public int getAssignedInvigilatorCount() {
        return (invigilations != null) ? invigilations.size() : 0;
    }

    public int getRemainingInvigilatorsNeeded() {
        return Math.max(0, getRequiredInvigilatorCount() - getAssignedInvigilatorCount());
    }

    public String getFacultyStaffingStatus() {
        int assigned = getAssignedInvigilatorCount();
        int required = getRequiredInvigilatorCount();
        if (assigned == 0) return "FACULTY_REQUIRED";
        if (assigned < required) return "PARTIALLY_ASSIGNED";
        return "FULLY_ASSIGNED";
    }

    /**
     * Final Examination Readiness Status:
     * Displays READY only when Timetable + Room + Seating + Invigilators are complete;
     * otherwise displays NEEDS_ROOM, NEEDS_INVIGILATOR, or NEEDS_SEATING.
     */
    public String getReadinessStatus() {
        if (status == ExamStatus.CANCELLED) return "CANCELLED";
        if (status == ExamStatus.COMPLETED) return "COMPLETED";

        // 1. Room Check
        if (examRoom == null && (seatingAllocations == null || seatingAllocations.isEmpty())) {
            return "NEEDS_ROOM";
        }

        // 2. Seating Check
        int seated = (seatingAllocations != null) ? seatingAllocations.size() : 0;
        if (registeredStudents > 0 && seated < registeredStudents) {
            return "NEEDS_SEATING";
        }

        // 3. Invigilator Check
        int invCount = getAssignedInvigilatorCount();
        int requiredInv = getRequiredInvigilatorCount();
        if (invCount < requiredInv) {
            return "NEEDS_INVIGILATOR";
        }

        return "READY";
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean overlaps(Examination other) {
        if (!this.examDate.equals(other.examDate)) return false;
        LocalTime thisEnd = this.startTime.plusMinutes(this.durationMinutes);
        LocalTime otherEnd = other.startTime.plusMinutes(other.durationMinutes);
        return this.startTime.isBefore(otherEnd) && other.startTime.isBefore(thisEnd);
    }
}
