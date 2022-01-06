package com.vaxicov.domain;

import com.vaxicov.pojo.Session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DoseTypeTest {

    private static Session session(Integer total, Integer dose1, Integer dose2) {
        Session session = new Session();
        session.setAvailableCapacity(total);
        session.setAvailableCapacityDose1(dose1);
        session.setAvailableCapacityDose2(dose2);
        return session;
    }

    @Test
    public void usesPerDoseCapacityWhenPresent() {
        Session s = session(10, 7, 3);
        assertEquals(10, DoseType.ANY.capacity(s));
        assertEquals(7, DoseType.FIRST.capacity(s));
        assertEquals(3, DoseType.SECOND.capacity(s));
    }

    @Test
    public void fallsBackToTotalWhenRegistryDoesNotSplitDoses() {
        Session s = session(10, null, null);
        assertEquals(10, DoseType.FIRST.capacity(s));
        assertEquals(10, DoseType.SECOND.capacity(s));
    }

    @Test
    public void neverNegativeOrNull() {
        assertEquals(0, DoseType.ANY.capacity(null));
        assertEquals(0, DoseType.ANY.capacity(session(null, null, null)));
        assertEquals(0, DoseType.SECOND.capacity(session(5, 5, -1)));
    }

    @Test
    public void labelsRoundTrip() {
        for (DoseType type : DoseType.values()) {
            assertEquals(type, DoseType.fromLabel(type.label()));
        }
        assertEquals(DoseType.SECOND, DoseType.fromLabel(" dose 2 "));
        assertEquals(DoseType.ANY, DoseType.fromLabel(null));
        assertEquals(DoseType.ANY, DoseType.fromLabel("booster"));
    }
}
