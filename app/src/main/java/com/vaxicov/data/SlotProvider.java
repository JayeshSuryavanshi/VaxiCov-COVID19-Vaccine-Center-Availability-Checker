package com.vaxicov.data;

import androidx.annotation.NonNull;

import com.vaxicov.pojo.Center;
import com.vaxicov.pojo.District;
import com.vaxicov.pojo.State;

import java.io.IOException;
import java.util.List;

/**
 * Source of vaccination slot data. The app only depends on this interface,
 * so a different registry (another country, a future vaccination or testing
 * drive) can be supported by adding one implementation and registering it in
 * {@link SlotProviders}. All methods are blocking and must be called off the
 * main thread; {@link SlotRepository} takes care of that for the UI.
 */
public interface SlotProvider {

    /** Short identifier shown in the UI, e.g. "CoWIN" or "Sample data". */
    @NonNull
    String name();

    @NonNull
    List<State> states() throws IOException;

    @NonNull
    List<District> districts(int stateId) throws IOException;

    /** Centers with sessions for the seven days starting at {@code date} ({@code dd-MM-yyyy}). */
    @NonNull
    List<Center> centersByPin(int pincode, @NonNull String date) throws IOException;

    /** Centers with sessions for the seven days starting at {@code date} ({@code dd-MM-yyyy}). */
    @NonNull
    List<Center> centersByDistrict(int districtId, @NonNull String date) throws IOException;
}
