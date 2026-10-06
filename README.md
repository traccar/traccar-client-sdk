# Traccar Client SDK

A Kotlin Multiplatform background location tracking SDK for [Traccar](https://www.traccar.org) - and any other server that accepts the same simple HTTP protocol. Runs on Android and iOS, with Flutter and React Native wrappers.

- **Native** - Maven Central (`org.traccar:traccar-client-sdk`), iOS via Swift Package Manager
- **Flutter** - pub.dev (`traccar_client_sdk`)
- **React Native** - npm (`react-native-traccar-client-sdk`)

## Documentation

Full documentation - installation, configuration, API, and architecture - is on the Traccar website:

- **Overview:** https://www.traccar.org/traccar-client-sdk/
- **Flutter:** https://www.traccar.org/traccar-client-sdk-flutter/
- **React Native:** https://www.traccar.org/traccar-client-sdk-react-native/

## Development

Use JDK 17 or newer supported by Gradle, Android SDK 37, and Xcode 26.6 for native builds. The Gradle wrapper pins the build tool version. Flutter development requires Flutter 3.47+ and Dart 3.13+; the React Native package uses Node.js 24.

```sh
./gradlew :samples:android:assembleDebug :core:assembleTraccarClientSDKReleaseXCFramework
(cd flutter && flutter pub get && flutter analyze)
(cd react-native && npm ci && npm run typecheck)
```

Regenerate the iOS sample project with `xcodegen generate --spec samples/ios/project.yml` after changing its project settings.

## License

Apache License 2.0. See [LICENSE](LICENSE).
