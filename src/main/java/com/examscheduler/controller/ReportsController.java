package com.examscheduler.controller;

import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.NotificationService;
import com.examscheduler.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

@Controller
@RequestMapping("/admin/reports")
public class ReportsController {

    private final ReportService reportService;
    private final NotificationService notificationService;

    public ReportsController(ReportService reportService, NotificationService notificationService) {
        this.reportService = reportService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String index(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Reports & Document Generation");
        model.addAttribute("activePage", "reports");
        return "admin/reports/index";
    }

    @GetMapping("/room-utilization")
    public String roomUtilization(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        addCommonAttributes(model, userDetails);
        model.addAttribute("roomReports", reportService.getRoomUtilizationReport());
        model.addAttribute("pageTitle", "Room Utilization Report");
        model.addAttribute("activePage", "reports");
        return "admin/reports/room-utilization";
    }

    @GetMapping("/faculty-workload")
    public String facultyWorkload(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        addCommonAttributes(model, userDetails);
        model.addAttribute("facultyReports", reportService.getFacultyWorkloadReport());
        model.addAttribute("pageTitle", "Faculty Workload Report");
        model.addAttribute("activePage", "reports");
        return "admin/reports/faculty-workload";
    }

    @GetMapping("/timetable/excel")
    public ResponseEntity<byte[]> downloadTimetableExcel() throws IOException {
        byte[] bytes = reportService.generateTimetableExcel();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=smartexam-timetable.xlsx")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }

    @GetMapping("/timetable/pdf")
    public ResponseEntity<byte[]> downloadTimetablePdf() throws IOException {
        byte[] bytes = reportService.generateTimetablePdf();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=smartexam-timetable.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    @GetMapping("/faculty/excel")
    public ResponseEntity<byte[]> downloadFacultyWorkloadExcel() throws IOException {
        byte[] bytes = reportService.generateFacultyWorkloadExcel();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=smartexam-faculty-workload.xlsx")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
