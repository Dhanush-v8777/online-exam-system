package com.examscheduler.controller;

import com.examscheduler.dto.SeatingHallDTO;
import com.examscheduler.entity.ExamRoom;
import com.examscheduler.entity.Examination;
import com.examscheduler.entity.SeatingAllocation;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.ExamRoomRepository;
import com.examscheduler.repository.ExaminationRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.NotificationService;
import com.examscheduler.service.SeatingAllocationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping({"/admin/seating", "/coordinator/seating"})
public class SeatingController {

    private final SeatingAllocationService seatingAllocationService;
    private final ExaminationRepository examinationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public SeatingController(SeatingAllocationService seatingAllocationService,
                             ExaminationRepository examinationRepository,
                             ExamRoomRepository examRoomRepository,
                             NotificationService notificationService,
                             AuditService auditService) {
        this.seatingAllocationService = seatingAllocationService;
        this.examinationRepository = examinationRepository;
        this.examRoomRepository = examRoomRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @GetMapping({"", "/"})
    public String list(@RequestParam(required = false) Long examId,
                       @RequestParam(required = false) Long roomId,
                       @RequestParam(required = false) List<Long> roomIds,
                       @RequestParam(required = false) Long viewRoomId,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        // Load all examinations and examination rooms for selection
        List<Examination> allExams = examinationRepository.findAll();
        List<ExamRoom> allRooms = examRoomRepository.findAll();
        model.addAttribute("examinations", allExams != null ? allExams : Collections.emptyList());
        model.addAttribute("rooms", allRooms != null ? allRooms : Collections.emptyList());

        List<SeatingAllocation> allocations = Collections.emptyList();
        Examination selectedExam = null;
        ExamRoom viewRoom = null;
        List<ExamRoom> allocatedRooms = new ArrayList<>();
        int totalEligibleStudents = 0;

        if (examId == null && allExams != null && !allExams.isEmpty()) {
            examId = allExams.get(0).getId();
        }

        if (examId != null) {
            model.addAttribute("selectedExamId", examId);
            selectedExam = examinationRepository.findById(examId).orElse(null);
            model.addAttribute("selectedExam", selectedExam);

            if (selectedExam != null) {
                totalEligibleStudents = seatingAllocationService.getEligibleStudentsForExam(examId).size();
                allocations = seatingAllocationService.getSeatingForExam(examId);

                // Collect unique rooms allocated for this exam
                if (!allocations.isEmpty()) {
                    allocatedRooms = allocations.stream()
                        .map(SeatingAllocation::getExamRoom)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(Comparator.comparing(ExamRoom::getRoomNumber))
                        .collect(Collectors.toList());
                }

                // Determine which room to view in the hall diagram:
                // 1. Explicit viewRoomId parameter
                if (viewRoomId != null) {
                    viewRoom = examRoomRepository.findById(viewRoomId).orElse(null);
                }
                // 2. First allocated room
                if (viewRoom == null && !allocatedRooms.isEmpty()) {
                    viewRoom = allocatedRooms.get(0);
                }
                // 3. User-selected roomId from dropdown
                if (viewRoom == null && roomId != null) {
                    viewRoom = examRoomRepository.findById(roomId).orElse(null);
                }
                // 4. Examination's pre-assigned room
                if (viewRoom == null) {
                    viewRoom = selectedExam.getExamRoom();
                }

                model.addAttribute("selectedRoom", viewRoom);
                model.addAttribute("selectedRoomId", viewRoom != null ? viewRoom.getId() : null);
                model.addAttribute("viewRoomId", viewRoom != null ? viewRoom.getId() : null);
            } else {
                model.addAttribute("errorMsg", "The selected examination (ID: " + examId + ") was not found.");
            }
        } else {
            model.addAttribute("selectedExam", null);
            model.addAttribute("selectedExamId", null);
            model.addAttribute("selectedRoom", null);
            model.addAttribute("selectedRoomId", roomId);
        }

        // Build the 4-column visual examination hall model (2 Left, Central Aisle, 2 Right)
        SeatingHallDTO hall = seatingAllocationService.buildSeatingHall(allocations, viewRoom, totalEligibleStudents, allocatedRooms);
        model.addAttribute("hall", hall);
        model.addAttribute("seatingRows", hall.getRows());

        // Pass 'allocations' and 'seatingList' for full backwards compatibility
        model.addAttribute("allocations", allocations != null ? allocations : Collections.emptyList());
        model.addAttribute("seatingList", allocations != null ? allocations : Collections.emptyList());
        model.addAttribute("allocatedRooms", allocatedRooms);

        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Visual Examination Hall Seating Arrangement");
        model.addAttribute("activePage", "seating");
        return "admin/seating/list";
    }

    /**
     * Primary POST handler for Generate Seating Arrangement.
     * Supports both single roomId and multi-room roomIds.
     */
    @PostMapping(value = {"/generate", "/generate/"})
    public String generate(@RequestParam(required = false) Long examId,
                           @RequestParam(required = false) Long roomId,
                           @RequestParam(required = false) List<Long> roomIds,
                           @RequestParam(required = false) Integer studentLimit,
                           RedirectAttributes redirectAttributes) {
        if (examId == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please select an examination first.");
            return "redirect:/admin/seating";
        }
        List<Long> effectiveRoomIds = resolveRoomIds(roomId, roomIds);
        return doGenerate(examId, effectiveRoomIds, studentLimit, redirectAttributes);
    }

    /**
     * Path variable POST handler for /admin/seating/generate/{examId}
     */
    @PostMapping(value = {"/generate/{examId}", "/generate/{examId}/"})
    public String generateWithPath(@PathVariable Long examId,
                                   @RequestParam(required = false) Long roomId,
                                   @RequestParam(required = false) List<Long> roomIds,
                                   @RequestParam(required = false) Integer studentLimit,
                                   RedirectAttributes redirectAttributes) {
        List<Long> effectiveRoomIds = resolveRoomIds(roomId, roomIds);
        return doGenerate(examId, effectiveRoomIds, studentLimit, redirectAttributes);
    }

    /**
     * Safe GET fallback for /admin/seating/generate
     */
    @GetMapping(value = {"/generate", "/generate/"})
    public String generateGet(@RequestParam(required = false) Long examId,
                              @RequestParam(required = false) Long roomId) {
        if (examId != null) {
            return "redirect:/admin/seating?examId=" + examId + (roomId != null ? "&roomId=" + roomId : "");
        }
        return "redirect:/admin/seating";
    }

    /**
     * POST handler for Save Seating Arrangement
     */
    @PostMapping(value = {"/save", "/save/"})
    public String saveSeating(@RequestParam(required = false) Long examId,
                              @RequestParam(required = false) Long roomId,
                              @RequestParam(required = false) List<Long> roomIds,
                              RedirectAttributes redirectAttributes) {
        if (examId == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Please select an examination before saving.");
            return "redirect:/admin/seating";
        }

        Examination exam = examinationRepository.findById(examId).orElse(null);
        if (exam == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "Examination not found: " + examId);
            return "redirect:/admin/seating";
        }

        List<Long> effectiveRoomIds = resolveRoomIds(roomId, roomIds);

        // If rooms are provided, link primary room to the exam
        if (!effectiveRoomIds.isEmpty()) {
            ExamRoom room = examRoomRepository.findById(effectiveRoomIds.get(0)).orElse(null);
            if (room != null) {
                exam.setExamRoom(room);
                examinationRepository.save(exam);
            }
        }

        List<SeatingAllocation> existing = seatingAllocationService.getSeatingForExam(examId);
        if (existing.isEmpty()) {
            // Auto generate if not generated yet
            return doGenerate(examId, effectiveRoomIds, null, redirectAttributes);
        }

        exam.setRegisteredStudents(existing.size());
        examinationRepository.save(exam);

        auditService.log(AuditAction.SEATING_ALLOCATED,
            "Saved and locked seating plan for " + existing.size() + " students in " + exam.getExamName(),
            "Examination", examId);

        redirectAttributes.addFlashAttribute("successMsg",
            "Seating arrangement for '" + exam.getExamName() + "' (" + existing.size() + " students) saved and locked successfully!");

        String roomParam = !effectiveRoomIds.isEmpty() ? "&roomId=" + effectiveRoomIds.get(0) : "";
        return "redirect:/admin/seating?examId=" + examId + roomParam;
    }

