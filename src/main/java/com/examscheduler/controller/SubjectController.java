package com.examscheduler.controller;

import com.examscheduler.entity.Subject;
import com.examscheduler.repository.SubjectRepository;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.DepartmentService;
import com.examscheduler.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/subjects")
public class SubjectController {

    private final SubjectRepository subjectRepository;
    private final DepartmentService departmentService;
    private final NotificationService notificationService;

    public SubjectController(SubjectRepository subjectRepository, DepartmentService departmentService, NotificationService notificationService) {
        this.subjectRepository = subjectRepository;
        this.departmentService = departmentService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long departmentId,
                       @RequestParam(required = false) Integer semester,
                       Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Subject> subjects;
        if (keyword != null && !keyword.isBlank()) {
            subjects = subjectRepository.searchByKeyword(keyword.trim());
        } else if (departmentId != null && semester != null) {
            subjects = subjectRepository.findByDepartmentIdAndSemester(departmentId, semester);
        } else if (departmentId != null) {
            subjects = subjectRepository.findByDepartmentId(departmentId);
        } else {
            subjects = subjectRepository.findAll();
        }
        model.addAttribute("subjects", subjects);
        model.addAttribute("departments", departmentService.getAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedDeptId", departmentId);
        model.addAttribute("selectedSemester", semester);
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Subject Management");
        model.addAttribute("activePage", "subjects");
        return "admin/subjects/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("subject", new Subject());
        model.addAttribute("departments", departmentService.getAll());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Add Subject");
        model.addAttribute("activePage", "subjects");
        return "admin/subjects/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute Subject subject, BindingResult bindingResult,
                       @RequestParam Long departmentId,
                       RedirectAttributes redirectAttributes, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentService.getAll());
            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", "Subject Form");
            model.addAttribute("activePage", "subjects");
            return "admin/subjects/form";
        }
        try {
            if (subject.getId() == null && subjectRepository.existsBySubjectCode(subject.getSubjectCode()))
                throw new IllegalArgumentException("Subject code '" + subject.getSubjectCode() + "' already exists.");
            departmentService.findById(departmentId).ifPresent(subject::setDepartment);
            subjectRepository.save(subject);
            redirectAttributes.addFlashAttribute("successMsg", "Subject saved successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/subjects";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        Subject subject = subjectRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        model.addAttribute("subject", subject);
        model.addAttribute("departments", departmentService.getAll());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit Subject");
        model.addAttribute("activePage", "subjects");
        return "admin/subjects/form";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            subjectRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMsg", "Subject deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/subjects";
    }

    @GetMapping(value = "/by-department", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getSubjectsByDepartment(
            @RequestParam Long departmentId,
            @RequestParam(required = false) Integer semester) {
        List<Subject> subjects;
        if (semester != null && semester > 0) {
            subjects = subjectRepository.findByDepartmentIdAndSemester(departmentId, semester);
        } else {
            subjects = subjectRepository.findByDepartmentId(departmentId);
        }
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

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
