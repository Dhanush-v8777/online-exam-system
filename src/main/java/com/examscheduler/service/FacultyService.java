package com.examscheduler.service;

import com.examscheduler.entity.Faculty;
import com.examscheduler.entity.Department;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.FacultyRepository;
import com.examscheduler.repository.DepartmentRepository;
import com.examscheduler.repository.InvigilationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class FacultyService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final InvigilationRepository invigilationRepository;
    private final AuditService auditService;

    public FacultyService(FacultyRepository facultyRepository,
                          DepartmentRepository departmentRepository,
                          InvigilationRepository invigilationRepository,
                          AuditService auditService) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.invigilationRepository = invigilationRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Faculty> getAll() {
        return facultyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Faculty> findById(Long id) {
        return facultyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Faculty> search(String keyword, Long departmentId) {
        if (keyword != null && !keyword.isBlank() && departmentId != null) {
            return facultyRepository.searchByKeywordAndDepartment(keyword.trim(), departmentId);
        } else if (keyword != null && !keyword.isBlank()) {
            return facultyRepository.searchByKeyword(keyword.trim());
        } else if (departmentId != null) {
            return facultyRepository.findByDepartmentId(departmentId);
        }
        return getAll();
    }

    @Transactional(readOnly = true)
    public List<Faculty> getEligibleFaculty() {
        return facultyRepository.findEligibleFaculty();
    }

    @Transactional(readOnly = true)
    public List<Faculty> getAvailableFacultyOnDate(LocalDate date, Long excludeExamId) {
        List<Faculty> eligible = facultyRepository.findEligibleFaculty();
        if (date == null) return eligible;
        List<Long> assignedIds = invigilationRepository.findAssignedFacultyIdsOnDate(date, excludeExamId);
        if (assignedIds == null || assignedIds.isEmpty()) return eligible;
        return eligible.stream()
            .filter(f -> !assignedIds.contains(f.getId()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Faculty> getByDepartment(Long departmentId) {
        return facultyRepository.findByDepartmentId(departmentId);
    }

    public Faculty save(Faculty faculty) {
        boolean isNew = faculty.getId() == null;

        if (isNew) {
            if (facultyRepository.existsByFacultyId(faculty.getFacultyId()))
                throw new IllegalArgumentException("Faculty ID '" + faculty.getFacultyId() + "' already exists.");
            if (facultyRepository.existsByEmail(faculty.getEmail()))
                throw new IllegalArgumentException("Email '" + faculty.getEmail() + "' is already registered.");
        } else {
            if (facultyRepository.existsByFacultyIdAndIdNot(faculty.getFacultyId(), faculty.getId()))
                throw new IllegalArgumentException("Faculty ID '" + faculty.getFacultyId() + "' already exists.");
            if (facultyRepository.existsByEmailAndIdNot(faculty.getEmail(), faculty.getId()))
                throw new IllegalArgumentException("Email '" + faculty.getEmail() + "' is already registered.");
        }

        Faculty saved = facultyRepository.save(faculty);
        auditService.log(
            isNew ? AuditAction.FACULTY_CREATED : AuditAction.FACULTY_UPDATED,
            (isNew ? "Created" : "Updated") + " faculty: " + saved.getFullName(),
            "Faculty", saved.getId()
        );
        return saved;
    }

    public void delete(Long id) {
        Faculty faculty = facultyRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + id));
        auditService.log(AuditAction.FACULTY_DELETED, "Deleted faculty: " + faculty.getFullName(), "Faculty", id);
        facultyRepository.deleteById(id);
    }

    public void updateDutyCount(Long facultyId, int delta) {
        Faculty faculty = facultyRepository.findById(facultyId)
            .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + facultyId));
        faculty.setCurrentDutyCount(Math.max(0, faculty.getCurrentDutyCount() + delta));
        facultyRepository.save(faculty);
    }

    @Transactional(readOnly = true)
    public long count() {
        return facultyRepository.count();
    }
}
