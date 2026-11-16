package com.examscheduler.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class ExamReadinessDto {
    private Long id;
    private String examName;
    private String subjectName;
    private String subjectCode;
    private String deptCode;
    private int semester;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String roomNumber;

    // Status attributes
    private String timetableStatus; // "Confirmed" / "Pending"
    private String roomStatus;      // "Complete" / "Pending"
    private String seatingStatus;   // "Complete" / "Pending"
    private String facultyStatus;   // "Complete" / "Pending"
    private String conflictStatus;  // "Clear" / "Conflict Detected"
    private String overallStatus;   // "READY" / "ACTION REQUIRED" / "PENDING"

    private int eligibleStudents;
    private int allocatedStudents;
    private int requiredFaculty;
    private int assignedFaculty;

    public ExamReadinessDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getDeptCode() { return deptCode; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getTimetableStatus() { return timetableStatus; }
    public void setTimetableStatus(String timetableStatus) { this.timetableStatus = timetableStatus; }

    public String getRoomStatus() { return roomStatus; }
    public void setRoomStatus(String roomStatus) { this.roomStatus = roomStatus; }

    public String getSeatingStatus() { return seatingStatus; }
    public void setSeatingStatus(String seatingStatus) { this.seatingStatus = seatingStatus; }

    public String getFacultyStatus() { return facultyStatus; }
    public void setFacultyStatus(String facultyStatus) { this.facultyStatus = facultyStatus; }

    public String getConflictStatus() { return conflictStatus; }
    public void setConflictStatus(String conflictStatus) { this.conflictStatus = conflictStatus; }

    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }

    public int getEligibleStudents() { return eligibleStudents; }
    public void setEligibleStudents(int eligibleStudents) { this.eligibleStudents = eligibleStudents; }

    public int getAllocatedStudents() { return allocatedStudents; }
    public void setAllocatedStudents(int allocatedStudents) { this.allocatedStudents = allocatedStudents; }

    public int getRequiredFaculty() { return requiredFaculty; }
    public void setRequiredFaculty(int requiredFaculty) { this.requiredFaculty = requiredFaculty; }

    public int getAssignedFaculty() { return assignedFaculty; }
    public void setAssignedFaculty(int assignedFaculty) { this.assignedFaculty = assignedFaculty; }
}

