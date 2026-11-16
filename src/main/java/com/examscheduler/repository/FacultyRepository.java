package com.examscheduler.repository;

import com.examscheduler.entity.Department;
import com.examscheduler.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    Optional<Faculty> findByFacultyId(String facultyId);
    Optional<Faculty> findByEmail(String email);
    Optional<Faculty> findByUserId(Long userId);
    boolean existsByFacultyId(String facultyId);
    boolean existsByEmail(String email);
    boolean existsByFacultyIdAndIdNot(String facultyId, Long id);
    boolean existsByEmailAndIdNot(String email, Long id);

    List<Faculty> findByDepartment(Department department);
    List<Faculty> findByDepartmentId(Long departmentId);
    List<Faculty> findByAvailableTrue();
    List<Faculty> findByAccountActiveTrue();
    List<Faculty> findByAvailableTrueAndAccountActiveTrue();

    @Query("SELECT f FROM Faculty f WHERE f.available = true AND f.accountActive = true AND f.currentDutyCount < f.maxDuties")
    List<Faculty> findEligibleFaculty();

    @Query("SELECT f FROM Faculty f WHERE (LOWER(f.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.facultyId) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Faculty> searchByKeyword(String keyword);

    @Query("SELECT f FROM Faculty f WHERE f.department.id = :deptId AND (LOWER(f.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Faculty> searchByKeywordAndDepartment(String keyword, Long deptId);

    @Query("SELECT COUNT(f) FROM Faculty f WHERE f.department.id = :deptId")
    long countByDepartmentId(Long deptId);

    @Query("SELECT f FROM Faculty f ORDER BY f.currentDutyCount ASC")
    List<Faculty> findAllOrderByDutyCountAsc();
}
