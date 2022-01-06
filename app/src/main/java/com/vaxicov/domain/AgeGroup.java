package com.vaxicov.domain;

import androidx.annotation.Nullable;

/**
 * Age groups exposed by the CoWIN API through {@code min_age_limit}
 * (15 for the 15-17 drive that opened on 3 January 2022, 18 and 45).
 * <p>
 * {@link #ALL} matches every session; the other groups match sessions whose
 * minimum age limit equals the group's floor.
 */
public enum AgeGroup {
    ALL("All", -1),
    TEENS_15_17("15-17", 15),
    ADULTS_18_44("18-44", 18),
    SENIORS_45_PLUS("45+", 45);

    private final String label;
    private final int minAge;

    AgeGroup(String label, int minAge) {
        this.label = label;
        this.minAge = minAge;
    }

    /** Human readable label, also used as the persisted value. */
    public String label() {
        return label;
    }

    /** Minimum age of the group, or {@code -1} for {@link #ALL}. */
    public int minAge() {
        return minAge;
    }

    /** @return whether a session with the given {@code min_age_limit} belongs to this group. */
    public boolean matches(@Nullable Integer minAgeLimit) {
        if (this == ALL) {
            return true;
        }
        return minAgeLimit != null && minAgeLimit == minAge;
    }

    /** Parses a label produced by {@link #label()}; unknown or empty input maps to {@link #ALL}. */
    public static AgeGroup fromLabel(@Nullable String label) {
        if (label == null) {
            return ALL;
        }
        String trimmed = label.trim();
        for (AgeGroup group : values()) {
            if (group.label.equalsIgnoreCase(trimmed)) {
                return group;
            }
        }
        return ALL;
    }
}
