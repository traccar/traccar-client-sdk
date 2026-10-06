# react-native-traccar-client-sdk

React Native module for background location tracking. Wraps the [Traccar Client SDK](https://github.com/traccar/traccar-client-sdk) for Android and iOS, sending position updates over a simple HTTP protocol that works with [Traccar](https://www.traccar.org) and any compatible server.

Requires Android API 24+ and iOS 15+. Android hosts must compile with SDK 37 or newer and use Kotlin 2.4.20 or newer.

For package development, use Node.js 24 and run `npm ci` followed by `npm run typecheck`. The prepare script builds JavaScript and TypeScript declarations for publishing.

```sh
npm install react-native-traccar-client-sdk
```

## Documentation

Full documentation - installation, usage, configuration, and the required Android/iOS setup - is on the Traccar website:

**https://www.traccar.org/traccar-client-sdk-react-native/**

## License

Apache License 2.0.
