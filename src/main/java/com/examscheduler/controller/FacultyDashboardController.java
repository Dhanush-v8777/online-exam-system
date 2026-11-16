package com.examscheduler.controller;

import com.examscheduler.entity.Examination;
import com.examscheduler.entity.Faculty;
import com.examscheduler.entity.Invigilation;
import com.examscheduler.entity.SeatingAllocation;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.ExaminationRepository;
import com.examscheduler.repository.FacultyRepository;
import com.examscheduler.repository.InvigilationRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.NotificationService;
import com.examscheduler.service.SeatingAllocationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/faculty")
public class FacultyDashboardController {

    private final FacultyRepository facultyRepository;
    private final InvigilationRepository invigilationRepository;
    private final ExaminationRepository examinationRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SeatingAllocationService seatingAllocationService;

    public FacultyDashboardController(FacultyRepository facultyRepository,
            InvigilationRepository invigilationRepository,
            ExaminationRepository examinationRepository,
            NotificationService notificationService,
            AuditService auditService,
            SeatingAllocationService seatingAllocationService) {
        this.facultyRepository = facultyRepository;
        this.invigilationRepository = invigilationRepository;
        this.examinationRepository = examinationRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.seatingAllocationService = seatingAllocationService;
    }

    private Optional<Faculty> resolveFaculty(CustomUserDetails userDetails) {
        if (userDetails == null)
            return Optional.empty();
        Optional<Faculty> opt = facultyRepository.findByEmail(userDetails.getUsername());
        if (opt.isEmpty() && userDetails.getUserId() != null) {
            opt = facultyRepository.findByUserId(userDetails.getUserId());
        }
        return opt;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";

        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty()) {
            model.addAttribute("errorMsg", "No faculty profile linked to your user account.");
            addCommonAttributes(model, userDetails);
            return "faculty/dashboard";
        }

        Faculty faculty = facultyOpt.get();
        List<Invigilation> allDuties = invigilationRepository.findByFacultyId(faculty.getId());
        LocalDate today = LocalDate.now();

        long totalDuties = allDuties.size();
        long upcomingDuties = allDuties.stream()
                .filter(i -> i.getExamination() != null && !i.getExamination().getExamDate().isBefore(today))
                .count();
        long completedDuties = allDuties.stream()
                .filter(i -> i.getExamination() != null && i.getExamination().getExamDate().isBefore(today))
                .count();
        long pendingDuties = allDuties.stream()
                .filter(i -> !i.isConfirmed())
                .count();

        if (faculty.getCurrentDutyCount() != totalDuties) {
            faculty.setCurrentDutyCount((int) totalDuties);
            facultyRepository.save(faculty);
        }

        model.addAttribute("faculty", faculty);
        model.addAttribute("totalDuties", totalDuties);
        model.addAttribute("upcomingDutiesCount", upcomingDuties);
        model.addAttribute("completedDutiesCount", completedDuties);
        model.addAttribute("pendingDutiesCount", pendingDuties);

        model.addAttribute("upcomingDuties", invigilationRepository.findByFacultyFromDate(faculty.getId(), today));
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Faculty Portal Dashboard");
        model.addAttribute("activePage", "dashboard");

