package com.examscheduler.repository;

import com.examscheduler.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByDeptCode(String deptCode);
    boolean existsByDeptCode(String deptCode);
    boolean existsByDeptCodeAndIdNot(String deptCode, Long id);

    @Query("SELECT d FROM Department d WHERE LOWER(d.deptName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.deptCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Department> searchByKeyword(String keyword);
}
