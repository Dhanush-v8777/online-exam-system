package com.examscheduler.service;

import com.examscheduler.entity.AuditLog;
import com.examscheduler.entity.User;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.AuditLogRepository;
import com.examscheduler.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void log(AuditAction action, String description, String entityType, Long entityId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setDescription(description);
        log.setRelatedEntityType(entityType);
        log.setRelatedEntityId(entityId);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            userRepository.findByEmail(auth.getName()).ifPresent(log::setPerformedBy);
        }

        auditLogRepository.save(log);
    }

    public void log(AuditAction action, String description) {
        log(action, description, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAll(int page, int size) {
        return auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String keyword, int page, int size) {
        return auditLogRepository.searchByKeyword(keyword, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecent() {
        return auditLogRepository.findTop20ByOrderByTimestampDesc();
    }
}
