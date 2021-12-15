# VaxiCov – vaccination slot finder and notifier

VaxiCov is a small Android app that finds bookable vaccination slots by
pincode or district and keeps watching in the background so you get a
notification the moment new slots open up. It was built in May 2021, at the
height of India's second COVID-19 wave, when slots on the CoWIN portal were
gone within minutes of being published.

**Download:** [Google Play](https://play.google.com/store/apps/details?id=com.akshaychavan.vaxicov)

## What it does

- **Search** the next seven days of sessions for a pincode or a
  state/district, filtered to an age group (18-44, 45+ or all). Only
  sessions with at least one available dose are shown, best-stocked centers
  first.
- **Notify me** starts a background watch on the same query. The app checks
  roughly every 15 minutes, even when closed or after a reboot, and posts a
  notification only when the set of bookable sessions changes, so you are
  not pinged repeatedly about the same slots. Tapping the notification opens
  the app with the search already run.
- **Runs without a backend.** *Use sample data* in the overflow menu
  switches to a bundled, deterministic data source, so the whole flow can be
  demonstrated offline and the app stays usable for development when the
  live registry is unavailable.
- **No sign-in, no tracking.** The app needs the `INTERNET` permission and
  nothing else.

## Built for the next one too

The registry an app like this talks to is different for every country and
every outbreak. VaxiCov therefore depends on one small interface,
[`SlotProvider`](app/src/main/java/com/akshaychavan/vaxicov/data/SlotProvider.java),
and everything else (search, filtering, alerts, UI) is written against it:

```java
public interface SlotProvider {
    String name();
    List<State> states() throws IOException;
    List<District> districts(int stateId) throws IOException;
    List<Center> centersByPin(int pincode, String date) throws IOException;
    List<Center> centersByDistrict(int districtId, String date) throws IOException;
}
```

Two implementations ship today:

| Provider | Source |
| --- | --- |
| `CowinSlotProvider` | India's public [CoWIN API](https://apisetu.gov.in/public/marketplace/api/cowin) via Retrofit |
| `SampleSlotProvider` | Fabricated but realistic data that changes hourly; no network |

To support a different registry, implement `SlotProvider`, map its payload
onto the `Center`/`Session` models, and return it from
[`SlotProviders`](app/src/main/java/com/akshaychavan/vaxicov/data/SlotProviders.java).
Nothing else needs to change.

## Architecture

```
com.akshaychavan.vaxicov
├── MainActivity            single screen: form, results list, alert toggle
├── AppPreferences          typed SharedPreferences (watched query, data source)
├── domain/                 pure Java, unit tested
│   ├── AgeGroup            eligibility floors (18-44, 45+, all)
│   ├── SearchQuery         immutable pincode/district + age-group query
│   ├── SlotFilter          keeps bookable sessions, sorts centers, change signature
│   └── DateFormats         dd-MM-yyyy handling pinned to Locale.US
├── data/
│   ├── SlotProvider        the extension point (see above)
│   ├── CowinSlotProvider   live registry
│   ├── SampleSlotProvider  offline data
│   ├── SlotProviders       picks the active provider
│   └── SlotRepository      background executor + main-thread callbacks
├── network/                Retrofit service, OkHttp client, bundled state list
├── notifier/
│   ├── SlotNotifierScheduler   WorkManager scheduling (immediate + periodic)
│   ├── SlotCheckWorker         re-fetches the watched query, notifies on change
│   └── AvailabilityNotifier    notification channel and content
├── adapters/CenterAdapter  RecyclerView cards
└── pojo/                   Gson models for the CoWIN payloads
```

Design notes:

- The domain layer has no Android imports, which is what makes it testable
  with plain JUnit and reusable from both the Activity and the worker.
- Background work uses WorkManager instead of `AlarmManager`. It respects
  Doze, survives reboots, and the 15-minute period is the platform minimum.
- Dates sent to the API are formatted with `Locale.US` so devices set to a
  locale with non-Latin digits still produce `dd-MM-yyyy`.
- The registry base URL is a `BuildConfig` field read from
  `local.properties`, so a fork can point at a mirror or a staging endpoint
  without touching source.

## Building

Requirements: Android Studio 4.2 or newer (JDK 8 or 11), Android SDK 30.

```bash
git clone https://github.com/JayeshSuryavanshi/VaxiCov-COVID19-Vaccine-Center-Availability-Checker.git
cd VaxiCov-COVID19-Vaccine-Center-Availability-Checker
cp local.properties.example local.properties   # optional, see Configuration
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK lands in `app/build/outputs/apk/debug/`. Turn on
*Use sample data* from the overflow menu to try the app without network
access.

### Configuration

`local.properties` (git-ignored) can override:

| Key | Default | Purpose |
| --- | --- | --- |
| `vaxicov.cowinBaseUrl` | `https://cdn-api.co-vin.in/api/v2/` | Base URL of the CoWIN API |

### Tests

`app/src/test` covers the domain layer and the sample provider: age-group
matching, pincode validation, filtering and ordering, change signatures,
date formatting and parsing, and the shape and determinism of sample data.

## Screenshots

From the 1.0 release on Google Play. The 1.1 UI keeps the look but drops
the sign-in screen and the drawer.

<p>
  <img src="Screenshots/1.png" width="200" alt="Sign-in" />
  <img src="Screenshots/2.png" width="200" alt="Search by pincode" />
  <img src="Screenshots/3.png" width="200" alt="Results" />
  <img src="Screenshots/4.png" width="200" alt="Notification settings" />
</p>

## Credits

Built by Akshay Chavan and Jayesh Suryavanshi for family, friends and
neighbours who were refreshing CoWIN by hand in May 2021.
