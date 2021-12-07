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

    private static final int MIN_PINCODE = 100000;
    private static final int MAX_PINCODE = 999999;

    private final Mode mode;
    private final int pincode;
    private final int districtId;
    @Nullable private final String stateName;
    @Nullable private final String districtName;
    @NonNull private final AgeGroup ageGroup;

    private SearchQuery(Mode mode, int pincode, int districtId,
                        @Nullable String stateName, @Nullable String districtName,
                        @NonNull AgeGroup ageGroup) {
        this.mode = mode;
        this.pincode = pincode;
        this.districtId = districtId;
        this.stateName = stateName;
        this.districtName = districtName;
        this.ageGroup = ageGroup;
    }

    public static SearchQuery byPin(int pincode, @NonNull AgeGroup ageGroup) {
        return new SearchQuery(Mode.PIN, pincode, 0, null, null, ageGroup);
    }

    public static SearchQuery byDistrict(int districtId, @Nullable String stateName,
                                         @Nullable String districtName, @NonNull AgeGroup ageGroup) {
        return new SearchQuery(Mode.DISTRICT, 0, districtId, stateName, districtName, ageGroup);
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
        return result;
    }

    @Override
    public String toString() {
        return "SearchQuery{" + mode + ", " + describeArea() + ", " + ageGroup.label() + "}";
    }

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
