package com.akshaychavan.vaxicov.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgeGroupTest {

    @Test
    public void allMatchesEverything() {
        assertTrue(AgeGroup.ALL.matches(18));
        assertTrue(AgeGroup.ALL.matches(45));
        assertTrue(AgeGroup.ALL.matches(null));
    }

    @Test
    public void specificGroupsMatchOnlyTheirFloor() {
        assertTrue(AgeGroup.ADULTS_18_44.matches(18));
        assertFalse(AgeGroup.ADULTS_18_44.matches(45));
        assertFalse(AgeGroup.ADULTS_18_44.matches(null));

        assertTrue(AgeGroup.SENIORS_45_PLUS.matches(45));
        assertFalse(AgeGroup.SENIORS_45_PLUS.matches(18));

        assertTrue(AgeGroup.TEENS_15_17.matches(15));
        assertFalse(AgeGroup.TEENS_15_17.matches(18));
        assertFalse(AgeGroup.ADULTS_18_44.matches(15));
    }

    @Test
    public void labelsRoundTrip() {
        for (AgeGroup group : AgeGroup.values()) {
            assertEquals(group, AgeGroup.fromLabel(group.label()));
        }
    }

    @Test
    public void fromLabelIsLenient() {
        assertEquals(AgeGroup.SENIORS_45_PLUS, AgeGroup.fromLabel(" 45+ "));
        assertEquals(AgeGroup.ADULTS_18_44, AgeGroup.fromLabel("18-44"));
        assertEquals(AgeGroup.ALL, AgeGroup.fromLabel(null));
        assertEquals(AgeGroup.ALL, AgeGroup.fromLabel(""));
        assertEquals(AgeGroup.ALL, AgeGroup.fromLabel("Age Group"));
    }
}
