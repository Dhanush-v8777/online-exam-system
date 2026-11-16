package com.examscheduler.controller;

import com.examscheduler.model.Exam;
import com.examscheduler.repository.ExamRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ExamController {


private final ExamRepository examRepository;

public ExamController(ExamRepository examRepository) {
    this.examRepository = examRepository;
}

// Display the Add Exam form
@GetMapping("/exams/new")
public String showAddExamForm(Model model) {
    model.addAttribute("exam", new Exam());
    return "add-exam";
}

// Save an exam to MySQL
@PostMapping("/exams/save")
public String saveExam(@ModelAttribute Exam exam,
RedirectAttributes redirectAttributes) {


if (hasExamConflict(exam, null)) {
    redirectAttributes.addFlashAttribute(
            "errorMessage",
            "Exam conflict! Another exam is scheduled during this time."
    );
    return "redirect:/exams/new";
}

examRepository.save(exam);

redirectAttributes.addFlashAttribute(
        "successMessage",
        "Exam scheduled successfully!"
);

return "redirect:/exams";


}

// Display all exams
@GetMapping("/exams")
public String viewExams(
        @org.springframework.web.bind.annotation.RequestParam(
                required = false) String keyword,
        Model model) {

    java.util.List<Exam> exams = examRepository.findAll();

    if (keyword != null && !keyword.trim().isEmpty()) {
        String search = keyword.trim().toLowerCase();

        exams = exams.stream()
                .filter(exam ->
                        exam.getSubjectName() != null &&
                        exam.getSubjectName().toLowerCase().contains(search))
                .toList();
    }

    model.addAttribute("exams", exams);
    model.addAttribute("keyword", keyword);

    return "exams";
}

//Delete an exam
@PostMapping("/exams/delete/{id}")
public String deleteExam(
     @PathVariable Long id,
     RedirectAttributes redirectAttributes) {

 if (examRepository.existsById(id)) {
     examRepository.deleteById(id);

     redirectAttributes.addFlashAttribute(
             "successMessage",
             "Exam deleted successfully!");
 }

 return "redirect:/exams";
}
//Display the Edit Exam form
@GetMapping("/exams/edit/{id}")
public String showEditExamForm(
@PathVariable Long id,
Model model,
RedirectAttributes redirectAttributes) {

return examRepository.findById(id)
     .map(exam -> {
         model.addAttribute("exam", exam);
         return "edit-exam";
     })
     .orElseGet(() -> {
         redirectAttributes.addFlashAttribute(
                 "successMessage", "Exam not found.");
         return "redirect:/exams";
     });


}
//update exam
@PostMapping("/exams/update/{id}")
public String updateExam(
@PathVariable Long id,
@ModelAttribute("exam") Exam updatedExam,
RedirectAttributes redirectAttributes) {


return examRepository.findById(id)
        .map(existingExam -> {

            updatedExam.setId(id);

            if (hasExamConflict(updatedExam, id)) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Exam conflict! Another exam is scheduled during this time."
                );

                redirectAttributes.addFlashAttribute(
                        "editExamId", id
                );

                return "redirect:/exams/edit/" + id;
            }

            existingExam.setSubjectName(updatedExam.getSubjectName());
            existingExam.setExamDate(updatedExam.getExamDate());
            existingExam.setExamTime(updatedExam.getExamTime());
            existingExam.setDurationMinutes(updatedExam.getDurationMinutes());
            existingExam.setVenue(updatedExam.getVenue());

            examRepository.save(existingExam);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Exam updated successfully!"
            );

            return "redirect:/exams";
        })
        .orElseGet(() -> {
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Exam not found."
            );

            return "redirect:/exams";
        });


}




private boolean hasExamConflict(Exam newExam, Long excludeExamId) {


java.time.LocalTime newStart = newExam.getExamTime();

java.time.LocalTime newEnd =
        newStart.plusMinutes(newExam.getDurationMinutes());

java.util.List<Exam> existingExams = examRepository.findAll();

for (Exam existingExam : existingExams) {

    // Skip the same exam when updating it
    if (excludeExamId != null &&
            existingExam.getId().equals(excludeExamId)) {
        continue;
    }

    // Only compare exams scheduled on the same date
    if (!existingExam.getExamDate().equals(newExam.getExamDate())) {
        continue;
    }

    java.time.LocalTime existingStart = existingExam.getExamTime();

    java.time.LocalTime existingEnd =
            existingStart.plusMinutes(existingExam.getDurationMinutes());

    // Check whether the exam timings overlap
    if (newStart.isBefore(existingEnd) &&
            existingStart.isBefore(newEnd)) {
        return true;
    }
}

return false;


}

}
