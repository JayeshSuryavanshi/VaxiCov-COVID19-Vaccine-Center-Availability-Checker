package com.vaxicov.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.vaxicov.domain.DateFormats;
import com.vaxicov.domain.SearchQuery;
import com.vaxicov.domain.SlotFilter;
import com.vaxicov.pojo.Center;
import com.vaxicov.pojo.District;
import com.vaxicov.pojo.State;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs {@link SlotProvider} calls on a background thread and delivers results
 * on the main thread. Keeps the Activity free of threading and error plumbing.
 */
public final class SlotRepository {

    /** Result callback, always invoked on the main thread. */
    public interface Callback<T> {
        void onSuccess(@NonNull T result);

        void onError(@NonNull Exception error);
    }

    private final SlotProvider provider;
    private final ExecutorService executor;
    private final Handler mainThread;

    public SlotRepository(@NonNull SlotProvider provider) {
        this(provider, Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper()));
    }

    SlotRepository(@NonNull SlotProvider provider, @NonNull ExecutorService executor, @NonNull Handler mainThread) {
        this.provider = provider;
        this.executor = executor;
        this.mainThread = mainThread;
    }

    @NonNull
    public SlotProvider provider() {
        return provider;
    }

    public void loadStates(@NonNull Callback<List<State>> callback) {
        run(new Task<List<State>>() {
            @Override
            public List<State> call() throws Exception {
                return provider.states();
            }
        }, callback);
    }

    public void loadDistricts(final int stateId, @NonNull Callback<List<District>> callback) {
        run(new Task<List<District>>() {
            @Override
            public List<District> call() throws Exception {
                return provider.districts(stateId);
            }
        }, callback);
    }

    /** Fetches the calendar for the query and returns only bookable centers for its age group. */
    public void search(@NonNull final SearchQuery query, @NonNull Callback<List<Center>> callback) {
        run(new Task<List<Center>>() {
            @Override
            public List<Center> call() throws Exception {
                return fetchAvailable(provider, query);
            }
        }, callback);
    }

    /** Blocking variant shared with the background worker. */
    @NonNull
    public static List<Center> fetchAvailable(@NonNull SlotProvider provider, @NonNull SearchQuery query) throws Exception {
        String today = DateFormats.today();
        List<Center> centers = query.isByPin()
                ? provider.centersByPin(query.getPincode(), today)
                : provider.centersByDistrict(query.getDistrictId(), today);
        return SlotFilter.availableCenters(centers, query);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private interface Task<T> {
        T call() throws Exception;
    }

    private <T> void run(@NonNull final Task<T> task, @NonNull final Callback<T> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T result = task.call();
                    mainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(result);
                        }
                    });
                } catch (final Exception e) {
                    mainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        });
    }
}
