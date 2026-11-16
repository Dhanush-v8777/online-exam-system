package com.examscheduler.controller;

import com.examscheduler.dto.ConflictDto;
import com.examscheduler.entity.Examination;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.repository.*;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.ConflictDetectionService;
import com.examscheduler.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/coordinator")
public class CoordinatorController {

    private final ExaminationRepository examinationRepository;
    private final InvigilationRepository invigilationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final ConflictDetectionService conflictDetectionService;

    public CoordinatorController(ExaminationRepository examinationRepository,
                                  InvigilationRepository invigilationRepository,
                                  ExamRoomRepository examRoomRepository,
                                  FacultyRepository facultyRepository,
                                  DepartmentRepository departmentRepository,
                                  AuditLogRepository auditLogRepository,
                                  NotificationService notificationService,
                                  ConflictDetectionService conflictDetectionService) {
        this.examinationRepository = examinationRepository;
        this.invigilationRepository = invigilationRepository;
        this.examRoomRepository = examRoomRepository;
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.conflictDetectionService = conflictDetectionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate today = LocalDate.now();

        // 8 Dynamic Dashboard Statistics from actual DB records
        long totalExaminations = examinationRepository.count();
        List<Examination> upcomingExams = examinationRepository.findUpcomingExams(today);
        long completedExams = examinationRepository.countByStatus(ExamStatus.COMPLETED);
        long totalRooms = examRoomRepository.count();
        long availableRooms = examRoomRepository.findByAvailableTrue().size();
        List<Examination> pendingExams = examinationRepository.findUnassignedExaminations();
        long assignedInvigilators = invigilationRepository.countActiveAssignments();
        List<ConflictDto> conflicts = conflictDetectionService.detectAllConflicts();

        model.addAttribute("totalExaminations", totalExaminations);
        model.addAttribute("upcomingExams", upcomingExams);
        model.addAttribute("upcomingCount", upcomingExams.size());
        model.addAttribute("completedExams", completedExams);
        model.addAttribute("totalRooms", totalRooms);
        model.addAttribute("availableRooms", availableRooms);
        model.addAttribute("pendingAssignments", pendingExams.size());
        model.addAttribute("pendingExamList", pendingExams);
        model.addAttribute("assignedInvigilators", assignedInvigilators);
        model.addAttribute("availableFaculty", facultyRepository.findEligibleFaculty().size());
        model.addAttribute("conflicts", conflicts);
        model.addAttribute("conflictsCount", conflicts.size());

        // Overview sections
        model.addAttribute("recentActivities", auditLogRepository.findTop20ByOrderByTimestampDesc());
        model.addAttribute("rooms", examRoomRepository.findAll());

        // Examination Readiness Checklist Summary
        List<com.examscheduler.dto.ExamReadinessDto> readinessList = new java.util.ArrayList<>();
        List<Examination> allUpcoming = (upcomingExams != null && !upcomingExams.isEmpty()) ? upcomingExams : examinationRepository.findAll();
        for (Examination ex : allUpcoming) {
            com.examscheduler.dto.ExamReadinessDto r = new com.examscheduler.dto.ExamReadinessDto();
            r.setId(ex.getId());
            r.setExamName(ex.getExamName());
            r.setSubjectName(ex.getSubject() != null ? ex.getSubject().getSubjectName() : "N/A");
            r.setSubjectCode(ex.getSubject() != null ? ex.getSubject().getSubjectCode() : "-");
            r.setDeptCode(ex.getDepartment() != null ? ex.getDepartment().getDeptCode() : "N/A");
            r.setSemester(ex.getSemester());
            r.setExamDate(ex.getExamDate());
            r.setStartTime(ex.getStartTime());
            r.setEndTime(ex.getEndTime());
            r.setRoomNumber(ex.getExamRoom() != null ? ex.getExamRoom().getRoomNumber() : "TBD");

            boolean timeOk = (ex.getExamDate() != null && ex.getStartTime() != null && ex.getStatus() != ExamStatus.CANCELLED);
            boolean roomOk = (ex.getExamRoom() != null || (ex.getSeatingAllocations() != null && !ex.getSeatingAllocations().isEmpty()));
            int seated = ex.getSeatingAllocations() != null ? ex.getSeatingAllocations().size() : 0;
            int eligible = ex.getRegisteredStudents();
            boolean seatingOk = (eligible > 0 && seated >= eligible) || (seated > 0);
            int assignedFac = ex.getInvigilations() != null ? ex.getInvigilations().size() : 0;
            int reqFac = ex.getRequiredInvigilatorCount();
            boolean facultyOk = (assignedFac >= reqFac);

            boolean conflictFound = false;
            if (conflicts != null) {
                for (com.examscheduler.dto.ConflictDto cd : conflicts) {
                    if (ex.getId().equals(cd.getExamId1()) || ex.getId().equals(cd.getExamId2())) {
                        conflictFound = true;
                        break;
                    }
                }
            }

            r.setTimetableStatus(timeOk ? "Confirmed" : "Pending");
            r.setRoomStatus(roomOk ? "Complete" : "Pending");
            r.setSeatingStatus(seatingOk ? "Complete" : "Pending");
            r.setFacultyStatus(facultyOk ? "Complete" : "Pending");
            r.setConflictStatus(conflictFound ? "Conflict Detected" : "Clear");

            r.setEligibleStudents(eligible);
            r.setAllocatedStudents(seated);
            r.setRequiredFaculty(reqFac);
            r.setAssignedFaculty(assignedFac);

            if (timeOk && roomOk && seatingOk && facultyOk && !conflictFound) {
                r.setOverallStatus("READY");
            } else if (conflictFound || (assignedFac == 0 && roomOk)) {
                r.setOverallStatus("ACTION REQUIRED");
            } else {
                r.setOverallStatus("PENDING");
            }
            readinessList.add(r);
        }
        model.addAttribute("readinessList", readinessList);

        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
        model.addAttribute("pageTitle", "Exam Coordinator Command Center");
        model.addAttribute("activePage", "dashboard");
        return "coordinator/dashboard";
    }

