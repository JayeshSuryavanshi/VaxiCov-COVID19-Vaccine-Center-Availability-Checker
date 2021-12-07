package com.akshaychavan.vaxicov.domain;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
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
}
