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
            url: "https://github.com/traccar/traccar-client-sdk/releases/download/v1.1.2/TraccarClientSDK.xcframework.zip", checksum: "69c4b5feb62e3fa39d852be4cc53ae40a0355774c9639987111b7fb60c62673f"
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
