import SwiftUI
import TraccarClientSDK

private let defaultServerUrl = "https://demo.traccar.org/"
private let defaultDeviceId = "123456"

@main
struct SampleApp: App {
    @SwiftUI.State private var tracker: Tracker?

    var body: some Scene {
        WindowGroup {
            if let tracker = tracker {
                ContentView(initialTracker: tracker)
            } else {
                ProgressView()
                    .task {
                        let config = Config(serverUrl: defaultServerUrl, deviceId: defaultDeviceId)
                        tracker = try? await TrackerKt.sharedTracker(config: config)
                    }
            }
        }
    }
}

struct ContentView: View {
    let initialTracker: Tracker

    @SwiftUI.State private var tracker: Tracker
    @SwiftUI.State private var serverUrl: String
    @SwiftUI.State private var deviceId: String
    @SwiftUI.State private var isTracking = false

    init(initialTracker: Tracker) {
        self.initialTracker = initialTracker
        self._tracker = SwiftUI.State(initialValue: initialTracker)
        self._serverUrl = SwiftUI.State(initialValue: initialTracker.config.serverUrl)
        self._deviceId = SwiftUI.State(initialValue: initialTracker.config.deviceId)
    }

    var body: some View {
        Form {
            Section {
                TextField("Server URL", text: $serverUrl)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                TextField("Device ID", text: $deviceId)
                    .textInputAutocapitalization(.never)
            }
            Section {
                Button("Apply config") {
                    Task {
                        tracker = try await tracker.updateConfig(newConfig: Config(serverUrl: serverUrl, deviceId: deviceId))
                    }
                }
                Button(isTracking ? "Stop" : "Start") {
                    Task {
                        if isTracking {
                            try await tracker.stop()
                            isTracking = false
                        } else {
                            try await tracker.start()
                            isTracking = true
                        }
                    }
                }
            }
        }
    }
}
