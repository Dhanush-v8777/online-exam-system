package com.examscheduler.repository;

import com.examscheduler.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findBySubjectCode(String subjectCode);
    boolean existsBySubjectCode(String subjectCode);
    boolean existsBySubjectCodeAndIdNot(String subjectCode, Long id);

    List<Subject> findByDepartmentId(Long departmentId);
    List<Subject> findBySemester(int semester);
    List<Subject> findByDepartmentIdAndSemester(Long departmentId, int semester);

    @Query("SELECT s FROM Subject s WHERE LOWER(s.subjectName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.subjectCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Subject> searchByKeyword(String keyword);
}
