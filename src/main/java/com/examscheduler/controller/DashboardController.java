package com.examscheduler.controller;

import com.examscheduler.model.Exam;
import com.examscheduler.repository.ExamRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    private final ExamRepository examRepository;

    public DashboardController(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    @GetMapping("/legacy-dashboard")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        long totalExams = examRepository.count();

        List<Exam> upcomingExams =
                examRepository.findByExamDateGreaterThanEqual(today)
                        .stream()
                        .sorted((a, b) -> {
                            int dateCompare = a.getExamDate().compareTo(b.getExamDate());
                            if (dateCompare != 0) return dateCompare;
                            return a.getExamTime().compareTo(b.getExamTime());
                        })
                        .limit(5)
                        .toList();

        long todayExams = examRepository.findByExamDate(today).size();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime next24Hours = now.plusHours(24);
        java.time.LocalDate sevenDaysLater = today.plusDays(7);

        java.util.List<Exam> allExams = examRepository.findAll();

        long completedExams = allExams.stream()
                .filter(exam -> exam.getExamDate() != null && exam.getExamDate().isBefore(today))
                .count();

        long totalUpcomingExams = allExams.stream()
                .filter(exam -> exam.getExamDate() != null && !exam.getExamDate().isBefore(today))
                .count();

        java.util.List<Exam> urgentExams = allExams.stream()
                .filter(exam -> exam.getExamDate() != null && exam.getExamTime() != null)
                .filter(exam -> {
                    java.time.LocalDateTime examDateTime = java.time.LocalDateTime.of(exam.getExamDate(), exam.getExamTime());
                    return !examDateTime.isBefore(now) && !examDateTime.isAfter(next24Hours);
                })
                .toList();

        java.util.List<Exam> nextSevenDaysExams = allExams.stream()
                .filter(exam -> exam.getExamDate() != null && !exam.getExamDate().isBefore(today) && !exam.getExamDate().isAfter(sevenDaysLater))
                .sorted((a, b) -> {
                    int dateCompare = a.getExamDate().compareTo(b.getExamDate());
                    if (dateCompare != 0) return dateCompare;
                    return a.getExamTime().compareTo(b.getExamTime());
                })
                .toList();

        model.addAttribute("totalExams", totalExams);
        model.addAttribute("upcomingExams", upcomingExams.size());
        model.addAttribute("todayExams", todayExams);
        model.addAttribute("upcomingExamList", upcomingExams);
        model.addAttribute("today", today);
        model.addAttribute("completedExams", completedExams);
        model.addAttribute("totalUpcomingExams", totalUpcomingExams);
        model.addAttribute("urgentExams", urgentExams);
        model.addAttribute("nextSevenDaysExams", nextSevenDaysExams);

        return "dashboard";
    }
}
