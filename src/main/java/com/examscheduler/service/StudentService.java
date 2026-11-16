package com.examscheduler.service;

import com.examscheduler.dto.StudentImportRowDto;
import com.examscheduler.entity.Student;
import com.examscheduler.entity.Department;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.StudentRepository;
import com.examscheduler.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;
    private final StudentImportService studentImportService;

    public StudentService(StudentRepository studentRepository,
                          DepartmentRepository departmentRepository,
                          AuditService auditService,
                          StudentImportService studentImportService) {
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
        this.studentImportService = studentImportService;
    }

    @Transactional(readOnly = true)
    public List<Student> getAll() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Student> search(String keyword, Long departmentId, Integer semester) {
        List<Student> results;
        if (keyword != null && !keyword.isBlank()) {
            results = studentRepository.searchByKeyword(keyword.trim());
        } else {
            results = getAll();
        }
        if (departmentId != null) {
            results = results.stream().filter(s -> s.getDepartment().getId().equals(departmentId)).toList();
        }
        if (semester != null) {
            results = results.stream().filter(s -> s.getSemester() == semester).toList();
        }
        return results;
    }

    public Student save(Student student) {
        boolean isNew = student.getId() == null;

        if (isNew && studentRepository.existsByRegisterNumber(student.getRegisterNumber()))
            throw new IllegalArgumentException("Register number '" + student.getRegisterNumber() + "' already exists.");
        if (!isNew && studentRepository.existsByRegisterNumberAndIdNot(student.getRegisterNumber(), student.getId()))
            throw new IllegalArgumentException("Register number '" + student.getRegisterNumber() + "' already exists.");

        Student saved = studentRepository.save(student);
        auditService.log(
            isNew ? AuditAction.STUDENT_CREATED : AuditAction.STUDENT_UPDATED,
            (isNew ? "Created" : "Updated") + " student: " + saved.getFullName() + " (" + saved.getRegisterNumber() + ")",
            "Student", saved.getId()
        );
        return saved;
    }

    public void delete(Long id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Student not found: " + id));
        auditService.log(AuditAction.STUDENT_DELETED, "Deleted student: " + student.getFullName(), "Student", id);
        studentRepository.deleteById(id);
    }

    public Map<String, Object> importFromCsv(MultipartFile file, Long departmentId, String academicYear) {
        int successCount = 0;
        int failCount = 0;
        int duplicateCount = 0;
        List<String> errors = new ArrayList<>();

        try {
            StudentImportService.ParseResult parseResult = studentImportService.parseAndValidateCsv(file, departmentId, academicYear);

            for (StudentImportRowDto invalid : parseResult.getInvalidRows()) {
                errors.add("Row " + invalid.getRowNumber() + ": " + String.join(", ", invalid.getErrors()));
                failCount++;
            }

            for (StudentImportRowDto dup : parseResult.getDuplicateRows()) {
                duplicateCount++;
                errors.add("Row " + dup.getRowNumber() + " skipped: " + dup.getDuplicateReason());
            }

            for (StudentImportRowDto validRow : parseResult.getValidRows()) {
                try {
                    studentImportService.saveSingleStudent(validRow);
                    successCount++;
                } catch (StudentImportService.DuplicateStudentException e) {
                    duplicateCount++;
                    errors.add("Row " + validRow.getRowNumber() + " skipped: " + e.getMessage());
                } catch (Exception e) {
                    failCount++;
                    errors.add("Row " + validRow.getRowNumber() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("File read error: " + e.getMessage());
            failCount++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", successCount);
        result.put("failed", failCount);
        result.put("duplicates", duplicateCount);
        result.put("errors", errors);
        return result;
    }

    @Transactional(readOnly = true)
    public long count() {
        return studentRepository.count();
    }

    @Transactional(readOnly = true)
    public long countByDepartmentAndSemester(Long departmentId, Integer semester) {
        if (departmentId != null && semester != null && semester > 0) {
            return studentRepository.countByDepartmentIdAndSemester(departmentId, semester);
        } else if (departmentId != null) {
            return studentRepository.countByDepartmentId(departmentId);
        } else if (semester != null && semester > 0) {
            return studentRepository.findBySemester(semester).size();
        }
        return studentRepository.count();
    }
}
