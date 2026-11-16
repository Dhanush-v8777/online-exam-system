package com.examscheduler.controller;

import com.examscheduler.entity.Examination;
import com.examscheduler.entity.Faculty;
import com.examscheduler.entity.Invigilation;
import com.examscheduler.repository.ExaminationRepository;
import com.examscheduler.repository.FacultyRepository;
import com.examscheduler.repository.InvigilationRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.InvigilatorAllocationService;
import com.examscheduler.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/invigilation")
public class InvigilationController {

    private final InvigilatorAllocationService invigilatorAllocationService;
    private final InvigilationRepository invigilationRepository;
    private final ExaminationRepository examinationRepository;
    private final FacultyRepository facultyRepository;
    private final NotificationService notificationService;

    public InvigilationController(InvigilatorAllocationService invigilatorAllocationService,
                                 InvigilationRepository invigilationRepository,
                                 ExaminationRepository examinationRepository,
                                 FacultyRepository facultyRepository,
                                 NotificationService notificationService) {
        this.invigilatorAllocationService = invigilatorAllocationService;
        this.invigilationRepository = invigilationRepository;
        this.examinationRepository = examinationRepository;
        this.facultyRepository = facultyRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long examId,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Examination> upcomingExams = examinationRepository.findAll().stream()
            .filter(e -> e.getStatus() == com.examscheduler.enums.ExamStatus.SCHEDULED || e.getStatus() == com.examscheduler.enums.ExamStatus.PUBLISHED)
            .filter(e -> e.getExamDate() != null)
            .sorted(java.util.Comparator.comparing(Examination::getExamDate).thenComparing(Examination::getStartTime))
            .toList();

        List<Invigilation> allInvigilations = invigilationRepository.findAll();
        java.util.Map<Long, List<Invigilation>> invigilationsByExam = allInvigilations.stream()
            .filter(inv -> inv.getExamination() != null)
            .collect(java.util.stream.Collectors.groupingBy(inv -> inv.getExamination().getId()));

        Examination selectedExam = null;
        List<Faculty> availableFacultyForExam = new java.util.ArrayList<>();
        if (examId != null) {
            selectedExam = examinationRepository.findById(examId).orElse(null);
            if (selectedExam != null && selectedExam.getExamDate() != null) {
                LocalDate targetDate = selectedExam.getExamDate();
                Long targetExamId = selectedExam.getId();
                List<Faculty> allEligible = facultyRepository.findByAvailableTrueAndAccountActiveTrue();
                for (Faculty f : allEligible) {
                    if (f.getCurrentDutyCount() >= f.getMaxDuties()) continue;
                    if (invigilationRepository.existsByFacultyIdAndExaminationId(f.getId(), targetExamId)) continue;
                    List<Invigilation> sameDay = invigilationRepository.findByFacultyAndDate(f.getId(), targetDate);
                    if (!sameDay.isEmpty()) continue;
                    availableFacultyForExam.add(f);
                }
            }
        }

        model.addAttribute("examinations", upcomingExams);
        model.addAttribute("invigilations", allInvigilations);
        model.addAttribute("invigilationsByExam", invigilationsByExam);
        model.addAttribute("totalAssigned", allInvigilations.size());
        model.addAttribute("allFaculty", facultyRepository.findByAvailableTrueAndAccountActiveTrue());
        model.addAttribute("selectedExamId", examId);
        model.addAttribute("selectedExam", selectedExam);
        model.addAttribute("availableFacultyForExam", availableFacultyForExam);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Invigilator Management");
        model.addAttribute("activePage", "invigilation");
        return "admin/invigilation/list";
    }

    @PostMapping("/auto-allocate")
    public String autoAllocate(
            @RequestParam(required = false) Long examId,
            @RequestParam(defaultValue = "false") boolean enforceExternalDept,
            @RequestParam(defaultValue = "1") int invigilatorsPerRoom,
            RedirectAttributes redirectAttributes) {

        InvigilatorAllocationService.AllocationResult result =
            invigilatorAllocationService.allocateInvigilators(examId, enforceExternalDept, invigilatorsPerRoom);

        if (result.getTotalAssigned() > 0) {
            redirectAttributes.addFlashAttribute("successMsg",
                "Auto-Allocation Complete: Successfully allocated " + result.getTotalAssigned() + " faculty invigilation duties.");
            redirectAttributes.addFlashAttribute("allocationLogs", result.getAssignmentLogs());
        } else if (result.getUnassignedDetails().isEmpty()) {
            redirectAttributes.addFlashAttribute("successMsg",
                "All scheduled examinations are already fully staffed with invigilators.");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Automatic allocation could not assign invigilators. Please check faculty availability and date constraints.");
        }

        if (!result.getUnassignedDetails().isEmpty()) {
            redirectAttributes.addFlashAttribute("unassignedExams", result.getUnassignedDetails());
            redirectAttributes.addFlashAttribute("warningMsg",
                result.getUnassignedDetails().size() + " scheduled examination(s) require additional invigilators.");
        }

        return "redirect:/admin/invigilation";
    }

    @GetMapping("/auto-allocate")
    public String autoAllocateGet(RedirectAttributes redirectAttributes) {
        return autoAllocate(null, false, 1, redirectAttributes);
    }

