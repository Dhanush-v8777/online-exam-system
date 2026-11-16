package com.examscheduler.service;

import com.examscheduler.entity.*;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.enums.ExamType;
import com.examscheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ExaminationService {

    private final ExaminationRepository examinationRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public ExaminationService(ExaminationRepository examinationRepository,
                              AuditService auditService,
                              NotificationService notificationService,
                              UserRepository userRepository) {
        this.examinationRepository = examinationRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Examination> getAll() {
        return examinationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Examination> findById(Long id) {
        return examinationRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Examination> getUpcoming() {
        return examinationRepository.findUpcomingExams(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Examination> getCompleted() {
        return examinationRepository.findByExamDateBeforeOrderByExamDateDescStartTimeDesc(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Examination> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAll();
        return examinationRepository.searchByKeyword(keyword.trim());
    }

    @Transactional(readOnly = true)
    public List<Examination> getByDepartmentAndSemester(Long deptId, int semester) {
        return examinationRepository.findByDepartmentAndSemester(deptId, semester);
    }

    @Transactional(readOnly = true)
    public List<Examination> getByDateRange(LocalDate from, LocalDate to) {
        return examinationRepository.findByExamDateBetween(from, to);
    }

    public Examination save(Examination examination) {
        boolean isNew = examination.getId() == null;
        // Default initial status for newly scheduled examination must always be SCHEDULED
        if (examination.getStatus() == null || (isNew && examination.getStatus() == ExamStatus.DRAFT)) {
            examination.setStatus(ExamStatus.SCHEDULED);
        }
        if (examination.getExamType() == null) {
            examination.setExamType(ExamType.END_SEMESTER_EXAMINATION);
        }
        if (examination.getExamName() == null || examination.getExamName().isBlank()) {
            examination.setExamName(examination.getExamType().getDisplayName());
        }
        // Auto-compute end time
        if (examination.getStartTime() != null && examination.getDurationMinutes() > 0) {
            examination.setEndTime(examination.getStartTime().plusMinutes(examination.getDurationMinutes()));
        }

        Examination saved = examinationRepository.save(examination);
        auditService.log(
            isNew ? AuditAction.EXAM_CREATED : AuditAction.EXAM_UPDATED,
            (isNew ? "Created" : "Updated") + " examination: " + saved.getExamName() + " on " + saved.getExamDate(),
            "Examination", saved.getId()
        );
        return saved;
    }

    public void delete(Long id) {
        Examination exam = examinationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Examination not found: " + id));
        auditService.log(AuditAction.EXAM_DELETED, "Deleted examination: " + exam.getExamName(), "Examination", id);
        examinationRepository.deleteById(id);
    }

    public Examination changeStatus(Long id, ExamStatus newStatus) {
        Examination exam = examinationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Examination not found: " + id));
        ExamStatus oldStatus = exam.getStatus();
        exam.setStatus(newStatus);
        Examination saved = examinationRepository.save(exam);

        if (newStatus == ExamStatus.PUBLISHED) {
            auditService.log(AuditAction.TIMETABLE_PUBLISHED, "Published examination: " + exam.getExamName(), "Examination", id);
            notifyAllUsers("Examination Published", "Examination '" + exam.getExamName() + "' has been published.", saved.getId());
        }

        return saved;
    }

    private void notifyAllUsers(String title, String message, Long examId) {
        userRepository.findAll().forEach(user -> {
            notificationService.createNotification(user, title, message,
                com.examscheduler.enums.NotificationType.SCHEDULE_PUBLISHED, examId);
        });
    }

    @Transactional(readOnly = true)
    public long count() {
        return examinationRepository.count();
    }

    @Transactional(readOnly = true)
    public long countUpcoming() {
        return examinationRepository.countByExamDateGreaterThanEqual(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long countByStatus(ExamStatus status) {
        return examinationRepository.countByStatus(status);
    }
}
