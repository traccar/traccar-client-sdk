import Flutter
import UIKit
import TraccarClientSDK

public class TraccarClientSdkPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "traccar_client_sdk", binaryMessenger: registrar.messenger())
    let instance = TraccarClientSdkPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "init":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      runHandler(result) {
        _ = try await TrackerKt.sharedTracker(config: config)
        return nil
      }
    case "setConfig":
      let args = call.arguments as! [String: Any]
      let config = parseConfig(args)
      runHandler(result) {
        _ = try await TrackerKt.sharedTracker()!.updateConfig(newConfig: config)
        return nil
      }
    case "start":
      runHandler(result) {
        try await TrackerKt.sharedTracker()!.start()
        return nil
      }
    case "stop":
      runHandler(result) {
        try await TrackerKt.sharedTracker()?.stop()
        return nil
      }
    case "requestPosition":
      let alarm = (call.arguments as? [String: Any])?["alarm"] as? String
      runHandler(result) {
        return try await TrackerKt.sharedTracker()?.requestPosition(alarm: alarm) ?? false
      }
    case "isTracking":
      runHandler(result) {
        guard let tracker = try await TrackerKt.sharedTracker() else { return false }
        return (tracker.state.value as? State)?.enabled ?? false
      }
    case "getLogs":
      runHandler(result) {
        guard let tracker = try await TrackerKt.sharedTracker() else { return [] as [[String: Any]] }
        return try await tracker.getLogs().map { ["time": $0.time, "message": $0.message] as [String: Any] }
      }
    case "clearLogs":
      runHandler(result) {
        try await TrackerKt.sharedTracker()?.clearLogs()
        return nil
      }
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func runHandler(_ result: @escaping FlutterResult, block: @escaping () async throws -> Any?) {
    Task {
      do {
        let value = try await block()
        result(value)
      } catch {
        result(FlutterError(code: String(describing: type(of: error)), message: error.localizedDescription, details: nil))
      }
    }
  }

  private func parseConfig(_ args: [String: Any]) -> Config {
    let location = args["location"] as! [String: Any]
    let notification = args["notification"] as! [String: Any]
    return Config(
      serverUrl: args["serverUrl"] as! String,
      deviceId: args["deviceId"] as! String,
      location: LocationConfig(
        accuracy: parseAccuracy(location["accuracy"] as! String),
        distanceMeters: Int32(location["distanceMeters"] as! Int),
        intervalSeconds: Int32(location["intervalSeconds"] as! Int),
        angleDegrees: Int32(location["angleDegrees"] as! Int),
        stopDetection: location["stopDetection"] as! Bool,
        stopTimeoutSeconds: Int32(location["stopTimeoutSeconds"] as! Int),
        stationaryRadiusMeters: Int32(location["stationaryRadiusMeters"] as! Int),
        heartbeatIntervalSeconds: Int32(location["heartbeatIntervalSeconds"] as! Int)
      ),
      wakeLock: args["wakeLock"] as! Bool,
      buffer: args["buffer"] as! Bool,
      preferPlatformProviders: args["preferPlatformProviders"] as! Bool,
      notification: NotificationConfig(text: notification["text"] as! String),
      headers: (args["headers"] as? [String: String]) ?? [:],
      imei: args["imei"] as? String,
      attributes: (args["attributes"] as? [String: String]) ?? [:],
      uploadJson: (args["uploadJson"] as? Bool) ?? false
    )
  }

  private func parseAccuracy(_ name: String) -> Accuracy {
    switch name {
    case "HIGHEST": return Accuracy.highest
    case "HIGH": return Accuracy.high
    case "LOW": return Accuracy.low
    default: return Accuracy.medium
    }
  }
}
