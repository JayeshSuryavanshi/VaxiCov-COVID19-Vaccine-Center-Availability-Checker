package com.vaxicov.domain;

import androidx.annotation.Nullable;

import com.vaxicov.pojo.Session;

/**
 * Which dose the user needs. CoWIN reports per-dose availability through
 * {@code available_capacity_dose1} / {@code available_capacity_dose2};
 * registries that do not split doses fall back to the total capacity.
 */
public enum DoseType {
    ANY("Any"),
    FIRST("Dose 1"),
    SECOND("Dose 2");

    private final String label;

    DoseType(String label) {
        this.label = label;
    }

    /** Human readable label, also used as the persisted value. */
    public String label() {
        return label;
    }

    /** Doses available in the session for this dose type; never negative. */
    public int capacity(@Nullable Session session) {
        if (session == null) {
            return 0;
        }
        Integer total = session.getAvailableCapacity();
        Integer perDose = null;
        if (this == FIRST) {
            perDose = session.getAvailableCapacityDose1();
        } else if (this == SECOND) {
            perDose = session.getAvailableCapacityDose2();
        }
        Integer value = perDose != null ? perDose : total;
        return value == null || value < 0 ? 0 : value;
    }

    /** Parses a label produced by {@link #label()}; unknown or empty input maps to {@link #ANY}. */
    public static DoseType fromLabel(@Nullable String label) {
        if (label == null) {
            return ANY;
        }
        String trimmed = label.trim();
        for (DoseType type : values()) {
            if (type.label.equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        return ANY;
    }
}
