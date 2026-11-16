package com.examscheduler.dto;

import com.examscheduler.entity.ExamRoom;
import java.util.ArrayList;
import java.util.List;

public class SeatingHallDTO {

    public static class DeskSlotDTO {
        private String seatNumber;
        private String studentName;
        private String registerNumber;
        private boolean allocated;
        private int row;
        private int col;
        private String section; // "LEFT" or "RIGHT"

        public DeskSlotDTO() {}

        public DeskSlotDTO(String seatNumber, String studentName, String registerNumber, boolean allocated, int row, int col, String section) {
            this.seatNumber = seatNumber;
            this.studentName = studentName;
            this.registerNumber = registerNumber;
            this.allocated = allocated;
            this.row = row;
            this.col = col;
            this.section = section;
        }

        public String getSeatNumber() { return seatNumber; }
        public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }

        public String getRegisterNumber() { return registerNumber; }
        public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

        public boolean isAllocated() { return allocated; }
        public void setAllocated(boolean allocated) { this.allocated = allocated; }

        public int getRow() { return row; }
        public void setRow(int row) { this.row = row; }

        public int getCol() { return col; }
        public void setCol(int col) { this.col = col; }

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
    }

    public static class SeatingRowDTO {
        private int rowNumber;
        private DeskSlotDTO leftDesk1;
        private DeskSlotDTO leftDesk2;
        private DeskSlotDTO rightDesk1;
        private DeskSlotDTO rightDesk2;

        public SeatingRowDTO() {}

        public SeatingRowDTO(int rowNumber, DeskSlotDTO leftDesk1, DeskSlotDTO leftDesk2, DeskSlotDTO rightDesk1, DeskSlotDTO rightDesk2) {
            this.rowNumber = rowNumber;
            this.leftDesk1 = leftDesk1;
            this.leftDesk2 = leftDesk2;
            this.rightDesk1 = rightDesk1;
            this.rightDesk2 = rightDesk2;
        }

        public int getRowNumber() { return rowNumber; }
        public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

        public DeskSlotDTO getLeftDesk1() { return leftDesk1; }
        public void setLeftDesk1(DeskSlotDTO leftDesk1) { this.leftDesk1 = leftDesk1; }

        public DeskSlotDTO getLeftDesk2() { return leftDesk2; }
        public void setLeftDesk2(DeskSlotDTO leftDesk2) { this.leftDesk2 = leftDesk2; }

        public DeskSlotDTO getRightDesk1() { return rightDesk1; }
        public void setRightDesk1(DeskSlotDTO rightDesk1) { this.rightDesk1 = rightDesk1; }

        public DeskSlotDTO getRightDesk2() { return rightDesk2; }
        public void setRightDesk2(DeskSlotDTO rightDesk2) { this.rightDesk2 = rightDesk2; }
    }

    private int totalRows = 4;
    private int totalDesks = 16;
    private int allocatedCount = 0;
    private int availableCount = 16;
    private int totalEligibleStudents = 0;
    private int totalAllocatedStudents = 0;
    private int remainingUnallocated = 0;
    private int totalRoomCapacity = 0;
    private int availableRoomCapacity = 0;
    private ExamRoom currentRoom;
    private List<ExamRoom> allocatedRooms = new ArrayList<>();
    private List<SeatingRowDTO> rows = new ArrayList<>();

    public SeatingHallDTO() {}

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getTotalDesks() { return totalDesks; }
    public void setTotalDesks(int totalDesks) { this.totalDesks = totalDesks; }

    public int getAllocatedCount() { return allocatedCount; }
    public void setAllocatedCount(int allocatedCount) { this.allocatedCount = allocatedCount; }

    public int getAvailableCount() { return availableCount; }
    public void setAvailableCount(int availableCount) { this.availableCount = availableCount; }

    public int getTotalEligibleStudents() { return totalEligibleStudents; }
    public void setTotalEligibleStudents(int totalEligibleStudents) { this.totalEligibleStudents = totalEligibleStudents; }

    public int getTotalAllocatedStudents() { return totalAllocatedStudents; }
    public void setTotalAllocatedStudents(int totalAllocatedStudents) { this.totalAllocatedStudents = totalAllocatedStudents; }

    public int getRemainingUnallocated() { return remainingUnallocated; }
    public void setRemainingUnallocated(int remainingUnallocated) { this.remainingUnallocated = remainingUnallocated; }

    public int getTotalRoomCapacity() { return totalRoomCapacity; }
    public void setTotalRoomCapacity(int totalRoomCapacity) { this.totalRoomCapacity = totalRoomCapacity; }

    public int getAvailableRoomCapacity() { return availableRoomCapacity; }
    public void setAvailableRoomCapacity(int availableRoomCapacity) { this.availableRoomCapacity = availableRoomCapacity; }

    public int getVacantDesks() {
        return Math.max(0, totalRoomCapacity - totalAllocatedStudents);
    }

    public ExamRoom getCurrentRoom() { return currentRoom; }
    public void setCurrentRoom(ExamRoom currentRoom) { this.currentRoom = currentRoom; }

    public List<ExamRoom> getAllocatedRooms() { return allocatedRooms; }
    public void setAllocatedRooms(List<ExamRoom> allocatedRooms) { this.allocatedRooms = allocatedRooms; }

    public List<SeatingRowDTO> getRows() { return rows; }
    public void setRows(List<SeatingRowDTO> rows) { this.rows = rows; }
}