    @GetMapping(value = "/available-faculty", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public org.springframework.http.ResponseEntity<List<java.util.Map<String, Object>>> getAvailableFaculty(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate examDate) {
        
        LocalDate targetDate = examDate;
        if (targetDate == null && examId != null) {
            targetDate = examinationRepository.findById(examId).map(Examination::getExamDate).orElse(null);
        }

        List<Faculty> allEligible = facultyRepository.findByAvailableTrueAndAccountActiveTrue();
        final LocalDate checkDate = targetDate;

        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Faculty f : allEligible) {
            // Check duty quota
            if (f.getCurrentDutyCount() >= f.getMaxDuties()) {
                continue;
            }
            // Check if already assigned to this specific exam
            if (examId != null && invigilationRepository.existsByFacultyIdAndExaminationId(f.getId(), examId)) {
                continue;
            }
            // Check institution-wide single duty per calendar day constraint
            if (checkDate != null) {
                List<Invigilation> sameDay = invigilationRepository.findByFacultyAndDate(f.getId(), checkDate);
                if (!sameDay.isEmpty()) {
                    continue; // Skip faculty already assigned on this date
                }
            }
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", f.getId());
            map.put("fullName", f.getFullName());
            map.put("departmentCode", f.getDepartment() != null ? f.getDepartment().getDeptCode() : "N/A");
            map.put("departmentName", f.getDepartment() != null ? f.getDepartment().getDeptName() : "");
            map.put("designation", f.getDesignation() != null ? f.getDesignation() : "Faculty");
            map.put("currentDuties", f.getCurrentDutyCount());
            map.put("maxDuties", f.getMaxDuties());
            map.put("displayText", f.getFullName() + " - " + (f.getDepartment() != null ? f.getDepartment().getDeptCode() : "") +
                    " (Duties: " + f.getCurrentDutyCount() + "/" + f.getMaxDuties() + ")");
            result.add(map);
        }

        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(result);
    }

    @PostMapping("/assign")
    public String manualAssign(@RequestParam Long examId,
                               @RequestParam Long facultyId,
                               RedirectAttributes redirectAttributes) {
        Examination exam = examinationRepository.findById(examId).orElse(null);
        Faculty faculty = facultyRepository.findById(facultyId).orElse(null);
        if (exam == null || faculty == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Invalid examination or faculty selection.");
            return "redirect:/admin/invigilation" + (examId != null ? "?examId=" + examId : "");
        }
        if (invigilationRepository.existsByFacultyIdAndExaminationId(facultyId, examId)) {
            redirectAttributes.addFlashAttribute("errorMsg", faculty.getFullName() + " is already assigned to this examination.");
            return "redirect:/admin/invigilation?examId=" + examId;
        }

        // Hard Backend Validation: A faculty member must not be assigned to more than one examination on the same calendar day!
        List<Invigilation> sameDayAssignments = invigilationRepository.findByFacultyAndDate(facultyId, exam.getExamDate());
        if (!sameDayAssignments.isEmpty()) {
            Examination existingExam = sameDayAssignments.get(0).getExamination();
            redirectAttributes.addFlashAttribute("errorMsg",
                "Same-Day Duty Conflict: " + faculty.getFullName() + " is already assigned to invigilate '" +
                existingExam.getExamName() + "' on " + exam.getExamDate() + ". " +
                "A faculty member must not be assigned to more than one examination on the same calendar day.");
            return "redirect:/admin/invigilation?examId=" + examId;
        }

        if (faculty.getCurrentDutyCount() >= faculty.getMaxDuties()) {
            redirectAttributes.addFlashAttribute("errorMsg",
                faculty.getFullName() + " has already reached their maximum assigned duty quota (" +
                faculty.getMaxDuties() + " duties).");
            return "redirect:/admin/invigilation?examId=" + examId;
        }

        Invigilation inv = new Invigilation();
        inv.setExamination(exam);
        inv.setFaculty(faculty);
        inv.setConfirmed(true);
        inv.setAssignmentNote("Manual coordinator assignment");
        invigilationRepository.save(inv);
        faculty.setCurrentDutyCount(faculty.getCurrentDutyCount() + 1);
        facultyRepository.save(faculty);

        if (faculty.getUser() != null) {
            notificationService.createNotification(
                faculty.getUser(),
                "Invigilation Duty Assigned",
                "You have been assigned to invigilate '" + exam.getExamName() + "' on " +
                    exam.getExamDate() + " at " + exam.getStartTime() +
                    (exam.getExamRoom() != null ? " in Room " + exam.getExamRoom().getRoomNumber() : "") + ".",
                com.examscheduler.enums.NotificationType.INVIGILATION_ASSIGNED,
                exam.getId()
            );
        }

        redirectAttributes.addFlashAttribute("successMsg", "Successfully assigned " + faculty.getFullName() + " to " + exam.getExamName());
        return "redirect:/admin/invigilation?examId=" + examId;
    }

    @PostMapping("/delete/{id}")
    public String removeAssignment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        invigilationRepository.findById(id).ifPresent(inv -> {
            if (inv.getFaculty() != null) {
                inv.getFaculty().setCurrentDutyCount(Math.max(0, inv.getFaculty().getCurrentDutyCount() - 1));
                facultyRepository.save(inv.getFaculty());
            }
            invigilationRepository.deleteById(id);
        });
        redirectAttributes.addFlashAttribute("successMsg", "Invigilation duty unassigned successfully.");
        return "redirect:/admin/invigilation";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
