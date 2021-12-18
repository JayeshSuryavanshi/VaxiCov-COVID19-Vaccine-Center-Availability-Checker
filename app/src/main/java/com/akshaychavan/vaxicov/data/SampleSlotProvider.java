package com.akshaychavan.vaxicov.data;

import androidx.annotation.NonNull;

import com.akshaychavan.vaxicov.domain.DateFormats;
import com.akshaychavan.vaxicov.network.StateDirectory;
import com.akshaychavan.vaxicov.pojo.Center;
import com.akshaychavan.vaxicov.pojo.District;
import com.akshaychavan.vaxicov.pojo.Session;
import com.akshaychavan.vaxicov.pojo.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, offline {@link SlotProvider} that fabricates plausible data.
 * <p>
 * It lets the whole app (search, filters, background alerts) run without any
 * backend: for demos, for UI work, and as the starting point when a new
 * registry has to be wired up during a future outbreak. Results depend only
 * on the requested area, the date and the current hour, so they are stable
 * within an hour and change afterwards, which exercises the notifier.
 */
public final class SampleSlotProvider implements SlotProvider {

    /** Number of days of sessions returned, matching CoWIN's calendar endpoints. */
    static final int DAYS = 7;
    private static final long HOUR_MILLIS = 60L * 60L * 1000L;
    private static final String[] VACCINES = {"COVISHIELD", "COVAXIN", "SPUTNIK V"};
    private static final String[] CENTER_KINDS = {
            "Primary Health Centre", "Civil Hospital", "Community Health Centre",
            "Municipal Hospital", "Rural Hospital", "Medical College"
    };

    private final long fixedNowMillis;

    /** Uses the wall clock. */
    public SampleSlotProvider() {
        this(-1L);
    }

    /** Uses a fixed clock so tests get repeatable output. */
    SampleSlotProvider(long fixedNowMillis) {
        this.fixedNowMillis = fixedNowMillis;
    }

    @NonNull
    @Override
    public String name() {
        return "Sample data";
    }

    @NonNull
    @Override
    public List<State> states() {
        return StateDirectory.defaultStates();
    }

    @NonNull
    @Override
    public List<District> districts(int stateId) {
        String stateName = stateName(stateId);
        List<District> districts = new ArrayList<>();
        String[] parts = {"Central", "North", "South", "East", "West"};
        for (int i = 0; i < parts.length; i++) {
            District district = new District();
            district.setDistrictId(districtId(stateId, i));
            district.setDistrictName(stateName + " " + parts[i]);
            districts.add(district);
        }
        return districts;
    }

    @NonNull
    @Override
    public List<Center> centersByPin(int pincode, @NonNull String date) {
        return centers("PIN " + pincode, pincode, pincode, date);
    }

    @NonNull
    @Override
    public List<Center> centersByDistrict(int districtId, @NonNull String date) {
        String area = districtName(districtId);
        int pincode = 100000 + (Math.abs(districtId * 7919) % 900000);
        return centers(area, districtId, pincode, date);
    }

    static int districtId(int stateId, int index) {
        return stateId * 1000 + index + 1;
    }

    @NonNull
    private static String stateName(int stateId) {
        for (State state : StateDirectory.defaultStates()) {
            if (state.getStateId() != null && state.getStateId() == stateId) {
                return state.getStateName();
            }
        }
        return "State " + stateId;
    }

    @NonNull
    private String districtName(int districtId) {
        int stateId = districtId / 1000;
        int index = districtId % 1000 - 1;
        for (District district : districts(stateId)) {
            if (district.getDistrictId() != null && district.getDistrictId() == districtId) {
                return district.getDistrictName();
            }
        }
        return "District " + districtId + (index >= 0 ? "" : "");
    }

    @NonNull
    private List<Center> centers(@NonNull String area, int areaKey, int pincode, @NonNull String date) {
        Date start = DateFormats.parseCowinDate(date);
        if (start == null) {
            start = new Date(now());
        }
        long hourBucket = now() / HOUR_MILLIS;
        int centerCount = 4 + Math.abs(hash(areaKey, 0, 0, 0)) % 3;
        List<Center> centers = new ArrayList<>();
        for (int c = 0; c < centerCount; c++) {
            Center center = new Center();
            center.setCenterId(areaKey * 10 + c);
            center.setName(area + " " + CENTER_KINDS[c % CENTER_KINDS.length]);
            center.setAddress(String.format(Locale.US, "%d Station Road, %s", 10 + c, area));
            center.setPincode(pincode);
            center.setFrom("09:00:00");
            center.setTo("17:00:00");
            boolean paid = c % 3 == 2;
            center.setFeeType(paid ? "Paid" : "Free");

            List<Session> sessions = new ArrayList<>();
            for (int day = 0; day < DAYS; day++) {
                int h = hash(areaKey, c, day, hourBucket);
                Session session = new Session();
                session.setSessionId(String.format(Locale.US, "sample-%d-%d-%d", areaKey, c, day));
                session.setDate(DateFormats.cowinDate(DateFormats.plusDays(start, day)));
                session.setVaccine(VACCINES[Math.abs(h >> 3) % VACCINES.length]);
                session.setMinAgeLimit(Math.abs(h >> 5) % 2 == 0 ? 18 : 45);
                session.setFee(paid ? "780" : "0");
                // Roughly 40% of sessions are fully booked, like a real busy day.
                int capacity = Math.abs(h) % 10 < 4 ? 0 : 1 + Math.abs(h >> 8) % 120;
                int dose1 = capacity * (Math.abs(h >> 13) % 101) / 100;
                session.setAvailableCapacity(capacity);
                session.setAvailableCapacityDose1(dose1);
                session.setAvailableCapacityDose2(capacity - dose1);
                session.setSlots(Collections.singletonList("09:00AM-05:00PM"));
                sessions.add(session);
            }
            center.setSessions(sessions);
            centers.add(center);
        }
        return centers;
    }

    private long now() {
        return fixedNowMillis >= 0 ? fixedNowMillis : System.currentTimeMillis();
    }

    private static int hash(int areaKey, int center, int day, long hourBucket) {
        long h = 1125899906842597L;
        h = 31 * h + areaKey;
        h = 31 * h + center;
        h = 31 * h + day;
        h = 31 * h + hourBucket;
        h ^= (h >>> 29);
        h *= 0x5bd1e995L;
        h ^= (h >>> 17);
        return (int) h;
    }
}
