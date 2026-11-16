package com.examscheduler.controller;

import com.examscheduler.dto.ConflictDto;
import com.examscheduler.entity.*;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.*;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.ConflictDetectionService;
import com.examscheduler.service.ExamRoomService;
import com.examscheduler.service.FacultyService;
import com.examscheduler.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/conflicts")
public class ConflictController {

    private final ConflictDetectionService conflictDetectionService;
    private final NotificationService notificationService;
    private final ExaminationRepository examinationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final FacultyRepository facultyRepository;
    private final InvigilationRepository invigilationRepository;
    private final ExamRoomService examRoomService;
    private final FacultyService facultyService;
    private final AuditService auditService;

    public ConflictController(ConflictDetectionService conflictDetectionService,
                              NotificationService notificationService,
                              ExaminationRepository examinationRepository,
                              ExamRoomRepository examRoomRepository,
                              FacultyRepository facultyRepository,
                              InvigilationRepository invigilationRepository,
                              ExamRoomService examRoomService,
                              FacultyService facultyService,
                              AuditService auditService) {
        this.conflictDetectionService = conflictDetectionService;
        this.notificationService = notificationService;
        this.examinationRepository = examinationRepository;
        this.examRoomRepository = examRoomRepository;
        this.facultyRepository = facultyRepository;
        this.invigilationRepository = invigilationRepository;
        this.examRoomService = examRoomService;
        this.facultyService = facultyService;
        this.auditService = auditService;
    }

    @GetMapping
    public String viewConflicts(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ConflictDto> conflicts = conflictDetectionService.detectAllConflicts();
        model.addAttribute("conflicts", conflicts);
        model.addAttribute("totalConflicts", conflicts.size());
        model.addAttribute("allRooms", examRoomRepository.findAll());
        model.addAttribute("allFaculty", facultyRepository.findEligibleFaculty());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Conflict Detection & Interactive Resolution");
        model.addAttribute("activePage", "conflicts");
        return "admin/conflicts/list";
    }

    /**
     * Interactive Resolution 1: Reassign Room
     */
    @PostMapping("/resolve/reassign-room")
    public String resolveReassignRoom(@RequestParam Long examId,
                                      @RequestParam Long newRoomId,
                                      RedirectAttributes redirectAttributes) {
        Optional<Examination> examOpt = examinationRepository.findById(examId);
        Optional<ExamRoom> roomOpt = examRoomRepository.findById(newRoomId);

        if (examOpt.isEmpty() || roomOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Invalid examination or room selection for resolution.");
            return "redirect:/admin/conflicts";
        }

        Examination exam = examOpt.get();
        ExamRoom newRoom = roomOpt.get();

        // Validate availability on that date
        if (!examRoomService.isRoomAvailableOnDate(newRoom.getId(), exam.getExamDate(), exam.getId())) {
            redirectAttributes.addFlashAttribute("errorMsg", 
                "Conflict Resolution Blocked: Room " + newRoom.getRoomNumber() + " is already occupied on " + exam.getExamDate() + ".");
            return "redirect:/admin/conflicts";
        }

        if (exam.getRegisteredStudents() > newRoom.getCapacity()) {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Capacity Warning: Room " + newRoom.getRoomNumber() + " has " + newRoom.getCapacity() + " seats, which is less than " + exam.getRegisteredStudents() + " students.");
            return "redirect:/admin/conflicts";
        }

        String oldRoomNumber = exam.getExamRoom() != null ? exam.getExamRoom().getRoomNumber() : "None";
        exam.setExamRoom(newRoom);
        examinationRepository.save(exam);

        auditService.log(AuditAction.EXAM_UPDATED,
            "Resolved room conflict: Changed room of exam '" + exam.getExamName() + "' from " + oldRoomNumber + " to " + newRoom.getRoomNumber(),
            "Examination", exam.getId());

        redirectAttributes.addFlashAttribute("successMsg", 
            "Room conflict resolved! Reallocated examination '" + exam.getExamName() + "' to Room " + newRoom.getRoomNumber() + ".");
        return "redirect:/admin/conflicts";
    }

