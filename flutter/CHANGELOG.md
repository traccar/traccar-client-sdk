## 1.0.11

* Add location to heartbeat

## 1.0.10

* Attempt to fix iOS tracking

## 1.0.9

* Validate location values

## 1.0.8

* Disable automatic location pausing on iOS

## 1.0.7

* Automate version stamping and changelog in release
* Create notification channel at startup
* Update plugin versions to 1.0.6
* Update README files

## 1.0.6

* Fix a crash when location permission is revoked while tracking is active. `AndroidLocationSource` and `FusedLocationSource` requested location updates from within a state observer without catching `SecurityException`. If the OS-level location permission was lost while the foreground service kept running, the next transition to active tracking called `requestLocationUpdates` with no permission, and the resulting `SecurityException` propagated out of the observer coroutine and crashed the app. The framework calls in both location sources — continuous updates and the one-off position fetch — now catch `SecurityException` and log it instead, so tracking stops cleanly and resumes once permission is restored.

## 1.0.5

* Fix a crash-loop on the persistent position queue (`no such column: charging` while reading the `Position` table). The `charging` column — like `altitude`/`speed`/`bearing`/`battery` before it — was added to the `Position` table definition without a matching migration, so it only reached fresh installs; databases created before it existed never gained the column. The schema version only advanced when the `State` migration shipped in 1.0.3, and that migration left `Position` untouched, so affected installs crash-looped on every read of the upload queue with no way to recover short of reinstalling. A new migration bumps the schema to version 3 and rebuilds `Position` with the current columns, preserving already-queued rows.

## 1.0.4

* Harden the iOS plugin bridge against an uninitialized tracker. `start`, `setConfig`, and `updateConfig` force-unwrapped `sharedTracker()`, so invoking any of them before `init` crashed the app; they now guard the optional and return a clean `notInitialized` `FlutterError` instead.
* Fix `requestPosition` always reporting success on iOS. The Kotlin/Native suspend `Boolean` arrives boxed as a `KotlinBoolean`, and the raw box was returned to the method channel instead of a Swift `Bool`, so callers could not tell whether the upload actually succeeded. It is now unboxed with `.boolValue`, propagating the real result (and `false` when the tracker is absent).

## 1.0.3

* Add the missing SQLDelight migration for the persistent `State` table. State persistence was added to the schema without a migration file, so the schema version never advanced past 1 — databases created before the state store existed stayed on the old schema and never gained the table, and reading or writing tracker state on those installs failed with `no such table: State`. The migration bumps the schema to version 2 and creates the table on upgrade (`CREATE TABLE IF NOT EXISTS State`, so installs that already have it are unaffected), restoring cross-process state persistence without a reinstall.

## 1.0.2

* Republish 1.0.1 with the native Android (Maven Central) and iOS (XCFramework) SDK included. The 1.0.1 release published only the Dart side, so the native fixes below did not actually reach Android and iOS consumers; 1.0.2 ships them properly.

## 1.0.1

* Fix an Android crash in the one-off `requestPosition` path when location permission is not granted (`SecurityException: uid ... does not have ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION`). Unlike `start`, the single-fix path went straight to the platform location API without checking permission, so the OS threw a raw `SecurityException`. It now ensures permission first — prompting the same way `start` does — and throws a clean `IllegalStateException` ("Location permission denied") on denial instead of an uncatchable-looking platform error.
* Android tracking no longer requires notification or activity-recognition permission. The permission flow now requires only fine location; when `POST_NOTIFICATIONS` is not granted the service falls back to a non-foreground start instead of forcing a foreground start, and activity-recognition registration is skipped when that permission is absent — so declining either no longer blocks tracking.

## 1.0.0

* Fix an Android crash on devices where the location provider matching the configured accuracy is not registered (`IllegalArgumentException: provider "gps" does not exist`). The SDK assumed the GPS/network/passive provider was always present, but some devices lack it (no GPS hardware, non-GMS builds). It now falls back to an available provider instead of crashing.
* Fix an Android crash on API 29 and older (`AbstractMethodError` on `LocationListener.onProviderDisabled`). The platform interface only gained default callback methods in API 30, so a lambda listener left them unimplemented. The listener is now built with `LocationListenerCompat`, whose default implementations are bundled with the app.

## 0.0.26

* Fix Android tracking staying in movement mode when the device is already stationary at start. Stop detection relies on the activity-recognition transition API, which only reports changes between activities and never the current one, so a device that was already still when tracking began never received a still-enter event and kept GPS at movement cadence — movement-interval uploads, no stationary heartbeat — until it physically moved. Tracking now takes a one-shot activity sample at startup to seed the stationary state.

## 0.0.25

