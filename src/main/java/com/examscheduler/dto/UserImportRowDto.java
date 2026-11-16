package com.examscheduler.dto;

import com.examscheduler.enums.UserRole;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class UserImportRowDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int rowNumber;
    private String fullName;
    private String email;
    private String rawPassword;
    private String rawRole;
    private UserRole resolvedRole;
    private boolean valid;
    private boolean duplicateInFile;
    private boolean duplicateInDb;
    private List<String> errors = new ArrayList<>();

    public UserImportRowDto() {}

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRawPassword() { return rawPassword; }
    public void setRawPassword(String rawPassword) { this.rawPassword = rawPassword; }

    public String getMaskedPassword() {
        return (rawPassword != null && !rawPassword.isEmpty()) ? "••••••••" : "-";
    }

    public String getRawRole() { return rawRole; }
    public void setRawRole(String rawRole) { this.rawRole = rawRole; }

    public UserRole getResolvedRole() { return resolvedRole; }
    public void setResolvedRole(UserRole resolvedRole) { this.resolvedRole = resolvedRole; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public boolean isDuplicateInFile() { return duplicateInFile; }
    public void setDuplicateInFile(boolean duplicateInFile) { this.duplicateInFile = duplicateInFile; }

    public boolean isDuplicateInDb() { return duplicateInDb; }
    public void setDuplicateInDb(boolean duplicateInDb) { this.duplicateInDb = duplicateInDb; }

    public boolean isDuplicate() { return duplicateInFile || duplicateInDb; }

    public String getDuplicateReason() {
        if (duplicateInDb) {
            return "Email address already exists.";
        }
        if (duplicateInFile) {
            return "Duplicate email address in CSV file.";
        }
        return "Duplicate record.";
    }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    public void addError(String error) { this.errors.add(error); }

    public String getErrorString() {
        return String.join("; ", errors);
    }
}

