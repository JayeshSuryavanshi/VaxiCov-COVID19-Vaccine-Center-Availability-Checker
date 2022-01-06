package com.vaxicov.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Date formatting helpers for the CoWIN API, which expects {@code dd-MM-yyyy}. */
public final class DateFormats {

    private static final String COWIN_PATTERN = "dd-MM-yyyy";

    private DateFormats() {
    }

    /**
     * Formats a date the way CoWIN expects. {@link Locale#US} is used on
     * purpose: the default locale may render non-Latin digits, which the API
     * rejects.
     */
    @NonNull
    public static String cowinDate(@NonNull Date date) {
        return new SimpleDateFormat(COWIN_PATTERN, Locale.US).format(date);
    }

    /** Today's date in CoWIN format. */
    @NonNull
    public static String today() {
        return cowinDate(new Date());
    }

    /** Parses a {@code dd-MM-yyyy} string; returns {@code null} for anything else. */
    @Nullable
    public static Date parseCowinDate(@Nullable String text) {
        if (text == null) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat(COWIN_PATTERN, Locale.US);
        format.setLenient(false);
        try {
            return format.parse(text.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    /** @return a new date {@code days} after {@code date}. */
    @NonNull
    public static Date plusDays(@NonNull Date date, int days) {
        Calendar calendar = Calendar.getInstance(Locale.US);
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }
}
