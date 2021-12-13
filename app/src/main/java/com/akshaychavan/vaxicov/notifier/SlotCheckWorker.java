package com.akshaychavan.vaxicov.notifier;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.akshaychavan.vaxicov.AppPreferences;
import com.akshaychavan.vaxicov.data.SlotProviders;
import com.akshaychavan.vaxicov.data.SlotRepository;
import com.akshaychavan.vaxicov.domain.SearchQuery;
import com.akshaychavan.vaxicov.domain.SlotFilter;
import com.akshaychavan.vaxicov.pojo.Center;

import java.util.List;

/**
 * Periodic background check scheduled by {@link SlotNotifierScheduler}.
 * Re-fetches the saved query and notifies only when the set of bookable
 * sessions changed since the last run, so the user is not pinged every
 * fifteen minutes about the same slots.
 */
public class SlotCheckWorker extends Worker {

    private static final String TAG = "SlotCheckWorker";

    public SlotCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AppPreferences preferences = new AppPreferences(context);
        SearchQuery query = preferences.getNotifierQuery();
        if (query == null) {
            return Result.success();
        }
        try {
            List<Center> available = SlotRepository.fetchAvailable(SlotProviders.current(preferences), query);
            String signature = SlotFilter.signature(available);
            if (!available.isEmpty() && !signature.equals(preferences.getLastSignature())) {
                AvailabilityNotifier.notifyAvailable(context, query, available);
            }
            preferences.setLastSignature(signature);
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "Slot check failed for " + query, e);
            return Result.retry();
        }
    }
}
