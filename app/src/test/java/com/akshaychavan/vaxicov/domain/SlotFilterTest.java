package com.akshaychavan.vaxicov.domain;

import com.akshaychavan.vaxicov.pojo.Center;
import com.akshaychavan.vaxicov.pojo.Session;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class SlotFilterTest {

    private static Session session(String id, int capacity, int minAge) {
        Session session = new Session();
        session.setSessionId(id);
        session.setAvailableCapacity(capacity);
        session.setMinAgeLimit(minAge);
        session.setVaccine("COVISHIELD");
        session.setDate("01-12-2021");
        return session;
    }

    private static Center center(int id, String name, Session... sessions) {
        Center center = new Center();
        center.setCenterId(id);
        center.setName(name);
        center.setFeeType("Free");
        center.setSessions(new ArrayList<>(Arrays.asList(sessions)));
        return center;
    }

    @Test
    public void dropsSessionsWithoutCapacity() {
        Center c = center(1, "A", session("s1", 0, 18), session("s2", 5, 18));

        List<Center> result = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getSessions().size());
        assertEquals("s2", result.get(0).getSessions().get(0).getSessionId());
    }

    @Test
    public void dropsCentersLeftWithoutSessions() {
        Center empty = center(1, "Empty", session("s1", 0, 18));
        Center nullSessions = center(2, "Null");
        nullSessions.setSessions(null);

        assertTrue(SlotFilter.availableCenters(Arrays.asList(empty, nullSessions), AgeGroup.ALL).isEmpty());
    }

    @Test
    public void filtersByAgeGroup() {
        Center c = center(1, "A", session("young", 3, 18), session("old", 4, 45));

        List<Center> adults = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ADULTS_18_44);
        List<Center> seniors = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.SENIORS_45_PLUS);
        List<Center> all = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL);

        assertEquals("young", adults.get(0).getSessions().get(0).getSessionId());
        assertEquals("old", seniors.get(0).getSessions().get(0).getSessionId());
        assertEquals(2, all.get(0).getSessions().size());
    }

    @Test
    public void sortsByAvailableCapacityThenName() {
        Center small = center(1, "Zeta", session("a", 2, 18));
        Center big = center(2, "Alpha", session("b", 10, 18), session("c", 1, 45));
        Center tie = center(3, "Beta", session("d", 2, 18));

        List<Center> result = SlotFilter.availableCenters(Arrays.asList(small, big, tie), AgeGroup.ALL);

        assertEquals("Alpha", result.get(0).getName());
        assertEquals("Beta", result.get(1).getName());
        assertEquals("Zeta", result.get(2).getName());
    }

    @Test
    public void doesNotMutateInput() {
        Session dead = session("s1", 0, 18);
        Session live = session("s2", 5, 18);
        Center c = center(1, "A", dead, live);

        List<Center> result = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL);

        assertNotSame(c, result.get(0));
        assertEquals(2, c.getSessions().size());
        assertEquals("A", result.get(0).getName());
        assertEquals("Free", result.get(0).getFeeType());
    }

    @Test
    public void handlesNullAndMissingValues() {
        Session noCapacity = session("s1", 0, 18);
        noCapacity.setAvailableCapacity(null);
        Center c = center(1, "A", noCapacity, null);

        assertTrue(SlotFilter.availableCenters(null, AgeGroup.ALL).isEmpty());
        assertTrue(SlotFilter.availableCenters(Arrays.asList(c, null), AgeGroup.ALL).isEmpty());
        assertEquals(0, SlotFilter.totalAvailable((Center) null));
        assertEquals(0, SlotFilter.totalAvailable((List<Center>) null));
        assertEquals("", SlotFilter.signature(null));
    }

    @Test
    public void totalsCapacityAcrossCenters() {
        Center a = center(1, "A", session("s1", 3, 18), session("s2", 0, 18));
        Center b = center(2, "B", session("s3", 4, 45));

        assertEquals(3, SlotFilter.totalAvailable(a));
        assertEquals(7, SlotFilter.totalAvailable(Arrays.asList(a, b)));
    }

    @Test
    public void signatureIsOrderIndependentAndIgnoresUnbookable() {
        Center a = center(1, "A", session("s1", 3, 18), session("dead", 0, 18));
        Center b = center(2, "B", session("s3", 4, 45));

        String forward = SlotFilter.signature(Arrays.asList(a, b));
        String backward = SlotFilter.signature(Arrays.asList(b, a));

        assertEquals("s1,s3", forward);
        assertEquals(forward, backward);
        assertFalse(forward.contains("dead"));
    }

    @Test
    public void signatureFallsBackWhenSessionIdMissing() {
        Session anonymous = session(null, 2, 18);
        Center c = center(7, "A", anonymous);

        assertEquals("7@01-12-2021@COVISHIELD", SlotFilter.signature(Collections.singletonList(c)));
    }

    private static Session dosed(String id, int total, int dose1, int dose2, String vaccine) {
        Session session = session(id, total, 18);
        session.setAvailableCapacityDose1(dose1);
        session.setAvailableCapacityDose2(dose2);
        session.setVaccine(vaccine);
        return session;
    }

    @Test
    public void doseFilterUsesPerDoseCapacityAndRewritesTotals() {
        Center c = center(1, "A",
                dosed("only-first", 10, 10, 0, "COVISHIELD"),
                dosed("mixed", 8, 3, 5, "COVISHIELD"));

        List<Center> second = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL, DoseType.SECOND, null);

        assertEquals(1, second.size());
        assertEquals(1, second.get(0).getSessions().size());
        assertEquals("mixed", second.get(0).getSessions().get(0).getSessionId());
        assertEquals(5, (int) second.get(0).getSessions().get(0).getAvailableCapacity());
        assertEquals(5, SlotFilter.totalAvailable(second));
        // the original data is untouched
        assertEquals(8, (int) c.getSessions().get(1).getAvailableCapacity());
    }

    @Test
    public void vaccineFilterIsCaseInsensitiveAndTreatsAnyAsNoFilter() {
        Center c = center(1, "A",
                dosed("cs", 4, 4, 0, "COVISHIELD"),
                dosed("cx", 6, 6, 0, "Covaxin"));

        List<Center> covaxin = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL, DoseType.ANY, "covaxin");
        List<Center> any = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL, DoseType.ANY, "Any");
        List<Center> none = SlotFilter.availableCenters(Collections.singletonList(c), AgeGroup.ALL, DoseType.ANY, "SPUTNIK V");

        assertEquals("cx", covaxin.get(0).getSessions().get(0).getSessionId());
        assertEquals(2, any.get(0).getSessions().size());
        assertTrue(none.isEmpty());
    }

    @Test
    public void queryOverloadAppliesAllFilters() {
        Center c = center(1, "A",
                dosed("young-cs", 4, 2, 2, "COVISHIELD"),
                dosed("young-cx", 6, 0, 6, "COVAXIN"),
                session("old-cx", 9, 45));
        c.getSessions().get(2).setVaccine("COVAXIN");
        SearchQuery query = SearchQuery.byPin(422011, AgeGroup.ADULTS_18_44).withFilters(DoseType.SECOND, "COVAXIN");

        List<Center> result = SlotFilter.availableCenters(Collections.singletonList(c), query);

        assertEquals(1, result.get(0).getSessions().size());
        assertEquals("young-cx", result.get(0).getSessions().get(0).getSessionId());
        assertEquals(6, SlotFilter.totalAvailable(result));
    }
}
