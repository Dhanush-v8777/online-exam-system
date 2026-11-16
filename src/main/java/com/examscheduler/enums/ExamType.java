package com.examscheduler.enums;

import java.util.List;

public enum ExamType {
    INTERNAL_EXAMINATION("Internal Examination"),
    PREPARATORY_EXAMINATION("Preparatory Examination"),
    MID_TERM_EXAMINATION("Mid-Term Examination"),
    END_SEMESTER_EXAMINATION("End-Semester Examination"),
    VIVA_EXAMINATION("Viva Examination"),
    LABORATORY_EXAMINATION("Laboratory Examination"),

    // Backwards-compatible mappings for existing database records and legacy short names
    REGULAR("End-Semester Examination"),
    SUPPLEMENTARY("Supplementary Examination"),
    RETEST("Retest Examination"),
    PRACTICAL("Laboratory Examination"),
    INTERNAL("Internal Examination"),
    PREPARATORY("Preparatory Examination"),
    MID_TERM("Mid-Term Examination"),
    END_SEMESTER("End-Semester Examination"),
    VIVA("Viva Examination"),
    LABORATORY("Laboratory Examination");

    private final String displayName;

    ExamType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static List<ExamType> getStandardTypes() {
        return List.of(
            INTERNAL_EXAMINATION,
            PREPARATORY_EXAMINATION,
            MID_TERM_EXAMINATION,
            END_SEMESTER_EXAMINATION,
            VIVA_EXAMINATION,
            LABORATORY_EXAMINATION
        );
    }

    public static ExamType fromString(String text) {
        if (text == null || text.isBlank()) {
            return END_SEMESTER_EXAMINATION;
        }
        String clean = text.trim();
        for (ExamType t : ExamType.values()) {
            if (t.name().equalsIgnoreCase(clean) || t.displayName.equalsIgnoreCase(clean)) {
                return t;
            }
        }
        String lower = clean.toLowerCase();
        if (lower.contains("internal")) return INTERNAL_EXAMINATION;
        if (lower.contains("preparatory") || lower.contains("prep")) return PREPARATORY_EXAMINATION;
        if (lower.contains("mid")) return MID_TERM_EXAMINATION;
        if (lower.contains("end") || lower.contains("semester") || lower.contains("regular")) return END_SEMESTER_EXAMINATION;
        if (lower.contains("viva")) return VIVA_EXAMINATION;
        if (lower.contains("lab") || lower.contains("practical")) return LABORATORY_EXAMINATION;
        return END_SEMESTER_EXAMINATION;
    }
}

