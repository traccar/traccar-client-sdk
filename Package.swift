// swift-tools-version:5.5
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
            url: "https://github.com/traccar/traccar-client-sdk/releases/download/v0.0.2/TraccarClientSDK.xcframework.zip", checksum: "17e476d4ced362b100e1c640afae61d5bee139dd3dc5fc98b9cbefda66d0458c"
        ),
        .target(
            name: "TraccarClientAutoInit",
            dependencies: ["TraccarClientSDK"],
            path: "core/Sources/TraccarClientAutoInit",
            publicHeadersPath: "include"
        ),
    ]
)
