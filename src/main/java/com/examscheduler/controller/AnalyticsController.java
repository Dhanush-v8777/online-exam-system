package com.examscheduler.controller;

import com.examscheduler.entity.Department;
import com.examscheduler.entity.Faculty;
import com.examscheduler.repository.*;
import com.examscheduler.security.CustomUserDetails;
import com.examscheduler.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller
@RequestMapping("/admin/analytics")
public class AnalyticsController {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final ExaminationRepository examinationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final InvigilationRepository invigilationRepository;
    private final NotificationService notificationService;

    public AnalyticsController(FacultyRepository facultyRepository,
                               DepartmentRepository departmentRepository,
                               ExaminationRepository examinationRepository,
                               ExamRoomRepository examRoomRepository,
                               InvigilationRepository invigilationRepository,
                               NotificationService notificationService) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.examinationRepository = examinationRepository;
        this.examRoomRepository = examRoomRepository;
        this.invigilationRepository = invigilationRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public String index(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Faculty> faculties = facultyRepository.findAll();

        // Calculate statistics
        double avgWorkload = faculties.stream().mapToInt(Faculty::getCurrentDutyCount).average().orElse(0.0);
        int maxDuties = faculties.stream().mapToInt(Faculty::getCurrentDutyCount).max().orElse(0);
        int minDuties = faculties.stream().mapToInt(Faculty::getCurrentDutyCount).min().orElse(0);

        model.addAttribute("avgWorkload", String.format("%.2f", avgWorkload));
        model.addAttribute("maxDuties", maxDuties);
        model.addAttribute("minDuties", minDuties);
        model.addAttribute("totalFaculty", faculties.size());
        model.addAttribute("faculties", faculties);

        addCommonAttributes(model, userDetails);
        model.addAttribute("pageTitle", "Analytics & Workload Distribution");
        model.addAttribute("activePage", "analytics");
        return "admin/analytics/index";
    }

    @GetMapping("/data")
    @ResponseBody
    public Map<String, Object> getChartData() {
        Map<String, Object> data = new HashMap<>();

        // 1. Faculty Workload Distribution
        List<Faculty> faculties = facultyRepository.findAll();
        List<String> facultyNames = new ArrayList<>();
        List<Integer> assignedDuties = new ArrayList<>();
        List<Integer> maxDuties = new ArrayList<>();

        for (Faculty f : faculties) {
            facultyNames.add(f.getFullName());
            assignedDuties.add(f.getCurrentDutyCount());
            maxDuties.add(f.getMaxDuties());
        }

        data.put("facultyNames", facultyNames);
        data.put("assignedDuties", assignedDuties);
        data.put("maxDuties", maxDuties);

        // 2. Department-wise Examination Distribution
        List<Department> depts = departmentRepository.findAll();
        List<String> deptNames = new ArrayList<>();
        List<Long> deptExamCounts = new ArrayList<>();

        for (Department d : depts) {
            deptNames.add(d.getDeptCode());
            deptExamCounts.add((long) d.getExaminations().size());
        }

        data.put("deptNames", deptNames);
        data.put("deptExamCounts", deptExamCounts);

        return data;
    }

    private void addCommonAttributes(Model model, CustomUserDetails userDetails) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUser());
            model.addAttribute("unreadNotifications", notificationService.getUnreadCount(userDetails.getUserId()));
        }
    }
}
