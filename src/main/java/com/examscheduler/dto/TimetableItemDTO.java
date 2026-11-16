package com.examscheduler.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class TimetableItemDTO {
    private Long subjectId;
    private String subjectName;
    private String subjectCode;
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private int semester;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int durationMinutes;
    private int studentCount;
    private Long roomId;
    private String roomNumber;
    private int roomCapacity;
    private boolean multiRoomRequired;
    private String status;

    private java.util.List<Long> roomIds = new java.util.ArrayList<>();
    private java.util.List<String> roomNames = new java.util.ArrayList<>();
    private int totalAllocatedCapacity;
    private int remainingSeats;
    private int requiredInvigilators = 1;
    private java.util.List<Long> facultyIds = new java.util.ArrayList<>();
    private java.util.List<String> facultyNames = new java.util.ArrayList<>();
    private String roomStatus = "PENDING";
    private String invigilatorStatus = "PENDING";
    private String readinessStatus = "NEEDS_ROOM";

    public TimetableItemDTO() {}

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public int getRoomCapacity() { return roomCapacity; }
    public void setRoomCapacity(int roomCapacity) { this.roomCapacity = roomCapacity; }

    public boolean isMultiRoomRequired() { return multiRoomRequired; }
    public void setMultiRoomRequired(boolean multiRoomRequired) { this.multiRoomRequired = multiRoomRequired; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.util.List<Long> getRoomIds() { return roomIds; }
    public void setRoomIds(java.util.List<Long> roomIds) { this.roomIds = roomIds; }

    public java.util.List<String> getRoomNames() { return roomNames; }
    public void setRoomNames(java.util.List<String> roomNames) { this.roomNames = roomNames; }

    public int getTotalAllocatedCapacity() { return totalAllocatedCapacity; }
    public void setTotalAllocatedCapacity(int totalAllocatedCapacity) { this.totalAllocatedCapacity = totalAllocatedCapacity; }

    public int getRemainingSeats() { return remainingSeats; }
    public void setRemainingSeats(int remainingSeats) { this.remainingSeats = remainingSeats; }

    public int getRequiredInvigilators() { return requiredInvigilators; }
    public void setRequiredInvigilators(int requiredInvigilators) { this.requiredInvigilators = requiredInvigilators; }

    public java.util.List<Long> getFacultyIds() { return facultyIds; }
    public void setFacultyIds(java.util.List<Long> facultyIds) { this.facultyIds = facultyIds; }

    public java.util.List<String> getFacultyNames() { return facultyNames; }
    public void setFacultyNames(java.util.List<String> facultyNames) { this.facultyNames = facultyNames; }

    public String getRoomStatus() { return roomStatus; }
    public void setRoomStatus(String roomStatus) { this.roomStatus = roomStatus; }

    public String getInvigilatorStatus() { return invigilatorStatus; }
    public void setInvigilatorStatus(String invigilatorStatus) { this.invigilatorStatus = invigilatorStatus; }

    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }
}

