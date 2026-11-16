package com.examscheduler.repository;

import com.examscheduler.entity.Invigilation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvigilationRepository extends JpaRepository<Invigilation, Long> {
    @Query("SELECT i FROM Invigilation i JOIN FETCH i.examination e LEFT JOIN FETCH e.examRoom LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.department WHERE i.faculty.id = :facultyId ORDER BY e.examDate DESC, e.startTime DESC")
    List<Invigilation> findByFacultyId(Long facultyId);

    @Query("SELECT i FROM Invigilation i JOIN FETCH i.examination e LEFT JOIN FETCH e.examRoom LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.department WHERE i.faculty.id = :facultyId ORDER BY e.examDate ASC, e.startTime ASC")
    List<Invigilation> findByFacultyIdOrderByExamDateAsc(Long facultyId);

    List<Invigilation> findByExaminationId(Long examinationId);
    Optional<Invigilation> findByFacultyIdAndExaminationId(Long facultyId, Long examinationId);
    boolean existsByFacultyIdAndExaminationId(Long facultyId, Long examinationId);

    @Query("SELECT i FROM Invigilation i JOIN FETCH i.examination e LEFT JOIN FETCH e.examRoom LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.department WHERE i.faculty.id = :facultyId AND e.examDate >= :fromDate ORDER BY e.examDate ASC, e.startTime ASC")
    List<Invigilation> findByFacultyFromDate(Long facultyId, LocalDate fromDate);

    @Query("SELECT COUNT(i) FROM Invigilation i WHERE i.faculty.id = :facultyId")
    long countByFacultyId(Long facultyId);

    @Query("SELECT i FROM Invigilation i WHERE i.examination.examDate = :date AND i.faculty.id = :facultyId")
    List<Invigilation> findByFacultyAndDate(Long facultyId, LocalDate date);

    @Query("SELECT i FROM Invigilation i JOIN i.examination e WHERE e.examDate = :date AND e.startTime < :endTime AND :startTime < FUNCTION('ADDTIME', e.startTime, FUNCTION('SEC_TO_TIME', e.durationMinutes * 60)) AND i.faculty.id = :facultyId")
    List<Invigilation> findOverlappingAssignments(Long facultyId, LocalDate date, java.time.LocalTime startTime, java.time.LocalTime endTime);

    @Query("SELECT COUNT(i) FROM Invigilation i WHERE i.examination.id IN (SELECT e.id FROM Examination e WHERE e.status = 'PUBLISHED' OR e.status = 'SCHEDULED')")
    long countActiveAssignments();

    @Query("SELECT i.faculty.department.id, COUNT(i) FROM Invigilation i GROUP BY i.faculty.department.id")
    List<Object[]> countByDepartment();

    @Query("SELECT DISTINCT i.faculty.id FROM Invigilation i WHERE i.examination.examDate = :date AND (:excludeExamId IS NULL OR i.examination.id <> :excludeExamId)")
    List<Long> findAssignedFacultyIdsOnDate(@org.springframework.data.repository.query.Param("date") LocalDate date,
                                           @org.springframework.data.repository.query.Param("excludeExamId") Long excludeExamId);

    @Query("SELECT DISTINCT i.faculty.id FROM Invigilation i WHERE i.examination.examDate = :date")
    List<Long> findAssignedFacultyIdsOnDate(@org.springframework.data.repository.query.Param("date") LocalDate date);
}