* The `buffer` config flag is honored again. With `buffer: false`, each position is uploaded directly and dropped on failure (real-time only) instead of being persisted to the retry queue. The flag had become inert after an earlier refactor, so everything was buffered regardless of the setting.
* The `stopDetection` config flag is honored again. With `stopDetection: false`, the motion-detection components are no longer created (activity recognition + geofence on Android, motion activity + region monitoring on iOS), so tracking never pauses when the device is stationary. The flag had been ignored and stop detection always ran.
* `HIGHEST` accuracy no longer forces stop detection off. Accuracy (the sampling regime while moving) and stationary suspension are independent, so highest accuracy can be combined with stationary battery savings.
* The background heartbeat is only scheduled when stop detection is enabled and `heartbeatIntervalSeconds > 0`; it only emits while paused, so it had no effect otherwise.
* Fix location filtering when `intervalSeconds` is 0. The time trigger no longer accepts every fix — which previously defeated distance filtering — and is disabled when the interval is zero, matching the angle trigger.

## 0.0.24

* Android foreground service now returns `START_STICKY`, so after the OS kills the process under memory pressure it is recreated and re-foregrounded, resuming tracking from persisted state. Previously `START_NOT_STICKY` left tracking dead until an external trigger (boot, activity-recognition, geofence) fired. The failure path — initial `startForeground` blocked — still returns `START_NOT_STICKY` and calls `stopSelf()` to avoid restarting straight back into the same blocked state.
* Harden Android foreground service start. `startForegroundService()` is wrapped to catch the Android 12+ background-start ban (`ForegroundServiceStartNotAllowedException`), and `startForeground()` is guarded so a denial tears the service down with `stopSelf()` instead of risking the uncatchable 5-second "did not call startForeground in time" crash.
* Android heartbeat alarm schedules against the monotonic clock (`ELAPSED_REALTIME_WAKEUP` + `SystemClock.elapsedRealtime()`) instead of `RTC_WAKEUP`, so the interval survives wall-clock changes, and uses an immutable `PendingIntent`.
* Fix the iOS network monitor being deallocated after construction. `nw_path_monitor` is now retained in a property; previously its only reference was a local, so once it was collected the monitor stopped delivering updates and `isOnline` could stick at `false`, stalling all uploads behind the sync loop's offline wait.

## 0.0.23

* `requestPosition` accepts an optional `alarm` string that becomes the Traccar protocol `alarm` field on the upload (e.g. `"sos"`). One-off remains direct — no buffer/retry — so a failed SOS is not persisted.
* `fetchOnce` is now resilient. All three location sources wait up to 30s for a fresh fix and fall back to the platform's last-known cache: `getLastLocation` on Fused, `getLastKnownLocation(provider)` on `LocationManager`, `manager.location` on iOS. Single timeout constant lives in `commonMain`.
* Fix iOS `fetchOnce` returning null instantly when the tracker was not started. It now builds a transient `CLLocationManager` on demand so the one-off works independently of `start`/`stop` (matching Android, where the platform single-fix APIs never required a running manager). `didFailWithError` completes the pending fix with null, so denial/error fails fast instead of waiting the full 30s.

## 0.0.22

* Keep stop-detection signals subscribed while parked so movement out of stationary mode is observed as soon as the OS reports a non-still activity transition, rather than waiting for the user to cross the `stationaryRadiusMeters` geofence/region boundary. `ActivityRecognitionDetector` (Android) and `MotionActivityDetector` (iOS) now gate only on `state.enabled`, not on `enabled && !paused`. The geofence/region still provides the kill-resilient exit signal.

## 0.0.21

* Fix the iOS auto-init shim failing to compile against the SDK. `register()` is now called on the `IosBackgroundHeartbeat` companion (it was sent to the class, which only exposes `companion`), and the obsolete `Tracker.resume()` call — removed in the tracker-construction restructure but left in the shim — is dropped. Reconstructing the shared tracker on launch already re-attaches OS signals and resumes from persisted state.

## 0.0.20

* Wire `LocationConfig.heartbeatIntervalSeconds` through the Flutter plugin (the field has existed on the native SDK since 0.0.15 but was never exposed). Dart `LocationConfig` now accepts the value (default `0`) and both the Android and iOS plugin bridges parse it from the method-channel payload. Fixes the iOS build break where Kotlin/Native exposes the field as a required initializer argument with no default.

## 0.0.19

* Wire `Config.preferPlatformProviders` through the Flutter plugin (0.0.18 added the field on the native SDK only). Dart `Config` now exposes the flag and both the Android and iOS plugin bridges parse it from the method-channel payload.

## 0.0.18

* Add `Config.preferPlatformProviders` — when `true`, the Android SDK uses `LocationManager` directly even when Google Play Services is available. Default `false` keeps the existing Fused-when-available behaviour.
* Restore and round out diagnostic logging across components: receiver wake-ups (boot, activity recognition, geofence, heartbeat alarm), geofence and region exit events, location updates starting and stopping on every source, stop-detection timer arming and cancellation, network restored after an offline wait, plus matching teardown lines for activity transitions, geofences, heartbeats, and region monitoring. Activity transitions on Android are now logged with lowercase, human-readable names to match the iOS motion log.
* `requestPosition` brackets its work with `Position requested` / `Position fetched` / `Position request: no fix`.

## 0.0.17

* Fix `setConfig` / `Tracker.updateConfig` returning a tracker bound to the old config — every subsequent action (uploads, observers, `Tracker.config`) kept seeing the previous `serverUrl`/`deviceId`. Caused by Koin caching singleton instances on shared `Module` objects across `KoinApplication` instances; modules are now constructed per Koin app.

