package com.examscheduler.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "faculty_availability")
public class FacultyAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "available_all_day", nullable = false)
    private boolean availableAllDay = true;

    @Column(name = "reason")
    private String reason;

    public FacultyAvailability() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Faculty getFaculty() { return faculty; }
    public void setFaculty(Faculty faculty) { this.faculty = faculty; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public boolean isAvailableAllDay() { return availableAllDay; }
    public void setAvailableAllDay(boolean availableAllDay) { this.availableAllDay = availableAllDay; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
