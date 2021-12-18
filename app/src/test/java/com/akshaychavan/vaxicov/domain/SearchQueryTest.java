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

    @Test
    public void filtersDefaultToAnyAndNormaliseVaccine() {
        SearchQuery plain = SearchQuery.byPin(422011, AgeGroup.ALL);
        assertEquals(DoseType.ANY, plain.getDose());
        assertEquals(null, plain.getVaccine());
        assertTrue(plain.acceptsVaccine("COVAXIN"));
        assertTrue(plain.acceptsVaccine(null));

        SearchQuery filtered = plain.withFilters(DoseType.SECOND, " covaxin ");
        assertEquals("COVAXIN", filtered.getVaccine());
        assertTrue(filtered.acceptsVaccine("Covaxin"));
        assertFalse(filtered.acceptsVaccine("COVISHIELD"));
        assertFalse(filtered.acceptsVaccine(null));

        assertEquals(null, plain.withFilters(DoseType.ANY, "Any").getVaccine());
        assertEquals(null, plain.withFilters(DoseType.ANY, "  ").getVaccine());
    }

    @Test
    public void describesFilters() {
        SearchQuery plain = SearchQuery.byPin(422011, AgeGroup.ALL);
        assertEquals("All ages", plain.describeFilters());
        assertEquals("45+", SearchQuery.byPin(422011, AgeGroup.SENIORS_45_PLUS).describeFilters());
        assertEquals("Dose 2 · COVAXIN", plain.withFilters(DoseType.SECOND, "COVAXIN").describeFilters());
        assertEquals("18-44 · Dose 1",
                SearchQuery.byPin(422011, AgeGroup.ADULTS_18_44).withFilters(DoseType.FIRST, null).describeFilters());
    }

    @Test
    public void filtersTakePartInEquality() {
        SearchQuery a = SearchQuery.byPin(422011, AgeGroup.ALL);
        assertNotEquals(a, a.withFilters(DoseType.FIRST, null));
        assertNotEquals(a, a.withFilters(DoseType.ANY, "COVAXIN"));
        assertEquals(a, a.withFilters(DoseType.ANY, "Any"));
        assertEquals(a.withFilters(DoseType.FIRST, "covaxin"), a.withFilters(DoseType.FIRST, "COVAXIN"));
    }
}
