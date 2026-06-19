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
            url: "https://github.com/traccar/traccar-client-sdk/releases/download/v0.0.19/TraccarClientSDK.xcframework.zip", checksum: "5cb42db9c73d3dbe9fd032acae63bfdfbc67e0e314275a09372f83089cd79aef"
        ),
        .target(
            name: "TraccarClientAutoInit",
            dependencies: ["TraccarClientSDK"],
            path: "core/Sources/TraccarClientAutoInit",
            publicHeadersPath: "include"
        ),
    ]
)
