package com.vaxicov;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vaxicov.domain.AgeGroup;
import com.vaxicov.domain.DoseType;
import com.vaxicov.domain.SearchQuery;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Typed wrapper around the app's single SharedPreferences file. Holds the
 * watchlist the background notifier checks, the last search, and the
 * data-source setting.
 */
public final class AppPreferences {

    /** Upper bound on watched areas: enough for a family, cheap enough to poll. */
    public static final int MAX_WATCHES = 5;

    private static final String FILE_NAME = "sharedprefs";
    private static final String KEY_SAMPLE_DATA = "sample_data";
    private static final String KEY_WATCHES = "watches";
    private static final String KEY_LAST_SEARCH = "last_search";
    private static final String KEY_SIGNATURE_PREFIX = "signature_";

    private static final Type WATCH_LIST = new TypeToken<List<WatchDto>>() {}.getType();

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public AppPreferences(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    // ---- Watchlist -----------------------------------------------------------

    /** Queries the notifier checks, oldest first. Never null. */
    @NonNull
    public List<SearchQuery> getWatches() {
        List<SearchQuery> queries = new ArrayList<>();
        for (WatchDto dto : readWatches()) {
            SearchQuery query = dto.toQuery();
            if (query != null && query.isValid()) {
                queries.add(query);
            }
        }
        return queries;
    }

    public boolean hasWatches() {
        return !getWatches().isEmpty();
    }

    /**
     * Adds a watch. Returns {@code false} when it is already present or the
     * list is full ({@link #MAX_WATCHES}).
     */
    public boolean addWatch(@NonNull SearchQuery query) {
        List<SearchQuery> watches = getWatches();
        if (watches.contains(query) || watches.size() >= MAX_WATCHES) {
            return false;
        }
        watches.add(query);
        writeWatches(watches);
        prefs.edit().remove(signatureKey(query)).apply();
        return true;
    }

    public void removeWatch(@NonNull SearchQuery query) {
        List<SearchQuery> watches = getWatches();
        if (watches.remove(query)) {
            writeWatches(watches);
        }
        prefs.edit().remove(signatureKey(query)).apply();
    }

    public void clearWatches() {
        SharedPreferences.Editor editor = prefs.edit().remove(KEY_WATCHES);
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_SIGNATURE_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /** Signature of the bookable sessions seen on the last check for this watch. */
    @NonNull
    public String getLastSignature(@NonNull SearchQuery query) {
        return prefs.getString(signatureKey(query), "");
    }

    public void setLastSignature(@NonNull SearchQuery query, @NonNull String signature) {
        prefs.edit().putString(signatureKey(query), signature).apply();
    }

    private static String signatureKey(SearchQuery query) {
        return KEY_SIGNATURE_PREFIX + String.format(Locale.US, "%08x", query.hashCode());
    }

    private List<WatchDto> readWatches() {
        String json = prefs.getString(KEY_WATCHES, null);
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<WatchDto> list = gson.fromJson(json, WATCH_LIST);
            return list == null ? Collections.<WatchDto>emptyList() : list;
        } catch (JsonSyntaxException e) {
            return Collections.emptyList();
        }
    }

    private void writeWatches(List<SearchQuery> queries) {
        List<WatchDto> dtos = new ArrayList<>();
        for (SearchQuery query : queries) {
            dtos.add(WatchDto.from(query));
        }
        prefs.edit().putString(KEY_WATCHES, gson.toJson(dtos, WATCH_LIST)).apply();
    }

    // ---- Last search ---------------------------------------------------------

    public void setLastSearch(@NonNull SearchQuery query) {
        prefs.edit().putString(KEY_LAST_SEARCH, gson.toJson(WatchDto.from(query))).apply();
    }

    @Nullable
    public SearchQuery getLastSearch() {
        String json = prefs.getString(KEY_LAST_SEARCH, null);
        if (json == null) {
            return null;
        }
        try {
            WatchDto dto = gson.fromJson(json, WatchDto.class);
            SearchQuery query = dto == null ? null : dto.toQuery();
            return query != null && query.isValid() ? query : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
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

    /** JSON shape of a persisted query; kept separate so SearchQuery can stay immutable. */
    static final class WatchDto {
        String mode;
        int pincode;
        int districtId;
        String stateName;
        String districtName;
        String ageGroup;
        String dose;
        String vaccine;

        static WatchDto from(SearchQuery query) {
            WatchDto dto = new WatchDto();
            dto.mode = query.getMode().name();
            dto.pincode = query.getPincode();
            dto.districtId = query.getDistrictId();
            dto.stateName = query.getStateName();
            dto.districtName = query.getDistrictName();
            dto.ageGroup = query.getAgeGroup().label();
            dto.dose = query.getDose().label();
            dto.vaccine = query.getVaccine();
            return dto;
        }

        @Nullable
        SearchQuery toQuery() {
            if (mode == null) {
                return null;
            }
            AgeGroup age = AgeGroup.fromLabel(ageGroup);
            SearchQuery query = SearchQuery.Mode.PIN.name().equals(mode)
                    ? SearchQuery.byPin(pincode, age)
                    : SearchQuery.byDistrict(districtId, stateName, districtName, age);
            return query.withFilters(DoseType.fromLabel(dose), vaccine);
        }
    }
}
