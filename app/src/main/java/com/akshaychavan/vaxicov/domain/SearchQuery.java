package com.akshaychavan.vaxicov.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Immutable description of what the user is looking for: an area (pincode or
 * district) plus an age group. Used both for on-demand searches and for the
 * background notifier, which persists it between runs.
 */
public final class SearchQuery {

    public enum Mode { PIN, DISTRICT }

    /** Label used in the UI and in persistence for "no vaccine filter". */
    public static final String ANY_VACCINE = "Any";

    private static final int MIN_PINCODE = 100000;
    private static final int MAX_PINCODE = 999999;

    private final Mode mode;
    private final int pincode;
    private final int districtId;
    @Nullable private final String stateName;
    @Nullable private final String districtName;
    @NonNull private final AgeGroup ageGroup;
    @NonNull private final DoseType dose;
    @Nullable private final String vaccine;

    private SearchQuery(Mode mode, int pincode, int districtId,
                        @Nullable String stateName, @Nullable String districtName,
                        @NonNull AgeGroup ageGroup, @NonNull DoseType dose, @Nullable String vaccine) {
        this.mode = mode;
        this.pincode = pincode;
        this.districtId = districtId;
        this.stateName = stateName;
        this.districtName = districtName;
        this.ageGroup = ageGroup;
        this.dose = dose;
        this.vaccine = normaliseVaccine(vaccine);
    }

    public static SearchQuery byPin(int pincode, @NonNull AgeGroup ageGroup) {
        return new SearchQuery(Mode.PIN, pincode, 0, null, null, ageGroup, DoseType.ANY, null);
    }

    public static SearchQuery byDistrict(int districtId, @Nullable String stateName,
                                         @Nullable String districtName, @NonNull AgeGroup ageGroup) {
        return new SearchQuery(Mode.DISTRICT, 0, districtId, stateName, districtName, ageGroup, DoseType.ANY, null);
    }

    /**
     * Returns a copy with the dose and vaccine filters set. {@code vaccine} is
     * matched case-insensitively; {@code null}, empty or "Any" means no filter.
     */
    @NonNull
    public SearchQuery withFilters(@NonNull DoseType dose, @Nullable String vaccine) {
        return new SearchQuery(mode, pincode, districtId, stateName, districtName, ageGroup, dose, vaccine);
    }

    @Nullable
    private static String normaliseVaccine(@Nullable String vaccine) {
        if (vaccine == null) {
            return null;
        }
        String trimmed = vaccine.trim();
        if (trimmed.isEmpty() || ANY_VACCINE.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.toUpperCase(Locale.US);
    }

    /** @return whether the raw text is a plausible six digit Indian pincode. */
    public static boolean isValidPincode(@Nullable String text) {
        if (text == null || text.trim().length() != 6) {
            return false;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value >= MIN_PINCODE && value <= MAX_PINCODE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isByPin() {
        return mode == Mode.PIN;
    }

    public int getPincode() {
        return pincode;
    }

    public int getDistrictId() {
        return districtId;
    }

    @Nullable
    public String getStateName() {
        return stateName;
    }

    @Nullable
    public String getDistrictName() {
        return districtName;
    }

    @NonNull
    public AgeGroup getAgeGroup() {
        return ageGroup;
    }

    @NonNull
    public DoseType getDose() {
        return dose;
    }

    /** Upper-cased vaccine name, or {@code null} when any vaccine is acceptable. */
    @Nullable
    public String getVaccine() {
        return vaccine;
    }

    /** @return whether the session's vaccine passes the vaccine filter. */
    public boolean acceptsVaccine(@Nullable String sessionVaccine) {
        if (vaccine == null) {
            return true;
        }
        return sessionVaccine != null && vaccine.equalsIgnoreCase(sessionVaccine.trim());
    }

    /**
     * Short description of the active filters ("45+ · Dose 2 · COVAXIN").
     * Filters left at their default are omitted; "All ages" when none is set.
     */
    @NonNull
    public String describeFilters() {
        StringBuilder sb = new StringBuilder();
        if (ageGroup != AgeGroup.ALL) {
            sb.append(ageGroup.label());
        }
        if (dose != DoseType.ANY) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(dose.label());
        }
        if (vaccine != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(vaccine);
        }
        return sb.length() == 0 ? "All ages" : sb.toString();
    }

    /** @return whether the query holds enough information to be sent to the API. */
    public boolean isValid() {
        if (mode == Mode.PIN) {
            return pincode >= MIN_PINCODE && pincode <= MAX_PINCODE;
        }
        return districtId > 0;
    }

    /** Short description of the searched area for notifications and toasts. */
    @NonNull
    public String describeArea() {
        if (mode == Mode.PIN) {
            return String.format(Locale.US, "pincode %06d", pincode);
        }
        if (districtName != null && stateName != null) {
            return districtName + ", " + stateName;
        }
        if (districtName != null) {
            return districtName;
        }
        return String.format(Locale.US, "district #%d", districtId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchQuery)) return false;
        SearchQuery that = (SearchQuery) o;
        return pincode == that.pincode
                && districtId == that.districtId
                && mode == that.mode
                && ageGroup == that.ageGroup
                && dose == that.dose
                && equalsNullable(vaccine, that.vaccine)
                && equalsNullable(stateName, that.stateName)
                && equalsNullable(districtName, that.districtName);
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + pincode;
        result = 31 * result + districtId;
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (districtName != null ? districtName.hashCode() : 0);
        result = 31 * result + ageGroup.hashCode();
        result = 31 * result + dose.hashCode();
        result = 31 * result + (vaccine != null ? vaccine.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SearchQuery{" + mode + ", " + describeArea() + ", " + describeFilters() + "}";
    }

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
