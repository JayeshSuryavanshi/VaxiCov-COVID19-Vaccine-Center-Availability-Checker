package com.vaxicov.notifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.vaxicov.AppPreferences;
import com.vaxicov.MainActivity;
import com.vaxicov.R;
import com.vaxicov.domain.SearchQuery;
import com.vaxicov.domain.SlotFilter;
import com.vaxicov.pojo.Center;

import java.util.List;

/** Builds and posts the "slots available" notification. */
public final class AvailabilityNotifier {

    public static final String CHANNEL_ID = "slot_alerts";
    public static final String EXTRA_FROM_NOTIFICATION = "com.vaxicov.FROM_NOTIFICATION";
    /** Index into {@link AppPreferences#getWatches()} of the watch that fired, or -1. */
    public static final String EXTRA_WATCH_INDEX = "com.vaxicov.WATCH_INDEX";
    private static final int NOTIFICATION_ID_BASE = 100;
    private static final int MAX_CENTERS_LISTED = 5;

    private AvailabilityNotifier() {
    }

    /** Creates the notification channel; safe to call repeatedly. */
    public static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.notification_channel_description));
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void notifyAvailable(@NonNull Context context, @NonNull SearchQuery query,
                                       @NonNull List<Center> centers) {
        ensureChannel(context);

        int doses = SlotFilter.totalAvailable(centers);
        String summary = context.getResources().getQuantityString(
                R.plurals.notification_text, doses, doses, centers.size(), query.describeArea());

        StringBuilder details = new StringBuilder(summary);
        int listed = 0;
        for (Center center : centers) {
            if (listed++ == MAX_CENTERS_LISTED) {
                details.append('\n').append(context.getString(R.string.notification_more_centers,
                        centers.size() - MAX_CENTERS_LISTED));
                break;
            }
            details.append('\n').append("• ").append(center.getName())
                    .append(" (").append(SlotFilter.totalAvailable(center)).append(')');
        }

        int notificationId = NOTIFICATION_ID_BASE + (query.hashCode() & 0x7fffffff) % 1000;
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_FROM_NOTIFICATION, true)
                .putExtra(EXTRA_WATCH_INDEX, watchIndex(context, query))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent tap = PendingIntent.getActivity(context, notificationId, open, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(context.getString(R.string.notification_title_format, query.describeFilters()))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(details.toString()))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    private static int watchIndex(@NonNull Context context, @NonNull SearchQuery query) {
        return new AppPreferences(context).getWatches().indexOf(query);
    }
}
