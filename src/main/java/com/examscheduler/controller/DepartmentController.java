package com.examscheduler.controller;

import com.examscheduler.entity.Department;
import com.examscheduler.service.DepartmentService;
import com.examscheduler.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.examscheduler.security.CustomUserDetails;

@Controller
@RequestMapping("/admin/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final NotificationService notificationService;

    public DepartmentController(DepartmentService departmentService, NotificationService notificationService) {
        this.departmentService = departmentService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("departments", departmentService.search(keyword));
        model.addAttribute("keyword", keyword);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Department Management");
        model.addAttribute("activePage", "departments");
        return "admin/departments/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("department", new Department());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Add Department");
        model.addAttribute("activePage", "departments");
        return "admin/departments/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Department department, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (bindingResult.hasErrors()) {
            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", department.getId() == null ? "Add Department" : "Edit Department");
            model.addAttribute("activePage", "departments");
            return "admin/departments/form";
        }
        try {
            departmentService.save(department);
            redirectAttributes.addFlashAttribute("successMsg", "Department saved successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        Department dept = departmentService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Department not found: " + id));
        model.addAttribute("department", dept);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit Department");
        model.addAttribute("activePage", "departments");
        return "admin/departments/form";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Department deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/departments";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
