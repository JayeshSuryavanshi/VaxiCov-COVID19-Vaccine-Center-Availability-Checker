package com.akshaychavan.vaxicov;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.akshaychavan.vaxicov.domain.AgeGroup;
import com.akshaychavan.vaxicov.domain.SearchQuery;

/**
 * Typed wrapper around the app's single SharedPreferences file. Holds the
 * query the background notifier should watch and the data source setting.
 */
public final class AppPreferences {

    private static final String FILE_NAME = "sharedprefs";

    private static final String KEY_SAMPLE_DATA = "sample_data";

    private static final String KEY_QUERY_MODE = "notifier_mode";
    private static final String KEY_QUERY_PINCODE = "notifier_pincode";
    private static final String KEY_QUERY_DISTRICT_ID = "notifier_district_id";
    private static final String KEY_QUERY_STATE_NAME = "notifier_state_name";
    private static final String KEY_QUERY_DISTRICT_NAME = "notifier_district_name";
    private static final String KEY_QUERY_AGE_GROUP = "notifier_age_group";
    private static final String KEY_LAST_SIGNATURE = "notifier_last_signature";
    /** Public so screens can react to notifier changes through {@link #registerListener}. */
    public static final String KEY_NOTIFIER_MODE = KEY_QUERY_MODE;

    private final SharedPreferences prefs;

    public AppPreferences(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    // ---- Background notifier ----------------------------------------------

    public void saveNotifierQuery(@NonNull SearchQuery query) {
        prefs.edit()
                .putString(KEY_QUERY_MODE, query.getMode().name())
                .putInt(KEY_QUERY_PINCODE, query.getPincode())
                .putInt(KEY_QUERY_DISTRICT_ID, query.getDistrictId())
                .putString(KEY_QUERY_STATE_NAME, query.getStateName())
                .putString(KEY_QUERY_DISTRICT_NAME, query.getDistrictName())
                .putString(KEY_QUERY_AGE_GROUP, query.getAgeGroup().label())
                .remove(KEY_LAST_SIGNATURE)
                .apply();
    }

    /** @return the query the notifier watches, or {@code null} when it is not active. */
    @Nullable
    public SearchQuery getNotifierQuery() {
        String mode = prefs.getString(KEY_QUERY_MODE, null);
        if (mode == null) {
            return null;
        }
        AgeGroup ageGroup = AgeGroup.fromLabel(prefs.getString(KEY_QUERY_AGE_GROUP, null));
        SearchQuery query;
        if (SearchQuery.Mode.PIN.name().equals(mode)) {
            query = SearchQuery.byPin(prefs.getInt(KEY_QUERY_PINCODE, 0), ageGroup);
        } else {
            query = SearchQuery.byDistrict(
                    prefs.getInt(KEY_QUERY_DISTRICT_ID, 0),
                    prefs.getString(KEY_QUERY_STATE_NAME, null),
                    prefs.getString(KEY_QUERY_DISTRICT_NAME, null),
                    ageGroup);
        }
        return query.isValid() ? query : null;
    }

    public void clearNotifierQuery() {
        prefs.edit()
                .remove(KEY_QUERY_MODE)
                .remove(KEY_QUERY_PINCODE)
                .remove(KEY_QUERY_DISTRICT_ID)
                .remove(KEY_QUERY_STATE_NAME)
                .remove(KEY_QUERY_DISTRICT_NAME)
                .remove(KEY_QUERY_AGE_GROUP)
                .remove(KEY_LAST_SIGNATURE)
                .apply();
    }

    @NonNull
    public String getLastSignature() {
        return prefs.getString(KEY_LAST_SIGNATURE, "");
    }

    public void setLastSignature(@NonNull String signature) {
        prefs.edit().putString(KEY_LAST_SIGNATURE, signature).apply();
    }

    // ---- Data source ---------------------------------------------------------

    /** Whether the bundled sample data is used instead of the live registry. */
    public boolean isSampleDataEnabled() {
        return prefs.getBoolean(KEY_SAMPLE_DATA, false);
    }

    public void setSampleDataEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAMPLE_DATA, enabled).apply();
    }

    // ---- Change notifications ---------------------------------------------

    /**
     * Note: SharedPreferences keeps listeners in a weak set, so callers must
     * hold a strong reference to the listener they register.
     */
    public void registerListener(@NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(@NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
