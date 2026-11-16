package com.examscheduler.controller;

import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.AuditService;
import com.examscheduler.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditService auditService;
    private final NotificationService notificationService;

    public AuditLogController(AuditService auditService, NotificationService notificationService) {
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String keyword,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("logs", auditService.search(keyword, page, 20));
        } else {
            model.addAttribute("logs", auditService.getAll(page, 20));
        }
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentPage", page);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Audit Logs");
        model.addAttribute("activePage", "audit");
        return "admin/audit/list";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
