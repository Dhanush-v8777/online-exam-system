package com.examscheduler.controller;

import com.examscheduler.entity.ExamRoom;
import com.examscheduler.enums.RoomType;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.ExamRoomService;
import com.examscheduler.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/rooms")
public class ExamRoomController {

    private final ExamRoomService examRoomService;
    private final NotificationService notificationService;

    public ExamRoomController(ExamRoomService examRoomService, NotificationService notificationService) {
        this.examRoomService = examRoomService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("rooms", examRoomService.search(keyword));
        model.addAttribute("keyword", keyword);
        model.addAttribute("buildings", examRoomService.getAllBuildings());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Room Management");
        model.addAttribute("activePage", "rooms");
        return "admin/rooms/list";
    }

    @GetMapping("/new")
    public String newForm(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        model.addAttribute("room", new ExamRoom());
        model.addAttribute("roomTypes", RoomType.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Add Room");
        model.addAttribute("activePage", "rooms");
        return "admin/rooms/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("room") ExamRoom room, BindingResult bindingResult,
                       RedirectAttributes redirectAttributes, Model model,
                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roomTypes", RoomType.values());
            addCommonAttributes(model, userDetails);
            model.addAttribute("pageTitle", "Room Form");
            model.addAttribute("activePage", "rooms");
            return "admin/rooms/form";
        }
        try {
            examRoomService.save(room);
            redirectAttributes.addFlashAttribute("successMsg", "Room saved successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model,
                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        ExamRoom room = examRoomService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        model.addAttribute("room", room);
        model.addAttribute("roomTypes", RoomType.values());
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Edit Room");
        model.addAttribute("activePage", "rooms");
        return "admin/rooms/form";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            examRoomService.delete(id);
            redirectAttributes.addFlashAttribute("successMsg", "Room deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/rooms";
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
