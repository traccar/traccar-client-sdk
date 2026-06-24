# Traccar Client SDK

A Kotlin Multiplatform background location tracking SDK for [Traccar](https://www.traccar.org) - and any other server that accepts the same simple HTTP GET protocol. Runs on Android and iOS, persists positions in a local SQLite queue, and uploads them with network-aware retry.

This repository publishes two artifacts:

- **Native SDK** - Maven Central (`org.traccar:traccar-client-sdk`) and an XCFramework distributed via Swift Package Manager.
- **Flutter plugin** - pub.dev (`traccar_client_sdk`).

## Install

### Android (Gradle)

```kotlin
dependencies {
    implementation("org.traccar:traccar-client-sdk:0.0.25")
}
```

### iOS (Swift Package Manager)

```swift
.package(url: "https://github.com/traccar/traccar-client-sdk.git", from: "0.0.25")
```

### Flutter

```yaml
dependencies:
  traccar_client_sdk: ^0.0.25
```

## Quick start

### Android (Kotlin)

```kotlin
val config = Config(
    serverUrl = "https://demo.traccar.org",
    deviceId = "123456",
)
// suspend; returns false if required permissions were denied.
Tracker.shared(activity).start(activity, config)

// later
Tracker.shared(context).stop(context)
```

`start` requires a `ComponentActivity` because it launches a transparent permission activity if location, notification, or activity-recognition permissions are not yet granted.

### iOS (Swift)

```swift
import TraccarClientSDK

let config = Config(
    serverUrl: "https://demo.traccar.org",
    deviceId: "123456"
)
Tracker.shared.start(config: config)

// in AppDelegate.application(_:didFinishLaunchingWithOptions:)
Tracker.shared.resume()
```

`Tracker.resume()` reloads the saved config and restarts the engine if iOS launches the app in the background in response to a significant location change or region exit. Without it, those background wakes are silent.

### Flutter

```dart
import 'package:traccar_client_sdk/traccar_client_sdk.dart';

final tracker = TraccarClientSdk();

await tracker.start(Config(
  serverUrl: 'https://demo.traccar.org',
  deviceId: '123456',
));

// later
await tracker.stop();
```

## Configuration

### `Config`

| Field | Type | Default | Description |
|---|---|---|---|
| `serverUrl` | `String` | - | Traccar server endpoint (`https://demo.traccar.org`). |
| `deviceId` | `String` | - | Device identifier reported to the server. |
| `location` | `LocationConfig` | defaults | Tuning parameters for the location pipeline. |
| `wakeLock` | `Boolean` | `false` | Hold a partial CPU wakelock while tracking (Android only). |
| `buffer` | `Boolean` | `true` | When `true`, persist positions to a local SQLite queue and retry on failure. When `false`, attempt direct upload per position and drop on failure (real-time only). |
| `notification` | `NotificationConfig` | defaults | Foreground-service notification text (Android only). |
| `headers` | `Map<String, String>` | `emptyMap()` | Custom headers to include in the HTTP upload request. |

### `LocationConfig`

| Field | Type | Default | Description |
|---|---|---|---|
| `accuracy` | `Accuracy` | `MEDIUM` | `HIGHEST`, `HIGH`, `MEDIUM`, or `LOW`. See accuracy mapping below. |
| `distanceMeters` | `Int` | `75` | Minimum displacement between accepted positions. |
| `intervalSeconds` | `Int` | `300` | Time-based heartbeat between accepted positions. |
| `angleDegrees` | `Int` | `0` | Heading-change threshold for additional acceptance. `0` disables. |
| `stopDetection` | `Boolean` | `true` | Pause GPS while the user is stationary (motion-aware). |
| `stopTimeoutSeconds` | `Int` | `60` | How long the user must be detected as STILL before location updates pause. |
| `stationaryRadiusMeters` | `Int` | `100` | iOS only - radius of the geofence monitored around the stationary point. |

`Accuracy.HIGHEST` is a special mode: it overrides `distanceMeters = 0`, `intervalSeconds = 0`, and `stopDetection = false`, requesting the maximum-rate stream from the OS. Use it for navigation-style scenarios where battery is not a concern.

| Accuracy | Android (Fused) | Android (Plain) | iOS |
|---|---|---|---|
| `HIGHEST` | `PRIORITY_HIGH_ACCURACY` | `GPS_PROVIDER` | `kCLLocationAccuracyBestForNavigation` |
| `HIGH` | `PRIORITY_HIGH_ACCURACY` | `GPS_PROVIDER` | `kCLLocationAccuracyBest` |
| `MEDIUM` | `PRIORITY_BALANCED_POWER_ACCURACY` | `NETWORK_PROVIDER` | `kCLLocationAccuracyHundredMeters` |
| `LOW` | `PRIORITY_LOW_POWER` | `PASSIVE_PROVIDER` | `kCLLocationAccuracyKilometer` |

### `NotificationConfig` (Android)

| Field | Type | Default | Description |
|---|---|---|---|
| `text` | `String` | `"Location tracking"` | Body text of the foreground-service notification. |

## API

| Method | Kotlin (Android) | Swift (iOS) | Dart (Flutter) | Notes |
|---|---|---|---|---|
| Start tracking | `Tracker.shared(ctx).start(activity, config): Boolean` (suspend) | `Tracker.shared.start(config:)` | `tracker.start(config)` | Returns `false` on Android if required permissions were denied. |
| Stop tracking | `Tracker.shared(ctx).stop(context)` | `Tracker.shared.stop()` | `tracker.stop()` | Stops the engine and clears the persisted config. |
| Resume after background wake | n/a | `Tracker.shared.resume()` | n/a | iOS only. Reloads saved config and restarts the engine if not already running. |
| Query state | `Tracker.shared(ctx).isTracking` | `Tracker.shared.isTracking` | `tracker.isTracking()` | Boolean. |
| One-off fix and upload | `Tracker.shared(ctx).requestPosition(ctx, config)` | `Tracker.shared.requestPosition(config:)` | `tracker.requestPosition(config)` | Independent of `start` / `stop`. Disables stop-detection for the request and times out after 30s. |
| Read diagnostic log | `Tracker.shared(ctx).getLogs(): List<LogEntry>` | `Tracker.shared.getLogs()` | `tracker.getLogs()` | Returns recent entries with `time` (epoch ms) and `message`. |
| Clear diagnostic log | `Tracker.shared(ctx).clearLogs()` | `Tracker.shared.clearLogs()` | `tracker.clearLogs()` | |

## How it works

The pipeline is the same on both platforms:

```
PositionProvider → LocationFilter → TrackerEngine → PositionQueue → HttpUploader → server
```

- **PositionProvider** - wraps the platform location API. On Android, `FusedLocationProvider` is preferred when Google Play Services is available, otherwise `AndroidLocationProvider` (plain `LocationManager`). On iOS, `IosLocationProvider` wraps `CLLocationManager`. Each provider also subscribes to activity recognition so the engine can pause GPS while the user is stationary.
- **LocationFilter** - application-level OR filter: a position is accepted if it satisfies any of the time, distance, or angle thresholds.
- **TrackerEngine** - collects accepted positions and, depending on `Config.buffer`, either enqueues them for retry-on-failure upload or attempts a direct upload per position. Sync loop uses exponential backoff (5s → 5min) on upload failure and waits on `NetworkMonitor` when offline.
- **PositionQueue / DatabaseQueue** - SQLite-backed FIFO queue (via SQLDelight). Survives app and OS restarts.
- **HttpUploader** - Ktor client; sends each position as an HTTP GET with query parameters (`id`, `lat`, `lon`, `timestamp`, `accuracy`, optionally `altitude`, `speed` in knots, `bearing`, `batt`). This is the OsmAnd-style protocol Traccar consumes; any server that accepts the same params can be the endpoint. Returns success on any 2xx.
- **NetworkMonitor** - platform-specific connectivity observer used by the sync loop to wait for the network before retrying.

### Filters and OS request shape (Android)

`LocationFilter` is OR (any trigger accepts). The OS request is single-criterion to avoid the AND deadlock that produces silent "stationary forever" behavior: if `distanceMeters > 0`, the OS is asked to deliver on distance only; otherwise it delivers on time only. `Accuracy.HIGHEST` zeroes both and requests the max rate.

### Stop detection

Both platforms use the OS's activity recognition to pause GPS when the user is sitting still:

- **Android** - `ActivityRecognitionClient.requestActivityTransitionUpdates` (transitions) **and** a one-shot `requestActivityUpdates` snapshot at start so that already-stationary devices are correctly classified rather than waiting for a transition that never fires.
- **iOS** - `CMMotionActivityManager.startActivityUpdates` (live updates) **and** a `queryActivityStarting` historical query at start (24h window) for the same already-stationary case. When confirmed stationary, the SDK starts monitoring a `CLCircularRegion` around the device so iOS can wake the app on exit.

### Fast first fix

To avoid a silent initial period when the configured interval is large, both Android providers issue a one-shot `getCurrentLocation` alongside the periodic stream. iOS does not need this - `startUpdatingLocation` delivers within seconds.

### Persistence and recovery

- **`ConfigStore`** - persists the active config so background-launched services know what to do.
- **`PositionQueue`** - persists positions across restarts.
- **`LogStore`** - persists the diagnostic log retrievable via `getLogs`.

Both platforms hold a small, self-contained SQLite database (`tracker.db`).

## Reliability

### Android

- **Foreground service** (`TrackerService`) with `FOREGROUND_SERVICE_TYPE_LOCATION` keeps the process alive and visible to the user.
- **`START_REDELIVER_INTENT`** restarts the service with the original intent if the OS kills it (e.g., memory pressure).
- **`BootReceiver`** restarts the service after `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.

Realistic limits: aggressive OEM task killers (Xiaomi/Huawei) can ignore Android's contract; the user can force-stop the app from settings; the *restricted* App Standby Bucket disables most background work. These are platform limitations, not bugs.

### iOS

- **Significant Location Changes** (`startMonitoringSignificantLocationChanges`) - the key API that wakes the app from a terminated state on roughly ~500m shifts.
- **Region monitoring** - when the SDK detects the user is stationary it registers a `CLCircularRegion` around the spot so iOS wakes the app on exit.
- **`Tracker.resume()`** - must be called from `application(_:didFinishLaunchingWithOptions:)` (or equivalent) so SLC / region wakes actually restart the engine.

Realistic limits: user-initiated force-quit from the App Switcher disables SLC until the user reopens the app; phone reboot requires the user to open the app once before tracking resumes (iOS has no `BootReceiver` equivalent); Low Power Mode can reduce wake frequency.

## Permissions

### Android (in your `AndroidManifest.xml`)

The SDK manifest already declares:

- `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `ACTIVITY_RECOGNITION` (Android 10+)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS` (Android 13+)
- `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `INTERNET`, `ACCESS_NETWORK_STATE`

Runtime permission prompts (location, notifications, activity recognition, background location) are launched automatically by `start`. On first start, the SDK also opens the *Ignore Battery Optimization* settings screen once.

### iOS (in your `Info.plist`)

Add both:

- `NSLocationAlwaysAndWhenInUseUsageDescription`
- `NSLocationWhenInUseUsageDescription`
- `NSMotionUsageDescription` (for activity-based stop detection)

And enable the **Location updates** background mode in your target capabilities.

If you use `heartbeatIntervalSeconds`, also add `fetch` to `UIBackgroundModes` and register the background task identifier:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>org.traccar.client.heartbeat</string>
</array>
```

Note: iOS schedules `BGAppRefreshTask` at its discretion — the interval is a "no sooner than" hint, not a guarantee.

## Diagnostic log

`getLogs()` returns the SDK's internal log entries, oldest first. Each `LogEntry` carries `time` (epoch ms) and `message`. Useful for surfacing tracker state in a debug screen. `clearLogs()` empties the store.

## License

Apache License 2.0. See [LICENSE](LICENSE).
