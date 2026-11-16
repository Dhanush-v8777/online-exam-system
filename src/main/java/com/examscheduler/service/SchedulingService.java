package com.examscheduler.service;

import com.examscheduler.dto.TimetableBatchRequest;
import com.examscheduler.dto.TimetableItemDTO;
import com.examscheduler.entity.*;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.ExamStatus;
import com.examscheduler.enums.ExamType;
import com.examscheduler.enums.NotificationType;
import com.examscheduler.enums.UserRole;
import com.examscheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchedulingService {

    public static class ScheduleSlot {
        private final LocalDate date;
        private final LocalTime startTime;
        private final int durationMinutes;
        private final String slotLabel; // Morning / Afternoon

        public ScheduleSlot(LocalDate date, LocalTime startTime, int durationMinutes, String slotLabel) {
            this.date = date;
            this.startTime = startTime;
            this.durationMinutes = durationMinutes;
            this.slotLabel = slotLabel;
        }

        public LocalDate getDate() { return date; }
        public LocalTime getStartTime() { return startTime; }
        public int getDurationMinutes() { return durationMinutes; }
        public String getSlotLabel() { return slotLabel; }
    }

    public static class SchedulingResult {
        private boolean success;
        private String message;
        private int totalScheduled;
        private List<String> explanations = new ArrayList<>();
        private List<Examination> scheduledExams = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getTotalScheduled() { return totalScheduled; }
        public void setTotalScheduled(int totalScheduled) { this.totalScheduled = totalScheduled; }
        public List<String> getExplanations() { return explanations; }
        public List<Examination> getScheduledExams() { return scheduledExams; }
        public void setScheduledExams(List<Examination> scheduledExams) { this.scheduledExams = scheduledExams; }
    }

    private final ExaminationRepository examinationRepository;
    private final ExamRoomRepository examRoomRepository;
    private final ExamRoomService examRoomService;
    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;
    private final StudentRepository studentRepository;
    private final SeatingAllocationRepository seatingAllocationRepository;
    private final SeatingAllocationService seatingAllocationService;
    private final InvigilatorAllocationService invigilatorAllocationService;
    private final FacultyService facultyService;
    private final FacultyRepository facultyRepository;
    private final InvigilationRepository invigilationRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public SchedulingService(ExaminationRepository examinationRepository,
                             ExamRoomRepository examRoomRepository,
                             ExamRoomService examRoomService,
                             SubjectRepository subjectRepository,
                             DepartmentRepository departmentRepository,
                             StudentRepository studentRepository,
                             SeatingAllocationRepository seatingAllocationRepository,
                             SeatingAllocationService seatingAllocationService,
                             InvigilatorAllocationService invigilatorAllocationService,
                             FacultyService facultyService,
                             FacultyRepository facultyRepository,
                             InvigilationRepository invigilationRepository,
                             NotificationService notificationService,
                             AuditService auditService,
                             UserRepository userRepository) {
        this.examinationRepository = examinationRepository;
        this.examRoomRepository = examRoomRepository;
        this.examRoomService = examRoomService;
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
        this.studentRepository = studentRepository;
        this.seatingAllocationRepository = seatingAllocationRepository;
        this.seatingAllocationService = seatingAllocationService;
        this.invigilatorAllocationService = invigilatorAllocationService;
        this.facultyService = facultyService;
        this.facultyRepository = facultyRepository;
        this.invigilationRepository = invigilationRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    /**
     * Constraint-based automated timetable generator with full seating arrangement integration.
     */
    public SchedulingResult generateTimetable(Long departmentId, Integer semester,
                                             LocalDate startDate, LocalDate endDate,
                                             int durationMinutes, boolean excludeSundays) {
        SchedulingResult result = new SchedulingResult();

        // 1. Fetch eligible subjects
        List<Subject> subjects;
        if (departmentId != null && semester != null) {
            subjects = subjectRepository.findByDepartmentIdAndSemester(departmentId, semester);
        } else if (departmentId != null) {
            subjects = subjectRepository.findByDepartmentId(departmentId);
        } else {
            subjects = subjectRepository.findAll();
        }

        if (subjects.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No subjects found for the selected department/semester criteria.");
            return result;
        }

        // 2. Fetch available rooms sorted by capacity descending (largest first)
        List<ExamRoom> availableRooms = new ArrayList<>(examRoomRepository.findByAvailableTrue());
        availableRooms.sort(Comparator.comparingInt(ExamRoom::getCapacity).reversed());
        if (availableRooms.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No active exam rooms available for scheduling.");
            return result;
        }

        // 3. Generate candidate slots
        List<ScheduleSlot> candidateSlots = buildCandidateSlots(startDate, endDate, durationMinutes, excludeSundays);
        if (candidateSlots.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No valid time slots found in the specified date range.");
            return result;
        }

        // Map to track cohort exam dates to enforce gap constraint
        Map<String, LocalDate> cohortLastExamDate = new HashMap<>();

        // Room-slot occupancy matrix: "roomId_yyyy-MM-dd_HH:mm" -> Boolean
        Set<String> roomOccupancy = new HashSet<>();

        // Prepopulate occupancy from existing scheduled exams
        List<Examination> existingExams = examinationRepository.findAll();
        for (Examination ex : existingExams) {
            if (ex.getExamRoom() != null && ex.getExamDate() != null && ex.getStartTime() != null) {
                String key = ex.getExamRoom().getId() + "_" + ex.getExamDate() + "_" + ex.getStartTime();
                roomOccupancy.add(key);
            }
            if (ex.getDepartment() != null && ex.getExamDate() != null) {
                String cohortKey = ex.getDepartment().getId() + "_" + ex.getSemester();
                LocalDate prev = cohortLastExamDate.get(cohortKey);
                if (prev == null || ex.getExamDate().isAfter(prev)) {
                    cohortLastExamDate.put(cohortKey, ex.getExamDate());
                }
            }
        }

        List<Examination> planned = new ArrayList<>();
        Map<Long, List<ExamRoom>> examRoomsMap = new HashMap<>();
        int slotIndex = 0;

        for (Subject subject : subjects) {
            // Check if active scheduled exam already exists for this subject to prevent unintentional overwriting
            Examination existingExam = existingExams.stream()
                .filter(e -> e.getSubject() != null && e.getSubject().getId().equals(subject.getId())
                          && e.getStatus() != ExamStatus.COMPLETED)
                .findFirst().orElse(null);

            // Fetch actual eligible students from MySQL using exact Course + Semester criteria
            List<Student> eligibleStudents = studentRepository.findByDepartmentIdAndSemesterOrderByRegisterNumberAsc(
                subject.getDepartment().getId(), subject.getSemester());
            int studentCount = eligibleStudents.size();

            // If an examination is already scheduled with date, time, and room, preserve it and ensure seating is generated
            if (existingExam != null && existingExam.getExamDate() != null && existingExam.getStartTime() != null && existingExam.getExamRoom() != null) {
                planned.add(existingExam);
                examRoomsMap.put(existingExam.getId(), Collections.singletonList(existingExam.getExamRoom()));
                result.getExplanations().add(
                    "Preserved existing schedule for '" + subject.getSubjectCode() + " - " + subject.getSubjectName() +
                    "' on " + existingExam.getExamDate() + " at " + existingExam.getStartTime() +
                    " in Room " + existingExam.getExamRoom().getRoomNumber()
                );
                continue;
            }

            String cohortKey = subject.getDepartment().getId() + "_" + subject.getSemester();
            LocalDate lastDate = cohortLastExamDate.get(cohortKey);

            boolean scheduled = false;

            // Search for an eligible slot satisfying hard & soft constraints
            for (int s = 0; s < candidateSlots.size(); s++) {
                int testSlotIdx = (slotIndex + s) % candidateSlots.size();
                ScheduleSlot slot = candidateSlots.get(testSlotIdx);

                // Soft constraint: At least 1 day gap between exams for the same cohort
                if (lastDate != null && !slot.getDate().isAfter(lastDate.plusDays(1))) {
                    continue;
                }

                // Hard constraint: Find a room (or multiple rooms) with sufficient capacity and no overlap
                List<ExamRoom> chosenRooms = new ArrayList<>();

                // Option 1: Single room with capacity >= studentCount
                for (ExamRoom room : availableRooms) {
                    if (room.getCapacity() >= studentCount) {
                        String occKey = room.getId() + "_" + slot.getDate() + "_" + slot.getStartTime();
                        if (!roomOccupancy.contains(occKey)) {
                            chosenRooms.add(room);
                            break;
                        }
                    }
                }

                // Option 2: Multi-room allocation when studentCount exceeds any single available room
                if (chosenRooms.isEmpty() && studentCount > 0) {
                    int combinedCap = 0;
                    List<ExamRoom> candidateRooms = new ArrayList<>();
                    for (ExamRoom room : availableRooms) {
                        String occKey = room.getId() + "_" + slot.getDate() + "_" + slot.getStartTime();
                        if (!roomOccupancy.contains(occKey)) {
                            candidateRooms.add(room);
                            combinedCap += room.getCapacity();
                            if (combinedCap >= studentCount) {
                                chosenRooms.addAll(candidateRooms);
                                break;
                            }
                        }
                    }
                }

                if (!chosenRooms.isEmpty() || (studentCount == 0 && !availableRooms.isEmpty())) {
                    if (chosenRooms.isEmpty()) {
                        for (ExamRoom room : availableRooms) {
                            String occKey = room.getId() + "_" + slot.getDate() + "_" + slot.getStartTime();
                            if (!roomOccupancy.contains(occKey)) {
                                chosenRooms.add(room);
                                break;
                            }
                        }
                    }

                    if (!chosenRooms.isEmpty()) {
                        ExamRoom primaryRoom = chosenRooms.get(0);

                        Examination exam = (existingExam != null) ? existingExam : new Examination();
                        exam.setExamName(ExamType.END_SEMESTER_EXAMINATION.getDisplayName());
                        exam.setExamType(ExamType.END_SEMESTER_EXAMINATION);
                        exam.setSubject(subject);
                        exam.setDepartment(subject.getDepartment());
                        exam.setSemester(subject.getSemester());
                        exam.setExamDate(slot.getDate());
                        exam.setStartTime(slot.getStartTime());
                        exam.setDurationMinutes(slot.getDurationMinutes());
                        exam.setEndTime(slot.getStartTime().plusMinutes(slot.getDurationMinutes()));
                        exam.setExamRoom(primaryRoom);
                        exam.setRegisteredStudents(studentCount);
                        exam.setStatus(ExamStatus.SCHEDULED);

                        Examination savedExam = examinationRepository.saveAndFlush(exam);
                        planned.add(savedExam);
                        examRoomsMap.put(savedExam.getId(), chosenRooms);

                        for (ExamRoom r : chosenRooms) {
                            roomOccupancy.add(r.getId() + "_" + slot.getDate() + "_" + slot.getStartTime());
                        }
                        cohortLastExamDate.put(cohortKey, slot.getDate());

                        slotIndex = testSlotIdx + 1;
                        scheduled = true;
                        String roomDesc = chosenRooms.size() > 1
                            ? chosenRooms.stream().map(ExamRoom::getRoomNumber).collect(Collectors.joining(", ")) + " (" + chosenRooms.size() + " rooms)"
                            : "Room " + primaryRoom.getRoomNumber();

                        result.getExplanations().add(
                            "Scheduled '" + subject.getSubjectCode() + " - " + subject.getSubjectName() +
                            "' on " + slot.getDate() + " at " + slot.getStartTime() +
                            " in " + roomDesc + " [Slot: " + slot.getSlotLabel() + "]"
                        );
                        break;
                    }
                }
            }

            if (!scheduled) {
                result.getExplanations().add(
                    "Warning: Could not automatically schedule subject '" + subject.getSubjectCode() +
                    "' without violating room capacity or time conflict constraints."
                );
            }
        }

        // Commit and integrate Seating Allocations for all scheduled examinations
        if (!planned.isEmpty()) {
            examinationRepository.flush();

            int seatedExamsCount = 0;
            int totalAllocationsSaved = 0;

            for (Examination exam : planned) {
                List<ExamRoom> rooms = examRoomsMap.get(exam.getId());
                List<Long> roomIds = (rooms != null) ? rooms.stream().map(ExamRoom::getId).collect(Collectors.toList()) : null;

                try {
                    SeatingAllocationService.SeatingResult seatResult = seatingAllocationService.generateSeatingForExam(
                        exam.getId(), roomIds);

                    if (seatResult.isSuccess()) {
                        seatedExamsCount++;
                        totalAllocationsSaved += seatResult.getTotalSeated();
                        result.getExplanations().add(
                            "Seating arrangement generated for '" + exam.getExamName() + "': " + seatResult.getMessage()
                        );
                    } else {
                        result.getExplanations().add(
                            "Seating notice for '" + exam.getExamName() + "': " + seatResult.getMessage()
                        );
                    }
                } catch (Exception ex) {
                    result.getExplanations().add(
                        "Seating arrangement warning for '" + exam.getExamName() + "': " + ex.getMessage()
                    );
                }
            }

            result.setSuccess(true);
            result.setTotalScheduled(planned.size());
            result.setScheduledExams(planned);
            result.setMessage("Successfully generated examination timetable and room seating arrangements for "
                + planned.size() + " examinations (" + totalAllocationsSaved + " students seated and saved to MySQL).");
            auditService.log(AuditAction.TIMETABLE_PUBLISHED,
                "Intelligent timetable engine generated " + planned.size() + " exam schedules with " + totalAllocationsSaved + " seating allocations saved.");
        } else {
            result.setSuccess(false);
            result.setMessage("Could not schedule any examinations with the specified constraints.");
        }

        return result;
    }

    private List<ScheduleSlot> buildCandidateSlots(LocalDate start, LocalDate end, int durationMinutes, boolean excludeSundays) {
        List<ScheduleSlot> slots = new ArrayList<>();
        LocalDate curr = start;
        LocalTime examTime = LocalTime.of(10, 0);

        // Prepopulate institution-wide booked dates (One exam per day global constraint)
        Set<LocalDate> bookedDates = new HashSet<>(examinationRepository.findBookedDates(null));

        while (!curr.isAfter(end)) {
            if (excludeSundays && curr.getDayOfWeek() == DayOfWeek.SUNDAY) {
                curr = curr.plusDays(1);
                continue;
            }
            if (bookedDates.contains(curr)) {
                curr = curr.plusDays(1);
                continue;
            }
            slots.add(new ScheduleSlot(curr, examTime, durationMinutes, "Examination Session (10:00 AM)"));
            curr = curr.plusDays(1);
        }
        return slots;
    }

    /**
     * Generates a conflict-free, 1-exam-per-day complete timetable preview for all subjects
     * mapped to the specified department and semester.
     */
    @Transactional(readOnly = true)
    public List<TimetableItemDTO> generateCompleteTimetablePreview(Long departmentId, Integer semester,
                                                                   LocalDate startDate, LocalTime startTime,
                                                                   int durationMinutes, boolean excludeSundays) {
        List<Subject> subjects;
        if (departmentId != null && semester != null && semester > 0) {
            subjects = new ArrayList<>(subjectRepository.findByDepartmentIdAndSemester(departmentId, semester));
        } else if (departmentId != null) {
            subjects = new ArrayList<>(subjectRepository.findByDepartmentId(departmentId));
        } else {
            subjects = new ArrayList<>(subjectRepository.findAll());
        }

        if (subjects.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort subjects in ascending natural order by subject code (e.g., MCA301, MCA302, ...)
        subjects.sort(Comparator.comparing(Subject::getSubjectCode, Comparator.nullsLast(String::compareTo)));

        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().plusDays(7);
        if (effectiveStart.isBefore(LocalDate.now())) {
            effectiveStart = LocalDate.now().plusDays(1);
        }
        LocalTime effectiveTime = startTime != null ? startTime : LocalTime.of(10, 0);

        List<ExamRoom> availableRooms = new ArrayList<>(examRoomRepository.findByAvailableTrue());
        availableRooms.sort(Comparator.comparingInt(ExamRoom::getCapacity).reversed());

        Set<LocalDate> bookedDates = new HashSet<>(examinationRepository.findBookedDates(null));
        List<TimetableItemDTO> previewList = new ArrayList<>();

        LocalDate candidateDate = effectiveStart;

        for (Subject subject : subjects) {
            long studentCount = studentRepository.countByDepartmentIdAndSemester(
                subject.getDepartment().getId(), subject.getSemester());

            // Search for next available date respecting institution-wide 1 exam per day rule
            int safetyLimit = 0;
            while ((bookedDates.contains(candidateDate) || (excludeSundays && candidateDate.getDayOfWeek() == DayOfWeek.SUNDAY)) && safetyLimit < 1000) {
                candidateDate = candidateDate.plusDays(1);
                safetyLimit++;
            }

            LocalDate chosenDate = candidateDate;
            bookedDates.add(chosenDate); // Reserve for this subject in the preview

            // Minimum 1 study day gap between exams of the same cohort
            candidateDate = candidateDate.plusDays(2);

            // Multi-room & smart room assignment on chosenDate
            List<ExamRoom> roomsOnDate = examRoomService.getAvailableRoomsOnDate(chosenDate, null);
            roomsOnDate.sort(Comparator.comparingInt(ExamRoom::getCapacity).reversed());

            List<ExamRoom> allocatedRooms = new ArrayList<>();
            int allocatedCap = 0;

            // Option 1: Single room fits whole student cohort
            for (ExamRoom r : roomsOnDate) {
                if (r.getCapacity() >= studentCount) {
                    allocatedRooms.add(r);
                    allocatedCap = r.getCapacity();
                    break;
                }
            }

            // Option 2: Multi-room allocation when studentCount exceeds any single room
            if (allocatedRooms.isEmpty() && !roomsOnDate.isEmpty() && studentCount > 0) {
                for (ExamRoom r : roomsOnDate) {
                    allocatedRooms.add(r);
                    allocatedCap += r.getCapacity();
                    if (allocatedCap >= studentCount) break;
                }
            }

            if (allocatedRooms.isEmpty() && !roomsOnDate.isEmpty()) {
                allocatedRooms.add(roomsOnDate.get(0));
                allocatedCap = roomsOnDate.get(0).getCapacity();
            }

            TimetableItemDTO item = new TimetableItemDTO();
            item.setSubjectId(subject.getId());
            item.setSubjectName(subject.getSubjectName());
            item.setSubjectCode(subject.getSubjectCode());
            item.setDepartmentId(subject.getDepartment().getId());
            item.setDepartmentName(subject.getDepartment().getDeptName());
            item.setDepartmentCode(subject.getDepartment().getDeptCode());
            item.setSemester(subject.getSemester());
            item.setExamDate(chosenDate);
            item.setStartTime(effectiveTime);
            item.setEndTime(effectiveTime.plusMinutes(durationMinutes));
            item.setDurationMinutes(durationMinutes);
            item.setStudentCount((int) studentCount);

            if (!allocatedRooms.isEmpty()) {
                ExamRoom primary = allocatedRooms.get(0);
                item.setRoomId(primary.getId());
                item.setRoomNumber(allocatedRooms.size() > 1
                    ? allocatedRooms.stream().map(ExamRoom::getRoomNumber).collect(Collectors.joining(", ")) + " (" + allocatedRooms.size() + " halls)"
                    : primary.getRoomNumber());
                item.setRoomCapacity(allocatedCap);
                item.setTotalAllocatedCapacity(allocatedCap);
                item.setMultiRoomRequired(allocatedRooms.size() > 1 || allocatedCap < studentCount);
                for (ExamRoom r : allocatedRooms) {
                    item.getRoomIds().add(r.getId());
                    item.getRoomNames().add(r.getRoomNumber() + " (" + r.getBuilding() + " - Cap: " + r.getCapacity() + ")");
                }
            }

            item.setRemainingSeats(allocatedCap - (int) studentCount);
            item.setRoomStatus(allocatedCap >= studentCount ? "SUFFICIENT" : "INSUFFICIENT");

            // Required invigilators
            int numRooms = allocatedRooms.isEmpty() ? 1 : allocatedRooms.size();
            int reqInv = Math.max(numRooms, 1);
            if (allocatedCap > 70 || studentCount > 40) {
                reqInv = Math.max(reqInv, 2);
            }
            item.setRequiredInvigilators(reqInv);

            // Available faculty assignment for preview
            List<Faculty> availFaculty = facultyService.getAvailableFacultyOnDate(chosenDate, null);
            availFaculty.sort(Comparator.comparingInt(Faculty::getCurrentDutyCount));
            List<Faculty> assignedFaculty = new ArrayList<>();
            // Prioritize external department
            for (Faculty f : availFaculty) {
                if (assignedFaculty.size() >= reqInv) break;
                if (f.getDepartment() != null && !f.getDepartment().getId().equals(subject.getDepartment().getId())) {
                    assignedFaculty.add(f);
                }
            }
            // Fill remaining if needed
            for (Faculty f : availFaculty) {
                if (assignedFaculty.size() >= reqInv) break;
                if (!assignedFaculty.contains(f)) {
                    assignedFaculty.add(f);
                }
            }

            for (Faculty f : assignedFaculty) {
                item.getFacultyIds().add(f.getId());
                item.getFacultyNames().add(f.getFullName() + " (" + (f.getDepartment() != null ? f.getDepartment().getDeptCode() : "Dept") + ")");
            }

            item.setInvigilatorStatus(assignedFaculty.size() >= reqInv ? "FULLY_STAFFED" : "NEEDS_INVIGILATOR");
            item.setReadinessStatus(item.getRoomStatus().equals("SUFFICIENT") && item.getInvigilatorStatus().equals("FULLY_STAFFED") ? "READY" : (item.getRoomStatus().equals("INSUFFICIENT") ? "NEEDS_ROOM" : "NEEDS_INVIGILATOR"));
            item.setStatus("PROPOSED");
            previewList.add(item);
        }

        return previewList;
    }

    /**
     * Transactional batch scheduler: Validates constraints, saves all examinations,
     * automatically generates student seating allocations, and allocates invigilators.
     * If ANY item fails validation, the entire batch transaction is rolled back.
     */
    @Transactional(rollbackFor = Exception.class)
    public SchedulingResult saveCompleteTimetableBatch(TimetableBatchRequest request) {
        SchedulingResult result = new SchedulingResult();

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No examination timetable items provided for batch scheduling.");
            return result;
        }

        LocalDate today = LocalDate.now();

        // 1. HARD VALIDATION: Pre-check ALL items before persisting ANY record
        Set<LocalDate> batchDates = new HashSet<>();
        Set<Long> batchSubjects = new HashSet<>();

        for (TimetableBatchRequest.Item item : request.getItems()) {
            if (item.getSubjectId() == null) {
                throw new IllegalArgumentException("Invalid subject reference in proposed timetable.");
            }
            if (item.getExamDate() == null) {
                throw new IllegalArgumentException("Examination date is required for all timetable items.");
            }
            if (item.getExamDate().isBefore(today)) {
                throw new IllegalArgumentException("Examination date " + item.getExamDate() + " cannot be in the past.");
            }

            // Rule: Duplicate subjects in the same batch
            if (!batchSubjects.add(item.getSubjectId())) {
                Subject sub = subjectRepository.findById(item.getSubjectId()).orElse(null);
                String name = sub != null ? sub.getSubjectName() : "ID " + item.getSubjectId();
                throw new IllegalArgumentException("Duplicate subject in batch: '" + name + "' appears more than once.");
            }

            // Rule: Exactly ONE examination per calendar day across the institution!
            if (!batchDates.add(item.getExamDate())) {
                throw new IllegalArgumentException(
                    "Duplicate Date Conflict: Multiple examinations are scheduled on " + item.getExamDate() +
                    ". Only ONE examination per day is permitted across the entire institution.");
            }

            // Check if date is already booked in MySQL by another active examination not in this batch
            List<Examination> activeOnDate = examinationRepository.findActiveExamsOnDate(item.getExamDate(), null);
            for (Examination conflict : activeOnDate) {
                if (conflict.getSubject() == null || !conflict.getSubject().getId().equals(item.getSubjectId())) {
                    throw new IllegalArgumentException(
                        "Institution-Wide Date Conflict: The date " + item.getExamDate() +
                        " is already booked by '" + conflict.getExamName() + "'. Only ONE examination per calendar day is permitted across the entire institution.");
                }
            }

            // Verify subject exists
            Subject sub = subjectRepository.findById(item.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + item.getSubjectId()));

            long studentCount = studentRepository.countByDepartmentIdAndSemester(
                sub.getDepartment().getId(), sub.getSemester());

            // Validate rooms if explicitly selected
            List<Long> assignedRoomIds = item.getRoomIds() != null && !item.getRoomIds().isEmpty()
                ? item.getRoomIds()
                : (item.getRoomId() != null ? Collections.singletonList(item.getRoomId()) : Collections.emptyList());

            if (!assignedRoomIds.isEmpty()) {
                int totalCap = 0;
                for (Long rId : assignedRoomIds) {
                    ExamRoom r = examRoomRepository.findById(rId)
                        .orElseThrow(() -> new IllegalArgumentException("Room not found: " + rId));
                    if (!examRoomService.isRoomAvailableOnDate(rId, item.getExamDate(), null)) {
                        throw new IllegalArgumentException(
                            "Room Conflict on " + item.getExamDate() + ": Hall '" + r.getRoomNumber() +
                            " (" + r.getBuilding() + ")' is already occupied by another examination on this date.");
                    }
                    totalCap += r.getCapacity();
                }
                if (studentCount > 0 && totalCap < studentCount) {
                    throw new IllegalArgumentException(
                        "Insufficient Room Capacity on " + item.getExamDate() + ": Selected halls have capacity of " +
                        totalCap + ", but " + studentCount + " students require seats. Please allocate additional halls.");
                }
            }

            // Validate faculty if explicitly assigned
            if (item.getFacultyIds() != null && !item.getFacultyIds().isEmpty()) {
                Set<Long> itemFacultySet = new HashSet<>();
                for (Long fId : item.getFacultyIds()) {
                    if (!itemFacultySet.add(fId)) {
                        throw new IllegalArgumentException("Duplicate faculty assigned to the same examination.");
                    }
                    Faculty f = facultyRepository.findById(fId)
                        .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + fId));
                    List<Invigilation> sameDay = invigilationRepository.findByFacultyAndDate(fId, item.getExamDate());
                    if (!sameDay.isEmpty()) {
                        throw new IllegalArgumentException(
                            "Faculty Conflict on " + item.getExamDate() + ": " + f.getFullName() +
                            " is already assigned to another examination on this date. Only one duty per day is allowed.");
                    }
                    if (f.getCurrentDutyCount() >= f.getMaxDuties()) {
                        throw new IllegalArgumentException(
                            "Faculty Quota Exceeded: " + f.getFullName() + " has reached the maximum allowed duties (" +
                            f.getMaxDuties() + ").");
                    }
                }
            }
        }

        // 2. ALL VALIDATIONS PASSED -> Persist examinations, generate seating, allocate invigilators
        List<Examination> savedExams = new ArrayList<>();
        int totalStudentsSeated = 0;
        int totalInvigilatorsAssigned = 0;

        List<Examination> existingCohortExams = examinationRepository.findAll();

        for (TimetableBatchRequest.Item item : request.getItems()) {
            Subject subject = subjectRepository.findById(item.getSubjectId()).orElseThrow();
            long studentCount = studentRepository.countByDepartmentIdAndSemester(
                subject.getDepartment().getId(), subject.getSemester());

            Examination existingExam = existingCohortExams.stream()
                .filter(e -> e.getSubject() != null && e.getSubject().getId().equals(subject.getId())
                          && e.getStatus() != ExamStatus.CANCELLED && e.getStatus() != ExamStatus.COMPLETED)
                .findFirst().orElse(null);

            Examination exam = (existingExam != null) ? existingExam : new Examination();
            ExamType resolvedType = ExamType.fromString(item.getExamType() != null ? item.getExamType() : request.getExamType());
            String title = (item.getExamName() != null && !item.getExamName().isBlank())
                ? item.getExamName()
                : ((request.getExamName() != null && !request.getExamName().isBlank()) ? request.getExamName() : resolvedType.getDisplayName());

            exam.setExamName(title);
            exam.setExamType(resolvedType);
            exam.setSubject(subject);
            exam.setDepartment(subject.getDepartment());
            exam.setSemester(subject.getSemester());
            exam.setExamDate(item.getExamDate());
            exam.setStartTime(item.getStartTime() != null ? item.getStartTime() : LocalTime.of(10, 0));
            int duration = item.getDurationMinutes() > 0 ? item.getDurationMinutes() : 180;
            exam.setDurationMinutes(duration);
            exam.setEndTime(exam.getStartTime().plusMinutes(duration));
            exam.setRegisteredStudents((int) studentCount);
            exam.setStatus(ExamStatus.SCHEDULED);

            List<Long> assignedRoomIds = item.getRoomIds() != null && !item.getRoomIds().isEmpty()
                ? new ArrayList<>(item.getRoomIds())
                : (item.getRoomId() != null ? new ArrayList<>(Collections.singletonList(item.getRoomId())) : new ArrayList<>());

            if (assignedRoomIds.isEmpty()) {
                // Auto-pick available rooms on that date
                List<ExamRoom> roomsOnDate = examRoomService.getAvailableRoomsOnDate(item.getExamDate(), null);
                roomsOnDate.sort(Comparator.comparingInt(ExamRoom::getCapacity).reversed());
                int capAccum = 0;
                for (ExamRoom r : roomsOnDate) {
                    assignedRoomIds.add(r.getId());
                    capAccum += r.getCapacity();
                    if (capAccum >= studentCount) break;
                }
            }

            if (!assignedRoomIds.isEmpty()) {
                examRoomRepository.findById(assignedRoomIds.get(0)).ifPresent(exam::setExamRoom);
            }

            Examination savedExam = examinationRepository.saveAndFlush(exam);
            savedExams.add(savedExam);

            // Multi-Room Automatic Seating Allocation:
            try {
                SeatingAllocationService.SeatingResult seatResult =
                    seatingAllocationService.generateSeatingForExam(savedExam.getId(), assignedRoomIds, null);
                if (seatResult.isSuccess()) {
                    totalStudentsSeated += seatResult.getTotalSeated();
                    result.getExplanations().add("Seating generated for '" + savedExam.getExamName() + "': " + seatResult.getMessage());
                } else {
                    result.getExplanations().add("Seating notice for '" + savedExam.getExamName() + "': " + seatResult.getMessage());
                }
            } catch (Exception ex) {
                result.getExplanations().add("Seating allocation warning: " + ex.getMessage());
            }

            // Invigilator Allocation:
            if (item.getFacultyIds() != null && !item.getFacultyIds().isEmpty()) {
                for (Long fId : item.getFacultyIds()) {
                    Faculty f = facultyRepository.findById(fId).orElse(null);
                    if (f != null && !invigilationRepository.existsByFacultyIdAndExaminationId(fId, savedExam.getId())) {
                        Invigilation inv = new Invigilation();
                        inv.setFaculty(f);
                        inv.setExamination(savedExam);
                        inv.setConfirmed(true);
                        inv.setAssignmentNote("Assigned during complete examination timetable confirmation.");
                        invigilationRepository.save(inv);
                        f.setCurrentDutyCount(f.getCurrentDutyCount() + 1);
                        facultyRepository.save(f);
                        totalInvigilatorsAssigned++;

                        // Faculty notification with reporting time & hall
                        if (f.getUser() != null) {
                            LocalTime repTime = savedExam.getStartTime().minusMinutes(30);
                            String hall = savedExam.getExamRoom() != null ? savedExam.getExamRoom().getFullRoomName() : "Allocated Hall";
                            notificationService.createNotification(
                                f.getUser(),
                                "Invigilation Duty: " + savedExam.getExamName(),
                                "Examination Duty assigned on " + savedExam.getExamDate() + " at " + savedExam.getStartTime() +
                                " (Reporting Time: " + repTime + ") in " + hall + ".",
                                NotificationType.INVIGILATION_ASSIGNED,
                                savedExam.getId()
                            );
                        }
                    }
                }
            } else {
                try {
                    int reqPerRoom = assignedRoomIds.size() > 1 ? 1 : 1;
                    InvigilatorAllocationService.AllocationResult invResult =
                        invigilatorAllocationService.allocateInvigilators(savedExam.getId(), true, reqPerRoom);
                    if (invResult.isSuccess()) {
                        totalInvigilatorsAssigned += invResult.getTotalAssigned();
                        result.getExplanations().add("Invigilation assigned for '" + savedExam.getExamName() + "': " + invResult.getTotalAssigned() + " faculty allocated.");
                    } else if (!invResult.getWarnings().isEmpty()) {
                        result.getExplanations().add("Invigilation notice for '" + savedExam.getExamName() + "': " + invResult.getWarnings().get(0));
                    }
                } catch (Exception ex) {
                    result.getExplanations().add("Invigilator allocation warning: " + ex.getMessage());
                }
            }

            // Send notification for students with hall and seat number
            try {
                List<SeatingAllocation> allocations = seatingAllocationRepository.findByExaminationId(savedExam.getId());
                if (!allocations.isEmpty()) {
                    for (SeatingAllocation sa : allocations) {
                        Student st = sa.getStudent();
                        if (st != null && st.getEmail() != null) {
                            userRepository.findByEmail(st.getEmail()).ifPresent(user -> {
                                String hallStr = sa.getExamRoom() != null ? sa.getExamRoom().getFullRoomName() : "Examination Hall";
                                notificationService.createNotification(
                                    user,
                                    "Exam Hall & Seat: " + savedExam.getExamName(),
                                    "Your examination for " + subject.getSubjectName() + " (" + subject.getSubjectCode() + ") is on " +
                                    savedExam.getExamDate() + " at " + savedExam.getStartTime() + " in " + hallStr +
                                    ". Seat Number: " + sa.getSeatNumber() + ".",
                                    NotificationType.SCHEDULE_PUBLISHED,
                                    savedExam.getId()
                                );
                            });
                        }
                    }
                } else {
                    List<Student> cohortStudents = studentRepository.findByDepartmentIdAndSemesterOrderByRegisterNumberAsc(
                        subject.getDepartment().getId(), subject.getSemester());
                    for (Student st : cohortStudents) {
                        if (st.getEmail() != null) {
                            userRepository.findByEmail(st.getEmail()).ifPresent(user -> {
                                notificationService.createNotification(
                                    user,
                                    "Exam Scheduled: " + savedExam.getExamName(),
                                    "Your examination for " + subject.getSubjectName() + " (" + subject.getSubjectCode() + ") has been scheduled on " +
                                    savedExam.getExamDate() + " at " + savedExam.getStartTime() + ".",
                                    NotificationType.SCHEDULE_PUBLISHED,
                                    savedExam.getId()
                                );
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Notify Coordinators & Admins of complete timetable publication
        try {
            List<User> coordinators = userRepository.findByRole(UserRole.EXAM_COORDINATOR);
            for (User coord : coordinators) {
                notificationService.createNotification(
                    coord,
                    "Timetable Published: " + savedExams.size() + " Examinations",
                    "Complete examination schedule with seating arrangements has been published for " +
                    savedExams.size() + " subjects.",
                    NotificationType.SCHEDULE_PUBLISHED
                );
            }
        } catch (Exception ignored) {}

        result.setSuccess(true);
        result.setTotalScheduled(savedExams.size());
        result.setScheduledExams(savedExams);
        result.setMessage("Successfully generated complete examination timetable: " + savedExams.size() +
            " examinations scheduled, " + totalStudentsSeated + " student seats allocated in MySQL, and " +
            totalInvigilatorsAssigned + " faculty invigilators assigned.");

        auditService.log(AuditAction.TIMETABLE_PUBLISHED,
            "Batch Timetable Generator created " + savedExams.size() + " examinations with " +
            totalStudentsSeated + " seating allocations and " + totalInvigilatorsAssigned + " invigilator assignments.");

        return result;
    }
}
