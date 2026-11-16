package com.examscheduler.controller;

import com.examscheduler.dto.UserImportRowDto;
import com.examscheduler.entity.Faculty;
import com.examscheduler.entity.User;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.UserRole;
import com.examscheduler.repository.FacultyRepository;
import com.examscheduler.repository.UserRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.NotificationService;
import com.examscheduler.service.UserImportService;
import com.opencsv.CSVReader;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin/users")
public class UserController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserImportService userImportService;

    public UserController(UserRepository userRepository,
                          FacultyRepository facultyRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService,
                          NotificationService notificationService,
                          UserImportService userImportService) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.userImportService = userImportService;
    }

    @GetMapping
    public String listUsers(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) UserRole role,
                            Model model,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            HttpSession session) {
        List<User> users;
        if (keyword != null && !keyword.trim().isEmpty()) {
            users = userRepository.searchByKeyword(keyword.trim());
        } else if (role != null) {
            users = userRepository.findByRole(role);
        } else {
            users = userRepository.findAll();
        }

        @SuppressWarnings("unchecked")
        List<UserImportRowDto> failedRows = (List<UserImportRowDto>) session.getAttribute("FAILED_USER_IMPORT");
        model.addAttribute("failedReportAvailable", failedRows != null && !failedRows.isEmpty());
        if (!model.containsAttribute("importSummaryAvailable")) {
            model.addAttribute("importSummaryAvailable", false);
        }

        model.addAttribute("users", users);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("selectedRole", role);
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("adminCount", userRepository.countByRole(UserRole.ADMIN));
        model.addAttribute("coordinatorCount", userRepository.countByRole(UserRole.EXAM_COORDINATOR));
        model.addAttribute("facultyCount", userRepository.countByRole(UserRole.FACULTY));

        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("activePage", "users");
        return "admin/users/list";
    }

    @GetMapping({"/create", "/new"})
    public String showCreateForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", UserRole.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Create User");
        model.addAttribute("activePage", "users");
        return "admin/users/form";
    }

    @PostMapping({"/create", "/new"})
    public String createUser(@ModelAttribute User user,
                             RedirectAttributes redirectAttributes,
                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userRepository.existsByEmailIgnoreCase(user.getEmail().trim())) {
            redirectAttributes.addFlashAttribute("errorMsg", "A user with this email address already exists.");
            return "redirect:/admin/users/create";
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User saved = userRepository.save(user);

        if (saved.getRole() == UserRole.FACULTY) {
            Optional<Faculty> facOpt = facultyRepository.findByEmail(saved.getEmail());
            if (facOpt.isPresent()) {
                Faculty fac = facOpt.get();
                if (fac.getUser() == null) {
                    fac.setUser(saved);
                    facultyRepository.save(fac);
                }
            }
        }

        auditService.log(AuditAction.USER_CREATED,
                "Created user " + saved.getEmail() + " with role " + saved.getRole(),
                "User", saved.getId());

        redirectAttributes.addFlashAttribute("successMsg", "User " + saved.getFullName() + " created successfully.");
        return "redirect:/admin/users";
    }

    @GetMapping("/edit/{id:[0-9]+}")
    public String showEditForm(@PathVariable Long id, Model model,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "User not found.");
            return "redirect:/admin/users";
        }

        model.addAttribute("user", userOpt.get());
        model.addAttribute("roles", UserRole.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit User");
        model.addAttribute("activePage", "users");
        return "admin/users/form";
    }

    @PostMapping("/edit/{id:[0-9]+}")
    public String updateUser(@PathVariable Long id,
                             @ModelAttribute User userForm,
                             @RequestParam(required = false) String newPassword,
                             RedirectAttributes redirectAttributes,
                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "User not found.");
            return "redirect:/admin/users";
        }

        User existing = userOpt.get();
        existing.setFullName(userForm.getFullName());
        existing.setRole(userForm.getRole());
        existing.setEnabled(userForm.isEnabled());

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(newPassword.trim()));
        }

        userRepository.save(existing);
        auditService.log(AuditAction.USER_UPDATED,
                "Updated user " + existing.getEmail() + " details and role to " + existing.getRole(),
                "User", existing.getId());

        redirectAttributes.addFlashAttribute("successMsg", "User " + existing.getFullName() + " updated successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        if (userDetails != null && userDetails.getUserId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMsg", "You cannot delete your own active administrator account.");
            return "redirect:/admin/users";
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            userRepository.delete(user);
            auditService.log(AuditAction.USER_DELETED,
                    "Deleted user " + user.getEmail(), "User", id);
            redirectAttributes.addFlashAttribute("successMsg", "User deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "User not found.");
        }
        return "redirect:/admin/users";
    }

    // =========================================================================
    // BATCH CSV USER IMPORT MODULE (DUPLICATE-SAFE & ISOLATED TRANSACTIONS)
    // =========================================================================

    /**
     * Download CSV template for user bulk import.
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        String csvContent = "fullName,emailAddress,password,userRole\n"
                + "Ananya Sharma,ananya.sharma01@example.com,Temp@Fac2026A1,FACULTY\n"
                + "Rahul Verma,rahul.verma02@example.com,Temp@Fac2026B2,FACULTY\n"
                + "Suresh Patel,suresh.patel03@example.com,Temp@Coord2026C3,COORDINATOR\n"
                + "System Admin,backup.admin@example.com,Temp@Admin2026D4,ADMIN\n";
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users_import_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    /**
     * Display the CSV import interface.
     */
    @GetMapping("/import")
    public String showImportPage(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("previewMode", false);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Import Users (CSV)");
        model.addAttribute("activePage", "users");
        return "admin/users/import";
    }

    /**
     * Upload and parse CSV file, perform case-insensitive duplicate checks, and show preview.
     */
    @PostMapping("/import/preview")
    public String previewImport(@RequestParam("file") MultipartFile file,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes,
                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please select a valid CSV file to upload.");
            return "redirect:/admin/users/import";
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            redirectAttributes.addFlashAttribute("errorMsg", "Only .csv files are supported. Please upload a valid CSV file.");
            return "redirect:/admin/users/import";
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            redirectAttributes.addFlashAttribute("errorMsg", "File size exceeds the 5 MB limit.");
            return "redirect:/admin/users/import";
        }

        List<String[]> allRows;
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            allRows = reader.readAll();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to parse CSV file: " + e.getMessage());
            return "redirect:/admin/users/import";
        }

        if (allRows == null || allRows.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Uploaded CSV file is empty. Please provide a file with valid headers and data.");
            return "redirect:/admin/users/import";
        }

        String[] header = allRows.get(0);
        int nameIdx = -1, emailIdx = -1, passIdx = -1, roleIdx = -1;
        for (int i = 0; i < header.length; i++) {
            if (header[i] == null) continue;
            String h = header[i].trim().toLowerCase().replace("\uFEFF", "").replace(" ", "").replace("_", "");
            if (h.equals("fullname") || h.equals("name") || h.equals("username") || h.equals("userfullname")) {
                nameIdx = i;
            } else if (h.equals("emailaddress") || h.equals("email") || h.equals("useremail") || h.equals("mail")) {
                emailIdx = i;
            } else if (h.equals("password") || h.equals("pass") || h.equals("temppassword")) {
                passIdx = i;
            } else if (h.equals("userrole") || h.equals("role") || h.equals("userroles") || h.equals("roles")) {
                roleIdx = i;
            }
        }

        List<String> missingHeaders = new ArrayList<>();
        if (nameIdx == -1) missingHeaders.add("fullName");
        if (emailIdx == -1) missingHeaders.add("emailAddress");
        if (passIdx == -1) missingHeaders.add("password");
        if (roleIdx == -1) missingHeaders.add("userRole");

        if (!missingHeaders.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg",
                    "Invalid CSV format. Missing required columns: " + String.join(", ", missingHeaders)
                            + ". Expected header columns: fullName, emailAddress, password, userRole");
            return "redirect:/admin/users/import";
        }

        if (allRows.size() <= 1) {
            redirectAttributes.addFlashAttribute("errorMsg", "CSV file contains only header columns without any user records to import.");
            return "redirect:/admin/users/import";
        }

        Set<String> seenEmailsInFile = new HashSet<>();
        List<UserImportRowDto> validRows = new ArrayList<>();
        List<UserImportRowDto> duplicateRows = new ArrayList<>();
        List<UserImportRowDto> invalidRows = new ArrayList<>();

        for (int i = 1; i < allRows.size(); i++) {
            String[] row = allRows.get(i);
            int rowNum = i + 1;

            boolean allBlank = true;
            for (String c : row) {
                if (c != null && !c.trim().isEmpty()) {
                    allBlank = false;
                    break;
                }
            }
            if (allBlank) continue;

            String name = (nameIdx < row.length && row[nameIdx] != null) ? row[nameIdx].trim() : "";
            String email = (emailIdx < row.length && row[emailIdx] != null) ? row[emailIdx].trim() : "";
            String password = (passIdx < row.length && row[passIdx] != null) ? row[passIdx].trim() : "";
            String roleStr = (roleIdx < row.length && row[roleIdx] != null) ? row[roleIdx].trim() : "";

            UserImportRowDto dto = new UserImportRowDto();
            dto.setRowNumber(rowNum);
            dto.setFullName(name);
            dto.setEmail(email);
            dto.setRawPassword(password);
            dto.setRawRole(roleStr);

            // 1. Full name validation
            if (name.isEmpty()) {
                dto.addError("Full name is required");
            }

            // 2. Email format & Case-Insensitive Duplicate Check
            if (email.isEmpty()) {
                dto.addError("Email address is required");
            } else if (!EMAIL_PATTERN.matcher(email).matches()) {
                dto.addError("Invalid email address format: '" + email + "'");
            } else {
                String normEmail = email.toLowerCase();
                if (seenEmailsInFile.contains(normEmail)) {
                    dto.addError("Duplicate email address in CSV file.");
                    dto.setDuplicateInFile(true);
                } else if (userRepository.existsByEmailIgnoreCase(email)) {
                    // Exact reason required: "Email address already exists."
                    dto.addError("Email address already exists.");
                    dto.setDuplicateInDb(true);
                } else {
                    seenEmailsInFile.add(normEmail);
                }
            }

            // 3. Password validation
            if (password.isEmpty()) {
                dto.addError("Password is required");
            } else if (password.length() < 6) {
                dto.addError("Password must be at least 6 characters long");
            }

            // 4. Role validation
            if (roleStr.isEmpty()) {
                dto.addError("User role is required (ADMIN, COORDINATOR, or FACULTY)");
            } else {
                String r = roleStr.toUpperCase().replace(" ", "_");
                if ("ADMIN".equals(r) || "ROLE_ADMIN".equals(r)) {
                    dto.setResolvedRole(UserRole.ADMIN);
                } else if ("FACULTY".equals(r) || "ROLE_FACULTY".equals(r)) {
                    dto.setResolvedRole(UserRole.FACULTY);
                } else if ("COORDINATOR".equals(r) || "EXAM_COORDINATOR".equals(r) || "ROLE_EXAM_COORDINATOR".equals(r)) {
                    dto.setResolvedRole(UserRole.EXAM_COORDINATOR);
                } else {
                    dto.addError("Unsupported role '" + roleStr + "'. Allowed roles are ADMIN, COORDINATOR, FACULTY");
                }
            }

            // Separate classification: Duplicate vs Invalid vs Valid
            if (dto.isDuplicate()) {
                dto.setValid(false);
                duplicateRows.add(dto);
            } else if (!dto.getErrors().isEmpty()) {
                dto.setValid(false);
                invalidRows.add(dto);
            } else {
                dto.setValid(true);
                validRows.add(dto);
            }
        }

        // Store in session for confirm and diagnostic report
        session.setAttribute("PENDING_USER_IMPORT", validRows);
        session.setAttribute("DUPLICATE_USER_IMPORT", duplicateRows);
        session.setAttribute("INVALID_USER_IMPORT", invalidRows);

        List<UserImportRowDto> allFailedRows = new ArrayList<>();
        allFailedRows.addAll(duplicateRows);
        allFailedRows.addAll(invalidRows);
        session.setAttribute("FAILED_USER_IMPORT", allFailedRows);

        int totalRecords = validRows.size() + duplicateRows.size() + invalidRows.size();

        model.addAttribute("previewMode", true);
        model.addAttribute("validRows", validRows);
        model.addAttribute("duplicateRows", duplicateRows);
        model.addAttribute("invalidRows", invalidRows);
        model.addAttribute("totalRecords", totalRecords);
        model.addAttribute("validRecords", validRows.size());
        model.addAttribute("duplicateRecords", duplicateRows.size());
        model.addAttribute("invalidRecords", invalidRows.size());

        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Preview User Import");
        model.addAttribute("activePage", "users");
        return "admin/users/import";
    }

    /**
     * Commit confirmed valid user accounts into the database with isolated per-record transactions.
     * Prevents any duplicate key constraint collision from aborting the entire batch.
     */
    @PostMapping("/import/confirm")
    public String confirmImport(HttpSession session,
                                RedirectAttributes redirectAttributes,
                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        @SuppressWarnings("unchecked")
        List<UserImportRowDto> validRows = (List<UserImportRowDto>) session.getAttribute("PENDING_USER_IMPORT");

        if (validRows == null || validRows.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "No valid pending records found for import. Please re-upload your file.");
            return "redirect:/admin/users/import";
        }

        @SuppressWarnings("unchecked")
        List<UserImportRowDto> duplicateRows = (List<UserImportRowDto>) session.getAttribute("DUPLICATE_USER_IMPORT");
        @SuppressWarnings("unchecked")
        List<UserImportRowDto> invalidRows = (List<UserImportRowDto>) session.getAttribute("INVALID_USER_IMPORT");

        int preDuplicateCount = (duplicateRows != null) ? duplicateRows.size() : 0;
        int preInvalidCount = (invalidRows != null) ? invalidRows.size() : 0;
        int totalCsvRecords = validRows.size() + preDuplicateCount + preInvalidCount;

        int successfullyImported = 0;
        int duplicateSkipped = preDuplicateCount;
        int failedRecords = 0;

        List<UserImportRowDto> dynamicFailedRows = new ArrayList<>();
        if (duplicateRows != null) dynamicFailedRows.addAll(duplicateRows);
        if (invalidRows != null) dynamicFailedRows.addAll(invalidRows);

        for (UserImportRowDto row : validRows) {
            String email = row.getEmail().trim();

            // Case-insensitive check before attempting insertion
            if (userRepository.existsByEmailIgnoreCase(email)) {
                duplicateSkipped++;
                row.addError("Email address already exists.");
                row.setDuplicateInDb(true);
                dynamicFailedRows.add(row);
                continue;
            }

            try {
                // Uses isolated REQUIRES_NEW transaction so any constraint error only rolls back this single user
                userImportService.saveSingleUser(row.getFullName(), email, row.getRawPassword(), row.getResolvedRole());
                successfullyImported++;
            } catch (UserImportService.DuplicateEmailException | DataIntegrityViolationException ex) {
                // Graceful fallback for concurrent duplicate insertion
                duplicateSkipped++;
                row.addError("Email address already exists.");
                row.setDuplicateInDb(true);
                dynamicFailedRows.add(row);
            } catch (Exception ex) {
                failedRecords++;
                row.addError("System error saving user: " + ex.getMessage());
                dynamicFailedRows.add(row);
            }
        }

        session.removeAttribute("PENDING_USER_IMPORT");
        session.setAttribute("FAILED_USER_IMPORT", dynamicFailedRows);

        // Required summary attributes
        redirectAttributes.addFlashAttribute("importSummaryAvailable", true);
        redirectAttributes.addFlashAttribute("summaryTotalRecords", totalCsvRecords);
        redirectAttributes.addFlashAttribute("summarySuccessRecords", successfullyImported);
        redirectAttributes.addFlashAttribute("summaryDuplicateRecords", duplicateSkipped);
        redirectAttributes.addFlashAttribute("summaryInvalidRecords", preInvalidCount);
        redirectAttributes.addFlashAttribute("summaryFailedRecords", failedRecords);

        String summaryMsg = String.format(
                "Total CSV records: %d | Successfully imported: %d | Duplicate records skipped: %d | Invalid records: %d | Failed records: %d",
                totalCsvRecords, successfullyImported, duplicateSkipped, preInvalidCount, failedRecords
        );
        redirectAttributes.addFlashAttribute("successMsg", summaryMsg);

        return "redirect:/admin/users";
    }

    /**
     * Download CSV diagnostic report for records that failed validation or were skipped.
     */
    @GetMapping("/import/failed-report")
    public ResponseEntity<byte[]> downloadFailedReport(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<UserImportRowDto> failedRows = (List<UserImportRowDto>) session.getAttribute("FAILED_USER_IMPORT");

        StringBuilder sb = new StringBuilder();
        sb.append("Row Number,Full Name,Email Address,Role,Failure Reasons\n");

        if (failedRows != null && !failedRows.isEmpty()) {
            for (UserImportRowDto row : failedRows) {
                sb.append(row.getRowNumber()).append(",");
                sb.append("\"").append(row.getFullName() != null ? row.getFullName().replace("\"", "\"\"") : "").append("\",");
                sb.append("\"").append(row.getEmail() != null ? row.getEmail().replace("\"", "\"\"") : "").append("\",");
                sb.append("\"").append(row.getRawRole() != null ? row.getRawRole().replace("\"", "\"\"") : "").append("\",");
                sb.append("\"").append(row.getErrorString().replace("\"", "\"\"")).append("\"\n");
            }
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"failed_users_import_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
