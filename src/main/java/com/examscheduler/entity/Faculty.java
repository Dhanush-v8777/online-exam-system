package com.examscheduler.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "faculty", uniqueConstraints = {
    @UniqueConstraint(columnNames = "faculty_id", name = "uk_faculty_id"),
    @UniqueConstraint(columnNames = "email", name = "uk_faculty_email")
})
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Faculty ID is required")
    @Column(name = "faculty_id", nullable = false, unique = true, length = 20)
    private String facultyId;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Email(message = "Valid email required")
    @NotBlank(message = "Email is required")
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be 10 digits")
    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "designation")
    private String designation;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @Min(value = 0, message = "Max duties cannot be negative")
    @Max(value = 50, message = "Max duties cannot exceed 50")
    @Column(name = "max_duties", nullable = false)
    private int maxDuties = 10;

    @Column(name = "current_duty_count", nullable = false)
    private int currentDutyCount = 0;

    @Column(name = "account_active", nullable = false)
    private boolean accountActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "faculty", cascade = CascadeType.ALL)
    private List<Invigilation> invigilations = new ArrayList<>();

    @OneToMany(mappedBy = "faculty", cascade = CascadeType.ALL)
    private List<FacultyAvailability> availabilitySlots = new ArrayList<>();

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
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Faculty() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFacultyId() { return facultyId; }
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }
    public String getFullName() { return fullName; }
    public String getName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public int getMaxDuties() { return maxDuties; }
    public void setMaxDuties(int maxDuties) { this.maxDuties = maxDuties; }
    public int getCurrentDutyCount() { return currentDutyCount; }
    public void setCurrentDutyCount(int currentDutyCount) { this.currentDutyCount = currentDutyCount; }
    public boolean isAccountActive() { return accountActive; }
    public void setAccountActive(boolean accountActive) { this.accountActive = accountActive; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public List<Invigilation> getInvigilations() { return invigilations; }
    public void setInvigilations(List<Invigilation> invigilations) { this.invigilations = invigilations; }
    public List<FacultyAvailability> getAvailabilitySlots() { return availabilitySlots; }
    public void setAvailabilitySlots(List<FacultyAvailability> availabilitySlots) { this.availabilitySlots = availabilitySlots; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public int getRemainingDuties() {
        return Math.max(0, maxDuties - currentDutyCount);
    }

    public boolean hasCapacity() {
        return available && accountActive && currentDutyCount < maxDuties;
    }
}
