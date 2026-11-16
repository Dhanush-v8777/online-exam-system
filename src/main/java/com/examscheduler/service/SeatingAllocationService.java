package com.examscheduler.service;

import com.examscheduler.dto.SeatingHallDTO;
import com.examscheduler.entity.*;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SeatingAllocationService {

    public static class SeatingResult {
        private boolean success;
        private int totalSeated;
        private String message;
        private List<String> details = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getTotalSeated() { return totalSeated; }
        public void setTotalSeated(int totalSeated) { this.totalSeated = totalSeated; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<String> getDetails() { return details; }
    }

    private final SeatingAllocationRepository seatingAllocationRepository;
    private final ExaminationRepository examinationRepository;
    private final StudentRepository studentRepository;
    private final ExamRoomRepository examRoomRepository;
    private final ExamRegistrationRepository examRegistrationRepository;
    private final AuditService auditService;

    public SeatingAllocationService(SeatingAllocationRepository seatingAllocationRepository,
                                  ExaminationRepository examinationRepository,
                                  StudentRepository studentRepository,
                                  ExamRoomRepository examRoomRepository,
                                  ExamRegistrationRepository examRegistrationRepository,
                                  AuditService auditService) {
        this.seatingAllocationRepository = seatingAllocationRepository;
        this.examinationRepository = examinationRepository;
        this.studentRepository = studentRepository;
        this.examRoomRepository = examRoomRepository;
        this.examRegistrationRepository = examRegistrationRepository;
        this.auditService = auditService;
    }

    public SeatingResult generateSeatingForExam(Long examId) {
        return generateSeatingForExam(examId, (List<Long>) null, null);
    }

    public SeatingResult generateSeatingForExam(Long examId, Long roomId) {
        return generateSeatingForExam(examId, roomId != null ? Collections.singletonList(roomId) : null, null);
    }

    public SeatingResult generateSeatingForExam(Long examId, Long roomId, Integer studentLimit) {
        return generateSeatingForExam(examId, roomId != null ? Collections.singletonList(roomId) : null, studentLimit);
    }

    public SeatingResult generateSeatingForExam(Long examId, List<Long> roomIds) {
        return generateSeatingForExam(examId, roomIds, null);
    }

    /**
     * Identifies all students eligible for the specified examination.
     * Uses actual MySQL student records with exact filtering:
     * 1. Explicit ExamRegistration records for this examination.
     * 2. Students matching examination's Department and Semester (ordered by register number).
     * Prevents cross-department, cross-semester, or unrelated student pollution.
     */
    @Transactional(readOnly = true)
    public List<Student> getEligibleStudentsForExam(Long examId) {
        if (examId == null) {
            return Collections.emptyList();
        }
        Examination exam = examinationRepository.findById(examId).orElse(null);
        if (exam == null) {
            return Collections.emptyList();
        }

        // 1. Fetch from explicit ExamRegistration if registrations exist
        List<ExamRegistration> registrations = examRegistrationRepository.findByExaminationId(examId);
        if (registrations != null && !registrations.isEmpty()) {
            List<Student> regStudents = registrations.stream()
                .map(ExamRegistration::getStudent)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(Student::getRegisterNumber, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
            if (!regStudents.isEmpty()) {
                return regStudents;
            }
        }

        // 2. Resolve Department and Semester accurately
        Department dept = exam.getDepartment();
        if (dept == null && exam.getSubject() != null) {
            dept = exam.getSubject().getDepartment();
        }
        int sem = exam.getSemester();
        if (sem <= 0 && exam.getSubject() != null) {
            sem = exam.getSubject().getSemester();
        }

        // 3. Fetch by Department and Semester (with register number ordering)
        if (dept != null && sem > 0) {
            List<Student> deptSemStudents = studentRepository.findByDepartmentIdAndSemesterOrderByRegisterNumberAsc(
                dept.getId(), sem);
            if (deptSemStudents != null && !deptSemStudents.isEmpty()) {
                return deptSemStudents;
            }
        }

        // Strictly avoid cross-semester or cross-department leaks:
        // Do not return students from unrelated semesters or other departments!
        return Collections.emptyList();
    }

    /**
     * Automatic student seating generator supporting multi-room allocation, capacity validation,
     * and idempotent regeneration in a single database transaction.
     */
    @Transactional
    public SeatingResult generateSeatingForExam(Long examId, List<Long> roomIds, Integer studentLimit) {
        SeatingResult result = new SeatingResult();

        if (examId == null) {
            result.setSuccess(false);
            result.setMessage("Please select a valid examination.");
            return result;
        }

        Examination exam = examinationRepository.findById(examId)
            .orElseThrow(() -> new IllegalArgumentException("Examination not found: " + examId));

        // 1. Determine selected rooms
        List<ExamRoom> selectedRooms = new ArrayList<>();
        if (roomIds != null && !roomIds.isEmpty()) {
            for (Long rId : roomIds) {
                if (rId != null) {
                    examRoomRepository.findById(rId).ifPresent(r -> {
                        if (!selectedRooms.contains(r)) {
                            selectedRooms.add(r);
                        }
                    });
                }
            }
        }

        // If no rooms passed in roomIds, check exam's pre-assigned room
        if (selectedRooms.isEmpty() && exam.getExamRoom() != null) {
            selectedRooms.add(exam.getExamRoom());
        }

        if (selectedRooms.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("Cannot generate seating: No examination room assigned or selected for '" + exam.getExamName() + "'.");
            return result;
        }

        // 2. Fetch ALL eligible students without pagination or limits
        List<Student> eligibleStudents = getEligibleStudentsForExam(examId);
        if (eligibleStudents.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No eligible students found for examination '" + exam.getExamName() + "'.");
            return result;
        }

        List<Student> studentsToSeat = eligibleStudents;
        if (studentLimit != null && studentLimit > 0 && studentLimit < eligibleStudents.size()) {
            studentsToSeat = eligibleStudents.subList(0, studentLimit);
        }

        int totalStudents = studentsToSeat.size();
        int totalCapacity = selectedRooms.stream().mapToInt(ExamRoom::getCapacity).sum();

        // 3. Room Capacity Validation & Multi-Room Support:
        // If roomIds was not explicitly provided by the user, automatically augment with other
        // active available examination rooms to fit all students when one room is insufficient.
        if (totalCapacity < totalStudents && (roomIds == null || roomIds.isEmpty())) {
            List<ExamRoom> allAvail = examRoomRepository.findByAvailableTrue();
            for (ExamRoom r : allAvail) {
                if (!selectedRooms.contains(r)) {
                    List<Examination> conflicts = examinationRepository.findConflictingExamsInRoom(
                        exam.getExamDate(), r.getId(), exam.getId(), exam.getStartTime(), exam.getEndTime());
                    if (conflicts == null || conflicts.isEmpty()) {
                        selectedRooms.add(r);
                        totalCapacity += r.getCapacity();
                        if (totalCapacity >= totalStudents) {
                            break;
                        }
                    }
                }
            }
        }

        // Do NOT allocate partially if capacity is insufficient.
        // Return explicit error message and keep all students unallocated.
        if (totalCapacity < totalStudents) {
            result.setSuccess(false);
            String roomPhrase = selectedRooms.size() > 1 ? "the selected rooms have only " : "the selected room has only ";
            result.setMessage("Insufficient seating capacity. " + totalStudents + " students require seats, but "
                + roomPhrase + totalCapacity + " seats. Please select a larger room or multiple rooms.");
            return result;
        }

        // 4. Clear existing allocations for clean idempotent generation in the same transaction
        seatingAllocationRepository.deleteByExaminationId(examId);
        seatingAllocationRepository.flush();

        // 5. Complete Allocation across selected rooms with strict duplicate prevention
        List<SeatingAllocation> allocations = new ArrayList<>();
        Set<Long> allocatedStudentIds = new HashSet<>();
        Set<String> allocatedSeatKeys = new HashSet<>();
        int studentIdx = 0;

        for (ExamRoom room : selectedRooms) {
            int roomCap = room.getCapacity();
            int seatInRoom = 1;

            while (seatInRoom <= roomCap && studentIdx < totalStudents) {
                Student s = studentsToSeat.get(studentIdx);
                String seatNum = String.format("%s-S%02d", room.getRoomNumber(), seatInRoom);
                String seatKey = room.getId() + "_" + seatNum;

                if (allocatedStudentIds.contains(s.getId())) {
                    throw new IllegalStateException("Duplicate student allocation detected: Student ID " + s.getId() + " (" + s.getFullName() + ") is already assigned a seat.");
                }
                if (allocatedSeatKeys.contains(seatKey)) {
                    throw new IllegalStateException("Duplicate seat allocation detected: Seat " + seatNum + " in Room " + room.getRoomNumber() + " is already occupied.");
                }
                allocatedStudentIds.add(s.getId());
                allocatedSeatKeys.add(seatKey);

                SeatingAllocation sa = new SeatingAllocation();
                sa.setExamination(exam);
                sa.setStudent(s);
                sa.setExamRoom(room);
                sa.setSeatNumber(seatNum);

                allocations.add(sa);
                studentIdx++;
                seatInRoom++;
            }
        }

        exam.setRegisteredStudents(allocations.size());
        if (!selectedRooms.isEmpty()) {
            exam.setExamRoom(selectedRooms.get(0));
        }

        // 6. Save all allocations and flush immediately directly to MySQL
        seatingAllocationRepository.saveAllAndFlush(allocations);
        examinationRepository.saveAndFlush(exam);

        // 7. Verify persistence directly in MySQL database
        long verifiedCount = seatingAllocationRepository.countByExaminationId(examId);
        if (verifiedCount != allocations.size()) {
            throw new IllegalStateException("Database persistence verification failed: Expected " 
                + allocations.size() + " records committed to MySQL, but verified " + verifiedCount + ".");
        }

        result.setSuccess(true);
        result.setTotalSeated(allocations.size());
        String roomSummary = selectedRooms.stream().map(ExamRoom::getRoomNumber).collect(Collectors.joining(", "));
        result.setMessage("Successfully generated seating arrangement for all " + allocations.size() + " students across "
            + (selectedRooms.size() > 1 ? selectedRooms.size() + " rooms (" + roomSummary + ")" : "Room " + roomSummary) + ".");

        auditService.log(AuditAction.SEATING_ALLOCATED,
            "Generated seating for " + allocations.size() + " students in " + exam.getExamName() + " (" + roomSummary + ")",
            "Examination", examId);

        return result;
    }

    @Transactional(readOnly = true)
    public List<SeatingAllocation> getSeatingForExam(Long examId) {
        if (examId == null) {
            return Collections.emptyList();
        }
        List<SeatingAllocation> allocations = seatingAllocationRepository.findByExaminationId(examId);
        return allocations != null ? allocations : Collections.emptyList();
    }

    /**
     * Builds a structured 4-column examination hall model (2 Left, Central Aisle, 2 Right).
     * Integrates multi-room metrics, unallocated counts, and expands dynamically for room capacity.
     */
    @Transactional(readOnly = true)
    public SeatingHallDTO buildSeatingHall(List<SeatingAllocation> allAllocations, ExamRoom room, int totalEligibleStudents, List<ExamRoom> allocatedRooms) {
        SeatingHallDTO hall = new SeatingHallDTO();

        List<ExamRoom> effectiveRooms = allocatedRooms != null ? new ArrayList<>(allocatedRooms) : new ArrayList<>();
        if (effectiveRooms.isEmpty() && room != null) {
            effectiveRooms.add(room);
        }

        // Filter allocations for current room being viewed
        List<SeatingAllocation> roomAllocations = new ArrayList<>();
        if (allAllocations != null) {
            if (room != null) {
                for (SeatingAllocation sa : allAllocations) {
                    if (sa.getExamRoom() != null && sa.getExamRoom().getId().equals(room.getId())) {
                        roomAllocations.add(sa);
                    }
                }
            } else {
                roomAllocations.addAll(allAllocations);
            }
        }

        int allocatedCount = roomAllocations.size();
        int roomCapacity = room != null ? room.getCapacity() : 16;
        int displayDesks = Math.max(16, Math.max(roomCapacity, allocatedCount));
        int rowsNeeded = (int) Math.ceil((double) displayDesks / 4.0);
        int totalDesks = rowsNeeded * 4;

        hall.setTotalRows(rowsNeeded);
        hall.setTotalDesks(totalDesks);
        hall.setAllocatedCount(allocatedCount);
        hall.setAvailableCount(Math.max(0, totalDesks - allocatedCount));

        int totalAllocated = allAllocations != null ? allAllocations.size() : 0;
        hall.setTotalEligibleStudents(totalEligibleStudents);
        hall.setTotalAllocatedStudents(totalAllocated);
        hall.setRemainingUnallocated(Math.max(0, totalEligibleStudents - totalAllocated));

        int totalCap = effectiveRooms.stream().mapToInt(ExamRoom::getCapacity).sum();
        if (totalCap == 0 && room != null) {
            totalCap = room.getCapacity();
        }
        hall.setTotalRoomCapacity(totalCap);
        hall.setAvailableRoomCapacity(Math.max(0, totalCap - totalAllocated));
        hall.setCurrentRoom(room);
        hall.setAllocatedRooms(effectiveRooms);

        String roomPrefix = (room != null && room.getRoomNumber() != null) ? room.getRoomNumber() : "DESK";

        List<SeatingHallDTO.SeatingRowDTO> rowList = new ArrayList<>();
        for (int r = 1; r <= rowsNeeded; r++) {
            int baseIdx = (r - 1) * 4;
            SeatingHallDTO.DeskSlotDTO d1 = createDesk(roomAllocations, baseIdx, r, 1, "LEFT", roomPrefix);
            SeatingHallDTO.DeskSlotDTO d2 = createDesk(roomAllocations, baseIdx + 1, r, 2, "LEFT", roomPrefix);
            SeatingHallDTO.DeskSlotDTO d3 = createDesk(roomAllocations, baseIdx + 2, r, 3, "RIGHT", roomPrefix);
            SeatingHallDTO.DeskSlotDTO d4 = createDesk(roomAllocations, baseIdx + 3, r, 4, "RIGHT", roomPrefix);

            rowList.add(new SeatingHallDTO.SeatingRowDTO(r, d1, d2, d3, d4));
        }

        hall.setRows(rowList);
        return hall;
    }

    @Transactional(readOnly = true)
    public SeatingHallDTO buildSeatingHall(List<SeatingAllocation> allocations, ExamRoom room) {
        int eligible = allocations != null ? allocations.size() : 0;
        List<ExamRoom> rooms = room != null ? Collections.singletonList(room) : Collections.emptyList();
        return buildSeatingHall(allocations, room, eligible, rooms);
    }

    private SeatingHallDTO.DeskSlotDTO createDesk(List<SeatingAllocation> allocations, int index, int row, int col, String section, String roomPrefix) {
        if (allocations != null && index < allocations.size()) {
            SeatingAllocation sa = allocations.get(index);
            String seatNum = sa.getSeatNumber();
            if (seatNum == null || seatNum.isBlank()) {
                seatNum = String.format("%s-S%02d", roomPrefix, index + 1);
            }
            String studentName = (sa.getStudent() != null && sa.getStudent().getFullName() != null)
                ? sa.getStudent().getFullName() : "Allocated Student";
            String regNum = (sa.getStudent() != null && sa.getStudent().getRegisterNumber() != null)
                ? sa.getStudent().getRegisterNumber() : "";
            return new SeatingHallDTO.DeskSlotDTO(seatNum, studentName, regNum, true, row, col, section);
        } else {
            String seatNum = String.format("%s-S%02d", roomPrefix, index + 1);
            return new SeatingHallDTO.DeskSlotDTO(seatNum, "Available", "", false, row, col, section);
        }
    }
}
