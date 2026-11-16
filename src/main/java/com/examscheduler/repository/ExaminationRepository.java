package com.examscheduler.repository;

import com.examscheduler.entity.Examination;
import com.examscheduler.enums.ExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExaminationRepository extends JpaRepository<Examination, Long> {
    List<Examination> findByStatus(ExamStatus status);
    List<Examination> findByDepartmentId(Long departmentId);
    List<Examination> findBySemester(int semester);
    List<Examination> findByExamDate(LocalDate examDate);
    List<Examination> findByExamDateBetween(LocalDate startDate, LocalDate endDate);
    List<Examination> findByExamDateGreaterThanEqualOrderByExamDateAscStartTimeAsc(LocalDate date);
    List<Examination> findByExamDateBeforeOrderByExamDateDescStartTimeDesc(LocalDate date);

    @Query("SELECT e FROM Examination e WHERE e.examDate >= :today ORDER BY e.examDate ASC, e.startTime ASC")
    List<Examination> findUpcomingExams(LocalDate today);

    @Query("SELECT e FROM Examination e WHERE e.examDate = :date AND e.examRoom.id = :roomId AND (:excludeId IS NULL OR e.id != :excludeId) AND (e.startTime < :endTime AND :startTime < FUNCTION('ADDTIME', e.startTime, FUNCTION('SEC_TO_TIME', e.durationMinutes * 60)))")
    List<Examination> findConflictingExamsInRoom(LocalDate date, Long roomId, Long excludeId, java.time.LocalTime startTime, java.time.LocalTime endTime);

    @Query("SELECT COUNT(e) FROM Examination e WHERE e.status = :status")
    long countByStatus(ExamStatus status);

    @Query("SELECT e FROM Examination e WHERE LOWER(e.examName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(e.subject.subjectName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Examination> searchByKeyword(String keyword);

    @Query("SELECT e FROM Examination e WHERE e.department.id = :deptId AND e.semester = :semester ORDER BY e.examDate ASC")
    List<Examination> findByDepartmentAndSemester(Long deptId, int semester);

    @Query("SELECT e FROM Examination e WHERE e.examRoom.id = :roomId AND e.examDate = :date")
    List<Examination> findByRoomAndDate(Long roomId, LocalDate date);

    long countByExamDateGreaterThanEqual(LocalDate date);

    @Query("SELECT e FROM Examination e WHERE e.invigilations IS EMPTY AND e.status != 'COMPLETED'")
    List<Examination> findUnassignedExaminations();

    @Query("SELECT e FROM Examination e WHERE e.examDate = :date AND e.status != com.examscheduler.enums.ExamStatus.CANCELLED AND (:excludeId IS NULL OR e.id != :excludeId)")
    List<Examination> findActiveExamsOnDate(@org.springframework.data.repository.query.Param("date") LocalDate date,
                                            @org.springframework.data.repository.query.Param("excludeId") Long excludeId);

    @Query("SELECT DISTINCT e.examDate FROM Examination e WHERE e.status != com.examscheduler.enums.ExamStatus.CANCELLED AND e.examDate IS NOT NULL AND (:excludeId IS NULL OR e.id != :excludeId) ORDER BY e.examDate ASC")
    List<LocalDate> findBookedDates(@org.springframework.data.repository.query.Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(e) > 0 FROM Examination e WHERE e.subject.id = :subjectId AND e.department.id = :deptId AND e.semester = :semester AND e.status != com.examscheduler.enums.ExamStatus.CANCELLED")
    boolean existsActiveExamForSubject(@org.springframework.data.repository.query.Param("subjectId") Long subjectId,
                                       @org.springframework.data.repository.query.Param("deptId") Long deptId,
                                       @org.springframework.data.repository.query.Param("semester") int semester);
}
