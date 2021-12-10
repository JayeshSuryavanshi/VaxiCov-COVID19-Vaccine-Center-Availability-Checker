package com.akshaychavan.vaxicov.network;

import androidx.annotation.NonNull;

import com.akshaychavan.vaxicov.pojo.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Offline fallback for the CoWIN {@code admin/location/states} endpoint so
 * the state dropdown still works when the first request fails.
 */
public final class StateDirectory {

    private StateDirectory() {
    }

    private static final List<State> DEFAULT_STATES = Collections.unmodifiableList(Arrays.asList(
            new State(1, "Andaman and Nicobar Islands"),
            new State(2, "Andhra Pradesh"),
            new State(3, "Arunachal Pradesh"),
            new State(4, "Assam"),
            new State(5, "Bihar"),
            new State(6, "Chandigarh"),
            new State(7, "Chhattisgarh"),
            new State(8, "Dadra and Nagar Haveli"),
            new State(37, "Daman and Diu"),
            new State(9, "Delhi"),
            new State(10, "Goa"),
            new State(11, "Gujarat"),
            new State(12, "Haryana"),
            new State(13, "Himachal Pradesh"),
            new State(14, "Jammu and Kashmir"),
            new State(15, "Jharkhand"),
            new State(16, "Karnataka"),
            new State(17, "Kerala"),
            new State(18, "Ladakh"),
            new State(19, "Lakshadweep"),
            new State(20, "Madhya Pradesh"),
            new State(21, "Maharashtra"),
            new State(22, "Manipur"),
            new State(23, "Meghalaya"),
            new State(24, "Mizoram"),
            new State(25, "Nagaland"),
            new State(26, "Odisha"),
            new State(27, "Puducherry"),
            new State(28, "Punjab"),
            new State(29, "Rajasthan"),
            new State(30, "Sikkim"),
            new State(31, "Tamil Nadu"),
            new State(32, "Telangana"),
            new State(33, "Tripura"),
            new State(34, "Uttar Pradesh"),
            new State(35, "Uttarakhand"),
            new State(36, "West Bengal")));

    /** Bundled list of states, sorted by name. */
    @NonNull
    public static List<State> defaultStates() {
        return sortedByName(DEFAULT_STATES);
    }

    /** @return a new list sorted alphabetically by state name. */
    @NonNull
    public static List<State> sortedByName(@NonNull List<State> states) {
        List<State> sorted = new ArrayList<>(states);
        Collections.sort(sorted, new Comparator<State>() {
            @Override
            public int compare(State a, State b) {
                String nameA = a.getStateName() == null ? "" : a.getStateName();
                String nameB = b.getStateName() == null ? "" : b.getStateName();
                return nameA.compareToIgnoreCase(nameB);
            }
        });
        return sorted;
    }
}
