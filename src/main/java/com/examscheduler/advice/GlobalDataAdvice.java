package com.examscheduler.advice;

import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalDataAdvice {

    private final NotificationService notificationService;

    public GlobalDataAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute
    public void populateGlobalAttributes(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null && model != null) {
            Long userId = userDetails.getUserId();
            if (!model.containsAttribute("currentUser")) {
                model.addAttribute("currentUser", userDetails.getUser());
            }
            if (!model.containsAttribute("unreadNotifications")) {
                model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userId));
            }
            if (!model.containsAttribute("headerNotifications")) {
                model.addAttribute("headerNotifications", notificationService.getRecentByUser(userId, 6));
            }
        }
    }
}

