package com.examscheduler.service;

import com.examscheduler.dto.StudentImportRowDto;
import com.examscheduler.entity.Department;
import com.examscheduler.entity.Student;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.DepartmentRepository;
import com.examscheduler.repository.StudentRepository;
import com.opencsv.CSVReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StudentImportService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    public StudentImportService(StudentRepository studentRepository,
                                DepartmentRepository departmentRepository,
                                AuditService auditService) {
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.auditService = auditService;
    }

    public static class ParseResult {
        private int totalRecords;
        private List<StudentImportRowDto> validRows = new ArrayList<>();
        private List<StudentImportRowDto> duplicateRows = new ArrayList<>();
        private List<StudentImportRowDto> invalidRows = new ArrayList<>();
        private Map<String, Long> studentsByDepartment = new LinkedHashMap<>();

        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }

        public List<StudentImportRowDto> getValidRows() { return validRows; }
        public List<StudentImportRowDto> getDuplicateRows() { return duplicateRows; }
        public List<StudentImportRowDto> getInvalidRows() { return invalidRows; }
        public Map<String, Long> getStudentsByDepartment() { return studentsByDepartment; }
    }

    /**
     * Parses and validates uploaded CSV spreadsheet with multi-department support.
     * Department selection is optional; department is identified per row from the CSV.
     */
    @Transactional(readOnly = true)
    public ParseResult parseAndValidateCsv(MultipartFile file, Long optionalDeptId, String defaultAcademicYear) throws Exception {
        ParseResult result = new ParseResult();
        String academicYear = (defaultAcademicYear != null && !defaultAcademicYear.isBlank()) ? defaultAcademicYear.trim() : "2025-26";

        // Load all departments for fast in-memory matching
        List<Department> allDepts = departmentRepository.findAll();
        Map<String, Department> deptMap = new HashMap<>();
        for (Department d : allDepts) {
            if (d.getDeptCode() != null) {
                deptMap.put(d.getDeptCode().trim().toLowerCase(), d);
            }
            if (d.getDeptName() != null) {
                deptMap.put(d.getDeptName().trim().toLowerCase(), d);
            }
        }

        Department optionalDeptFallback = null;
        if (optionalDeptId != null) {
            optionalDeptFallback = departmentRepository.findById(optionalDeptId).orElse(null);
        }

        Set<String> seenRegisterNumbers = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> allLines = reader.readAll();
            if (allLines.isEmpty()) {
                return result;
            }

            int startIndex = 0;
            int regNoIdx = 0;
            int nameIdx = 1;
            int emailIdx = 2;
            int deptIdx = 3;
            int semIdx = 4;

            // Check if first row is header
            String[] firstRow = allLines.get(0);
            boolean hasHeader = isHeaderRow(firstRow);

            if (hasHeader) {
                startIndex = 1;
                // Dynamically resolve column indices from header names
                Map<String, Integer> colMap = new HashMap<>();
                for (int i = 0; i < firstRow.length; i++) {
                    String h = firstRow[i].trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                    colMap.put(h, i);
                }

                regNoIdx = findIndex(colMap, "registernumber", "regno", "registerno", "reg", "studentid", "id");
                nameIdx = findIndex(colMap, "fullname", "name", "studentname", "student");
                emailIdx = findIndex(colMap, "email", "emailaddress", "mail");
                deptIdx = findIndex(colMap, "department", "dept", "departmentcode", "deptcode", "course");
                semIdx = findIndex(colMap, "semester", "sem");

                // Fallbacks if some headers were standard positional
                if (regNoIdx == -1) regNoIdx = 0;
                if (nameIdx == -1) nameIdx = 1;
                if (emailIdx == -1) emailIdx = (firstRow.length > 2 && containsEmailOrDept(firstRow[2], true)) ? 2 : -1;
                if (deptIdx == -1) deptIdx = (firstRow.length > 3) ? 3 : (firstRow.length > 2 && containsEmailOrDept(firstRow[2], false) ? 2 : -1);
                if (semIdx == -1) semIdx = (firstRow.length > 4) ? 4 : (firstRow.length > 3 && isNumeric(firstRow[3]) ? 3 : -1);
            } else {
                // If 4 columns: registerNumber, fullName, department, semester (or registerNumber, fullName, semester, email)
                if (firstRow.length == 4) {
                    if (isNumeric(firstRow[2])) {
                        semIdx = 2;
                        deptIdx = -1;
                        emailIdx = 3;
                    } else {
                        deptIdx = 2;
                        semIdx = 3;
                        emailIdx = -1;
                    }
                }
            }

            int rowNumber = startIndex;
            for (int i = startIndex; i < allLines.size(); i++) {
                rowNumber++;
                String[] line = allLines.get(i);
                if (line == null || line.length == 0 || (line.length == 1 && line[0].trim().isEmpty())) {
                    continue; // Skip blank rows
                }

                result.setTotalRecords(result.getTotalRecords() + 1);

                StudentImportRowDto row = new StudentImportRowDto();
                row.setRowNumber(rowNumber);
                row.setAcademicYear(academicYear);

                String regNo = (regNoIdx >= 0 && regNoIdx < line.length) ? line[regNoIdx].trim() : "";
                String name = (nameIdx >= 0 && nameIdx < line.length) ? line[nameIdx].trim() : "";
                String email = (emailIdx >= 0 && emailIdx < line.length) ? line[emailIdx].trim() : "";
                String rawDept = (deptIdx >= 0 && deptIdx < line.length) ? line[deptIdx].trim() : "";
                String rawSem = (semIdx >= 0 && semIdx < line.length) ? line[semIdx].trim() : "";

                row.setRegisterNumber(regNo);
                row.setFullName(name);
                row.setEmail(email);
                row.setRawDepartment(rawDept);
                row.setRawSemester(rawSem);

                List<String> errors = new ArrayList<>();

                // 1. Validate Register Number
                if (regNo.isEmpty()) {
                    errors.add("Register number is required.");
                } else if (regNo.length() > 20) {
                    errors.add("Register number exceeds 20 characters limit.");
                }

                // 2. Validate Full Name
                if (name.isEmpty()) {
                    errors.add("Student full name is required.");
                }

                // 3. Validate Email format
                if (!email.isEmpty()) {
                    if (!EMAIL_PATTERN.matcher(email).matches()) {
                        errors.add("Invalid email address format: '" + email + "'.");
                    }
                }

                // 4. Validate Department
                Department matchedDept = null;
                if (!rawDept.isEmpty()) {
                    matchedDept = deptMap.get(rawDept.toLowerCase());
                    if (matchedDept == null) {
                        // Check loose match
                        for (Department d : allDepts) {
                            if (d.getDeptCode() != null && d.getDeptCode().equalsIgnoreCase(rawDept)) {
                                matchedDept = d;
                                break;
                            }
                            if (d.getDeptName() != null && d.getDeptName().equalsIgnoreCase(rawDept)) {
                                matchedDept = d;
                                break;
                            }
                        }
                    }
                    if (matchedDept == null) {
                        errors.add("Department '" + rawDept + "' does not exist in MySQL. Please verify department code/name.");
                    }
                } else if (optionalDeptFallback != null) {
                    matchedDept = optionalDeptFallback;
                } else {
                    errors.add("Department is required. Specify department code in CSV or select a default department.");
                }

                if (matchedDept != null) {
                    row.setMatchedDepartmentId(matchedDept.getId());
                    row.setMatchedDepartmentCode(matchedDept.getDeptCode());
                    row.setMatchedDepartmentName(matchedDept.getDeptName());
                }

                // 5. Validate Semester
                if (rawSem.isEmpty()) {
                    errors.add("Semester is required.");
                } else {
                    try {
                        int semVal = Integer.parseInt(rawSem.replaceAll("[^0-9]", ""));
                        if (semVal < 1 || semVal > 8) {
                            errors.add("Semester must be between 1 and 8 (received '" + rawSem + "').");
                        } else {
                            row.setSemester(semVal);
                        }
                    } catch (NumberFormatException e) {
                        errors.add("Invalid semester value: '" + rawSem + "'. Must be a numeric semester 1-8.");
                    }
                }

                // 6. Check Duplicate Register Number
                boolean isDuplicate = false;
                String duplicateReason = null;

                if (!regNo.isEmpty()) {
                    String regKey = regNo.toLowerCase();
                    if (seenRegisterNumbers.contains(regKey)) {
                        isDuplicate = true;
                        duplicateReason = "Duplicate register number '" + regNo + "' in this CSV file.";
                    } else if (studentRepository.existsByRegisterNumber(regNo)) {
                        isDuplicate = true;
                        duplicateReason = "Register number '" + regNo + "' already exists in MySQL database.";
                    } else {
                        seenRegisterNumbers.add(regKey);
                    }
                }

                // 7. Check Duplicate Email
                if (!isDuplicate && !email.isEmpty() && errors.isEmpty()) {
                    String emailKey = email.toLowerCase();
                    if (seenEmails.contains(emailKey)) {
                        isDuplicate = true;
                        duplicateReason = "Duplicate email address '" + email + "' in this CSV file.";
                    } else if (studentRepository.existsByEmailIgnoreCase(email)) {
                        isDuplicate = true;
                        duplicateReason = "Email address '" + email + "' already exists in MySQL database.";
                    } else {
                        seenEmails.add(emailKey);
                    }
                }

                row.setErrors(errors);

                if (!errors.isEmpty()) {
                    row.setValid(false);
                    row.setDuplicate(false);
                    result.getInvalidRows().add(row);
                } else if (isDuplicate) {
                    row.setValid(false);
                    row.setDuplicate(true);
                    row.setDuplicateReason(duplicateReason);
                    result.getDuplicateRows().add(row);
                } else {
                    row.setValid(true);
                    row.setDuplicate(false);
                    result.getValidRows().add(row);
                }
            }
        }

        // Compute students grouped by department
        Map<String, Long> deptCount = result.getValidRows().stream()
                .filter(r -> r.getMatchedDepartmentCode() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getMatchedDepartmentCode() + " - " + r.getMatchedDepartmentName(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        result.getStudentsByDepartment().putAll(deptCount);

        return result;
    }

    /**
     * Saves a single student inside an independent transaction (REQUIRES_NEW).
     * If a single row encounters an integrity constraint violation, only that row rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Student saveSingleStudent(StudentImportRowDto row) {
        String regNo = row.getRegisterNumber().trim();
        if (studentRepository.existsByRegisterNumber(regNo)) {
            throw new DuplicateStudentException("Register number '" + regNo + "' already exists.");
        }

        String email = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : null;
        if (email != null && !email.isBlank()) {
            if (studentRepository.existsByEmailIgnoreCase(email)) {
                throw new DuplicateStudentException("Email address '" + email + "' already exists.");
            }
        } else {
            email = null;
        }

        Department dept = departmentRepository.findById(row.getMatchedDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department ID " + row.getMatchedDepartmentId() + " not found."));

        Student student = new Student();
        student.setRegisterNumber(regNo);
        student.setFullName(row.getFullName().trim());
        student.setEmail(email);
        student.setDepartment(dept);
        student.setSemester(row.getSemester() != null ? row.getSemester() : 1);
        student.setAcademicYear(row.getAcademicYear() != null && !row.getAcademicYear().isBlank() ? row.getAcademicYear() : "2025-26");

        Student saved = studentRepository.saveAndFlush(student);

        auditService.log(
                AuditAction.STUDENT_CREATED,
                "Imported student '" + saved.getFullName() + "' (" + saved.getRegisterNumber() + ") under " + dept.getDeptCode() + " via CSV batch",
                "Student",
                saved.getId()
        );

        return saved;
    }

    private boolean isHeaderRow(String[] row) {
        if (row == null || row.length == 0) return false;
        String combined = String.join(" ", row).toLowerCase();
        return combined.contains("register") || combined.contains("regno") ||
                combined.contains("name") || combined.contains("department") ||
                combined.contains("semester") || combined.contains("email");
    }

    private int findIndex(Map<String, Integer> map, String... keys) {
        for (String k : keys) {
            Integer idx = map.get(k);
            if (idx != null) return idx;
        }
        return -1;
    }

    private boolean containsEmailOrDept(String val, boolean checkEmail) {
        if (val == null) return false;
        if (checkEmail) return val.contains("@");
        return val.length() >= 2 && !val.contains("@") && !isNumeric(val);
    }

    private boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) return false;
        try {
            Integer.parseInt(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static class DuplicateStudentException extends RuntimeException {
        public DuplicateStudentException(String message) {
            super(message);
        }
    }
}

