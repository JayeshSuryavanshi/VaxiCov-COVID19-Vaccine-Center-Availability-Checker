package com.akshaychavan.vaxicov.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.akshaychavan.vaxicov.AppPreferences;

/**
 * Chooses the active {@link SlotProvider}. To support another registry, add a
 * provider implementation and return it from here (for example based on a
 * build flavor or a user setting).
 */
public final class SlotProviders {

    private SlotProviders() {
    }

    @NonNull
    public static SlotProvider current(@NonNull Context context) {
        return current(new AppPreferences(context));
    }

    @NonNull
    public static SlotProvider current(@NonNull AppPreferences preferences) {
        if (preferences.isSampleDataEnabled()) {
            return new SampleSlotProvider();
        }
        return new CowinSlotProvider();
    }
}
