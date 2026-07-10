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
            url: "https://github.com/traccar/traccar-client-sdk/releases/download/v1.0.3/TraccarClientSDK.xcframework.zip", checksum: "c83a81cba4f9bcb23021f1eef67113f3c0e50b53723ecadb71e48e9723fe5dc9"
        ),
        .target(
            name: "TraccarClientAutoInit",
            dependencies: ["TraccarClientSDK"],
            path: "core/Sources/TraccarClientAutoInit",
            publicHeadersPath: "include"
        ),
    ]
)
