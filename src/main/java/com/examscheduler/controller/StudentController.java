package com.examscheduler.controller;

import com.examscheduler.dto.StudentImportRowDto;
import com.examscheduler.entity.Department;
import com.examscheduler.entity.Student;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.DepartmentService;
import com.examscheduler.service.NotificationService;
import com.examscheduler.service.StudentImportService;
import com.examscheduler.service.StudentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/admin/students")
public class StudentController {

    private final StudentService studentService;
    private final StudentImportService studentImportService;
    private final DepartmentService departmentService;
    private final NotificationService notificationService;

    public StudentController(StudentService studentService,
                             StudentImportService studentImportService,
                             DepartmentService departmentService,
                             NotificationService notificationService) {
        this.studentService = studentService;
        this.studentImportService = studentImportService;
        this.departmentService = departmentService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long departmentId,
                       @RequestParam(required = false) Integer semester,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("students", studentService.search(keyword, departmentId, semester));
        model.addAttribute("departments", departmentService.getAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedDeptId", departmentId);
        model.addAttribute("selectedSemester", semester);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Student Management");
        model.addAttribute("activePage", "students");
        return "admin/students/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("student", new Student());
        model.addAttribute("departments", departmentService.getAll());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Add Student");
        model.addAttribute("activePage", "students");
        return "admin/students/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Student student, BindingResult bindingResult,
                       @RequestParam Long departmentId,
                       RedirectAttributes redirectAttributes, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentService.getAll());
            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", "Student Form");
            model.addAttribute("activePage", "students");
            return "admin/students/form";
        }
        try {
            departmentService.findById(departmentId).ifPresent(student::setDepartment);
            studentService.save(student);
            redirectAttributes.addFlashAttribute("successMsg", "Student saved successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/students";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        Student student = studentService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        model.addAttribute("student", student);
        model.addAttribute("departments", departmentService.getAll());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit Student");
        model.addAttribute("activePage", "students");
        return "admin/students/form";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            studentService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Student deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/students";
    }

    @GetMapping("/import")
    public String importForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("departments", departmentService.getAll());
        model.addAttribute("previewMode", false);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Import Students");
        model.addAttribute("activePage", "students");
        return "admin/students/import";
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        String csv = "registerNumber,fullName,email,department,semester\n" +
                     "MCA2026001,Ananya Sharma,ananya.sharma@example.com,MCA,1\n" +
                     "MBA2026001,Rahul Verma,rahul.verma@example.com,MBA,1\n" +
                     "CSE2026001,Priya Nair,priya.nair@example.com,CSE,2\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_import_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @PostMapping("/import/preview")
    public String previewCsv(@RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(defaultValue = "2025-26") String academicYear,
                             Model model, HttpSession session, RedirectAttributes redirectAttributes,
                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please select a valid CSV file to upload.");
            return "redirect:/admin/students/import";
        }

        try {
            StudentImportService.ParseResult result = studentImportService.parseAndValidateCsv(file, departmentId, academicYear);

            session.setAttribute("PENDING_STUDENT_IMPORT", result.getValidRows());
            session.setAttribute("PENDING_STUDENT_TOTAL", result.getTotalRecords());
            session.setAttribute("PENDING_STUDENT_INVALID_COUNT", result.getInvalidRows().size());
            session.setAttribute("PENDING_STUDENT_DUP_COUNT", result.getDuplicateRows().size());

            model.addAttribute("previewMode", true);
            model.addAttribute("totalRecords", result.getTotalRecords());
            model.addAttribute("validRecords", result.getValidRows().size());
            model.addAttribute("duplicateRecords", result.getDuplicateRows().size());
            model.addAttribute("invalidRecords", result.getInvalidRows().size());
            model.addAttribute("validRows", result.getValidRows());
            model.addAttribute("duplicateRows", result.getDuplicateRows());
            model.addAttribute("invalidRows", result.getInvalidRows());
            model.addAttribute("studentsByDepartment", result.getStudentsByDepartment());
            model.addAttribute("departments", departmentService.getAll());
            model.addAttribute("selectedDeptId", departmentId);
            model.addAttribute("academicYear", academicYear);

            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", "Student Import Preview");
            model.addAttribute("activePage", "students");
            return "admin/students/import";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed parsing CSV file: " + e.getMessage());
            return "redirect:/admin/students/import";
        }
    }

    @PostMapping("/import/confirm")
    public String confirmImport(HttpSession session, RedirectAttributes redirectAttributes) {
        @SuppressWarnings("unchecked")
        List<StudentImportRowDto> pendingRows = (List<StudentImportRowDto>) session.getAttribute("PENDING_STUDENT_IMPORT");

        if (pendingRows == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "No pending import session found. Please upload the CSV file again.");
            return "redirect:/admin/students/import";
        }