    @GetMapping(value = {"/save", "/save/"})
    public String saveGet(@RequestParam(required = false) Long examId,
                          @RequestParam(required = false) Long roomId) {
        if (examId != null) {
            return "redirect:/admin/seating?examId=" + examId + (roomId != null ? "&roomId=" + roomId : "");
        }
        return "redirect:/admin/seating";
    }

    private List<Long> resolveRoomIds(Long roomId, List<Long> roomIds) {
        List<Long> effective = new ArrayList<>();
        if (roomIds != null && !roomIds.isEmpty()) {
            effective.addAll(roomIds);
        } else if (roomId != null) {
            effective.add(roomId);
        }
        return effective;
    }

    private String doGenerate(Long examId, List<Long> roomIds, Integer studentLimit, RedirectAttributes redirectAttributes) {
        try {
            Examination exam = examinationRepository.findById(examId).orElse(null);
            if (exam == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "Examination not found: " + examId);
                return "redirect:/admin/examinations";
            }
            if ((roomIds == null || roomIds.isEmpty()) && exam.getExamRoom() != null) {
                roomIds = new ArrayList<>(Collections.singletonList(exam.getExamRoom().getId()));
            }

            SeatingAllocationService.SeatingResult result = seatingAllocationService.generateSeatingForExam(examId, roomIds, studentLimit);
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMsg", result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("errorMsg", result.getMessage());
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMsg", "Database persistence failed: " + ex.getMessage());
        }
        String roomParam = (roomIds != null && !roomIds.isEmpty()) ? "&roomId=" + roomIds.get(0) : "";
        return "redirect:/admin/seating?examId=" + examId + roomParam;
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
