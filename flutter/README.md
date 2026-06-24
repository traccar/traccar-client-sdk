# traccar_client_sdk

Flutter plugin for background location tracking. Sends position updates over a simple HTTP GET protocol - works with [Traccar](https://www.traccar.org) and any compatible server. Wraps the [Traccar Client SDK](https://github.com/traccar/traccar-client-sdk) for Android and iOS - see the main repository for architecture, reliability, and configuration details.

Requires Android API 24+ and iOS 15+.

## Usage

```dart
import 'package:traccar_client_sdk/traccar_client_sdk.dart';

final tracker = TraccarClientSdk();

await tracker.start(Config(
  serverUrl: 'https://demo.traccar.org',
  deviceId: '123456',
));

await tracker.stop();
```

### API

- `start(Config)` - begin tracking. Returns `false` if required Android permissions were denied.
- `stop()` - stop tracking and clear saved config.
- `requestPosition(Config)` - one-off fix and upload, independent of `start`/`stop`.
- `isTracking()` - query current state.
- `getLogs()` - recent diagnostic entries (`time` ms, `message`).
- `clearLogs()` - clear the diagnostic store.

### Configuration

`Config` accepts a `LocationConfig` (`accuracy`, `distanceMeters`, `intervalSeconds`, `angleDegrees`, `stopDetection`, `stopTimeoutSeconds`, `stationaryRadiusMeters`), a Boolean `buffer` (real-time-only when `false`), Android-only `wakeLock`, a `NotificationConfig` for the Android foreground-service text, and a Map of custom `headers`. See the [main repo](https://github.com/traccar/traccar-client-sdk#configuration) for details and defaults.

## Permissions

The plugin requests location, notification (Android 13+), and activity recognition (Android 10+) permissions at runtime via a transparent activity. On first start it also opens the *Ignore Battery Optimization* settings screen once.

On iOS, add these keys to your app's `Info.plist`:

- `NSLocationAlwaysAndWhenInUseUsageDescription`
- `NSLocationWhenInUseUsageDescription`
- `NSMotionUsageDescription`

and enable the **Location updates** background mode in your target capabilities.

If you set `heartbeatIntervalSeconds`, also add `fetch` to `UIBackgroundModes` and add `org.traccar.client.heartbeat` to `BGTaskSchedulerPermittedIdentifiers`. iOS schedules the wake at its discretion.

## License

Apache License 2.0 - see [LICENSE](LICENSE).
