package com.akshaychavan.vaxicov.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SearchQueryTest {

    @Test
    public void pincodeValidationRequiresSixDigits() {
        assertTrue(SearchQuery.isValidPincode("422011"));
        assertTrue(SearchQuery.isValidPincode(" 110001 "));
        assertFalse(SearchQuery.isValidPincode("42201"));
        assertFalse(SearchQuery.isValidPincode("4220111"));
        assertFalse(SearchQuery.isValidPincode("012345"));
        assertFalse(SearchQuery.isValidPincode("abcdef"));
        assertFalse(SearchQuery.isValidPincode(""));
        assertFalse(SearchQuery.isValidPincode(null));
    }

    @Test
    public void pinQueryIsValidOnlyWithRealPincode() {
        assertTrue(SearchQuery.byPin(422011, AgeGroup.ALL).isValid());
        assertFalse(SearchQuery.byPin(0, AgeGroup.ALL).isValid());
        assertFalse(SearchQuery.byPin(12345, AgeGroup.ALL).isValid());
    }

    @Test
    public void districtQueryNeedsPositiveId() {
        assertTrue(SearchQuery.byDistrict(391, "Maharashtra", "Nashik", AgeGroup.ALL).isValid());
        assertFalse(SearchQuery.byDistrict(0, "Maharashtra", "Nashik", AgeGroup.ALL).isValid());
        assertFalse(SearchQuery.byDistrict(-1, null, null, AgeGroup.ALL).isValid());
    }

    @Test
    public void describesAreaForHumans() {
        assertEquals("pincode 422011", SearchQuery.byPin(422011, AgeGroup.ALL).describeArea());
        assertEquals("Nashik, Maharashtra",
                SearchQuery.byDistrict(391, "Maharashtra", "Nashik", AgeGroup.ALL).describeArea());
        assertEquals("Nashik", SearchQuery.byDistrict(391, null, "Nashik", AgeGroup.ALL).describeArea());
        assertEquals("district #391", SearchQuery.byDistrict(391, null, null, AgeGroup.ALL).describeArea());
    }

    @Test
    public void equalityCoversAllFields() {
        SearchQuery a = SearchQuery.byDistrict(391, "Maharashtra", "Nashik", AgeGroup.ADULTS_18_44);
        SearchQuery b = SearchQuery.byDistrict(391, "Maharashtra", "Nashik", AgeGroup.ADULTS_18_44);
        SearchQuery c = SearchQuery.byDistrict(391, "Maharashtra", "Nashik", AgeGroup.SENIORS_45_PLUS);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, SearchQuery.byPin(422011, AgeGroup.ADULTS_18_44));
    }
}
