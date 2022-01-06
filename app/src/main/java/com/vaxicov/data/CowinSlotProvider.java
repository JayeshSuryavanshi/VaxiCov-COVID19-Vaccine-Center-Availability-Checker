package com.vaxicov.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vaxicov.network.ApiClient;
import com.vaxicov.network.CowinApi;
import com.vaxicov.network.StateDirectory;
import com.vaxicov.pojo.CalendarResponse;
import com.vaxicov.pojo.Center;
import com.vaxicov.pojo.District;
import com.vaxicov.pojo.DistrictsResponse;
import com.vaxicov.pojo.State;
import com.vaxicov.pojo.StatesResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/** {@link SlotProvider} backed by India's public CoWIN API. */
public final class CowinSlotProvider implements SlotProvider {

    private final CowinApi api;

    public CowinSlotProvider() {
        this(ApiClient.cowin());
    }

    public CowinSlotProvider(@NonNull CowinApi api) {
        this.api = api;
    }

    @NonNull
    @Override
    public String name() {
        return "CoWIN";
    }

    @NonNull
    @Override
    public List<State> states() throws IOException {
        StatesResponse response = execute(api.getStates());
        List<State> states = response == null ? null : response.getStates();
        if (states == null || states.isEmpty()) {
            return StateDirectory.defaultStates();
        }
        return StateDirectory.sortedByName(states);
    }

    @NonNull
    @Override
    public List<District> districts(int stateId) throws IOException {
        DistrictsResponse response = execute(api.getDistrictsByState(stateId));
        return nonNull(response == null ? null : response.getDistricts());
    }

    @NonNull
    @Override
    public List<Center> centersByPin(int pincode, @NonNull String date) throws IOException {
        CalendarResponse response = execute(api.findCalendarByPin(pincode, date));
        return nonNull(response == null ? null : response.getCenters());
    }

    @NonNull
    @Override
    public List<Center> centersByDistrict(int districtId, @NonNull String date) throws IOException {
        CalendarResponse response = execute(api.findCalendarByDistrict(districtId, date));
        return nonNull(response == null ? null : response.getCenters());
    }

    @Nullable
    private static <T> T execute(@NonNull Call<T> call) throws IOException {
        Response<T> response = call.execute();
        if (!response.isSuccessful()) {
            throw new IOException("HTTP " + response.code() + " from " + call.request().url());
        }
        return response.body();
    }

    @NonNull
    private static <T> List<T> nonNull(@Nullable List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }
}
