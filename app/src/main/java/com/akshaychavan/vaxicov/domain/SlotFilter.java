package com.akshaychavan.vaxicov.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.akshaychavan.vaxicov.pojo.Center;
import com.akshaychavan.vaxicov.pojo.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure-Java slot selection logic shared by the search screen and the
 * background notifier: keeps only sessions that can actually be booked for
 * the requested age group and orders centers by how many doses they have.
 */
public final class SlotFilter {

    private SlotFilter() {
    }

    /**
     * Convenience for callers that only filter by age group.
     *
     * @see #availableCenters(List, AgeGroup, DoseType, String)
     */
    @NonNull
    public static List<Center> availableCenters(@Nullable List<Center> centers, @NonNull AgeGroup ageGroup) {
        return availableCenters(centers, ageGroup, DoseType.ANY, null);
    }

    /** Applies the query's age group, dose and vaccine filters. */
    @NonNull
    public static List<Center> availableCenters(@Nullable List<Center> centers, @NonNull SearchQuery query) {
        return availableCenters(centers, query.getAgeGroup(), query.getDose(), query.getVaccine());
    }

    /**
     * Returns copies of the given centers containing only sessions that match
     * the age group and vaccine and have at least one dose of the requested
     * type. In the copies, {@link Session#getAvailableCapacity()} holds the
     * capacity for that dose type, so totals, ordering and the change
     * signature all describe what the user can actually book. Centers left
     * without sessions are dropped. The result is sorted by total available
     * capacity (descending), then by center name.
     */
    @NonNull
    public static List<Center> availableCenters(@Nullable List<Center> centers, @NonNull AgeGroup ageGroup,
                                                @NonNull DoseType dose, @Nullable String vaccine) {
        List<Center> result = new ArrayList<>();
        if (centers == null) {
            return result;
        }
        String wantedVaccine = vaccine == null || vaccine.trim().isEmpty()
                || SearchQuery.ANY_VACCINE.equalsIgnoreCase(vaccine.trim()) ? null : vaccine.trim();
        for (Center center : centers) {
            if (center == null) {
                continue;
            }
            List<Session> bookable = new ArrayList<>();
            List<Session> sessions = center.getSessions();
            if (sessions != null) {
                for (Session session : sessions) {
                    if (session == null || !ageGroup.matches(session.getMinAgeLimit())) {
                        continue;
                    }
                    if (wantedVaccine != null && (session.getVaccine() == null
                            || !wantedVaccine.equalsIgnoreCase(session.getVaccine().trim()))) {
                        continue;
                    }
                    int capacity = dose.capacity(session);
                    if (capacity > 0) {
                        bookable.add(dose == DoseType.ANY ? session : new Session(session, capacity));
                    }
                }
            }
            if (!bookable.isEmpty()) {
                result.add(new Center(center, bookable));
            }
        }
        Collections.sort(result, BY_CAPACITY_DESC);
        return result;
    }

    /** @return whether at least one dose can be booked in the session. */
    public static boolean isBookable(@Nullable Session session) {
        return session != null
                && session.getAvailableCapacity() != null
                && session.getAvailableCapacity() > 0;
    }

    /** Sum of available doses across the center's sessions. */
    public static int totalAvailable(@Nullable Center center) {
        if (center == null || center.getSessions() == null) {
            return 0;
        }
        int total = 0;
        for (Session session : center.getSessions()) {
            if (session != null && session.getAvailableCapacity() != null && session.getAvailableCapacity() > 0) {
                total += session.getAvailableCapacity();
            }
        }
        return total;
    }

    /** Sum of available doses across all centers. */
    public static int totalAvailable(@Nullable List<Center> centers) {
        if (centers == null) {
            return 0;
        }
        int total = 0;
        for (Center center : centers) {
            total += totalAvailable(center);
        }
        return total;
    }

    /**
     * Stable fingerprint of which sessions are bookable, used by the notifier
     * to avoid re-notifying about the same availability on every run. Two
     * results with the same set of bookable session ids share a signature.
     */
    @NonNull
    public static String signature(@Nullable List<Center> centers) {
        if (centers == null) {
            return "";
        }
        TreeSet<String> ids = new TreeSet<>();
        for (Center center : centers) {
            if (center == null || center.getSessions() == null) {
                continue;
            }
            for (Session session : center.getSessions()) {
                if (isBookable(session)) {
                    String id = session.getSessionId();
                    if (id == null) {
                        id = center.getCenterId() + "@" + session.getDate() + "@" + session.getVaccine();
                    }
                    ids.add(id);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static final Comparator<Center> BY_CAPACITY_DESC = new Comparator<Center>() {
        @Override
        public int compare(Center a, Center b) {
            int byCapacity = Integer.compare(totalAvailable(b), totalAvailable(a));
            if (byCapacity != 0) {
                return byCapacity;
            }
            String nameA = a.getName() == null ? "" : a.getName();
            String nameB = b.getName() == null ? "" : b.getName();
            return nameA.compareToIgnoreCase(nameB);
        }
    };
}
