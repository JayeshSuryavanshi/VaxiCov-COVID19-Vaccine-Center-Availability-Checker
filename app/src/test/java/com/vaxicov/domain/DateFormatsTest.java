package com.vaxicov.domain;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DateFormatsTest {

    @Test
    public void formatsAsDayMonthYear() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.US);
        calendar.set(2021, Calendar.MAY, 8, 12, 0, 0);
        assertEquals("08-05-2021", DateFormats.cowinDate(calendar.getTime()));
    }

    @Test
    public void usesLatinDigitsRegardlessOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("ar", "EG"));
            Calendar calendar = Calendar.getInstance();
            calendar.set(2021, Calendar.DECEMBER, 1, 12, 0, 0);
            assertEquals("01-12-2021", DateFormats.cowinDate(calendar.getTime()));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void todayMatchesPattern() {
        assertTrue(DateFormats.today().matches("\\d{2}-\\d{2}-\\d{4}"));
    }

    @Test
    public void parsesStrictly() {
        assertEquals("08-05-2021", DateFormats.cowinDate(DateFormats.parseCowinDate("08-05-2021")));
        assertEquals("08-05-2021", DateFormats.cowinDate(DateFormats.parseCowinDate(" 08-05-2021 ")));
        assertNull(DateFormats.parseCowinDate("2021-05-08"));
        assertNull(DateFormats.parseCowinDate("31-02-2021"));
        assertNull(DateFormats.parseCowinDate(""));
        assertNull(DateFormats.parseCowinDate(null));
    }

    @Test
    public void addsDaysAcrossMonthBoundaries() {
        assertEquals("02-01-2022", DateFormats.cowinDate(DateFormats.plusDays(DateFormats.parseCowinDate("30-12-2021"), 3)));
        assertEquals("28-11-2021", DateFormats.cowinDate(DateFormats.plusDays(DateFormats.parseCowinDate("01-12-2021"), -3)));
    }
}
