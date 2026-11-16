package com.examscheduler.controller;

import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.repository.*;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.ConflictDetectionService;
import com.examscheduler.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.examscheduler.security.CustomUserDetails;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final ExaminationRepository examinationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final InvigilationRepository invigilationRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public AdminDashboardController(UserRepository userRepository, FacultyRepository facultyRepository,
                                    StudentRepository studentRepository, DepartmentRepository departmentRepository,
                                    ExaminationRepository examinationRepository, ExamRoomRepository examRoomRepository,
                                    InvigilationRepository invigilationRepository, ConflictDetectionService conflictDetectionService,
                                    AuditService auditService, NotificationService notificationService,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.examinationRepository = examinationRepository;
        this.examRoomRepository = examRoomRepository;
        this.invigilationRepository = invigilationRepository;
        this.conflictDetectionService = conflictDetectionService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate today = LocalDate.now();

        // 10 Key University Statistics
        model.addAttribute("totalFaculty", facultyRepository.count());
        model.addAttribute("totalStudents", studentRepository.count());
        model.addAttribute("totalDepartments", departmentRepository.count());
        model.addAttribute("totalExaminations", examinationRepository.count());
        model.addAttribute("upcomingExaminations", examinationRepository.countByExamDateGreaterThanEqual(today));
        model.addAttribute("completedExaminations", examinationRepository.countByStatus(ExamStatus.COMPLETED));
        model.addAttribute("totalRooms", examRoomRepository.count());
        model.addAttribute("activeAssignments", invigilationRepository.countActiveAssignments());
        model.addAttribute("pendingAssignments", examinationRepository.findUnassignedExaminations().size());
        model.addAttribute("schedulingConflicts", conflictDetectionService.detectAllConflicts().size());

        // Upcoming exams list
        model.addAttribute("upcomingExamList", examinationRepository.findUpcomingExams(today).stream().limit(5).toList());

        // Recent activity
        model.addAttribute("recentActivity", auditService.getRecent());

        // User info
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            long unread = notificationService.getUnreadCount(userDetails.getUserId());
            model.addAttribute("unreadNotifications", unread);
        }

        model.addAttribute("pageTitle", "Admin Dashboard");
        model.addAttribute("activePage", "dashboard");

        return "admin/dashboard";
    }

    @GetMapping("/profile")
    public String profile(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";
        model.addAttribute("user", userDetails.getUser());
        model.addAttribute("currentUser", userDetails.getUser());
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        model.addAttribute("totalFaculty", facultyRepository.count());
        model.addAttribute("totalExaminations", examinationRepository.count());
        model.addAttribute("recentLogs", auditService.getRecent().stream().limit(10).toList());
        model.addAttribute("pageTitle", "Admin Profile");
        model.addAttribute("activePage", "profile");
        return "admin/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String fullName,
                                @RequestParam(required = false) String newPassword,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        userRepository.findById(userDetails.getUserId()).ifPresent(u -> {
            u.setFullName(fullName);
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                u.setPassword(passwordEncoder.encode(newPassword.trim()));
            }
            userRepository.save(u);
            auditService.log(AuditAction.USER_UPDATED, "Administrator updated profile details", "User", u.getId());
            redirectAttributes.addFlashAttribute("successMsg", "Profile updated successfully.");
        });
        return "redirect:/admin/profile";
    }
}