    @GetMapping("/timetable")
    public String timetable(@RequestParam(required = false) Long deptId,
                            @RequestParam(required = false) Integer semester,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model,
                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Examination> exams;
        if (deptId != null && semester != null) {
            exams = examinationRepository.findByDepartmentAndSemester(deptId, semester);
        } else if (date != null) {
            exams = examinationRepository.findByExamDate(date);
        } else {
            exams = examinationRepository.findAll();
        }

        model.addAttribute("examinations", exams);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedDept", deptId);
        model.addAttribute("selectedSemester", semester);
        model.addAttribute("selectedDate", date);

        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
        model.addAttribute("pageTitle", "Examination Timetable Overview");
        model.addAttribute("activePage", "timetable");
        return "coordinator/timetable";
    }

    @GetMapping({"/examinations/view/{id}", "/examinations/{id}"})
    public String viewExamRedirect(@PathVariable Long id) {
        return "redirect:/admin/examinations/view/" + id;
    }

    @GetMapping("/seating")
    public String seatingRedirect(@RequestParam(required = false) Long examId) {
        if (examId != null) {
            return "redirect:/admin/seating?examId=" + examId;
        }
        return "redirect:/admin/seating";
    }

    @GetMapping({"/seating/generate", "/seating/generate/**"})
    public String coordinatorSeatingGenerateGet(@RequestParam(required = false) Long examId,
                                                @RequestParam(required = false) Long roomId) {
        return "forward:/admin/seating/generate";
    }

    @PostMapping({"/seating/generate", "/seating/generate/**"})
    public String coordinatorSeatingGeneratePost(@RequestParam(required = false) Long examId,
                                                 @RequestParam(required = false) Long roomId) {
        return "forward:/admin/seating/generate";
    }

    @GetMapping("/profile")
    public String profile(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
        model.addAttribute("totalExamsManaged", examinationRepository.count());
        model.addAttribute("totalRoomsSupervised", examRoomRepository.count());
        model.addAttribute("activeAssignments", invigilationRepository.countActiveAssignments());
        model.addAttribute("pageTitle", "Coordinator Profile");
        model.addAttribute("activePage", "profile");
        return "coordinator/profile";
    }
}
