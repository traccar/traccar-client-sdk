## 0.0.25

* Add support for custom HTTP headers in `Config`, enabling users to supply custom authentication, tokens, or other headers with every HTTP position upload request.

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
