package com.examscheduler.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            Authentication authentication,
            Model model) {

        // Prevent already authenticated users from seeing the login page again (e.g. via back button)
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_EXAM_COORDINATOR".equals(role)) {
                return "redirect:/coordinator/dashboard";
            } else if ("ROLE_FACULTY".equals(role)) {
                return "redirect:/faculty/dashboard";
            } else {
                return "redirect:/admin/dashboard";
            }
        }

        if (error != null) model.addAttribute("errorMsg", "Invalid email or password. Please try again.");
        if (logout != null) model.addAttribute("logoutMsg", "You have been successfully logged out.");
        if (expired != null) model.addAttribute("errorMsg", "Your session has expired. Please log in again.");

        return "auth/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }

    @PostMapping("/access-denied")
    public String accessDeniedPost() {
        return "auth/access-denied";
    }

    @GetMapping("/")
    public String root(
            @RequestParam(required = false) String logout,
            Authentication authentication,
            Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String role = authentication.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_EXAM_COORDINATOR".equals(role)) {
                return "redirect:/coordinator/dashboard";
            } else if ("ROLE_FACULTY".equals(role)) {
                return "redirect:/faculty/dashboard";
            } else {
                return "redirect:/admin/dashboard";
            }
        }
        if (logout != null) {
            model.addAttribute("logoutMsg", "You have been successfully logged out of SmartExam.");
        }
        return "landing";
    }
}
