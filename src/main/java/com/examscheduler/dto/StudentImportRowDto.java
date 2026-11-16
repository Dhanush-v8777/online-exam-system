package com.examscheduler.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class StudentImportRowDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private int rowNumber;
    private String registerNumber;
    private String fullName;
    private String email;
    private String rawDepartment;
    private String rawSemester;
    private Integer semester;
    private Long matchedDepartmentId;
    private String matchedDepartmentCode;
    private String matchedDepartmentName;
    private String academicYear = "2025-26";

    private boolean valid;
    private boolean duplicate;
    private String duplicateReason;
    private List<String> errors = new ArrayList<>();

    public StudentImportRowDto() {}

    public int getRowNumber() { return rowNumber; }
    public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRawDepartment() { return rawDepartment; }
    public void setRawDepartment(String rawDepartment) { this.rawDepartment = rawDepartment; }

    public String getRawSemester() { return rawSemester; }
    public void setRawSemester(String rawSemester) { this.rawSemester = rawSemester; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public Long getMatchedDepartmentId() { return matchedDepartmentId; }
    public void setMatchedDepartmentId(Long matchedDepartmentId) { this.matchedDepartmentId = matchedDepartmentId; }

    public String getMatchedDepartmentCode() { return matchedDepartmentCode; }
    public void setMatchedDepartmentCode(String matchedDepartmentCode) { this.matchedDepartmentCode = matchedDepartmentCode; }

    public String getMatchedDepartmentName() { return matchedDepartmentName; }
    public void setMatchedDepartmentName(String matchedDepartmentName) { this.matchedDepartmentName = matchedDepartmentName; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public boolean isDuplicate() { return duplicate; }
    public void setDuplicate(boolean duplicate) { this.duplicate = duplicate; }

    public String getDuplicateReason() { return duplicateReason; }
    public void setDuplicateReason(String duplicateReason) { this.duplicateReason = duplicateReason; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}

