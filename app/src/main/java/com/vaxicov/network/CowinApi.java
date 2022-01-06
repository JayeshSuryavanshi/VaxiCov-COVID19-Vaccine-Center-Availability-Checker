package com.vaxicov.network;

import com.vaxicov.pojo.CalendarResponse;
import com.vaxicov.pojo.DistrictsResponse;
import com.vaxicov.pojo.StatesResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Public CoWIN endpoints (https://apisetu.gov.in/public/marketplace/api/cowin).
 * Dates are {@code dd-MM-yyyy}; see {@link com.vaxicov.domain.DateFormats}.
 */
public interface CowinApi {

    @GET("admin/location/states")
    Call<StatesResponse> getStates();

    @GET("admin/location/districts/{state_id}")
    Call<DistrictsResponse> getDistrictsByState(@Path("state_id") int stateId);

    @GET("appointment/sessions/public/calendarByPin")
    Call<CalendarResponse> findCalendarByPin(@Query("pincode") int pincode, @Query("date") String date);

    @GET("appointment/sessions/public/calendarByDistrict")
    Call<CalendarResponse> findCalendarByDistrict(@Query("district_id") int districtId, @Query("date") String date);
}