    /**
     * Interactive Resolution 2: Reschedule Exam Date & Time
     */
    @PostMapping("/resolve/reschedule")
    public String resolveReschedule(@RequestParam Long examId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newExamDate,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime newStartTime,
                                    RedirectAttributes redirectAttributes) {
        Optional<Examination> examOpt = examinationRepository.findById(examId);
        if (examOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Examination not found for rescheduling.");
            return "redirect:/admin/conflicts";
        }

        Examination exam = examOpt.get();

        // Enforce institution-wide single exam per day rule
        List<Examination> sameDay = examinationRepository.findByExamDate(newExamDate);
        boolean occupied = sameDay.stream().anyMatch(e -> !e.getId().equals(exam.getId()) && e.getStatus() != com.examscheduler.enums.ExamStatus.CANCELLED);
        if (occupied) {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Policy Violation: An active examination is already scheduled on " + newExamDate + ". Institution allows only one exam per day.");
            return "redirect:/admin/conflicts";
        }

        LocalDate oldDate = exam.getExamDate();
        exam.setExamDate(newExamDate);
        exam.setStartTime(newStartTime);
        if (exam.getDurationMinutes() > 0) {
            exam.setEndTime(newStartTime.plusMinutes(exam.getDurationMinutes()));
        }
        examinationRepository.save(exam);

        auditService.log(AuditAction.EXAM_UPDATED,
            "Resolved scheduling conflict: Rescheduled exam '" + exam.getExamName() + "' from " + oldDate + " to " + newExamDate,
            "Examination", exam.getId());

        redirectAttributes.addFlashAttribute("successMsg",
            "Scheduling conflict resolved! Rescheduled '" + exam.getExamName() + "' to " + newExamDate + " at " + newStartTime + ".");
        return "redirect:/admin/conflicts";
    }

    /**
     * Interactive Resolution 3: Reassign Faculty Invigilator
     */
    @PostMapping("/resolve/reassign-faculty")
    public String resolveReassignFaculty(@RequestParam Long examId,
                                         @RequestParam(required = false) Long oldFacultyId,
                                         @RequestParam Long newFacultyId,
                                         RedirectAttributes redirectAttributes) {
        Optional<Examination> examOpt = examinationRepository.findById(examId);
        Optional<Faculty> newFacultyOpt = facultyRepository.findById(newFacultyId);

        if (examOpt.isEmpty() || newFacultyOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Invalid exam or replacement faculty selection.");
            return "redirect:/admin/conflicts";
        }

        Examination exam = examOpt.get();
        Faculty newFaculty = newFacultyOpt.get();

        // Validate availability on exam date
        List<Invigilation> sameDay = invigilationRepository.findByFacultyAndDate(newFaculty.getId(), exam.getExamDate());
        boolean hasConflict = sameDay.stream().anyMatch(inv -> !inv.getExamination().getId().equals(exam.getId()));
        if (hasConflict) {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Faculty Conflict: " + newFaculty.getFullName() + " is already assigned to another examination on " + exam.getExamDate() + ".");
            return "redirect:/admin/conflicts";
        }

        if (newFaculty.getCurrentDutyCount() >= newFaculty.getMaxDuties()) {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Duty Quota Exceeded: " + newFaculty.getFullName() + " has reached max duty count (" + newFaculty.getMaxDuties() + ").");
            return "redirect:/admin/conflicts";
        }

        // Remove old faculty invigilation if provided
        if (oldFacultyId != null) {
            Optional<Invigilation> oldInvOpt = invigilationRepository.findByFacultyIdAndExaminationId(oldFacultyId, exam.getId());
            if (oldInvOpt.isPresent()) {
                invigilationRepository.delete(oldInvOpt.get());
                facultyService.updateDutyCount(oldFacultyId, -1);
            }
        }

        // Assign new faculty
        Invigilation newInv = new Invigilation();
        newInv.setExamination(exam);
        newInv.setFaculty(newFaculty);
        newInv.setAssignmentNote("Assigned via Conflict Resolution Engine");
        newInv.setConfirmed(true);
        invigilationRepository.save(newInv);
        facultyService.updateDutyCount(newFaculty.getId(), 1);

        auditService.log(AuditAction.INVIGILATOR_ASSIGNED,
            "Resolved faculty conflict: Assigned " + newFaculty.getFullName() + " to " + exam.getExamName(),
            "Invigilation", newInv.getId());

        redirectAttributes.addFlashAttribute("successMsg",
            "Faculty conflict resolved! Reassigned " + newFaculty.getFullName() + " to '" + exam.getExamName() + "'.");
        return "redirect:/admin/conflicts";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
