# SmartExam – Intelligent Examination Scheduling & Invigilation Management System

> **MCA Final-Year Academic Project**  
> **Architecture:** Spring Boot 4.1.x, Spring MVC, Spring Data JPA, Hibernate, Spring Security, Thymeleaf, MySQL 8, Bootstrap 5, Chart.js  
> **Java Version:** OpenJDK 25  

---

## 1. System Overview

**SmartExam** is an enterprise-grade university examination planning and administration platform designed to automate and optimize the complex constraints of university examination scheduling, room allocation, and invigilator duty assignments.

Built with a clean 3-tier architecture following SOLID principles, the platform eliminates scheduling overlaps, respects student cohort availability, balances faculty invigilation workload fairly, ensures room capacities are adhered to, and provides university leadership with real-time analytics and auditability.

---

## 2. Technology Stack

* **Backend Engine:** Java 25 (with modern pattern matching, records, virtual threads capability)
* **Framework:** Spring Boot 4.1.2
* **Persistence & ORM:** Spring Data JPA with Hibernate 7 (MySQL Connector/J 8.0)
* **Access Control:** Spring Security with BCrypt password hashing & role-based routing
* **Web & Templating:** Spring MVC & Thymeleaf with CSRF protection
* **Reporting Engines:**
  * **PDF Generation:** Apache PDFBox 3.0.4
  * **Excel Export:** Apache POI OOXML 5.3.0
  * **Bulk Import:** OpenCSV 5.9
* **Frontend Design:** HTML5, CSS3, Bootstrap 5.3, Bootstrap Icons, Chart.js 4.4

---

## 3. Demonstration & Default Credentials

The system comes pre-configured with a database auto-seeder (`DataInitializer`) that boots real test departments, faculty members, subjects, rooms, and accounts:

| Role | Email | Password | Access Scope |
| :--- | :--- | :--- | :--- |
| **System Administrator** | `admin@smartexam.edu` | `Admin@123` | Full administrative control, user/master data management, scheduling, audit logs, system-wide analytics |
| **Exam Coordinator** | `coordinator@smartexam.edu` | `Coord@123` | Schedule generation, conflict diagnosis, room allocation, seating plans, invigilator assignments |
| **Faculty Member** | `suresh@smartexam.edu` | `Faculty@123` | Personal invigilation roster, room assignments, duty quota tracking, notifications |
| **Faculty Member** | `ramesh@smartexam.edu` | `Faculty@123` | Alternative faculty profile for workload balancing tests |
| **Faculty Member** | `anita@smartexam.edu` | `Faculty@123` | Mathematics department faculty |
| **Faculty Member** | `priya@smartexam.edu` | `Faculty@123` | Electronics department faculty |

---

## 4. Key Architectural Modules

### 4.1 Constraint-Based Timetable Scheduling Engine
The intelligent scheduling engine (`SchedulingService.java`) uses a heuristic constraint-satisfaction algorithm with backtracking:
* **Hard Constraints (Must Satisfy):**
  1. *Student Cohort No-Overlap:* No student group/semester has overlapping examinations on the same date and slot.
  2. *Room Exclusivity:* An examination room cannot host two overlapping examinations.
  3. *Room Capacity Check:* Assigned students cannot exceed physical room seating capacity.
  4. *Session Availability:* Examinations are only placed in legitimate university sessions (Morning 09:30–12:30, Afternoon 14:00–17:00).
* **Soft Constraints (Optimized):**
  1. *Daily Workload Distribution:* Spreads exams across days to avoid fatigue.
  2. *Buffer Spacing:* A minimum of 1-day spacing between exams of the same cohort where dates permit.

### 4.2 Workload-Balanced Invigilator Allocation Engine
The invigilator assignment engine (`InvigilatorAllocationService.java`) computes dynamic allocation scores:
$$\text{Allocation Priority} = 100 - (\text{Assigned Duties} \times 10) + \text{Remaining Quota Bonus}$$
* Enforces department restriction rules (faculty cannot invigilate exams conducted by their own home department).
* Enforces maximum semester duty limits (`maxDuties`).
* Detects and blocks time-slot duty overlap.

### 4.3 Automated Student Seating Arrangement
The seating engine (`SeatingAllocationService.java`) automatically distributes registered candidates into assigned rooms with structured alphanumeric seat indices (e.g., `HALL-A-S01`, `HALL-A-S02`) respecting exact desk layout limits.

### 4.4 Diagnostic Conflict Detection
Real-time conflict scanner (`ConflictDetectionService.java`) continuously analyzes:
* Room double-bookings
* Room over-capacity emergencies
* Faculty time clashes and workload threshold violations
* Multi-subject cohort scheduling conflicts

### 4.5 University Reporting & Export
* **PDF Timetable:** Formatted exam schedules with university headers, course codes, time slots, and room locations generated using Apache PDFBox.
* **Excel Reports:** High-performance multi-sheet spreadsheets generated via Apache POI.
* **CSV Bulk Import:** Bulk student registration and onboarding with error tracking and duplicate detection via OpenCSV.

---

## 5. System Execution & Startup

### Prerequisites
* **Java:** JDK 25 installed
* **MySQL:** MySQL Server 8.0 running on `localhost:3306` with database `exam_scheduler` or `smartexam_db`
* **Credentials in `application.properties`:**
  ```properties
  spring.datasource.url=jdbc:mysql://localhost:3306/smartexam_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true
  spring.datasource.username=root
  spring.datasource.password=1414
  server.port=8081
  ```

### Running the Application

1. **Build and Package:**
   ```bash
   mvn clean package -DskipTests
   ```
2. **Run Spring Boot Application:**
   ```bash
   java -jar target/exam-scheduler-0.0.1-SNAPSHOT.jar
   ```
   *Or with Maven:*
   ```bash
   mvn spring-boot:run
   ```
3. **Open Browser:**
   Navigate to: **`http://localhost:8081/login`**

---

## 6. Academic Viva & Defense Questions

1. **How is fairness guaranteed in faculty invigilation assignments?**  
   *Answer:* The engine tracks previous duty history and current duty counts. It ranks faculty inversely proportional to their current workload ($\text{Duties Assigned}$) while enforcing department restrictions so professors never invigilate their own department's examinations.

2. **How does the timetable algorithm prevent exam conflicts?**  
   *Answer:* Before any time slot and room are committed to an exam, the algorithm checks cohort intersection (Department + Semester) and room availability. If any candidate in that cohort is already scheduled at the requested time, the slot is rejected and the algorithm backtracks to the next available slot.

3. **How does Spring Security protect the system against role escalation?**  
   *Answer:* Role-based request matchers enforce endpoint boundaries at the HTTP filter chain level (`/admin/**` requires `ROLE_ADMIN`, `/coordinator/**` requires `ROLE_EXAM_COORDINATOR`, and `/faculty/**` requires `ROLE_FACULTY`), supplemented by BCrypt password encryption and CSRF tokens on all state-modifying requests.

