package com.vaxicov.pojo;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response of both {@code calendarByPin} and {@code calendarByDistrict}: the
 * two endpoints share the same shape, so a single model serves both.
 */
public class CalendarResponse {

    @SerializedName("centers")
    @Expose
    private List<Center> centers = null;

    public List<Center> getCenters() {
        return centers;
    }

    public void setCenters(List<Center> centers) {
        this.centers = centers;
    }

}
