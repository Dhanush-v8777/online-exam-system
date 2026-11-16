package com.examscheduler.security;

import com.examscheduler.entity.User;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.repository.AuditLogRepository;
import com.examscheduler.entity.AuditLog;
import com.examscheduler.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class AuthenticationEventListener {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuthenticationEventListener(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            AuditLog log = new AuditLog();
            log.setPerformedBy(user);
            log.setAction(AuditAction.USER_LOGIN);
            log.setDescription("User logged in: " + user.getFullName() + " (" + email + ")");
            log.setRelatedEntityType("User");
            log.setRelatedEntityId(user.getId());
            auditLogRepository.save(log);
        }
    }
}
