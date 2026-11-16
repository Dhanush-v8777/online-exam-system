package com.examscheduler.controller;

import com.examscheduler.entity.Department;
import com.examscheduler.entity.ExamRoom;
import com.examscheduler.entity.Examination;
import com.examscheduler.entity.Subject;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.enums.ExamType;
import com.examscheduler.repository.ExaminationRepository;
import com.examscheduler.repository.SubjectRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.DepartmentService;
import com.examscheduler.service.ExamRoomService;
import com.examscheduler.service.ExaminationService;
import com.examscheduler.service.NotificationService;
import com.examscheduler.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/admin/examinations")
public class ExaminationController {

    private final ExaminationService examinationService;
    private final ExaminationRepository examinationRepository;
    private final DepartmentService departmentService;
    private final ExamRoomService examRoomService;
    private final SubjectRepository subjectRepository;
    private final NotificationService notificationService;
    private final StudentService studentService;
    private final com.examscheduler.service.SeatingAllocationService seatingAllocationService;
    private final com.examscheduler.repository.InvigilationRepository invigilationRepository;

    public ExaminationController(ExaminationService examinationService,
                                  ExaminationRepository examinationRepository,
                                  DepartmentService departmentService,
                                  ExamRoomService examRoomService,
                                  SubjectRepository subjectRepository,
                                  NotificationService notificationService,
                                  StudentService studentService,
                                  com.examscheduler.service.SeatingAllocationService seatingAllocationService,
                                  com.examscheduler.repository.InvigilationRepository invigilationRepository) {
        this.examinationService = examinationService;
        this.examinationRepository = examinationRepository;
        this.departmentService = departmentService;
        this.examRoomService = examRoomService;
        this.subjectRepository = subjectRepository;
        this.notificationService = notificationService;
        this.studentService = studentService;
        this.seatingAllocationService = seatingAllocationService;
        this.invigilationRepository = invigilationRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String filter,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if ("upcoming".equals(filter)) {
            model.addAttribute("examinations", examinationService.getUpcoming());
        } else if ("completed".equals(filter)) {
            model.addAttribute("examinations", examinationService.getCompleted());
        } else {
            model.addAttribute("examinations", examinationService.search(keyword));
        }
        model.addAttribute("keyword", keyword);
        model.addAttribute("filter", filter);
        model.addAttribute("examStatuses", ExamStatus.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Examination Management");
        model.addAttribute("activePage", "examinations");
        return "admin/examinations/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("examination", new Examination());
        model.addAttribute("departments", departmentService.getAll());
        model.addAttribute("subjects", List.of());
        model.addAttribute("rooms", examRoomService.getAvailable());
        model.addAttribute("examTypes", ExamType.getStandardTypes());
        model.addAttribute("examStatuses", ExamStatus.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Create Examination");
        model.addAttribute("activePage", "examinations");
        return "admin/examinations/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Examination examination, BindingResult bindingResult,
                       @RequestParam Long departmentId,
                       @RequestParam Long subjectId,
                       @RequestParam(required = false) Long examRoomId,
                       RedirectAttributes redirectAttributes, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentService.getAll());
            if (departmentId != null) {
                if (examination.getSemester() > 0) {
                    model.addAttribute("subjects", subjectRepository.findByDepartmentIdAndSemester(departmentId, examination.getSemester()));
                } else {
                    model.addAttribute("subjects", subjectRepository.findByDepartmentId(departmentId));
                }
            } else {
                model.addAttribute("subjects", List.of());
            }
            model.addAttribute("rooms", examRoomService.getAvailable());
            model.addAttribute("examTypes", ExamType.getStandardTypes());
            model.addAttribute("examStatuses", ExamStatus.values());
            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", examination.getId() != null ? "Edit Examination" : "Create Examination");
            model.addAttribute("activePage", "examinations");
            return "admin/examinations/form";
        }
        try {
            Department department = departmentService.findById(departmentId).orElse(null);
            if (department == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "Please select a valid academic department.");
                return examination.getId() != null ? "redirect:/admin/examinations/edit/" + examination.getId() : "redirect:/admin/examinations/new";
            }
            examination.setDepartment(department);

            Subject subject = subjectRepository.findById(subjectId).orElse(null);
            if (subject == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "Please select a valid curriculum subject.");
                return examination.getId() != null ? "redirect:/admin/examinations/edit/" + examination.getId() : "redirect:/admin/examinations/new";
            }

