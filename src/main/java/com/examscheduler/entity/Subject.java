package com.examscheduler.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subjects", uniqueConstraints = {
    @UniqueConstraint(columnNames = "subject_code", name = "uk_subject_code")
})
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Subject code is required")
    @Column(name = "subject_code", nullable = false, unique = true, length = 20)
    private String subjectCode;

    @NotBlank(message = "Subject name is required")
    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Min(value = 1)
    @Max(value = 8)
    @Column(name = "semester", nullable = false)
    private int semester;

    @Min(value = 1)
    @Max(value = 6)
    @Column(name = "credits", nullable = false)
    private int credits = 3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL)
    private List<Examination> examinations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Subject() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSubjectCode() { return subjectCode; }
    public String getCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getSubjectName() { return subjectName; }
    public String getName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }
    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public List<Examination> getExaminations() { return examinations; }
    public void setExaminations(List<Examination> examinations) { this.examinations = examinations; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
