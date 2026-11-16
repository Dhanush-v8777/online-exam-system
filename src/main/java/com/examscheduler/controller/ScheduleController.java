package com.examscheduler.controller;

import com.examscheduler.dto.TimetableBatchRequest;
import com.examscheduler.dto.TimetableItemDTO;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/admin/schedule")
public class ScheduleController {

    private final SchedulingService schedulingService;
    private final DepartmentService departmentService;
    private final ExaminationService examinationService;
    private final ExamRoomService examRoomService;
    private final FacultyService facultyService;
    private final NotificationService notificationService;

    public ScheduleController(SchedulingService schedulingService,
            DepartmentService departmentService,
            ExaminationService examinationService,
            ExamRoomService examRoomService,
            FacultyService facultyService,
            NotificationService notificationService) {
        this.schedulingService = schedulingService;
        this.departmentService = departmentService;
        this.examinationService = examinationService;
        this.examRoomService = examRoomService;
        this.facultyService = facultyService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String index() {
        return "redirect:/admin/schedule/generate";
    }

    @GetMapping("/generate")
    public String showGeneratePage(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("departments", departmentService.getAll());
        model.addAttribute("rooms", examRoomService.getAvailable());
        model.addAttribute("facultyList", facultyService.getEligibleFaculty());
        model.addAttribute("startDate", LocalDate.now().plusDays(7));
        model.addAttribute("endDate", LocalDate.now().plusDays(21));
        model.addAttribute("defaultDuration", 180);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Generate Complete Examination Timetable");
        model.addAttribute("activePage", "schedule");
        return "admin/schedule/generate";
    }

    @GetMapping(value = "/rooms-on-date", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, Object>>> getRoomsOnDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long excludeExamId) {
        List<com.examscheduler.entity.ExamRoom> available = examRoomService.getAvailableRoomsOnDate(date,
                excludeExamId);
        List<java.util.Map<String, Object>> resp = new java.util.ArrayList<>();
        for (com.examscheduler.entity.ExamRoom r : available) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("roomNumber", r.getRoomNumber());
            map.put("building", r.getBuilding());
            map.put("capacity", r.getCapacity());
            map.put("fullRoomName", r.getFullRoomName());
            resp.add(map);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping(value = "/faculty-on-date", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<java.util.Map<String, Object>>> getFacultyOnDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long excludeExamId) {
        List<com.examscheduler.entity.Faculty> available = facultyService.getAvailableFacultyOnDate(date,
                excludeExamId);
        List<java.util.Map<String, Object>> resp = new java.util.ArrayList<>();
        for (com.examscheduler.entity.Faculty f : available) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", f.getId());
            map.put("fullName", f.getFullName());
            map.put("departmentCode", f.getDepartment() != null ? f.getDepartment().getDeptCode() : "N/A");
            map.put("departmentId", f.getDepartment() != null ? f.getDepartment().getId() : null);
            map.put("designation", f.getDesignation() != null ? f.getDesignation() : "Faculty");
            map.put("currentDuties", f.getCurrentDutyCount());
            map.put("maxDuties", f.getMaxDuties());
            map.put("displayText",
                    f.getFullName() + " (" + (f.getDepartment() != null ? f.getDepartment().getDeptCode() : "")
                            + ") [Duties: " + f.getCurrentDutyCount() + "/" + f.getMaxDuties() + "]");
            resp.add(map);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping(value = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> previewTimetable(
            @RequestParam Long departmentId,
            @RequestParam int semester,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(defaultValue = "180") int durationMinutes,
            @RequestParam(defaultValue = "true") boolean excludeSundays) {
        try {
            List<TimetableItemDTO> preview = schedulingService.generateCompleteTimetablePreview(
                    departmentId, semester, startDate, startTime, durationMinutes, excludeSundays);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/batch-confirm")
    public String confirmBatchTimetable(
            @ModelAttribute TimetableBatchRequest batchRequest,
            RedirectAttributes redirectAttributes) {
        try {
            SchedulingService.SchedulingResult result = schedulingService.saveCompleteTimetableBatch(batchRequest);
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMsg", result.getMessage());
                redirectAttributes.addFlashAttribute("schedulingLogs", result.getExplanations());
                return "redirect:/coordinator/timetable";
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", result.getMessage());
                return "redirect:/admin/schedule/generate";
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Batch Scheduling Error (Rolled back): " + e.getMessage());
            return "redirect:/admin/schedule/generate";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Unexpected Batch Scheduling Failure: " + e.getMessage());
            return "redirect:/admin/schedule/generate";
        }
    }

    @PostMapping("/generate")
    public String runGenerate(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer semester,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "180") int durationMinutes,
            @RequestParam(defaultValue = "true") boolean excludeSundays,
            RedirectAttributes redirectAttributes, Model model,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (endDate.isBefore(startDate)) {
            redirectAttributes.addFlashAttribute("errorMsg", "End date cannot be prior to start date.");
            return "redirect:/admin/schedule/generate";
        }

        SchedulingService.SchedulingResult result = schedulingService.generateTimetable(
                departmentId, semester, startDate, endDate, durationMinutes, excludeSundays);

        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("successMsg", result.getMessage());
            if (result.getScheduledExams() != null && !result.getScheduledExams().isEmpty()) {
                Long firstExamId = result.getScheduledExams().get(0).getId();
                return "redirect:/admin/seating?examId=" + firstExamId;
            }
            return "redirect:/admin/examinations";
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", result.getMessage());
            redirectAttributes.addFlashAttribute("schedulingLogs", result.getExplanations());
            return "redirect:/admin/schedule/generate";
        }
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
