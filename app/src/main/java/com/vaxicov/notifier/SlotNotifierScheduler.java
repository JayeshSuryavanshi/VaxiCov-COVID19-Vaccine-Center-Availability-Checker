package com.vaxicov.notifier;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.vaxicov.AppPreferences;
import com.vaxicov.domain.SearchQuery;

import java.util.concurrent.TimeUnit;

/**
 * Maintains the watchlist and the WorkManager job that checks it: one
 * periodic {@link SlotCheckWorker} at the platform minimum of fifteen
 * minutes (plus an immediate run whenever the list changes), cancelled when
 * the last watch is removed. WorkManager survives reboots and does not need
 * the app in the foreground.
 */
public final class SlotNotifierScheduler {

    static final String PERIODIC_WORK = "vaxicov.slot-check";
    static final String IMMEDIATE_WORK = "vaxicov.slot-check.now";

    private SlotNotifierScheduler() {
    }

    /** Adds the query to the watchlist; returns {@code false} if it was already there or the list is full. */
    public static boolean addWatch(@NonNull Context context, @NonNull SearchQuery query) {
        boolean added = new AppPreferences(context).addWatch(query);
        if (added) {
            ensureScheduled(context);
        }
        return added;
    }

    public static void removeWatch(@NonNull Context context, @NonNull SearchQuery query) {
        AppPreferences preferences = new AppPreferences(context);
        preferences.removeWatch(query);
        if (preferences.hasWatches()) {
            runNow(context);
        } else {
            cancelWork(context);
        }
    }

    public static void clearWatches(@NonNull Context context) {
        new AppPreferences(context).clearWatches();
        cancelWork(context);
    }

    public static boolean isActive(@NonNull Context context) {
        return new AppPreferences(context).hasWatches();
    }

    /** Makes sure the periodic job exists and triggers an immediate check. */
    public static void ensureScheduled(@NonNull Context context) {
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(SlotCheckWorker.class,
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(online())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic);
        runNow(context);
    }

    private static void runNow(@NonNull Context context) {
        OneTimeWorkRequest immediate = new OneTimeWorkRequest.Builder(SlotCheckWorker.class)
                .setConstraints(online())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, immediate);
    }

    private static void cancelWork(@NonNull Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(PERIODIC_WORK);
        workManager.cancelUniqueWork(IMMEDIATE_WORK);
    }

    private static Constraints online() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}
