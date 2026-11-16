package com.examscheduler.service;

import com.examscheduler.dto.ConflictDto;
import com.examscheduler.entity.*;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConflictDetectionService {

    private final ExaminationRepository examinationRepository;
    private final InvigilationRepository invigilationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final FacultyRepository facultyRepository;
    private final ExamRegistrationRepository examRegistrationRepository;

    public ConflictDetectionService(ExaminationRepository examinationRepository,
                                  InvigilationRepository invigilationRepository,
                                  ExamRoomRepository examRoomRepository,
                                  FacultyRepository facultyRepository,
                                  ExamRegistrationRepository examRegistrationRepository) {
        this.examinationRepository = examinationRepository;
        this.invigilationRepository = invigilationRepository;
        this.examRoomRepository = examRoomRepository;
        this.facultyRepository = facultyRepository;
        this.examRegistrationRepository = examRegistrationRepository;
    }

    public List<ConflictDto> detectAllConflicts() {
        List<ConflictDto> conflicts = new ArrayList<>();
        conflicts.addAll(detectRoomConflicts());
        conflicts.addAll(detectFacultyConflicts());
        conflicts.addAll(detectSchedulingConflicts());
        for (int i = 0; i < conflicts.size(); i++) {
            conflicts.get(i).setId("conf-" + (i + 1));
        }
        return conflicts;
    }

    public List<ConflictDto> detectRoomConflicts() {
        List<ConflictDto> conflicts = new ArrayList<>();
        List<Examination> exams = examinationRepository.findAll();

        for (int i = 0; i < exams.size(); i++) {
            Examination e1 = exams.get(i);
            if (e1.getExamRoom() == null || e1.getExamDate() == null || e1.getStartTime() == null) continue;

            // Check room capacity
            if (e1.getRegisteredStudents() > e1.getExamRoom().getCapacity()) {
                ConflictDto cd = new ConflictDto(
                    ConflictDto.ConflictType.ROOM_CAPACITY_EXCEEDED,
                    "Room Capacity Exceeded",
                    "Exam '" + e1.getExamName() + "' has " + e1.getRegisteredStudents() +
                        " students registered, but Room " + e1.getExamRoom().getRoomNumber() +
                        " only holds " + e1.getExamRoom().getCapacity() + " seats.",
                    "HIGH",
                    e1.getExamDate(), e1.getStartTime(), e1.getEndTime(),
                    e1.getExamName(), "Room " + e1.getExamRoom().getRoomNumber(),
                    "Assign a larger room such as an Examination Hall or split into multiple rooms."
                );
                cd.setConflictCategory("ROOM");
                populateExam1Details(cd, e1);
                conflicts.add(cd);
            }

            // Check room availability flag
            if (!e1.getExamRoom().isAvailable()) {
                ConflictDto cd = new ConflictDto(
                    ConflictDto.ConflictType.ROOM_UNAVAILABLE,
                    "Room Inactive / Under Maintenance",
                    "Room " + e1.getExamRoom().getRoomNumber() + " is marked unavailable/maintenance for exam '" + e1.getExamName() + "'.",
                    "HIGH",
                    e1.getExamDate(), e1.getStartTime(), e1.getEndTime(),
                    e1.getExamName(), "Room " + e1.getExamRoom().getRoomNumber(),
                    "Reallocate exam to an available room."
                );
                cd.setConflictCategory("ROOM");
                populateExam1Details(cd, e1);
                conflicts.add(cd);
            }

            // Check room double bookings
            LocalTime e1End = e1.getStartTime().plusMinutes(e1.getDurationMinutes());
            for (int j = i + 1; j < exams.size(); j++) {
                Examination e2 = exams.get(j);
                if (e2.getExamRoom() == null || e2.getExamDate() == null || e2.getStartTime() == null) continue;

                if (e1.getExamRoom().getId().equals(e2.getExamRoom().getId()) &&
                    e1.getExamDate().equals(e2.getExamDate())) {
                    LocalTime e2End = e2.getStartTime().plusMinutes(e2.getDurationMinutes());
                    if (e1.getStartTime().isBefore(e2End) && e2.getStartTime().isBefore(e1End)) {
                        ConflictDto cd = new ConflictDto(
                            ConflictDto.ConflictType.ROOM_DOUBLE_BOOKING,
                            "Room Double Booking Collision",
                            "Room " + e1.getExamRoom().getRoomNumber() + " is assigned to both '" +
                                e1.getExamName() + "' and '" + e2.getExamName() + "' at overlapping times.",
                            "CRITICAL",
                            e1.getExamDate(), e1.getStartTime(), e1End,
                            e1.getExamName() + " & " + e2.getExamName(),
                            "Room " + e1.getExamRoom().getRoomNumber(),
                            "Reschedule one of the exams to another room or different time slot."
                        );
                        cd.setConflictCategory("ROOM");
                        populateExam1Details(cd, e1);
                        populateExam2Details(cd, e2);
                        conflicts.add(cd);
                    }
                }
            }
        }
        return conflicts;
    }

    public List<ConflictDto> detectFacultyConflicts() {
        List<ConflictDto> conflicts = new ArrayList<>();
        List<Faculty> allFaculty = facultyRepository.findAll();

        for (Faculty f : allFaculty) {
            List<Invigilation> duties = invigilationRepository.findByFacultyId(f.getId());

            // Check duty limit
            if (f.getCurrentDutyCount() > f.getMaxDuties()) {
                ConflictDto cd = new ConflictDto(
                    ConflictDto.ConflictType.FACULTY_EXCEEDED_DUTIES,
                    "Faculty Duty Overload",
                    "Faculty " + f.getFullName() + " has " + f.getCurrentDutyCount() +
                        " assigned duties, exceeding their maximum quota of " + f.getMaxDuties() + ".",
                    "MEDIUM",
                    LocalDate.now(), null, null,
                    "Multiple Exams", f.getFullName(),
                    "Reassign surplus duties to eligible faculty with remaining capacity."
                );
                cd.setConflictCategory("FACULTY");
                populateFacultyDetails(cd, f);
                if (duties != null && !duties.isEmpty()) {
                    populateExam1Details(cd, duties.get(0).getExamination());
                }
                conflicts.add(cd);
            }

            // Check same calendar day duties (Rule: Maximum 1 exam duty per calendar day)
            for (int i = 0; i < duties.size(); i++) {
                Examination exam1 = duties.get(i).getExamination();
                if (exam1 == null || exam1.getExamDate() == null || exam1.getStatus() == ExamStatus.CANCELLED) continue;
                LocalTime e1End = exam1.getStartTime() != null ? exam1.getStartTime().plusMinutes(exam1.getDurationMinutes()) : null;

                for (int j = i + 1; j < duties.size(); j++) {
                    Examination exam2 = duties.get(j).getExamination();
                    if (exam2 == null || exam2.getExamDate() == null || exam2.getStatus() == ExamStatus.CANCELLED) continue;

                    if (exam1.getExamDate().equals(exam2.getExamDate())) {
                        ConflictDto cd = new ConflictDto(
                            ConflictDto.ConflictType.FACULTY_OVERLAP,
                            "Faculty Multiple Duties On Same Calendar Day",
                            "Faculty " + f.getFullName() + " is assigned to multiple exams on the same calendar day (" +
                                exam1.getExamDate() + "): '" + exam1.getExamName() + "' and '" + exam2.getExamName() + "'.",
                            "CRITICAL",
                            exam1.getExamDate(), exam1.getStartTime(), e1End,
                            exam1.getExamName() + " & " + exam2.getExamName(), f.getFullName(),
                            "Reassign one of the duties to another available faculty member. Rule: Maximum 1 examination per day per faculty."
                        );
                        cd.setConflictCategory("FACULTY");
                        populateExam1Details(cd, exam1);
                        populateExam2Details(cd, exam2);
                        populateFacultyDetails(cd, f);
                        conflicts.add(cd);
                    }
                }
            }
        }
        return conflicts;
    }

    public List<ConflictDto> detectSchedulingConflicts() {
        List<ConflictDto> conflicts = new ArrayList<>();
        List<Examination> exams = examinationRepository.findAll();

        // 1. Institutional One-Exam-Per-Day rule collision check
        for (int i = 0; i < exams.size(); i++) {
            Examination e1 = exams.get(i);
            if (e1.getStatus() == ExamStatus.CANCELLED || e1.getExamDate() == null) continue;

            for (int j = i + 1; j < exams.size(); j++) {
                Examination e2 = exams.get(j);
                if (e2.getStatus() == ExamStatus.CANCELLED || e2.getExamDate() == null) continue;

                if (e1.getExamDate().equals(e2.getExamDate())) {
                    ConflictDto cd = new ConflictDto(
                        ConflictDto.ConflictType.DUPLICATE_EXAM_SCHEDULE,
                        "Institution-Wide Same-Day Examination Collision",
                        "Multiple active examinations scheduled on the same calendar day (" + e1.getExamDate() + "): '" +
                            e1.getExamName() + "' and '" + e2.getExamName() + "'.",
                        "CRITICAL",
                        e1.getExamDate(), e1.getStartTime(), e1.getEndTime(),
                        e1.getExamName() + " & " + e2.getExamName(),
                        "University-Wide",
                        "Reschedule one of the examinations to an available date. Policy: Maximum 1 examination per day institution-wide."
                    );
                    cd.setConflictCategory("SCHEDULE");
                    populateExam1Details(cd, e1);
                    populateExam2Details(cd, e2);
                    conflicts.add(cd);
                }
            }
        }

        // 2. Department-semester collision
        for (int i = 0; i < exams.size(); i++) {
            Examination e1 = exams.get(i);
            if (e1.getDepartment() == null || e1.getExamDate() == null || e1.getStartTime() == null) continue;
            LocalTime e1End = e1.getStartTime().plusMinutes(e1.getDurationMinutes());

            for (int j = i + 1; j < exams.size(); j++) {
                Examination e2 = exams.get(j);
                if (e2.getDepartment() == null || e2.getExamDate() == null || e2.getStartTime() == null) continue;

                if (e1.getDepartment().getId().equals(e2.getDepartment().getId()) &&
                    e1.getSemester() == e2.getSemester() &&
                    e1.getExamDate().equals(e2.getExamDate())) {
                    LocalTime e2End = e2.getStartTime().plusMinutes(e2.getDurationMinutes());
                    if (e1.getStartTime().isBefore(e2End) && e2.getStartTime().isBefore(e1End)) {
                        ConflictDto cd = new ConflictDto(
                            ConflictDto.ConflictType.STUDENT_OVERLAP,
                            "Cohort Timetable Collision",
                            "Same student cohort (" + e1.getDepartment().getDeptCode() + " Sem " + e1.getSemester() +
                                ") has overlapping examinations: '" + e1.getExamName() + "' and '" + e2.getExamName() + "'.",
                            "CRITICAL",
                            e1.getExamDate(), e1.getStartTime(), e1End,
                            e1.getExamName() + " & " + e2.getExamName(),
                            e1.getDepartment().getDeptCode() + " Semester " + e1.getSemester(),
                            "Separate the exams by at least one session or move one to a subsequent date."
                        );
                        cd.setConflictCategory("STUDENT");
                        populateExam1Details(cd, e1);
                        populateExam2Details(cd, e2);
                        conflicts.add(cd);
                    }
                }
            }
        }
        return conflicts;
    }

    private void populateExam1Details(ConflictDto cd, Examination e) {
        if (e == null) return;
        cd.setExamId1(e.getId());
        cd.setExam1Name(e.getExamType() != null ? e.getExamType().getDisplayName() : e.getExamName());
        cd.setExam1Course(e.getDepartment() != null ? e.getDepartment().getDeptCode() : "N/A");
        cd.setExam1Semester(e.getSemester());
        cd.setExam1Subject(e.getSubject() != null ? (e.getSubject().getSubjectName() + " (" + e.getSubject().getSubjectCode() + ")") : "N/A");
        cd.setExam1Date(e.getExamDate());
        cd.setExam1Time((e.getStartTime() != null ? e.getStartTime().toString() : "") +
            (e.getEndTime() != null ? " - " + e.getEndTime().toString() : ""));
        if (e.getExamRoom() != null) {
            cd.setExam1Room("Room " + e.getExamRoom().getRoomNumber() + " (" + e.getExamRoom().getBuilding() + ")");
            cd.setRoomId(e.getExamRoom().getId());
            cd.setRoomNumber(e.getExamRoom().getRoomNumber());
            cd.setRoomBuilding(e.getExamRoom().getBuilding());
            cd.setRoomCapacity(e.getExamRoom().getCapacity());
        } else {
            cd.setExam1Room("Unallocated");
        }
    }

    private void populateExam2Details(ConflictDto cd, Examination e) {
        if (e == null) return;
        cd.setExamId2(e.getId());
        cd.setExam2Name(e.getExamType() != null ? e.getExamType().getDisplayName() : e.getExamName());
        cd.setExam2Course(e.getDepartment() != null ? e.getDepartment().getDeptCode() : "N/A");
        cd.setExam2Semester(e.getSemester());
        cd.setExam2Subject(e.getSubject() != null ? (e.getSubject().getSubjectName() + " (" + e.getSubject().getSubjectCode() + ")") : "N/A");
        cd.setExam2Date(e.getExamDate());
        cd.setExam2Time((e.getStartTime() != null ? e.getStartTime().toString() : "") +
            (e.getEndTime() != null ? " - " + e.getEndTime().toString() : ""));
        if (e.getExamRoom() != null) {
            cd.setExam2Room("Room " + e.getExamRoom().getRoomNumber() + " (" + e.getExamRoom().getBuilding() + ")");
        } else {
            cd.setExam2Room("Unallocated");
        }
    }

    private void populateFacultyDetails(ConflictDto cd, Faculty f) {
        if (f == null) return;
        cd.setFacultyId(f.getId());
        cd.setFacultyName(f.getFullName());
        cd.setFacultyDept(f.getDepartment() != null ? f.getDepartment().getDeptCode() : "Faculty");
    }

    public boolean hasConflictsForExam(Examination candidate, Long excludeId) {
        if (candidate.getExamRoom() == null || candidate.getExamDate() == null || candidate.getStartTime() == null) {
            return false;
        }
        LocalTime candEnd = candidate.getStartTime().plusMinutes(candidate.getDurationMinutes());
        List<Examination> sameDay = examinationRepository.findByExamDate(candidate.getExamDate());

        for (Examination ex : sameDay) {
            if (excludeId != null && ex.getId().equals(excludeId)) continue;
            if (ex.getExamRoom() != null && ex.getExamRoom().getId().equals(candidate.getExamRoom().getId())) {
                LocalTime exEnd = ex.getStartTime().plusMinutes(ex.getDurationMinutes());
                if (candidate.getStartTime().isBefore(exEnd) && ex.getStartTime().isBefore(candEnd)) {
                    return true;
                }
            }
        }
        return false;
    }
}
