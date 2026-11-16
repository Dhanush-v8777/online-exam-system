package com.examscheduler.repository;

import com.examscheduler.model.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {


List<Exam> findByExamDate(LocalDate examDate);

List<Exam> findByExamDateGreaterThanEqual(LocalDate examDate);


}
