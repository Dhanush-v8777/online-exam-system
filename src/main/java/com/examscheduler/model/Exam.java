package com.examscheduler.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "exams")
public class Exam {


@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

private String subjectName;

private LocalDate examDate;

private LocalTime examTime;

private Integer durationMinutes;

private String venue;

public Exam() {
}

public Long getId() {
    return id;
}

public void setId(Long id) {
    this.id = id;
}

public String getSubjectName() {
    return subjectName;
}

public void setSubjectName(String subjectName) {
    this.subjectName = subjectName;
}

public LocalDate getExamDate() {
    return examDate;
}

public void setExamDate(LocalDate examDate) {
    this.examDate = examDate;
}

public LocalTime getExamTime() {
    return examTime;
}

public void setExamTime(LocalTime examTime) {
    this.examTime = examTime;
}

public Integer getDurationMinutes() {
    return durationMinutes;
}

public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
}

public String getVenue() {
    return venue;
}

public void setVenue(String venue) {
    this.venue = venue;
}


}
