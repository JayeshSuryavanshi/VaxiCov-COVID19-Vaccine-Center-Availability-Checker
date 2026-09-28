package com.akshaychavan.vaxicov.data;

import com.akshaychavan.vaxicov.domain.AgeGroup;
import com.akshaychavan.vaxicov.domain.SlotFilter;
import com.akshaychavan.vaxicov.pojo.Center;
import com.akshaychavan.vaxicov.pojo.District;
import com.akshaychavan.vaxicov.pojo.Session;
import com.akshaychavan.vaxicov.pojo.State;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SampleSlotProviderTest {

    private static final long NOON = 1_638_378_000_000L; // 2021-12-01T17:00:00Z

    private final SampleSlotProvider provider = new SampleSlotProvider(NOON);

    @Test
    public void listsAllStatesSorted() {
        List<State> states = provider.states();

        assertEquals(37, states.size());
        assertEquals("Andaman and Nicobar Islands", states.get(0).getStateName());
        assertEquals("West Bengal", states.get(states.size() - 1).getStateName());
    }

    @Test
    public void districtsAreNamedAfterTheirState() {
        List<District> districts = provider.districts(21);

        assertEquals(5, districts.size());
        assertEquals("Maharashtra Central", districts.get(0).getDistrictName());
        assertEquals(SampleSlotProvider.districtId(21, 0), (int) districts.get(0).getDistrictId());
    }

    @Test
    public void pinSearchReturnsSevenDaysPerCenter() {
        List<Center> centers = provider.centersByPin(422011, "01-12-2021");

        assertTrue(centers.size() >= 4);
        for (Center center : centers) {
            assertEquals(SampleSlotProvider.DAYS, center.getSessions().size());
            assertEquals("01-12-2021", center.getSessions().get(0).getDate());
            assertEquals("07-12-2021", center.getSessions().get(6).getDate());
            assertEquals(422011, (int) center.getPincode());
            assertTrue(center.getName().startsWith("PIN 422011"));
        }
    }

    @Test
    public void districtSearchUsesGeneratedDistrictName() {
        int nashikLike = SampleSlotProvider.districtId(21, 1);

        List<Center> centers = provider.centersByDistrict(nashikLike, "01-12-2021");

        assertFalse(centers.isEmpty());
        assertTrue(centers.get(0).getName().startsWith("Maharashtra North"));
    }

    @Test
    public void isDeterministicWithinTheSameHour() {
        List<Center> first = provider.centersByPin(110001, "01-12-2021");
        List<Center> second = new SampleSlotProvider(NOON + 5 * 60 * 1000).centersByPin(110001, "01-12-2021");

        assertEquals(SlotFilter.signature(first), SlotFilter.signature(second));
        assertEquals(SlotFilter.totalAvailable(first), SlotFilter.totalAvailable(second));
    }

    @Test
    public void availabilityChangesAcrossHours() {
        List<Center> now = provider.centersByPin(110001, "01-12-2021");
        List<Center> later = new SampleSlotProvider(NOON + 3 * 60 * 60 * 1000).centersByPin(110001, "01-12-2021");

        assertNotEquals(SlotFilter.signature(now), SlotFilter.signature(later));
    }

    @Test
    public void producesBothBookedAndOpenSessionsForEveryAgeGroup() {
        List<Center> centers = provider.centersByPin(400001, "01-12-2021");

        int open = 0;
        int booked = 0;
        Set<Integer> ages = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Center center : centers) {
            for (Session session : center.getSessions()) {
                ages.add(session.getMinAgeLimit());
                assertTrue(ids.add(session.getSessionId()));
                if (session.getAvailableCapacity() > 0) open++; else booked++;
            }
        }
        assertTrue(open > 0);
        assertTrue(booked > 0);
        assertTrue(ages.contains(15));
        assertTrue(ages.contains(18));
        assertTrue(ages.contains(45));
        assertFalse(SlotFilter.availableCenters(centers, AgeGroup.ADULTS_18_44).isEmpty());
    }

    @Test
    public void fallsBackToTodayWhenDateIsMalformed() {
        List<Center> centers = provider.centersByPin(400001, "not-a-date");

        assertEquals("01-12-2021", centers.get(0).getSessions().get(0).getDate());
    }
}