            // Backend validation: Verify subject belongs to the selected department
            if (!subject.getDepartment().getId().equals(department.getId())) {
                redirectAttributes.addFlashAttribute("errorMsg",
                    "Subject Department Mismatch: The subject '" + subject.getSubjectCode() + " - " + subject.getSubjectName() +
                    "' belongs to " + subject.getDepartment().getDeptName() + ", not " + department.getDeptName() + ".");
                return examination.getId() != null ? "redirect:/admin/examinations/edit/" + examination.getId() : "redirect:/admin/examinations/new";
            }

            // Backend validation: Verify subject matches the selected semester if applicable
            if (examination.getSemester() > 0 && subject.getSemester() > 0 && subject.getSemester() != examination.getSemester()) {
                redirectAttributes.addFlashAttribute("errorMsg",
                    "Subject Semester Mismatch: The subject '" + subject.getSubjectCode() + "' is configured for Semester " +
                    subject.getSemester() + ", but the examination is scheduled for Semester " + examination.getSemester() + ".");
                return examination.getId() != null ? "redirect:/admin/examinations/edit/" + examination.getId() : "redirect:/admin/examinations/new";
            }

            examination.setSubject(subject);

            // Backend validation: Examination date must not be in the past
            if (examination.getExamDate() != null) {
                if (examination.getExamDate().isBefore(LocalDate.now())) {
                    redirectAttributes.addFlashAttribute("errorMsg", "Past Date Error: Examination date cannot be in the past. Please select an upcoming date.");
                    return examination.getId() != null ? "redirect:/admin/examinations/edit/" + examination.getId() : "redirect:/admin/examinations/new";
                }

                // Backend validation: Global Institution-Wide One Examination Per Day restriction
                List<Examination> activeOnDate = examinationRepository.findActiveExamsOnDate(examination.getExamDate(), examination.getId());
                if (!activeOnDate.isEmpty()) {
                    Examination conflict = activeOnDate.get(0);
                    String deptName = conflict.getDepartment() != null ? conflict.getDepartment().getDeptCode() : "General";
                    redirectAttributes.addFlashAttribute("errorMsg",
                        "One Examination Per Day Restriction: The calendar date " + examination.getExamDate() +
                        " is already booked by '" + conflict.getExamName() + "' (" + deptName + "). " +
                        "Only ONE examination per day is permitted across the entire institution.");
                    return examination.getId() != null ? "redirect:/admin/examinations/edit/" + examination.getId() : "redirect:/admin/examinations/new";
                }
            }

