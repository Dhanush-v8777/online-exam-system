package com.examscheduler.config;

import com.examscheduler.entity.*;
import com.examscheduler.enums.*;
import com.examscheduler.repository.*;
import com.examscheduler.service.SeatingAllocationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final SubjectRepository subjectRepository;
    private final ExamRoomRepository examRoomRepository;
    private final ExaminationRepository examinationRepository;
    private final SeatingAllocationRepository seatingAllocationRepository;
    private final SeatingAllocationService seatingAllocationService;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public DataInitializer(UserRepository userRepository,
            FacultyRepository facultyRepository,
            StudentRepository studentRepository,
            DepartmentRepository departmentRepository,
            SubjectRepository subjectRepository,
            ExamRoomRepository examRoomRepository,
            ExaminationRepository examinationRepository,
            SeatingAllocationRepository seatingAllocationRepository,
            SeatingAllocationService seatingAllocationService,
            PasswordEncoder passwordEncoder,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.subjectRepository = subjectRepository;
        this.examRoomRepository = examRoomRepository;
        this.examinationRepository = examinationRepository;
        this.seatingAllocationRepository = seatingAllocationRepository;
        this.seatingAllocationService = seatingAllocationService;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        ensureDatabaseConstraints();
        if (userRepository.count() == 0) {
            initializeBaseData();
        }
        ensureTestData();
    }

    private void ensureDatabaseConstraints() {
        try {
            jdbcTemplate.execute("ALTER TABLE examinations DROP INDEX uq_exam_active_date");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE examinations ADD COLUMN active_exam_date DATE GENERATED ALWAYS AS (CASE WHEN status != 'CANCELLED' THEN exam_date ELSE NULL END) VIRTUAL");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE examinations ADD UNIQUE INDEX uq_active_exam_date (active_exam_date)");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute(
                "ALTER TABLE examinations MODIFY COLUMN exam_type ENUM(" +
                "'INTERNAL_EXAMINATION','PREPARATORY_EXAMINATION','MID_TERM_EXAMINATION'," +
                "'END_SEMESTER_EXAMINATION','VIVA_EXAMINATION','LABORATORY_EXAMINATION'," +
                "'REGULAR','SUPPLEMENTARY','RETEST','PRACTICAL','VIVA','INTERNAL'," +
                "'PREPARATORY','MID_TERM','END_SEMESTER','LABORATORY') NOT NULL DEFAULT 'END_SEMESTER_EXAMINATION'");
        } catch (Exception ignored) {
        }
    }

    private void initializeBaseData() {
        // --- Create Departments ---
        Department mca = createDept("MCA", "Master of Computer Applications", "Dr. Rajesh Kumar");
        Department mba = createDept("MBA", "Master of Business Administration", "Dr. Priya Sharma");
        Department msc = createDept("MSC", "Master of Science - CS", "Dr. Arun Mehta");

        // --- Create Admin User ---
        User admin = createUser("System Administrator", "admin@smartexam.edu", "Admin@123", UserRole.ADMIN);

        // --- Create Coordinator User ---
        User coordinator = createUser("Dr. Coordinator", "coordinator@smartexam.edu", "Coord@123",
                UserRole.EXAM_COORDINATOR);

        // --- Create Faculty Users ---
        User fUser1 = createUser("Prof. Suresh Babu", "suresh@smartexam.edu", "Faculty@123", UserRole.FACULTY);
        User fUser2 = createUser("Prof. Meena Raj", "meena@smartexam.edu", "Faculty@123", UserRole.FACULTY);
        User fUser3 = createUser("Prof. Karthik S", "karthik@smartexam.edu", "Faculty@123", UserRole.FACULTY);
        User fUser4 = createUser("Prof. Divya Menon", "divya@smartexam.edu", "Faculty@123", UserRole.FACULTY);
        User fUser5 = createUser("Prof. Ramesh T", "ramesh@smartexam.edu", "Faculty@123", UserRole.FACULTY);

        // --- Create Faculty Profiles ---
        createFaculty("FAC001", "Prof. Suresh Babu", "suresh@smartexam.edu", "9876543210", "Professor", mca, fUser1, 8);
        createFaculty("FAC002", "Prof. Meena Raj", "meena@smartexam.edu", "9876543211", "Associate Professor", mca,
                fUser2, 10);
        createFaculty("FAC003", "Prof. Karthik S", "karthik@smartexam.edu", "9876543212", "Assistant Professor", mba,
                fUser3, 12);
        createFaculty("FAC004", "Prof. Divya Menon", "divya@smartexam.edu", "9876543213", "Professor", msc, fUser4, 10);
        createFaculty("FAC005", "Prof. Ramesh T", "ramesh@smartexam.edu", "9876543214", "Assistant Professor", mba,
                fUser5, 8);

        // --- Create Subjects ---
        Subject s1 = createSubject("MCA101", "Advanced Java Programming", 1, 4, mca);
        Subject s2 = createSubject("MCA102", "Database Management Systems", 1, 4, mca);
        Subject s3 = createSubject("MCA201", "Software Engineering", 2, 3, mca);
        Subject s4 = createSubject("MCA202", "Machine Learning", 2, 4, mca);
        Subject s5 = createSubject("MBA101", "Financial Management", 1, 3, mba);
        Subject s6 = createSubject("MBA102", "Marketing Management", 1, 3, mba);

        // --- Create Exam Rooms ---
        ExamRoom r1 = createRoom("LH-101", "Main Block", "Ground Floor", 60, RoomType.LECTURE_HALL);
        ExamRoom r2 = createRoom("EH-201", "Main Block", "First Floor", 80, RoomType.EXAMINATION_HALL);
        ExamRoom r3 = createRoom("EH-202", "Main Block", "First Floor", 80, RoomType.EXAMINATION_HALL);
        ExamRoom r4 = createRoom("LH-301", "New Block", "Third Floor", 100, RoomType.LECTURE_HALL);
        ExamRoom r5 = createRoom("LAB-001", "Computer Block", "Ground Floor", 40, RoomType.LABORATORY);

        // --- Create Sample Examinations ---
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        createExam("Advanced Java Programming - Semester 1", nextWeek, LocalTime.of(9, 0), 180, s1, mca, r1, 1,
                ExamStatus.SCHEDULED);
        createExam("Database Management Systems - Semester 1", nextWeek.plusDays(2), LocalTime.of(9, 0), 180, s2, mca,
                r3, 1, ExamStatus.SCHEDULED);
        createExam("Financial Management - Semester 1", nextWeek.plusDays(1), LocalTime.of(9, 0), 180, s5, mba, r4, 1,
                ExamStatus.SCHEDULED);
    }

    /**
     * Ensures comprehensive test datasets are available in MySQL for all requested
     * test scenarios:
     * - 50 students for MCA Sem 1 (Tested with 60 capacity and 40 capacity)
     * - 20 students for MBA Sem 1 (Tested with 20 capacity)
     * - 100 students for CSE Sem 1 (Tested across multiple rooms: 60 + 60 = 120
     * capacity)
     */
    private void ensureTestData() {
        Department mca = departmentRepository.findByDeptCode("MCA")
                .orElseGet(() -> createDept("MCA", "Master of Computer Applications", "Dr. Rajesh Kumar"));
        Department mba = departmentRepository.findByDeptCode("MBA")
                .orElseGet(() -> createDept("MBA", "Master of Business Administration", "Dr. Priya Sharma"));
        Department cse = departmentRepository.findByDeptCode("CSE")
                .orElseGet(() -> createDept("CSE", "Computer Science & Engineering", "Dr. Vikram Seth"));

        // 1. Ensure Rooms with exact test capacities
        if (!examRoomRepository.existsByRoomNumberAndBuilding("LH-101", "Main Block")) {
            createRoom("LH-101", "Main Block", "Ground Floor", 60, RoomType.LECTURE_HALL);
        }
        if (!examRoomRepository.existsByRoomNumberAndBuilding("LAB-001", "Computer Block")) {
            createRoom("LAB-001", "Computer Block", "Ground Floor", 40, RoomType.LABORATORY);
        }
        if (!examRoomRepository.existsByRoomNumberAndBuilding("SR-105", "Science Block")) {
            createRoom("SR-105", "Science Block", "First Floor", 20, RoomType.SEMINAR_ROOM);
        }
        if (!examRoomRepository.existsByRoomNumberAndBuilding("LH-102", "Main Block")) {
            createRoom("LH-102", "Main Block", "Ground Floor", 60, RoomType.LECTURE_HALL);
        }

        // 2. Ensure MCA Semester 1 has 50 students
        long mcaCount = studentRepository.countByDepartmentIdAndSemester(mca.getId(), 1);
        for (int i = (int) mcaCount + 1; i <= 50; i++) {
            String regNum = "MCAS1_" + String.format("%03d", i);
            if (!studentRepository.existsByRegisterNumber(regNum)) {
                Student s = new Student();
                s.setRegisterNumber(regNum);
                s.setFullName("Student " + i + " MCA");
                s.setEmail("student" + i + ".mcas1@smartexam.edu");
                s.setSemester(1);
                s.setAcademicYear("2025-26");
                s.setDepartment(mca);
                studentRepository.save(s);
            }
        }

        // 3. Ensure MBA Semester 1 has 20 students
        long mbaCount = studentRepository.countByDepartmentIdAndSemester(mba.getId(), 1);
        for (int i = (int) mbaCount + 1; i <= 20; i++) {
            String regNum = "MBA2025" + String.format("%03d", i);
            if (!studentRepository.existsByRegisterNumber(regNum)) {
                Student s = new Student();
                s.setRegisterNumber(regNum);
                s.setFullName("Student " + i + " MBA");
                s.setEmail("student" + i + ".mba@smartexam.edu");
                s.setSemester(1);
                s.setAcademicYear("2025-26");
                s.setDepartment(mba);
                studentRepository.save(s);
            }
        }

        // 4. Ensure CSE Semester 1 has 100 students (for multi-room 100-student test)
        long cseCount = studentRepository.countByDepartmentIdAndSemester(cse.getId(), 1);
        for (int i = (int) cseCount + 1; i <= 100; i++) {
            String regNum = "CSE2025" + String.format("%03d", i);
            if (!studentRepository.existsByRegisterNumber(regNum)) {
                Student s = new Student();
                s.setRegisterNumber(regNum);
                s.setFullName("Student " + i + " CSE");
                s.setEmail("student" + i + ".cse@smartexam.edu");
                s.setSemester(1);
                s.setAcademicYear("2025-26");
                s.setDepartment(cse);
                studentRepository.save(s);
            }
        }

        // 5. Ensure Subject & Examination for CSE 100 students
        Subject csSubject = subjectRepository.findBySubjectCode("CS301")
                .orElseGet(() -> createSubject("CS301", "Data Structures & Algorithms", 1, 4, cse));

        boolean cseExamExists = examinationRepository.findAll().stream()
                .anyMatch(e -> "Data Structures & Algorithms - Semester 1".equals(e.getExamName()));
        if (!cseExamExists) {
            ExamRoom lh101 = examRoomRepository.findAll().stream()
                    .filter(r -> "LH-101".equals(r.getRoomNumber()))
                    .findFirst().orElse(null);
            createExam("Data Structures & Algorithms - Semester 1", LocalDate.now().plusDays(10),
                    LocalTime.of(9, 30), 180, csSubject, cse, lh101, 1, ExamStatus.SCHEDULED);
        }

        // 6. Ensure MCA Exam 1 has LH-101 (60 cap) and all 50 students allocated
        Examination mcaExam = examinationRepository.findAll().stream()
                .filter(e -> "Advanced Java Programming - Semester 1".equals(e.getExamName()))
                .findFirst().orElse(null);
        if (mcaExam != null) {
            ExamRoom lh101 = examRoomRepository.findAll().stream()
                    .filter(r -> "LH-101".equals(r.getRoomNumber()))
                    .findFirst().orElse(null);
            if (lh101 != null) {
                mcaExam.setExamRoom(lh101);
                examinationRepository.save(mcaExam);
            }
            if (seatingAllocationRepository.countByExaminationId(mcaExam.getId()) != 50) {
                seatingAllocationService.generateSeatingForExam(mcaExam.getId(), lh101 != null ? lh101.getId() : null);
            }
        }
    }

    private Department createDept(String code, String name, String head) {
        Department d = new Department();
        d.setDeptCode(code);
        d.setDeptName(name);
        d.setDeptHead(head);
        return departmentRepository.save(d);
    }

    private User createUser(String fullName, String email, String rawPassword, UserRole role) {
        User u = new User();
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setRole(role);
        u.setEnabled(true);
        return userRepository.save(u);
    }

    private Faculty createFaculty(String facultyId, String name, String email, String phone, String designation,
            Department dept, User user, int maxDuties) {
        Faculty f = new Faculty();
        f.setFacultyId(facultyId);
        f.setFullName(name);
        f.setEmail(email);
        f.setPhone(phone);
        f.setDesignation(designation);
        f.setDepartment(dept);
        f.setUser(user);
        f.setMaxDuties(maxDuties);
        f.setAvailable(true);
        f.setAccountActive(true);
        return facultyRepository.save(f);
    }

    private Subject createSubject(String code, String name, int semester, int credits, Department dept) {
        Subject s = new Subject();
        s.setSubjectCode(code);
        s.setSubjectName(name);
        s.setSemester(semester);
        s.setCredits(credits);
        s.setDepartment(dept);
        return subjectRepository.save(s);
    }

    private ExamRoom createRoom(String number, String building, String floor, int capacity, RoomType type) {
        ExamRoom r = new ExamRoom();
        r.setRoomNumber(number);
        r.setBuilding(building);
        r.setFloor(floor);
        r.setCapacity(capacity);
        r.setRoomType(type);
        r.setAvailable(true);
        return examRoomRepository.save(r);
    }

    private Examination createExam(String name, LocalDate date, LocalTime time, int duration,
            Subject subject, Department dept, ExamRoom room, int semester, ExamStatus status) {
        Examination e = new Examination();
        e.setExamName(name);
        e.setExamDate(date);
        e.setStartTime(time);
        e.setEndTime(time.plusMinutes(duration));
        e.setDurationMinutes(duration);
        e.setSubject(subject);
        e.setDepartment(dept);
        e.setExamRoom(room);
        e.setSemester(semester);
        e.setExamType(ExamType.REGULAR);
        e.setStatus(status);
        return examinationRepository.save(e);
    }
}
