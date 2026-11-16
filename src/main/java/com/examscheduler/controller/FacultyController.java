package com.examscheduler.controller;

import com.examscheduler.dto.FacultyImportRowDto;
import com.examscheduler.entity.Department;
import com.examscheduler.entity.Faculty;
import com.examscheduler.entity.User;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.UserRole;
import com.examscheduler.repository.DepartmentRepository;
import com.examscheduler.repository.FacultyRepository;
import com.examscheduler.repository.UserRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.DepartmentService;
import com.examscheduler.service.FacultyService;
import com.examscheduler.service.NotificationService;
import com.opencsv.CSVReader;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin/faculty")
public class FacultyController {

    private final FacultyService facultyService;
    private final FacultyRepository facultyRepository;
    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");

    public FacultyController(FacultyService facultyService,
                             FacultyRepository facultyRepository,
                             DepartmentService departmentService,
                             DepartmentRepository departmentRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             PasswordEncoder passwordEncoder,
                             AuditService auditService) {
        this.facultyService = facultyService;
        this.facultyRepository = facultyRepository;
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long departmentId,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("faculties", facultyService.search(keyword, departmentId));
        model.addAttribute("departments", departmentService.getAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedDeptId", departmentId);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Faculty Management");
        model.addAttribute("activePage", "faculty");
        return "admin/faculty/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("faculty", new Faculty());
        model.addAttribute("departments", departmentService.getAll());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Add Faculty");
        model.addAttribute("activePage", "faculty");
        return "admin/faculty/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Faculty faculty, BindingResult bindingResult,
                       @RequestParam Long departmentId,
                       RedirectAttributes redirectAttributes, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentService.getAll());
            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", "Faculty Form");
            model.addAttribute("activePage", "faculty");
            return "admin/faculty/form";
        }
        try {
            departmentService.findById(departmentId).ifPresent(faculty::setDepartment);
            facultyService.save(faculty);
            redirectAttributes.addFlashAttribute("successMsg", "Faculty saved successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/faculty";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        Faculty faculty = facultyService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + id));
        model.addAttribute("faculty", faculty);
        model.addAttribute("departments", departmentService.getAll());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit Faculty");
        model.addAttribute("activePage", "faculty");
        return "admin/faculty/form";
    }

    @GetMapping("/view/{id}")
    public String viewProfile(@PathVariable Long id, Model model,
                              @AuthenticationPrincipal CustomUserDetails userDetails) {
        Faculty faculty = facultyService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + id));
        model.addAttribute("faculty", faculty);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Faculty Profile - " + faculty.getFullName());
        model.addAttribute("activePage", "faculty");
        return "admin/faculty/view";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            facultyService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Faculty deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/faculty";
    }

    // =========================================================================
    // REQUIREMENT 5: ADMIN FACULTY CSV IMPORT WORKFLOW
    // =========================================================================

    /**
     * Download CSV template for Faculty Import.
     * Accessible by ROLE_ADMIN only.
     */
    @GetMapping("/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadTemplate() {
        String csv = "facultyId,fullName,email,phoneNumber,department,designation\n" +
                     "FAC101,Dr. Ananya Sharma,ananya.sharma@smartexam.edu,9876543210,MCA,Associate Professor\n" +
                     "FAC102,Prof. Rajesh Kumar,rajesh.kumar@smartexam.edu,9876543211,CSE,Assistant Professor\n" +
                     "FAC103,Dr. Meera Patel,meera.patel@smartexam.edu,9876543212,ECE,Professor\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"faculty_import_template.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .contentLength(bytes.length)
            .body(bytes);
    }

    /**
     * Render the Faculty Import View.
     * Accessible by ROLE_ADMIN only.
     */
    @GetMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public String importPage(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Import Faculty Members (CSV)");
        model.addAttribute("activePage", "faculty");
        model.addAttribute("previewMode", false);
        return "admin/faculty/import";
    }

    /**
     * Parse and Preview uploaded CSV with row-wise validation before commit.
     */
    @PostMapping("/import/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public String previewCsv(@RequestParam("file") MultipartFile file,
                             Model model,
                             RedirectAttributes redirectAttributes,
                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please select a non-empty CSV file to upload.");
            return "redirect:/admin/faculty/import";
        }

        List<FacultyImportRowDto> validRows = new ArrayList<>();
        List<FacultyImportRowDto> invalidRows = new ArrayList<>();
        Set<String> seenFacultyIds = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        // Cache all departments for fast lookup by code or name (case-insensitive)
        List<Department> departments = departmentRepository.findAll();
        Map<String, Department> deptLookup = new HashMap<>();
        for (Department d : departments) {
            deptLookup.put(d.getDeptCode().trim().toUpperCase(), d);
            deptLookup.put(d.getDeptName().trim().toUpperCase(), d);
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "The uploaded file is empty.");
                return "redirect:/admin/faculty/import";
            }

            // Find column indices
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                String cleanHeader = header[i].trim().toLowerCase().replaceAll("[^a-z]", "");
                colIndex.put(cleanHeader, i);
            }

            Integer facIdIdx = colIndex.get("facultyid");
            Integer nameIdx = colIndex.get("fullname");
            Integer emailIdx = colIndex.get("email");
            Integer deptIdx = colIndex.get("department");
            Integer phoneIdx = colIndex.get("phonenumber") != null ? colIndex.get("phonenumber") : colIndex.get("phone");
            Integer desigIdx = colIndex.get("designation");

            if (facIdIdx == null || nameIdx == null || emailIdx == null || deptIdx == null) {
                redirectAttributes.addFlashAttribute("errorMsg",
                    "Invalid CSV Header format. Mandatory headers: 'facultyId', 'fullName', 'email', 'department'. " +
                    "Optional headers: 'phoneNumber', 'designation'. Download the template for the required format.");
                return "redirect:/admin/faculty/import";
            }

            String[] line;
            int rowNumber = 1;
            while ((line = reader.readNext()) != null) {
                rowNumber++;
                // Skip empty rows
                if (line.length == 0 || (line.length == 1 && line[0].trim().isEmpty())) {
                    continue;
                }

                FacultyImportRowDto row = new FacultyImportRowDto();
                row.setRowNumber(rowNumber);

                String facId = (facIdIdx < line.length) ? line[facIdIdx].trim() : "";
                String name = (nameIdx < line.length) ? line[nameIdx].trim() : "";
                String email = (emailIdx < line.length) ? line[emailIdx].trim().toLowerCase() : "";
                String deptVal = (deptIdx < line.length) ? line[deptIdx].trim() : "";
                String phone = (phoneIdx != null && phoneIdx < line.length) ? line[phoneIdx].trim() : "";
                String desig = (desigIdx != null && desigIdx < line.length) ? line[desigIdx].trim() : "Faculty";

                row.setFacultyId(facId);
                row.setFullName(name);
                row.setEmail(email);
                row.setDepartmentCode(deptVal);
                row.setPhoneNumber(phone);
                row.setDesignation(desig.isBlank() ? "Faculty" : desig);

                // 1. Mandatory Fields Validation
                if (facId.isBlank()) row.addError("Missing mandatory 'facultyId'.");
                if (name.isBlank()) row.addError("Missing mandatory 'fullName'.");
                if (email.isBlank()) row.addError("Missing mandatory 'email'.");
                if (deptVal.isBlank()) row.addError("Missing mandatory 'department'.");

                // 2. Email format validation
                if (!email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
                    row.addError("Invalid email address format ('" + email + "').");
                }

                // 3. Phone format validation (optional)
                if (!phone.isBlank() && !PHONE_PATTERN.matcher(phone).matches()) {
                    row.addError("Phone number must be exactly 10 digits ('" + phone + "').");
                }

                // 4. In-CSV Duplicate Check
                if (!facId.isBlank()) {
                    if (seenFacultyIds.contains(facId.toUpperCase())) {
                        row.addError("Duplicate facultyId within this CSV ('" + facId + "').");
                    } else {
                        seenFacultyIds.add(facId.toUpperCase());
                    }
                }
                if (!email.isBlank()) {
                    if (seenEmails.contains(email)) {
                        row.addError("Duplicate email within this CSV ('" + email + "').");
                    } else {
                        seenEmails.add(email);
                    }
                }

                // 5. Database duplicate check
                if (!facId.isBlank() && facultyRepository.existsByFacultyId(facId)) {
                    row.addError("Faculty ID '" + facId + "' already exists in database.");
                }
                if (!email.isBlank() && (facultyRepository.existsByEmail(email) || userRepository.existsByEmailIgnoreCase(email))) {
                    row.addError("Email '" + email + "' is already registered in the system.");
                }

                // 6. Department existence check
                if (!deptVal.isBlank()) {
                    Department resolvedDept = deptLookup.get(deptVal.toUpperCase());
                    if (resolvedDept != null) {
                        row.setDepartmentId(resolvedDept.getId());
                        row.setDepartmentName(resolvedDept.getDeptName() + " (" + resolvedDept.getDeptCode() + ")");
                    } else {
                        row.addError("Department '" + deptVal + "' not found. Available codes: " +
                            deptLookup.values().stream().map(Department::getDeptCode).distinct().toList());
                    }
                }

                if (row.getErrors().isEmpty()) {
                    row.setValid(true);
                    validRows.add(row);
                } else {
                    row.setValid(false);
                    invalidRows.add(row);
                }
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to parse CSV file: " + e.getMessage());
            return "redirect:/admin/faculty/import";
        }

        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Import Faculty Members - Validation Preview");
        model.addAttribute("activePage", "faculty");
        model.addAttribute("previewMode", true);
        model.addAttribute("validRows", validRows);
        model.addAttribute("invalidRows", invalidRows);
        model.addAttribute("totalParsed", validRows.size() + invalidRows.size());
        model.addAttribute("validCount", validRows.size());
        model.addAttribute("invalidCount", invalidRows.size());

        return "admin/faculty/import";
    }

    /**
     * Commit valid faculty records into MySQL and generate secure user accounts.
     */
    @PostMapping("/import/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmImport(@RequestParam("facultyIds") List<String> facultyIds,
                                @RequestParam("fullNames") List<String> fullNames,
                                @RequestParam("emails") List<String> emails,
                                @RequestParam("phoneNumbers") List<String> phoneNumbers,
                                @RequestParam("departmentIds") List<Long> departmentIds,
                                @RequestParam("designations") List<String> designations,
                                RedirectAttributes redirectAttributes) {
        if (facultyIds == null || facultyIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "No valid records selected to import.");
            return "redirect:/admin/faculty/import";
        }

        int count = 0;
        int size = facultyIds.size();

        for (int i = 0; i < size; i++) {
            String facId = facultyIds.get(i).trim();
            String name = fullNames.get(i).trim();
            String email = emails.get(i).trim().toLowerCase();
            String phone = (i < phoneNumbers.size()) ? phoneNumbers.get(i).trim() : "";
            Long deptId = (i < departmentIds.size()) ? departmentIds.get(i) : null;
            String desig = (i < designations.size()) ? designations.get(i).trim() : "Faculty";

            if (facultyRepository.existsByFacultyId(facId) || facultyRepository.existsByEmail(email)) {
                continue; // Skip if already exists
            }

            Department dept = (deptId != null) ? departmentRepository.findById(deptId).orElse(null) : null;
            if (dept == null) {
                continue;
            }

            // 1. Find or create User account with role FACULTY
            Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);
            User user;
            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                user = new User();
                user.setFullName(name);
                user.setEmail(email.trim());
                user.setPassword(passwordEncoder.encode("Faculty@123")); // Secure default password
                user.setRole(UserRole.FACULTY);
                user.setEnabled(true);
                user = userRepository.save(user);
            }

            // 2. Create Faculty record
            Faculty faculty = new Faculty();
            faculty.setFacultyId(facId);
            faculty.setFullName(name);
            faculty.setEmail(email);
            if (!phone.isBlank()) {
                faculty.setPhone(phone);
            }
            faculty.setDesignation(desig);
            faculty.setDepartment(dept);
            faculty.setUser(user);
            faculty.setAvailable(true);
            faculty.setAccountActive(true);
            faculty.setMaxDuties(10);
            faculty.setCurrentDutyCount(0);

            facultyRepository.save(faculty);
            count++;

            auditService.log(AuditAction.FACULTY_CREATED,
                "Imported faculty member: " + name + " (" + facId + ") via CSV batch",
                "Faculty", faculty.getId());
        }

        redirectAttributes.addFlashAttribute("successMsg",
            "Batch Import Completed! Successfully imported " + count + " faculty members and generated their user login credentials (default password: Faculty@123).");
        return "redirect:/admin/faculty";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
