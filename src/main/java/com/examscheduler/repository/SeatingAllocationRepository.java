package com.examscheduler.repository;

import com.examscheduler.entity.SeatingAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatingAllocationRepository extends JpaRepository<SeatingAllocation, Long> {

    @Query("SELECT sa FROM SeatingAllocation sa JOIN FETCH sa.student s LEFT JOIN FETCH sa.examRoom r WHERE sa.examination.id = :examinationId ORDER BY sa.examRoom.roomNumber ASC, sa.seatNumber ASC")
    List<SeatingAllocation> findByExaminationId(Long examinationId);

    List<SeatingAllocation> findByExaminationIdAndExamRoomId(Long examinationId, Long roomId);

    Optional<SeatingAllocation> findByStudentIdAndExaminationId(Long studentId, Long examinationId);

    boolean existsByStudentIdAndExaminationId(Long studentId, Long examinationId);

    boolean existsByExaminationIdAndExamRoomIdAndSeatNumber(Long examinationId, Long roomId, String seatNumber);

    @Query("SELECT COUNT(sa) FROM SeatingAllocation sa WHERE sa.examination.id = :examId AND sa.examRoom.id = :roomId")
    long countSeatsAllocatedInRoom(Long examId, Long roomId);

    long countByExaminationId(Long examinationId);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM SeatingAllocation sa WHERE sa.examination.id = :examinationId")
    void deleteByExaminationId(Long examinationId);
}
