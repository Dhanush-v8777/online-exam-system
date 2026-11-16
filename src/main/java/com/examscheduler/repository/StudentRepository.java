package com.examscheduler.repository;

import com.examscheduler.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByRegisterNumber(String registerNumber);
    boolean existsByRegisterNumber(String registerNumber);
    boolean existsByRegisterNumberAndIdNot(String registerNumber, Long id);
    boolean existsByEmailIgnoreCase(String email);
    Optional<Student> findByEmailIgnoreCase(String email);

    List<Student> findByDepartmentId(Long departmentId);
    List<Student> findBySemester(int semester);
    List<Student> findByDepartmentIdAndSemester(Long departmentId, int semester);
    List<Student> findByDepartmentIdAndSemesterOrderByRegisterNumberAsc(Long departmentId, int semester);
    List<Student> findByDepartmentIdOrderByRegisterNumberAsc(Long departmentId);
    List<Student> findAllByOrderByRegisterNumberAsc();
    List<Student> findByAcademicYear(String academicYear);

    @Query("SELECT s FROM Student s WHERE LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Student> searchByKeyword(String keyword);

    @Query("SELECT COUNT(s) FROM Student s WHERE s.department.id = :deptId")
    long countByDepartmentId(Long deptId);

    @Query("SELECT COUNT(s) FROM Student s WHERE s.department.id = :deptId AND s.semester = :semester")
    long countByDepartmentIdAndSemester(Long deptId, int semester);
}