        return "faculty/dashboard";
    }

    @GetMapping("/profile")
    public String profile(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/faculty/dashboard";

        Faculty faculty = facultyOpt.get();
        long actualDuties = invigilationRepository.countByFacultyId(faculty.getId());
        faculty.setCurrentDutyCount((int) actualDuties);

        model.addAttribute("faculty", faculty);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Faculty Profile");
        model.addAttribute("activePage", "profile");
        return "faculty/profile";
    }

    @GetMapping("/availability")
    public String availability(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/faculty/dashboard";

        Faculty faculty = facultyOpt.get();
        model.addAttribute("faculty", faculty);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "My Availability");
        model.addAttribute("activePage", "availability");
        return "faculty/availability";
    }

    @GetMapping("/duties")
    public String myDuties(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/faculty/dashboard";

        Faculty faculty = facultyOpt.get();
        List<Invigilation> duties = invigilationRepository.findByFacultyId(faculty.getId());
        model.addAttribute("duties", duties);
        model.addAttribute("faculty", faculty);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "My Invigilation Duties");
        model.addAttribute("activePage", "duties");
        return "faculty/duties";
    }

    @GetMapping("/timetable")
    public String myTimetable(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/faculty/dashboard";

        Faculty faculty = facultyOpt.get();
        LocalDate today = LocalDate.now();

        // Department exams & assigned exams
        List<Examination> departmentExams = (faculty.getDepartment() != null)
                ? examinationRepository.findByDepartmentId(faculty.getDepartment().getId())
                : examinationRepository.findUpcomingExams(today);

        List<Invigilation> myAssignedDuties = invigilationRepository.findByFacultyId(faculty.getId());

        model.addAttribute("faculty", faculty);
        model.addAttribute("departmentExams", departmentExams);
        model.addAttribute("myDuties", myAssignedDuties);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Examination Timetable");
        model.addAttribute("activePage", "timetable");
        return "faculty/timetable";
    }

    @GetMapping("/history")
    public String dutyHistory(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/faculty/dashboard";

        Faculty faculty = facultyOpt.get();
        LocalDate today = LocalDate.now();

        List<Invigilation> pastDuties = invigilationRepository.findByFacultyId(faculty.getId()).stream()
                .filter(i -> i.getExamination() != null && i.getExamination().getExamDate().isBefore(today))
                .toList();

        model.addAttribute("faculty", faculty);
        model.addAttribute("pastDuties", pastDuties);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Duty History");
        model.addAttribute("activePage", "history");
        return "faculty/history";
    }

    @PostMapping("/duty/confirm/{id}")
    public String confirmDuty(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/faculty/dashboard";

        Faculty faculty = facultyOpt.get();
        invigilationRepository.findById(id).ifPresent(duty -> {
            if (duty.getFaculty().getId().equals(faculty.getId())) {
                duty.setConfirmed(true);
                invigilationRepository.save(duty);
                auditService.log(AuditAction.FACULTY_UPDATED,
                        "Faculty confirmed invigilation duty for " + duty.getExamination().getExamName(),
                        "Invigilation", duty.getId());
                redirectAttributes.addFlashAttribute("successMsg", "Invigilation duty confirmed successfully.");
            }
        });
        return "redirect:/faculty/duties";
    }

    @PostMapping("/availability/toggle")
    public String toggleAvailability(@AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> opt = resolveFaculty(userDetails);
        if (opt.isPresent()) {
            Faculty f = opt.get();
            boolean newState = !f.isAvailable();
            f.setAvailable(newState);
            facultyRepository.save(f);
            auditService.log(AuditAction.FACULTY_UPDATED,
                    "Faculty toggled availability to " + (newState ? "AVAILABLE" : "UNAVAILABLE"),
                    "Faculty", f.getId());
            redirectAttributes.addFlashAttribute("successMsg",
                    "Availability status successfully changed to: " + (newState ? "Available" : "Unavailable"));
        }
        return "redirect:/faculty/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam(required = false) String phone,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null)
            return "redirect:/login";
        Optional<Faculty> opt = resolveFaculty(userDetails);
        if (opt.isPresent()) {
            Faculty f = opt.get();
            if (phone != null && phone.matches("^[0-9]{10}$")) {
                f.setPhone(phone);
                facultyRepository.save(f);
                auditService.log(AuditAction.FACULTY_UPDATED,
                        "Updated contact phone number to " + phone,
                        "Faculty", f.getId());
                redirectAttributes.addFlashAttribute("successMsg", "Phone number updated successfully.");
            } else {
                redirectAttributes.addFlashAttribute("errorMsg",
                        "Please provide a valid 10-digit mobile phone number.");
            }
        }
        return "redirect:/faculty/profile";
    }

    @GetMapping("/seating")
    public String dutySeating(@RequestParam(required = false) Long examId,
                              Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return "redirect:/login";

        Optional<Faculty> facultyOpt = resolveFaculty(userDetails);
        if (facultyOpt.isEmpty())
            return "redirect:/login";

        Faculty faculty = facultyOpt.get();
        List<Invigilation> myDuties = invigilationRepository.findByFacultyIdOrderByExamDateAsc(faculty.getId());
        List<Examination> assignedExams = myDuties.stream()
                .map(Invigilation::getExamination)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        model.addAttribute("assignedExams", assignedExams);
        model.addAttribute("faculty", faculty);

        List<SeatingAllocation> allocations = Collections.emptyList();
        Examination selectedExam = null;

        if (examId != null) {
            boolean isAssigned = assignedExams.stream().anyMatch(e -> e.getId().equals(examId));
            if (isAssigned) {
                selectedExam = examinationRepository.findById(examId).orElse(null);
                if (selectedExam != null) {
                    allocations = seatingAllocationService.getSeatingForExam(examId);
                }
            } else {
                model.addAttribute("errorMsg", "Access Denied: You are not assigned to invigilate this examination.");
            }
        } else if (!assignedExams.isEmpty()) {
            selectedExam = assignedExams.get(0);
            allocations = seatingAllocationService.getSeatingForExam(selectedExam.getId());
        }

        model.addAttribute("selectedExam", selectedExam);
        model.addAttribute("allocations", allocations != null ? allocations : Collections.emptyList());
        model.addAttribute("seatingList", allocations != null ? allocations : Collections.emptyList());
        model.addAttribute("pageTitle", "Assigned Duty Seating Arrangement");
        model.addAttribute("activePage", "seating");
        addCommonAttributes(model, userDetails);
        return "faculty/seating";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
