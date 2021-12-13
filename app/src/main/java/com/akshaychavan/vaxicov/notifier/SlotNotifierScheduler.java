package com.akshaychavan.vaxicov.notifier;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.akshaychavan.vaxicov.AppPreferences;
import com.akshaychavan.vaxicov.domain.SearchQuery;

import java.util.concurrent.TimeUnit;

/**
 * Schedules {@link SlotCheckWorker} with WorkManager: an immediate run for
 * quick feedback plus a periodic run at the platform minimum of fifteen
 * minutes. WorkManager survives reboots and does not need the app to stay
 * in the foreground, unlike the old AlarmManager loop.
 */
public final class SlotNotifierScheduler {

    static final String PERIODIC_WORK = "vaxicov.slot-check";
    static final String IMMEDIATE_WORK = "vaxicov.slot-check.now";

    private SlotNotifierScheduler() {
    }

    public static void schedule(@NonNull Context context, @NonNull SearchQuery query) {
        new AppPreferences(context).saveNotifierQuery(query);

        Constraints online = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(SlotCheckWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(online)
                .build();
        OneTimeWorkRequest immediate = new OneTimeWorkRequest.Builder(SlotCheckWorker.class)
                .setConstraints(online)
                .build();

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.REPLACE, periodic);
        workManager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, immediate);
    }

    public static void cancel(@NonNull Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(PERIODIC_WORK);
        workManager.cancelUniqueWork(IMMEDIATE_WORK);
        new AppPreferences(context).clearNotifierQuery();
    }

    public static boolean isActive(@NonNull Context context) {
        return new AppPreferences(context).getNotifierQuery() != null;
    }
}
