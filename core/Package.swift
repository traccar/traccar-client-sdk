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
            url: "https://github.com/traccar/traccar-client-sdk/releases/download/v0.0.1/TraccarClientSDK.xcframework.zip", checksum: "d6cff078eacfe63a1f6edeeadf6445c1b7f7a6b707231daf8bf3f4a78abdc12b"
        ),
        .target(
            name: "TraccarClientAutoInit",
            dependencies: ["TraccarClientSDK"],
            path: "Sources/TraccarClientAutoInit",
            publicHeadersPath: "include"
        ),
    ]
)
