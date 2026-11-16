package com.examscheduler.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class ConflictDto {

    public enum ConflictType {
        FACULTY_OVERLAP,
        FACULTY_UNAVAILABLE,
        FACULTY_EXCEEDED_DUTIES,
        ROOM_DOUBLE_BOOKING,
        ROOM_CAPACITY_EXCEEDED,
        ROOM_UNAVAILABLE,
        STUDENT_OVERLAP,
        DUPLICATE_EXAM_SCHEDULE
    }

    private String id;
    private ConflictType type;
    private String conflictCategory; // ROOM, FACULTY, SCHEDULE, STUDENT
    private String title;
    private String description;
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    private LocalDate conflictDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String relatedExamName;
    private String relatedEntity;
    private String suggestedResolution;

    private Long examId1;
    private Long examId2;
    private Long roomId;
    private Long facultyId;
    private Long studentId;

    // Detailed metadata to automatically identify and populate problem examinations
    private String exam1Name;
    private String exam1Course;
    private int exam1Semester;
    private String exam1Subject;
    private LocalDate exam1Date;
    private String exam1Time;
    private String exam1Room;

    private String exam2Name;
    private String exam2Course;
    private int exam2Semester;
    private String exam2Subject;
    private LocalDate exam2Date;
    private String exam2Time;
    private String exam2Room;

    private String facultyName;
    private String facultyDept;
    private String roomNumber;
    private String roomBuilding;
    private int roomCapacity;

    public ConflictDto() {}

    public ConflictDto(ConflictType type, String title, String description, String severity,
                       LocalDate conflictDate, LocalTime startTime, LocalTime endTime,
                       String relatedExamName, String relatedEntity, String suggestedResolution) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.conflictDate = conflictDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.relatedExamName = relatedExamName;
        this.relatedEntity = relatedEntity;
        this.suggestedResolution = suggestedResolution;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ConflictType getType() { return type; }
    public void setType(ConflictType type) { this.type = type; }

    public String getConflictCategory() { return conflictCategory; }
    public void setConflictCategory(String conflictCategory) { this.conflictCategory = conflictCategory; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public LocalDate getConflictDate() { return conflictDate; }
    public void setConflictDate(LocalDate conflictDate) { this.conflictDate = conflictDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getRelatedExamName() { return relatedExamName; }
    public void setRelatedExamName(String relatedExamName) { this.relatedExamName = relatedExamName; }

    public String getRelatedEntity() { return relatedEntity; }
    public void setRelatedEntity(String relatedEntity) { this.relatedEntity = relatedEntity; }

    public String getSuggestedResolution() { return suggestedResolution; }
    public void setSuggestedResolution(String suggestedResolution) { this.suggestedResolution = suggestedResolution; }
    public String getRecommendation() { return suggestedResolution; }

    public Long getExamId1() { return examId1; }
    public void setExamId1(Long examId1) { this.examId1 = examId1; }

    public Long getExamId2() { return examId2; }
    public void setExamId2(Long examId2) { this.examId2 = examId2; }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getExam1Name() { return exam1Name; }
    public void setExam1Name(String exam1Name) { this.exam1Name = exam1Name; }

    public String getExam1Course() { return exam1Course; }
    public void setExam1Course(String exam1Course) { this.exam1Course = exam1Course; }

    public int getExam1Semester() { return exam1Semester; }
    public void setExam1Semester(int exam1Semester) { this.exam1Semester = exam1Semester; }

    public String getExam1Subject() { return exam1Subject; }
    public void setExam1Subject(String exam1Subject) { this.exam1Subject = exam1Subject; }

    public LocalDate getExam1Date() { return exam1Date; }
    public void setExam1Date(LocalDate exam1Date) { this.exam1Date = exam1Date; }

    public String getExam1Time() { return exam1Time; }
    public void setExam1Time(String exam1Time) { this.exam1Time = exam1Time; }

    public String getExam1Room() { return exam1Room; }
    public void setExam1Room(String exam1Room) { this.exam1Room = exam1Room; }

    public String getExam2Name() { return exam2Name; }
    public void setExam2Name(String exam2Name) { this.exam2Name = exam2Name; }

    public String getExam2Course() { return exam2Course; }
    public void setExam2Course(String exam2Course) { this.exam2Course = exam2Course; }

    public int getExam2Semester() { return exam2Semester; }
    public void setExam2Semester(int exam2Semester) { this.exam2Semester = exam2Semester; }

    public String getExam2Subject() { return exam2Subject; }
    public void setExam2Subject(String exam2Subject) { this.exam2Subject = exam2Subject; }

    public LocalDate getExam2Date() { return exam2Date; }
    public void setExam2Date(LocalDate exam2Date) { this.exam2Date = exam2Date; }

    public String getExam2Time() { return exam2Time; }
    public void setExam2Time(String exam2Time) { this.exam2Time = exam2Time; }

    public String getExam2Room() { return exam2Room; }
    public void setExam2Room(String exam2Room) { this.exam2Room = exam2Room; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }

    public String getFacultyDept() { return facultyDept; }
    public void setFacultyDept(String facultyDept) { this.facultyDept = facultyDept; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getRoomBuilding() { return roomBuilding; }
    public void setRoomBuilding(String roomBuilding) { this.roomBuilding = roomBuilding; }

    public int getRoomCapacity() { return roomCapacity; }
    public void setRoomCapacity(int roomCapacity) { this.roomCapacity = roomCapacity; }
}