## 0.0.16

* **Breaking:** split SDK setup from action methods. Replace `start(Config)` and `requestPosition(Config)` with explicit `init(Config)` (idempotent install, call once at app startup) plus zero-arg `start()` and `requestPosition()`. Add `setConfig(Config)` for runtime config updates against a running tracker.
* Native SDK rewrite: tracker now exposes `state` (StateFlow) for reactive UIs, `start`/`stop` are suspend and persist their writes before returning, `updateConfig` rebuilds the SDK in place without losing tracking state, and components clean up OS-level resources deterministically via a single `observeState` cancellation path.
* Stop-detection no longer waits up to ten seconds for a final GPS fix when the user just hits Stop; the wait only applies when transitioning to the SDK's stationary mode (where the fix is the geofence anchor).
* The actual stop position is now uploaded — previously dropped by the location filter's distance triggers because the user wasn't moving.
* Heartbeats survive process death: Android uses `AlarmManager` (`setAndAllowWhileIdle` through Doze), iOS uses `BGTaskScheduler` as a proper SDK signal source.
* Static manifest receivers for activity transitions, geofence exits, and heartbeats — events fire even when the SDK process has been killed.

## 0.0.15

* Persist tracker state across process kills: stationary mode and the location filter's reference position now survive cold launch, so resume after an iOS region exit or Android `BootReceiver` restart no longer re-runs the 60s stop-detection timeout or emits a duplicate-feeling first fix.
* SDK errors propagate to Flutter as `PlatformException` instead of silently hanging the method-channel result (includes permission denial from `start`).
* `requestPosition` returns `Future<bool>` indicating whether the upload succeeded; previously fire-and-forget with no result.
* Internal: all store I/O is properly async (no sync DB on the main thread), tracker construction uses a suspend factory plus Android `ContentProvider` / iOS `+load` for context capture, and runtime state has a single source of truth backed by `StateStore`.

## 0.0.14

* Drop the activity-recognition snapshot subscription added in 0.0.10. The transition subscription itself delivers the current state as an `ENTER` event on registration (verified on Android, assumed by symmetry on iOS), so the separate snapshot path is redundant.
* Remove `ON_FOOT` from the Android activity transition request as a precaution; the remaining types (`STILL`, `IN_VEHICLE`, `ON_BICYCLE`, `RUNNING`, `WALKING`) cover all real movement cases.
* Log every activity transition with human-readable names on Android, and every motion update on iOS, to aid stop-detection diagnostics.

## 0.0.13

* Fix Android stop-detection silently never engaging: the activity recognition broadcast receiver was registered with `RECEIVER_NOT_EXPORTED`, which blocks PendingIntent deliveries originated by Google Play Services. Switched to `RECEIVER_EXPORTED`.
* Register activity transitions for all supported types (STILL, IN_VEHICLE, ON_BICYCLE, ON_FOOT, RUNNING, WALKING) so any movement transition is observable, not just STILL enter/exit.
* Log activity recognition request results and incoming events on both Android and iOS to aid stop-detection debugging.

## 0.0.12

* Send position uploads as `POST` with form-encoded body instead of `GET` query string.

## 0.0.11

* Fix iOS build broken in 0.0.10 by an incorrect `NSDate` constructor used for the motion history query window.
* Remove `TrackerLivenessWorker` — Android 12+ restrictions made it silently fail to restart the foreground service; recovery now relies on `START_REDELIVER_INTENT` and `BootReceiver`.

## 0.0.10

* Detect already-stationary state at start (Android `requestActivityUpdates` snapshot, iOS `queryActivityStarting`) so stop-detection engages even when the user hasn't transitioned since tracking began.
* Use a single OS-level filter on Android: when `distanceMeters > 0`, request by distance only; otherwise request by time. Avoids the AND deadlock that left stationary users with no updates.

## 0.0.9

* Request an immediate first fix on Android via `getCurrentLocation`, avoiding a multi-minute silent period after `start` with large `intervalSeconds` or balanced-power accuracy.

## 0.0.8

* Add `requestPosition(Config)` for a one-off fix and upload, independent of `start`/`stop`. Disables stop-detection on the provider and applies a 30s timeout.

## 0.0.7

* Add `Config.buffer` (default `true`). When `false`, positions upload directly without queue or retry (real-time only).

## 0.0.6

* Add `isTracking()` to query current tracking state.

## 0.0.5

* No functional changes; ships pub.dev publishing pipeline fix.

## 0.0.3

* Expose full `Config` (`LocationConfig`, `NotificationConfig`, `Accuracy`).
* Bridge `clearLogs()`.
* `getLogs()` now returns `List<LogEntry>` with structured `time` and `message` fields.
* Breaking change vs 0.0.2: `start({serverUrl, deviceId})` is now `start(Config)`; `getLogs()` return type changed from `List<String>`.

## 0.0.2

* Initial plugin scaffold. `start`, `stop`, and `getLogs` for background location tracking on Android and iOS.
