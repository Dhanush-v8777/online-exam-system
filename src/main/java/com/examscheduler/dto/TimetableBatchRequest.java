package com.examscheduler.dto;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TimetableBatchRequest {
    private Long departmentId;
    private int semester;
    private String examType = "END_SEMESTER_EXAMINATION";
    private String examName;
    private List<Item> items = new ArrayList<>();

    public TimetableBatchRequest() {}

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public String getExamType() { return examType; }
    public void setExamType(String examType) { this.examType = examType; }

    public String getExamName() { return examName; }
    public void setExamName(String examName) { this.examName = examName; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        private Long subjectId;
        private String examType;
        private String examName;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate examDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime startTime = LocalTime.of(10, 0);
        private int durationMinutes = 180;
        private Long roomId;
        private List<Long> roomIds = new ArrayList<>();
        private List<Long> facultyIds = new ArrayList<>();

        public Item() {}

        public String getExamType() { return examType; }
        public void setExamType(String examType) { this.examType = examType; }

        public String getExamName() { return examName; }
        public void setExamName(String examName) { this.examName = examName; }

        public Long getSubjectId() { return subjectId; }
        public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

        public LocalDate getExamDate() { return examDate; }
        public void setExamDate(LocalDate examDate) { this.examDate = examDate; }

        public LocalTime getStartTime() { return startTime; }
        public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

        public Long getRoomId() { return roomId; }
        public void setRoomId(Long roomId) { this.roomId = roomId; }

        public List<Long> getRoomIds() { return roomIds; }
        public void setRoomIds(List<Long> roomIds) { this.roomIds = roomIds; }

        public List<Long> getFacultyIds() { return facultyIds; }
        public void setFacultyIds(List<Long> facultyIds) { this.facultyIds = facultyIds; }
    }
}

