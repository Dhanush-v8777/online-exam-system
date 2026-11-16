package com.examscheduler.service;

import com.examscheduler.entity.Faculty;
import com.examscheduler.entity.User;
import com.examscheduler.enums.AuditAction;
import com.examscheduler.enums.UserRole;
import com.examscheduler.repository.FacultyRepository;
import com.examscheduler.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserImportService {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserImportService(UserRepository userRepository,
                             FacultyRepository facultyRepository,
                             PasswordEncoder passwordEncoder,
                             AuditService auditService) {
        this.userRepository = userRepository;
        this.facultyRepository = facultyRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Saves a user in its own independent transaction (Propagation.REQUIRES_NEW).
     * If a duplicate key violation or constraint error occurs, only this transaction
     * rolls back, allowing the outer CSV import batch loop to continue safely.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User saveSingleUser(String fullName, String email, String rawPassword, UserRole role) {
        String cleanEmail = email.trim();
        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new DuplicateEmailException("Email address already exists.");
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        user.setAccountLocked(false);

        User saved = userRepository.saveAndFlush(user);

        if (role == UserRole.FACULTY) {
            Optional<Faculty> facOpt = facultyRepository.findByEmail(saved.getEmail());
            if (facOpt.isPresent()) {
                Faculty fac = facOpt.get();
                if (fac.getUser() == null) {
                    fac.setUser(saved);
                    facultyRepository.saveAndFlush(fac);
                }
            }
        }

        auditService.log(AuditAction.USER_CREATED,
                "Imported user " + saved.getEmail() + " with role " + saved.getRole() + " via CSV batch",
                "User", saved.getId());

        return saved;
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException(String message) {
            super(message);
        }
    }
}

