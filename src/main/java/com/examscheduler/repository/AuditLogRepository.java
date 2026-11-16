package com.examscheduler.repository;

import com.examscheduler.entity.AuditLog;
import com.examscheduler.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
    List<AuditLog> findByPerformedByIdOrderByTimestampDesc(Long userId);
    List<AuditLog> findByActionOrderByTimestampDesc(AuditAction action);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    List<AuditLog> findByDateRange(LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM AuditLog a WHERE LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY a.timestamp DESC")
    Page<AuditLog> searchByKeyword(String keyword, Pageable pageable);

    List<AuditLog> findTop20ByOrderByTimestampDesc();
}