            if (examRoomId != null) {
                ExamRoom room = examRoomService.findById(examRoomId).orElse(null);
                if (room != null) {
                    // Backend validation: Verify room is NOT occupied on the selected examination date
                    if (examination.getExamDate() != null && !examRoomService.isRoomAvailableOnDate(room.getId(), examination.getExamDate(), examination.getId())) {
                        redirectAttributes.addFlashAttribute("errorMsg",
                            "Room Occupancy Conflict: Examination hall '" + room.getRoomNumber() + " (" + room.getBuilding() +
                            ")' is already booked on " + examination.getExamDate() + ". Please choose an available room.");
                        if (examination.getId() != null) {
                            return "redirect:/admin/examinations/edit/" + examination.getId();
                        } else {
                            return "redirect:/admin/examinations/new";
                        }
                    }

                    if (departmentId != null && examination.getSemester() > 0) {
                        long count = studentService.countByDepartmentAndSemester(departmentId, examination.getSemester());
                        if (count > 0 && room.getCapacity() < count) {
                            redirectAttributes.addFlashAttribute("errorMsg",
                                "Insufficient Hall Capacity: Selected room '" + room.getRoomNumber() +
                                "' has capacity of " + room.getCapacity() + ", but this semester has " + count +
                                " registered students. Please select a larger examination hall or choose 'Unassigned (Auto-Assign Later)' to allow multi-room allocation.");
                            if (examination.getId() != null) {
                                return "redirect:/admin/examinations/edit/" + examination.getId();
                            } else {
                                return "redirect:/admin/examinations/new";
                            }
                        }
                    }
                    examination.setExamRoom(room);
                }
            } else {
                examination.setExamRoom(null);
            }
            examinationService.save(examination);
            redirectAttributes.addFlashAttribute("successMsg", "Examination saved successfully!");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMsg",
                "Institution-Wide Date Conflict: An examination is already scheduled on " + examination.getExamDate() +
                ". Only ONE examination per calendar day is permitted across the entire institution.");
            if (examination.getId() != null) {
                return "redirect:/admin/examinations/edit/" + examination.getId();
            } else {
                return "redirect:/admin/examinations/new";
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            if (examination.getId() != null) {
                return "redirect:/admin/examinations/edit/" + examination.getId();
            } else {
                return "redirect:/admin/examinations/new";
            }
        }
        return "redirect:/admin/examinations";
    }

    @GetMapping(value = "/booked-dates", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<String>> getBookedDates(@RequestParam(required = false) Long excludeId) {
        List<LocalDate> dates = examinationRepository.findBookedDates(excludeId);
        List<String> list = dates.stream().map(LocalDate::toString).toList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(list);
    }

    @GetMapping(value = "/check-date", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDateAvailability(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long excludeId) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("date", date.toString());

        if (date.isBefore(LocalDate.now())) {
            resp.put("available", false);
            resp.put("reason", "Past dates cannot be selected for examinations.");
            resp.put("message", date + " – Past Date (Cannot schedule in past)");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
        }

        List<Examination> active = examinationRepository.findActiveExamsOnDate(date, excludeId);
        if (!active.isEmpty()) {
            Examination ex = active.get(0);
            resp.put("available", false);
            resp.put("bookedExamId", ex.getId());
            resp.put("bookedExamName", ex.getExamName());
            resp.put("bookedExamDept", ex.getDepartment() != null ? ex.getDepartment().getDeptCode() : "");
            resp.put("reason", "Already booked by '" + ex.getExamName() + "'. Only ONE examination per day is permitted across the institution.");
            resp.put("message", date + " – Already Booked ('" + ex.getExamName() + "')");
        } else {
            resp.put("available", true);
            resp.put("reason", "Date is available for scheduling.");
            resp.put("message", date + " – Available");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
    }

    @GetMapping(value = "/available-rooms", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableRooms(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long excludeId) {
        List<ExamRoom> rooms = examRoomService.getAvailableRoomsOnDate(date, excludeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExamRoom r : rooms) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("roomNumber", r.getRoomNumber());
            map.put("building", r.getBuilding());
            map.put("capacity", r.getCapacity());
            map.put("fullRoomName", r.getFullRoomName());
            result.add(map);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping("/cancel/{id}")
    public String cancelExam(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            examinationService.changeStatus(id, ExamStatus.CANCELLED);
            redirectAttributes.addFlashAttribute("successMsg", "Examination cancelled successfully. The date has been released and is now available for new bookings.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to cancel examination: " + e.getMessage());
        }
        return "redirect:/admin/examinations";
    }

    @GetMapping(value = "/subjects", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getSubjects(
            @RequestParam Long departmentId,
            @RequestParam(required = false) Integer semester) {
        List<Subject> subjects;
        if (semester != null && semester > 0) {
            subjects = new ArrayList<>(subjectRepository.findByDepartmentIdAndSemester(departmentId, semester));
        } else {
            subjects = new ArrayList<>(subjectRepository.findByDepartmentId(departmentId));
        }
        subjects.sort(Comparator.comparing(Subject::getSubjectCode, Comparator.nullsLast(String::compareTo)));

        List<Map<String, Object>> result = subjects.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("subjectCode", s.getSubjectCode());
            map.put("subjectName", s.getSubjectName());
            map.put("displayName", s.getSubjectCode() + " - " + s.getSubjectName());
            map.put("semester", s.getSemester());
            map.put("credits", s.getCredits());
            return map;
        }).toList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/student-count", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStudentCount(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer semester) {
        long count = studentService.countByDepartmentAndSemester(departmentId, semester);
        String deptName = "";
        if (departmentId != null) {
            deptName = departmentService.findById(departmentId).map(d -> d.getDeptName()).orElse("");
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

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        Examination exam = examinationService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Examination not found"));
        model.addAttribute("examination", exam);
        model.addAttribute("departments", departmentService.getAll());
        if (exam.getDepartment() != null) {
            if (exam.getSemester() > 0) {
                model.addAttribute("subjects", subjectRepository.findByDepartmentIdAndSemester(exam.getDepartment().getId(), exam.getSemester()));
            } else {
                model.addAttribute("subjects", subjectRepository.findByDepartmentId(exam.getDepartment().getId()));
            }
        } else {
            model.addAttribute("subjects", List.of());
        }
        model.addAttribute("rooms", examRoomService.getAvailable());
        model.addAttribute("examTypes", ExamType.getStandardTypes());
        model.addAttribute("examStatuses", ExamStatus.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit Examination");
        model.addAttribute("activePage", "examinations");
        return "admin/examinations/form";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            examinationService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Examination deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/examinations";
    }

    @PostMapping("/status/{id}")
    public String changeStatus(@PathVariable Long id, @RequestParam ExamStatus status,
                               RedirectAttributes redirectAttributes) {
        try {
            examinationService.changeStatus(id, status);
            redirectAttributes.addFlashAttribute("successMsg", "Examination status updated to " + status + "!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/examinations";
    }

    @GetMapping("/view/{id}")
    public String viewExamination(@PathVariable Long id, Model model,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        Examination exam = examinationService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Examination not found: " + id));

        // Eligible students
        List<com.examscheduler.entity.Student> eligibleStudents = Collections.emptyList();
        if (exam.getDepartment() != null && exam.getSemester() > 0) {
            eligibleStudents = seatingAllocationService.getEligibleStudentsForExam(id);
        }

        // Seating allocations
        List<com.examscheduler.entity.SeatingAllocation> allocations = seatingAllocationService.getSeatingForExam(id);

        // Room allocations
        Set<ExamRoom> rooms = new LinkedHashSet<>();
        if (exam.getExamRoom() != null) {
            rooms.add(exam.getExamRoom());
        }
        for (com.examscheduler.entity.SeatingAllocation sa : allocations) {
            if (sa.getExamRoom() != null) {
                rooms.add(sa.getExamRoom());
            }
        }
        List<ExamRoom> assignedRooms = new ArrayList<>(rooms);

        // Stats
        int totalEligible = eligibleStudents.size();
        int totalAllocated = allocations.size();
        int remainingUnallocated = Math.max(0, totalEligible - totalAllocated);
        int totalRoomCapacity = assignedRooms.stream().mapToInt(ExamRoom::getCapacity).sum();
        int vacantDesks = Math.max(0, totalRoomCapacity - totalAllocated);

        // Invigilations
        List<com.examscheduler.entity.Invigilation> invigilations = invigilationRepository.findByExaminationId(id);

        // Reporting time is 30 minutes before exam start
        java.time.LocalTime reportingTime = exam.getStartTime() != null ? exam.getStartTime().minusMinutes(30) : null;

        model.addAttribute("examination", exam);
        model.addAttribute("eligibleStudents", eligibleStudents);
        model.addAttribute("allocations", allocations);
        model.addAttribute("assignedRooms", assignedRooms);
        model.addAttribute("invigilations", invigilations);
        model.addAttribute("totalEligible", totalEligible);
        model.addAttribute("totalAllocated", totalAllocated);
        model.addAttribute("remainingUnallocated", remainingUnallocated);
        model.addAttribute("totalRoomCapacity", totalRoomCapacity);
        model.addAttribute("vacantDesks", vacantDesks);
        model.addAttribute("reportingTime", reportingTime);

        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Examination Details - " + exam.getExamName());
        model.addAttribute("activePage", "examinations");
        return "admin/examinations/view";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
