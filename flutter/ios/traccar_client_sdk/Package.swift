// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "traccar_client_sdk",
    platforms: [
        .iOS("15.0")
    ],
    products: [
        .library(name: "traccar-client-sdk", targets: ["traccar_client_sdk"])
    ],
    dependencies: [
        .package(url: "https://github.com/traccar/traccar-client-sdk.git", exact: "1.0.6")
    ],
    targets: [
        .target(
            name: "traccar_client_sdk",
            dependencies: [
                .product(name: "TraccarClientSDK", package: "traccar-client-sdk")
            ]
        )
    ]
)