        Integer totalCsv = (Integer) session.getAttribute("PENDING_STUDENT_TOTAL");
        Integer preInvalid = (Integer) session.getAttribute("PENDING_STUDENT_INVALID_COUNT");
        Integer preDup = (Integer) session.getAttribute("PENDING_STUDENT_DUP_COUNT");

        int totalCsvRecords = totalCsv != null ? totalCsv : pendingRows.size();
        int preInvalidCount = preInvalid != null ? preInvalid : 0;
        int duplicateSkipped = preDup != null ? preDup : 0;
        int successfullyImported = 0;
        int failedRecords = 0;

        for (StudentImportRowDto row : pendingRows) {
            try {
                studentImportService.saveSingleStudent(row);
                successfullyImported++;
            } catch (StudentImportService.DuplicateStudentException | DataIntegrityViolationException ex) {
                duplicateSkipped++;
            } catch (Exception ex) {
                failedRecords++;
            }
        }

        session.removeAttribute("PENDING_STUDENT_IMPORT");
        session.removeAttribute("PENDING_STUDENT_TOTAL");
        session.removeAttribute("PENDING_STUDENT_INVALID_COUNT");
        session.removeAttribute("PENDING_STUDENT_DUP_COUNT");

        String summaryMsg = String.format(
                "Total CSV records: %d | Successfully imported: %d | Duplicate records skipped: %d | Invalid records: %d | Failed records: %d",
                totalCsvRecords, successfullyImported, duplicateSkipped, preInvalidCount, failedRecords
        );

        redirectAttributes.addFlashAttribute("importSummaryAvailable", true);
        redirectAttributes.addFlashAttribute("summaryTotalRecords", totalCsvRecords);
        redirectAttributes.addFlashAttribute("summarySuccessRecords", successfullyImported);
        redirectAttributes.addFlashAttribute("summaryDuplicateRecords", duplicateSkipped);
        redirectAttributes.addFlashAttribute("summaryInvalidRecords", preInvalidCount);
        redirectAttributes.addFlashAttribute("summaryFailedRecords", failedRecords);
        redirectAttributes.addFlashAttribute("successMsg", summaryMsg);

        return "redirect:/admin/students";
    }

    @PostMapping("/import")
    public String importCsv(@RequestParam("file") MultipartFile file,
                            @RequestParam(required = false) Long departmentId,
                            @RequestParam(defaultValue = "2025-26") String academicYear,
                            RedirectAttributes redirectAttributes) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please select a CSV file to upload.");
            return "redirect:/admin/students/import";
        }

        try {
            StudentImportService.ParseResult result = studentImportService.parseAndValidateCsv(file, departmentId, academicYear);

            int successfullyImported = 0;
            int duplicateSkipped = result.getDuplicateRows().size();
            int failedRecords = 0;

            for (StudentImportRowDto row : result.getValidRows()) {
                try {
                    studentImportService.saveSingleStudent(row);
                    successfullyImported++;
                } catch (StudentImportService.DuplicateStudentException | DataIntegrityViolationException ex) {
                    duplicateSkipped++;
                } catch (Exception ex) {
                    failedRecords++;
                }
            }

            String summaryMsg = String.format(
                    "Total CSV records: %d | Successfully imported: %d | Duplicate records skipped: %d | Invalid records: %d | Failed records: %d",
                    result.getTotalRecords(), successfullyImported, duplicateSkipped, result.getInvalidRows().size(), failedRecords
            );

            redirectAttributes.addFlashAttribute("importSummaryAvailable", true);
            redirectAttributes.addFlashAttribute("summaryTotalRecords", result.getTotalRecords());
            redirectAttributes.addFlashAttribute("summarySuccessRecords", successfullyImported);
            redirectAttributes.addFlashAttribute("summaryDuplicateRecords", duplicateSkipped);
            redirectAttributes.addFlashAttribute("summaryInvalidRecords", result.getInvalidRows().size());
            redirectAttributes.addFlashAttribute("summaryFailedRecords", failedRecords);
            redirectAttributes.addFlashAttribute("successMsg", summaryMsg);

            return "redirect:/admin/students";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed processing CSV file: " + e.getMessage());
            return "redirect:/admin/students/import";
        }
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStudentCount(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer semester) {
        long count = studentService.countByDepartmentAndSemester(departmentId, semester);
        String deptName = "";
        if (departmentId != null) {
            deptName = departmentService.findById(departmentId).map(Department::getDeptName).orElse("");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", count);
        resp.put("departmentId", departmentId != null ? departmentId : 0L);
        resp.put("departmentName", deptName);
        resp.put("semester", semester != null ? semester : 0);
        resp.put("hasStudents", count > 0);
        resp.put("message", count > 0 ? ("Total Students: " + count) : "No students registered for this department and semester.");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}

