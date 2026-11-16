package com.examscheduler.service;

import com.examscheduler.entity.*;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.enums.NotificationType;
import com.examscheduler.enums.UserRole;
import com.examscheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvigilatorAllocationService {

    public static class AllocationResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean success;
        private int totalAssigned;
        private List<String> assignmentLogs = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<UnassignedExamDetail> unassignedDetails = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getTotalAssigned() { return totalAssigned; }
        public void setTotalAssigned(int totalAssigned) { this.totalAssigned = totalAssigned; }
        public List<String> getAssignmentLogs() { return assignmentLogs; }
        public List<String> getWarnings() { return warnings; }
        public List<UnassignedExamDetail> getUnassignedDetails() { return unassignedDetails; }
    }

    public static class UnassignedExamDetail implements Serializable {
        private static final long serialVersionUID = 1L;

        private String examName;
        private String date;
        private String reason;

        public UnassignedExamDetail(String examName, String date, String reason) {
            this.examName = examName;
            this.date = date;
            this.reason = reason;
        }

        public String getExamName() { return examName; }
        public String getDate() { return date; }
        public String getReason() { return reason; }
    }

    private final FacultyRepository facultyRepository;
    private final ExaminationRepository examinationRepository;
    private final InvigilationRepository invigilationRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public InvigilatorAllocationService(FacultyRepository facultyRepository,
                                        ExaminationRepository examinationRepository,
                                        InvigilationRepository invigilationRepository,
                                        DepartmentRepository departmentRepository,
                                        UserRepository userRepository,
                                        NotificationService notificationService,
                                        AuditService auditService) {
        this.facultyRepository = facultyRepository;
        this.examinationRepository = examinationRepository;
        this.invigilationRepository = invigilationRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Ensures all registered faculty user accounts have a corresponding active Faculty entity.
     */
    private void ensureFacultyProfiles() {
        List<User> facultyUsers = userRepository.findByRole(UserRole.FACULTY);
        List<Department> depts = departmentRepository.findAll();
        Department defaultDept = depts.isEmpty() ? null : depts.get(0);
        if (defaultDept == null || facultyUsers.isEmpty()) return;

        int deptIndex = 0;
        for (User u : facultyUsers) {
            if (!facultyRepository.existsByEmail(u.getEmail())) {
                Department assignedDept = depts.get(deptIndex % depts.size());
                deptIndex++;

                Faculty f = new Faculty();
                f.setFacultyId("FAC_" + u.getId());
                f.setFullName(u.getFullName());
                f.setEmail(u.getEmail());
                f.setDepartment(assignedDept);
                f.setUser(u);
                f.setAvailable(true);
                f.setAccountActive(true);
                f.setMaxDuties(15);
                f.setCurrentDutyCount(0);
                facultyRepository.save(f);
            }
        }
    }

    /**
     * Synchronizes live currentDutyCount with actual Invigilation records in MySQL.
     */
    private void syncFacultyWorkload(List<Faculty> faculties) {
        for (Faculty f : faculties) {
            int realDuties = (int) invigilationRepository.countByFacultyId(f.getId());
            if (f.getCurrentDutyCount() != realDuties) {
                f.setCurrentDutyCount(realDuties);
                facultyRepository.save(f);
            }
        }
    }

    /**
     * Automatic workload-balanced invigilator allocation algorithm.
     *
     * @param examId Specific exam or null for all scheduled exams
     * @param enforceExternalDept If true, prefers faculty outside the examining department
     * @param defaultInvigilatorsPerRoom Base number of faculty per room (adjusted by room/student size)
     */
    public AllocationResult allocateInvigilators(Long examId, boolean enforceExternalDept, int defaultInvigilatorsPerRoom) {
        ensureFacultyProfiles();

        AllocationResult result = new AllocationResult();

        List<Examination> targetExams;
        if (examId != null) {
            targetExams = examinationRepository.findById(examId)
                .filter(e -> e.getStatus() != ExamStatus.CANCELLED && e.getStatus() != ExamStatus.COMPLETED)
                .map(List::of)
                .orElse(Collections.emptyList());
        } else {
            targetExams = examinationRepository.findAll().stream()
                .filter(e -> e.getStatus() == ExamStatus.SCHEDULED || e.getStatus() == ExamStatus.PUBLISHED)
                .filter(e -> e.getExamDate() != null)
                .sorted(Comparator.comparing(Examination::getExamDate).thenComparing(Examination::getStartTime))
                .collect(Collectors.toList());
        }

        if (targetExams.isEmpty()) {
            result.setSuccess(false);
            result.getWarnings().add("No active scheduled examinations found for invigilator allocation.");
            return result;
        }

        List<Faculty> allFaculty = facultyRepository.findByAvailableTrueAndAccountActiveTrue();
        if (allFaculty.isEmpty()) {
            result.setSuccess(false);
            result.getWarnings().add("No active, available faculty members found in the system.");
            return result;
        }

        syncFacultyWorkload(allFaculty);

        int totalAssigned = 0;
        Set<String> batchAssignedDateKeys = new HashSet<>();

        for (Examination exam : targetExams) {
            int required = exam.getRequiredInvigilatorCount();
            if (defaultInvigilatorsPerRoom > required) {
                required = defaultInvigilatorsPerRoom;
            }

            List<Invigilation> currentAssignments = invigilationRepository.findByExaminationId(exam.getId());
            int needed = Math.max(0, required - currentAssignments.size());

            if (needed == 0) {
                continue; // Already fully staffed
            }

            Set<Long> alreadyAssignedFacultyIds = currentAssignments.stream()
                .filter(inv -> inv.getFaculty() != null)
                .map(inv -> inv.getFaculty().getId())
                .collect(Collectors.toSet());

            List<FacultyScored> candidates = rankEligibleFaculty(
                allFaculty, exam, enforceExternalDept, alreadyAssignedFacultyIds, batchAssignedDateKeys
            );

            int assignedThisExam = 0;
            for (FacultyScored candidate : candidates) {
                if (assignedThisExam >= needed) break;
                Faculty faculty = candidate.faculty;

                Invigilation inv = new Invigilation();
                inv.setFaculty(faculty);
                inv.setExamination(exam);
                inv.setAllocationScore(candidate.score);
                inv.setConfirmed(true);
                String roomInfo = exam.getExamRoom() != null ? "Room " + exam.getExamRoom().getRoomNumber() : "Hall TBD";
                inv.setAssignmentNote("Auto-allocated by Intelligent Workload Balancing Engine. Hall: " + roomInfo);

                invigilationRepository.save(inv);

                faculty.setCurrentDutyCount(faculty.getCurrentDutyCount() + 1);
                facultyRepository.save(faculty);

                batchAssignedDateKeys.add(faculty.getId() + "_" + exam.getExamDate());
                assignedThisExam++;
                totalAssigned++;

                if (faculty.getUser() != null) {
                    notificationService.createNotification(
                        faculty.getUser(),
                        "Invigilation Duty Assigned",
                        "You have been assigned to invigilate '" + exam.getExamName() + "' on " +
                            exam.getExamDate() + " at " + exam.getStartTime() +
                            (exam.getExamRoom() != null ? " in Room " + exam.getExamRoom().getRoomNumber() : "") + ".",
                        NotificationType.INVIGILATION_ASSIGNED,
                        exam.getId()
                    );
                }

                result.getAssignmentLogs().add(
                    "Assigned " + faculty.getFullName() + " (" +
                    (faculty.getDepartment() != null ? faculty.getDepartment().getDeptCode() : "Faculty") +
                    ") to '" + exam.getExamName() + "' on " + exam.getExamDate() + " (" + roomInfo + ")"
                );
            }

            if (assignedThisExam < needed) {
                String reason = "Requires " + (needed - assignedThisExam) + " more invigilator(s). " +
                    "Other faculty are either already assigned on " + exam.getExamDate() +
                    " or have reached their maximum assigned duty quotas.";
                result.getWarnings().add("Exam '" + exam.getExamName() + "': " + reason);
                result.getUnassignedDetails().add(new UnassignedExamDetail(
                    exam.getExamName(),
                    exam.getExamDate() != null ? exam.getExamDate().toString() : "TBD",
                    reason
                ));
            }
        }

        result.setTotalAssigned(totalAssigned);
        result.setSuccess(totalAssigned > 0 || result.getUnassignedDetails().isEmpty());

        if (totalAssigned > 0) {
            auditService.log(AuditAction.INVIGILATOR_ASSIGNED,
                "Intelligent allocation engine assigned " + totalAssigned + " invigilator duties.");
        }

        return result;
    }

    private List<FacultyScored> rankEligibleFaculty(List<Faculty> facultyList,
                                                    Examination exam,
                                                    boolean enforceExternalDept,
                                                    Set<Long> alreadyAssignedFacultyIds,
                                                    Set<String> batchAssignedDateKeys) {
        List<FacultyScored> scoredList = new ArrayList<>();
        LocalDate examDate = exam.getExamDate();

        for (Faculty f : facultyList) {
            // Check 1: Availability and active account
            if (!f.isAvailable() || !f.isAccountActive()) continue;

            // Check 2: Max duty limit check
            if (f.getCurrentDutyCount() >= f.getMaxDuties()) continue;

            // Check 3: Do not assign same faculty twice to the same exam
            if (alreadyAssignedFacultyIds.contains(f.getId())) continue;

            // Check 4: Strict single-duty per day constraint (DB check + current batch check)
            if (examDate != null) {
                if (batchAssignedDateKeys.contains(f.getId() + "_" + examDate)) continue;
                List<Invigilation> sameDay = invigilationRepository.findByFacultyAndDate(f.getId(), examDate);
                if (!sameDay.isEmpty()) continue;
            }

            // Check 5: Department workload preference
            boolean isOwnDept = exam.getDepartment() != null && f.getDepartment() != null &&
                f.getDepartment().getId().equals(exam.getDepartment().getId());

            // Score formula: lower score is prioritized
            double score = (f.getCurrentDutyCount() * 10.0) - (Math.max(0, f.getMaxDuties() - f.getCurrentDutyCount()) * 2.0);
            if (isOwnDept) {
                score += (enforceExternalDept ? 40.0 : 15.0);
            }

            String reason = "Duties: " + f.getCurrentDutyCount() + "/" + f.getMaxDuties() +
                (isOwnDept ? ", Same Department" : ", External Department");

            scoredList.add(new FacultyScored(f, score, reason));
        }

        // Sort ascending: lowest workload score first
        scoredList.sort(Comparator.comparingDouble(fs -> fs.score));
        return scoredList;
    }

    private static class FacultyScored {
        final Faculty faculty;
        final double score;
        final String reason;

        FacultyScored(Faculty faculty, double score, String reason) {
            this.faculty = faculty;
            this.score = score;
            this.reason = reason;
        }
    }
}
