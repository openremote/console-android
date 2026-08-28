# OpenRemote Android Console

[![CI/CD](https://github.com/openremote/console-android/workflows/CI/CD/badge.svg)](https://github.com/openremote/console-android/actions?query=workflow%3ACI%2FCD+branch%3Amain)
[![Maven Central](https://img.shields.io/maven-central/v/io.openremote/orlib.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.openremote/orlib)
[![Open Source? Yes!](https://badgen.net/badge/Open%20Source%20%3F/Yes%21/blue?icon=github)](https://github.com/Naereen/badges/)

Android console application and library for [OpenRemote](https://openremote.io/).

OpenRemote applications are web applications which can run in a browser or inside a native console. This project provides the Android console implementation, based on an Android WebView with native integrations exposed to the web application.

Supported native functionality includes:

- Firebase Cloud Messaging push notifications
- geofencing and location services
- secure local storage
- QR code scanning
- ESP32 provisioning over Bluetooth Low Energy (BLE)

## Android app

The generic OpenRemote Android app can connect to an OpenRemote Manager deployment and is available from [Google Play](https://play.google.com/store/apps/details?id=io.openremote.app).

See [Using OpenRemote on mobile](https://docs.openremote.io/docs/user-guide/manager-ui/on-mobile/) for setup and usage information.

## Repository structure

### `ORLib`

Reusable Android library containing the OpenRemote WebView integration and native console providers.

Applications can use `ORLib` as the basis for a customised OpenRemote Android app.

Published to Maven Central as:

[`io.openremote:orlib`](https://central.sonatype.com/artifact/io.openremote/orlib)

### `GenericApp`

Generic Android application built on top of `ORLib`.

This is the basis of the official OpenRemote Android app and can also be used as an example implementation.

Available from:

- [Google Play](https://play.google.com/store/apps/details?id=io.openremote.app)
- [Maven Central (`io.openremote:app`)](https://central.sonatype.com/artifact/io.openremote/app)

### `protobuf`

Protocol Buffer definitions and generated classes used by `ORLib`, mainly for ESP32 provisioning.

Published to Maven Central as:

[`io.openremote:orlib-protobuf`](https://central.sonatype.com/artifact/io.openremote/orlib-protobuf)

## iOS

OpenRemote also provides native console support for iOS:

- [`openremote/console-ios-app`](https://github.com/openremote/console-ios-app): generic OpenRemote iOS application
- [`openremote/console-ios-lib`](https://github.com/openremote/console-ios-lib): reusable OpenRemote iOS library
- [OpenRemote App on the Apple App Store](https://apps.apple.com/app/openremote-app/id1526315885)

## Documentation

Relevant OpenRemote documentation:

- [Using OpenRemote on mobile](https://docs.openremote.io/docs/user-guide/manager-ui/on-mobile/)
- [Configure mobile app behaviour](https://docs.openremote.io/docs/tutorials/configure-mobile-app-behaviour/)
- [Working on the mobile consoles](https://docs.openremote.io/docs/developer-guide/working-on-the-mobile-consoles/)
- [Apps and consoles architecture](https://docs.openremote.io/docs/architecture/apps-and-consoles/)
- [ESP32 devices](https://docs.openremote.io/docs/architecture/esp32-device/)
- [Release management](https://docs.openremote.io/docs/user-guide/deploying/release-management/)

See [docs.openremote.io](https://docs.openremote.io/) for the complete documentation.

## Development

The project can be opened in Android Studio.

The build uses JDK 17 and the Gradle wrapper included in this repository.

Build all modules with:

```shell
./gradlew assemble
```

### Code formatting

Formatting is managed using [Spotless](https://github.com/diffplug/spotless).

Apply formatting:

```shell
./gradlew spotlessApply
```

Check formatting:

```shell
./gradlew spotlessCheck
```

`spotlessCheck` is also run by CI.

## License

OpenRemote is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE.txt).
