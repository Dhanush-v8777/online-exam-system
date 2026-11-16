package com.examscheduler.entity;

import com.examscheduler.enums.RoomType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_rooms", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"room_number", "building"}, name = "uk_room_building")
})
public class ExamRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Room number is required")
    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @NotBlank(message = "Building is required")
    @Column(name = "building", nullable = false, length = 50)
    private String building;

    @Column(name = "floor", length = 50)
    private String floor;

    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 500, message = "Capacity cannot exceed 500")
    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType = RoomType.EXAMINATION_HALL;

    @Column(name = "available", nullable = false)
    private boolean available = true;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL)
    private List<Examination> examinations = new ArrayList<>();

    @OneToMany(mappedBy = "examRoom", cascade = CascadeType.ALL)
    private List<SeatingAllocation> seatingAllocations = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public ExamRoom() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomNumber() { return roomNumber; }
    public String getName() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public RoomType getRoomType() { return roomType; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Examination> getExaminations() { return examinations; }
    public void setExaminations(List<Examination> examinations) { this.examinations = examinations; }
    public List<SeatingAllocation> getSeatingAllocations() { return seatingAllocations; }
    public void setSeatingAllocations(List<SeatingAllocation> seatingAllocations) { this.seatingAllocations = seatingAllocations; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getFullRoomName() {
        return roomNumber + " - " + building + (floor != null ? " (Floor " + floor + ")" : "");
    }
}
