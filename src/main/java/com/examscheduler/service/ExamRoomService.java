package com.examscheduler.service;

import com.examscheduler.entity.ExamRoom;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.ExamRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ExamRoomService {

    private final ExamRoomRepository examRoomRepository;
    private final AuditService auditService;

    public ExamRoomService(ExamRoomRepository examRoomRepository, AuditService auditService) {
        this.examRoomRepository = examRoomRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ExamRoom> getAll() {
        return examRoomRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ExamRoom> findById(Long id) {
        return examRoomRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ExamRoom> getAvailable() {
        return examRoomRepository.findByAvailableTrue();
    }

    @Transactional(readOnly = true)
    public List<ExamRoom> getAvailableWithMinCapacity(int minCapacity) {
        return examRoomRepository.findAvailableRoomsWithMinCapacity(minCapacity);
    }

    @Transactional(readOnly = true)
    public List<String> getAllBuildings() {
        return examRoomRepository.findAllBuildings();
    }

    @Transactional(readOnly = true)
    public List<ExamRoom> getAvailableRoomsOnDate(java.time.LocalDate date, Long excludeExamId) {
        List<ExamRoom> allAvailable = examRoomRepository.findByAvailableTrue();
        if (date == null) return allAvailable;
        List<Long> occupiedIds = examRoomRepository.findOccupiedRoomIdsOnDate(date, excludeExamId);
        if (occupiedIds == null || occupiedIds.isEmpty()) return allAvailable;
        return allAvailable.stream()
            .filter(r -> !occupiedIds.contains(r.getId()))
            .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExamRoom> getOccupiedRoomsOnDate(java.time.LocalDate date, Long excludeExamId) {
        if (date == null) return java.util.Collections.emptyList();
        List<Long> occupiedIds = examRoomRepository.findOccupiedRoomIdsOnDate(date, excludeExamId);
        if (occupiedIds == null || occupiedIds.isEmpty()) return java.util.Collections.emptyList();
        return examRoomRepository.findAllById(occupiedIds);
    }

    @Transactional(readOnly = true)
    public boolean isRoomAvailableOnDate(Long roomId, java.time.LocalDate date, Long excludeExamId) {
        if (roomId == null || date == null) return true;
        List<Long> occupiedIds = examRoomRepository.findOccupiedRoomIdsOnDate(date, excludeExamId);
        return occupiedIds == null || !occupiedIds.contains(roomId);
    }

    @Transactional(readOnly = true)
    public List<ExamRoom> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return getAll();
        return examRoomRepository.searchByKeyword(keyword.trim());
    }

    public ExamRoom save(ExamRoom room) {
        boolean isNew = room.getId() == null;

        if (isNew && examRoomRepository.existsByRoomNumberAndBuilding(room.getRoomNumber(), room.getBuilding()))
            throw new IllegalArgumentException("Room '" + room.getRoomNumber() + "' in building '" + room.getBuilding() + "' already exists.");
        if (!isNew && examRoomRepository.existsByRoomNumberAndBuildingAndIdNot(room.getRoomNumber(), room.getBuilding(), room.getId()))
            throw new IllegalArgumentException("Room '" + room.getRoomNumber() + "' in building '" + room.getBuilding() + "' already exists.");

        ExamRoom saved = examRoomRepository.save(room);
        auditService.log(
            isNew ? AuditAction.ROOM_ALLOCATED : AuditAction.ROOM_ALLOCATED,
            (isNew ? "Created" : "Updated") + " room: " + saved.getFullRoomName(),
            "ExamRoom", saved.getId()
        );
        return saved;
    }

    public void delete(Long id) {
        ExamRoom room = examRoomRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));
        examRoomRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return examRoomRepository.count();
    }
}
