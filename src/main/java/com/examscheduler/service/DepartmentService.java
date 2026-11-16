package com.examscheduler.service;

import com.examscheduler.entity.Department;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public DepartmentService(DepartmentRepository departmentRepository, AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Department> getAll() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Department> findById(Long id) {
        return departmentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Department> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAll();
        return departmentRepository.searchByKeyword(keyword.trim());
    }

    public Department save(Department department) {
        boolean isNew = department.getId() == null;

        if (isNew && departmentRepository.existsByDeptCode(department.getDeptCode())) {
            throw new IllegalArgumentException("Department code '" + department.getDeptCode() + "' already exists.");
        }
        if (!isNew && departmentRepository.existsByDeptCodeAndIdNot(department.getDeptCode(), department.getId())) {
            throw new IllegalArgumentException("Department code '" + department.getDeptCode() + "' already exists.");
        }

        Department saved = departmentRepository.save(department);
        auditService.log(
            isNew ? AuditAction.DEPARTMENT_CREATED : AuditAction.DEPARTMENT_UPDATED,
            (isNew ? "Created" : "Updated") + " department: " + saved.getDeptName(),
            "Department", saved.getId()
        );
        return saved;
    }

    public void delete(Long id) {
        Department dept = departmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Department not found: " + id));
        auditService.log(AuditAction.DEPARTMENT_DELETED, "Deleted department: " + dept.getDeptName(), "Department", id);
        departmentRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return departmentRepository.count();
    }
}
