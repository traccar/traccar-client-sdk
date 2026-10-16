// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TraccarClientSDK",
    platforms: [
        .iOS(.v15),
    ],
    products: [
        .library(name: "TraccarClientSDK", targets: ["TraccarClientSDK", "TraccarClientAutoInit"]),
    ],
    targets: [
        .binaryTarget(
            name: "TraccarClientSDK",
            url: "https://github.com/traccar/traccar-client-sdk/releases/download/v1.1.3/TraccarClientSDK.xcframework.zip", checksum: "73217e9d260f2db2c0142843fbef691c4a28a9d5dfd9b68f3dd567f6ca58afb2"
        ),
        .target(
            name: "TraccarClientAutoInit",
            dependencies: ["TraccarClientSDK"],
            path: "core/Sources/TraccarClientAutoInit",
            publicHeadersPath: "include",
            linkerSettings: [.linkedLibrary("sqlite3")]
        ),
    ]
)
