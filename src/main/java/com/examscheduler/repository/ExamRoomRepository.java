package com.examscheduler.repository;

import com.examscheduler.entity.ExamRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRoomRepository extends JpaRepository<ExamRoom, Long> {
    List<ExamRoom> findByAvailableTrue();
    List<ExamRoom> findByBuilding(String building);
    boolean existsByRoomNumberAndBuilding(String roomNumber, String building);
    boolean existsByRoomNumberAndBuildingAndIdNot(String roomNumber, String building, Long id);

    @Query("SELECT r FROM ExamRoom r WHERE r.available = true AND r.capacity >= :minCapacity ORDER BY r.capacity ASC")
    List<ExamRoom> findAvailableRoomsWithMinCapacity(int minCapacity);

    @Query("SELECT DISTINCT r.building FROM ExamRoom r ORDER BY r.building")
    List<String> findAllBuildings();

    @Query("SELECT r FROM ExamRoom r WHERE LOWER(r.roomNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(r.building) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<ExamRoom> searchByKeyword(String keyword);

    @Query("SELECT DISTINCT r.id FROM ExamRoom r WHERE r.id IN (" +
           "  SELECT e.examRoom.id FROM Examination e WHERE e.examDate = :date AND e.status <> com.examscheduler.enums.ExamStatus.CANCELLED " +
           "  AND (:excludeExamId IS NULL OR e.id <> :excludeExamId) AND e.examRoom IS NOT NULL" +
           ") OR r.id IN (" +
           "  SELECT sa.examRoom.id FROM SeatingAllocation sa WHERE sa.examination.examDate = :date " +
           "  AND sa.examination.status <> com.examscheduler.enums.ExamStatus.CANCELLED " +
           "  AND (:excludeExamId IS NULL OR sa.examination.id <> :excludeExamId)" +
           ")")
    List<Long> findOccupiedRoomIdsOnDate(@org.springframework.data.repository.query.Param("date") java.time.LocalDate date,
                                        @org.springframework.data.repository.query.Param("excludeExamId") Long excludeExamId);
}
