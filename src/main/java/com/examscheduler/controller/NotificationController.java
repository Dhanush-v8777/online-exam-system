package com.examscheduler.controller;

import com.examscheduler.entity.Examination;
import com.examscheduler.entity.Notification;
import com.examscheduler.enums.NotificationType;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.ExaminationService;
import com.examscheduler.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ExaminationService examinationService;

    public NotificationController(NotificationService notificationService, ExaminationService examinationService) {
        this.notificationService = notificationService;
        this.examinationService = examinationService;
    }

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";
        Long userId = userDetails.getUserId();
        model.addAttribute("notifications", notificationService.getByUser(userId));
        model.addAttribute("currentUser", userDetails.getUser());
        model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userId));
        model.addAttribute("pageTitle", "Notifications");
        model.addAttribute("activePage", "notifications");
        return "common/notifications";
    }

    @GetMapping("/open/{id}")
    public String openNotification(@PathVariable Long id,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        if (userDetails == null) return "redirect:/login";
        Long userId = userDetails.getUserId();

        Notification n = notificationService.findById(id).orElse(null);
        if (n == null || !n.getUser().getId().equals(userId)) {
            redirectAttributes.addFlashAttribute("errorMsg", "The requested notification could not be found.");
            return "redirect:/notifications";
        }

        // Mark as read immediately
        if (!n.isRead()) {
            notificationService.markAsRead(id, userId);
        }

        String role = userDetails.getUser().getRole() != null ? userDetails.getUser().getRole().name() : "";
        boolean isFaculty = role.contains("FACULTY");
        boolean isCoordinator = role.contains("COORDINATOR");
        Long examId = n.getRelatedExamId();
        Examination exam = null;

        if (examId != null) {
            exam = examinationService.findById(examId).orElse(null);
            if (exam == null) {
                redirectAttributes.addFlashAttribute("errorMsg",
                    "The referenced examination session (ID: " + examId + ") is no longer available or has been deleted.");
                if (isFaculty) {
                    return "redirect:/faculty/timetable";
                } else if (isCoordinator) {
                    return "redirect:/coordinator/timetable";
                } else {
                    return "redirect:/admin/examinations";
                }
            }
        }

        String targetUrl = resolveTargetUrl(n, exam, isFaculty, isCoordinator);
        return "redirect:" + targetUrl;
    }

    private String resolveTargetUrl(Notification n, Examination exam, boolean isFaculty, boolean isCoordinator) {
        NotificationType type = n.getNotificationType();
        String title = n.getTitle() != null ? n.getTitle().toLowerCase() : "";
        String msg = n.getMessage() != null ? n.getMessage().toLowerCase() : "";
        boolean isSeatingRelated = title.contains("seat") || msg.contains("seat") || title.contains("desk") || msg.contains("desk");

        // Seating arrangement links
        if (isSeatingRelated) {
            if (exam != null) {
                return isFaculty
                    ? "/faculty/seating?examId=" + exam.getId()
                    : "/admin/seating?examId=" + exam.getId();
            } else {
                return isFaculty ? "/faculty/dashboard" : "/admin/seating";
            }
        }

        // Invigilation duty assignments
        if (type == NotificationType.INVIGILATION_ASSIGNED || type == NotificationType.DUTY_REASSIGNED
                || title.contains("invigilat") || title.contains("duty")) {
            return isFaculty ? "/faculty/duties" : "/admin/invigilation";
        }

        // Conflict alerts
        if (type == NotificationType.CONFLICT_DETECTED || title.contains("conflict")) {
            return isFaculty ? "/faculty/timetable" : "/admin/conflicts";
        }

        // Schedule publication
        if (type == NotificationType.SCHEDULE_PUBLISHED || title.contains("timetable") || title.contains("schedule")) {
            if (isFaculty) return "/faculty/timetable";
            if (isCoordinator) return "/coordinator/timetable";
            return "/admin/examinations";
        }

        // Rescheduled or Room changed
        if (type == NotificationType.EXAM_RESCHEDULED || type == NotificationType.ROOM_CHANGED) {
            if (isFaculty) return "/faculty/timetable";
            if (exam != null) return "/admin/examinations/edit/" + exam.getId();
            return "/admin/examinations";
        }

        // General fallback with exam
        if (exam != null) {
            if (isFaculty) return "/faculty/timetable";
            return "/admin/examinations/edit/" + exam.getId();
        }

        // Role root dashboard fallback
        if (isFaculty) return "/faculty/dashboard";
        if (isCoordinator) return "/coordinator/dashboard";
        return "/admin/dashboard";
    }

    @PostMapping("/mark-read/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id,
                                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(401).body(Map.of("success", false));
        notificationService.markAsRead(id, userDetails.getUserId());
        long unread = notificationService.getUnreadCount(userDetails.getUserId());
        return ResponseEntity.ok(Map.of("success", true, "unreadCount", unread));
    }

    @PostMapping("/mark-all-read")
    public String markAllRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) notificationService.markAllAsRead(userDetails.getUserId());
        return "redirect:/notifications";
    }

    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Long>> getCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("count", 0L));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("count", notificationService.getUnreadCount(userDetails.getUserId())));
    }

    @GetMapping(value = "/recent", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getRecentNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(List.of());
        List<Notification> list = notificationService.getRecentByUser(userDetails.getUserId(), 8);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Notification n : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", n.getId());
            map.put("title", n.getTitle());
            map.put("message", n.getMessage());
            map.put("isRead", n.isRead());
            map.put("type", n.getNotificationType().name());
            map.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            map.put("openUrl", "/notifications/open/" + n.getId());
            result.add(map);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
