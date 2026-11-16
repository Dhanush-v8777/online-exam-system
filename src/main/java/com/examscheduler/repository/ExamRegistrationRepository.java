package com.examscheduler.repository;

import com.examscheduler.entity.ExamRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRegistrationRepository extends JpaRepository<ExamRegistration, Long> {
    List<ExamRegistration> findByStudentId(Long studentId);
    List<ExamRegistration> findByExaminationId(Long examinationId);
    Optional<ExamRegistration> findByStudentIdAndExaminationId(Long studentId, Long examinationId);
    boolean existsByStudentIdAndExaminationId(Long studentId, Long examinationId);

    @Query("SELECT COUNT(r) FROM ExamRegistration r WHERE r.examination.id = :examId")
    long countByExaminationId(Long examId);

    void deleteByExaminationId(Long examinationId);
    void deleteByStudentId(Long studentId);
}
