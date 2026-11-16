package com.examscheduler.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "departments", uniqueConstraints = {
    @UniqueConstraint(columnNames = "dept_code", name = "uk_dept_code")
})
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Department code is required")
    @Size(max = 10, message = "Department code must be at most 10 characters")
    @Column(name = "dept_code", nullable = false, unique = true, length = 10)
    private String deptCode;

    @NotBlank(message = "Department name is required")
    @Column(name = "dept_name", nullable = false)
    private String deptName;

    @Column(name = "dept_head")
    private String deptHead;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Faculty> faculties = new ArrayList<>();

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Student> students = new ArrayList<>();

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Subject> subjects = new ArrayList<>();

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Examination> examinations = new ArrayList<>();

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Department() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeptCode() { return deptCode; }
    public String getCode() { return deptCode; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }
    public String getDeptName() { return deptName; }
    public String getName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getDeptHead() { return deptHead; }
    public void setDeptHead(String deptHead) { this.deptHead = deptHead; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Faculty> getFaculties() { return faculties; }
    public void setFaculties(List<Faculty> faculties) { this.faculties = faculties; }
    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }
    public List<Subject> getSubjects() { return subjects; }
    public void setSubjects(List<Subject> subjects) { this.subjects = subjects; }
    public List<Examination> getExaminations() { return examinations; }
    public void setExaminations(List<Examination> examinations) { this.examinations = examinations; }
}
