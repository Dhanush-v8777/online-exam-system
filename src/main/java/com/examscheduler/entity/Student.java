package com.examscheduler.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "students", uniqueConstraints = {
        @UniqueConstraint(columnNames = "register_number", name = "uk_register_number"),
        @UniqueConstraint(columnNames = "email", name = "uk_student_email")
})
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Register number is required")
    @Column(name = "register_number", nullable = false, unique = true, length = 20)
    private String registerNumber;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Email(message = "Valid email required")
    @Column(name = "email")
    private String email;

    @Min(value = 1, message = "Semester must be at least 1")
    @Max(value = 8, message = "Semester cannot exceed 8")
    @Column(name = "semester", nullable = false)
    private int semester;

    @NotBlank(message = "Academic year is required")
    @Column(name = "academic_year", nullable = false, length = 10)
    private String academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL)
    private List<ExamRegistration> registrations = new ArrayList<>();

    @OneToMany(mappedBy = "student")
    private List<SeatingAllocation> seatingAllocations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Student() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRegisterNumber() {
        return registerNumber;
    }

    public void setRegisterNumber(String registerNumber) {
        this.registerNumber = registerNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getSemester() {
        return semester;
    }

    public void setSemester(int semester) {
        this.semester = semester;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public List<ExamRegistration> getRegistrations() {
        return registrations;
    }

    public void setRegistrations(List<ExamRegistration> registrations) {
        this.registrations = registrations;
    }

    public List<SeatingAllocation> getSeatingAllocations() {
        return seatingAllocations;
    }

    public void setSeatingAllocations(List<SeatingAllocation> seatingAllocations) {
        this.seatingAllocations = seatingAllocations;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
