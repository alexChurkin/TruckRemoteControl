Truck Remote Control
====================
ETS2/ATS truck remote control app for Android based on Joystick and Keyboard emulation.
Android 7.0 or newer is needed.

## How to use?
Launch the [server app](https://github.com/alexChurkin/TruckRemoteServer) on your PC: its setup wizard installs
and sets up everything the phone needs. Then:
1) Run the game
2) Connect the phone to the same network as the PC and run this app: it finds the server by itself
   (or scan the QR code of the server window in Settings → Connection)
3) Tilt the phone to steer and press the pedals: drag a pedal up to press it harder, swipe left on the gas
   to keep its level, double-tap the gas for cruise control. The steering is set up in Settings → Steering
   (sensitivity, dead zone, curve) with a live preview
4) The button at the bottom center opens the quick actions (engine, lights, retarder, cameras, map and more,
   several pages); long press a button to put another action in its place
5) The phone vibrates with the truck (bumps, rough roads, collisions, the engine, the blinkers, gear shifts) with
   the telemetry plugin the server installs; the strength and the kinds of vibration are set in Settings → Screen and buttons
6) A tablet or a second phone can show the instruments of the truck: choose **Dashboard** when the app asks how
   the device will be used at its first start (or later in Settings → Mode). It only shows, so it works beside
   the phone that drives

The guide in the app menu describes the rest.

That's all :) Enjoy it!

![Screenshot](https://github.com/alexChurkin/TruckRemoteControl/raw/master/Screenshot.png)


## Building

JDK 17+ and Android SDK (API 37) are needed:

    ./gradlew ktlintCheck detekt assembleDebug lintDebug testDebugUnitTest

Code checks (all of them run in CI and fail the build):

- [ktlint](https://pinterest.github.io/ktlint/) — code style (Android Studio style, `.editorconfig`)
  with [Compose rules](https://mrmans0n.github.io/compose-rules/); `./gradlew ktlintFormat` fixes it
- [detekt](https://detekt.dev) — code smells, complexity and potential bugs, the default rules
  with a few Compose/Android exceptions in `config/detekt/detekt.yml`
- Android Lint — `app/lint.xml`
- Kotlin compiler warnings are errors

Release build uses these values from `~/.gradle/gradle.properties` or environment variables
(in GitHub Actions they are repository secrets, the keystore is `TRUCKREMOTE_KEYSTORE_BASE64`):

- `TRUCKREMOTE_KEYSTORE_FILE`, `TRUCKREMOTE_KEYSTORE_PASSWORD`, `TRUCKREMOTE_KEY_ALIAS`, `TRUCKREMOTE_KEY_PASSWORD` —
  signing; without them the release APK is unsigned
- `TRUCKREMOTE_INTERSTITIAL_AD_ID` — Yandex Ads interstitial unit, the demo unit is used without it;
  `TRUCKREMOTE_APPMETRICA_API_KEY` — AppMetrica key, analytics is disabled without it.
  They can also be set in `app/ad.properties` (`interstitialAdId`, `appMetricaApiKey`); debug builds always use
  the demo ad unit and no analytics

## Architecture

The app follows the [Android app architecture guide](https://developer.android.com/topic/architecture):

- `data` — data layer: `controller` (UDP client of the server, `ControllerRepository` with the connection
  and truck state as flows), `settings`, `sensor` (tilt sensor as a flow), `device` (vibration: `Haptics` plays the events of the server as
  `HapticPatterns` — composition primitives where the phone has them, amplitude waveforms or plain on/off otherwise —
  and Wi-Fi),
  `billing`, `ads`, `analytics`, `viewer` (`ViewerClient`: the truck state for the dashboard mode, which
  doesn't control anything).
- `domain` — logic without Android dependencies: `SteeringProcessor` (the steering angle from the fused
  gyroscope and accelerometer is smoothed by the adaptive `OneEuroFilter`, then `SteeringCurve` applies the settings),
  `PedalHandler`.
- `ui` — screens: a view model holds the UI state (`StateFlow`) and one-off effects, the activity
  only renders the state and passes user actions (unidirectional data flow). The controller screen (`ui/main`)
  uses views (landscape, touches of several pedals at once), a dialog fragment for the menu and a Compose pager
  for the quick actions panel (`ActionsPanel`, buttons by pages in `ActionButton.kt`), the dashboard mode
  (`ui/dashboard`) is Compose,
  the settings screen (`ui/settings`) is Jetpack Compose with Material 3, the guide (`ui/guide`) shows
  its pages as fragments.
- `data/controller/BinaryProtocol` — the compact binary protocol of the server 1.3+ (the text one is kept for older
  servers); actions are sent by fixed codes, so buttons can be moved freely.
- `di/AppContainer` — manual dependency injection: the app-wide objects are created once,
  view models get them through their factories (fakes are used in unit tests).

## Third-party software

Licenses of all dependencies are collected at build time (AboutLibraries plugin) and shown in
Settings → Third-party software and licenses. Icons and other assets that aren't Gradle dependencies
must be described in `app/config/libraries` (and `app/config/licenses` for a license unknown to SPDX).
New icons must have a free license (e.g. MIT, Apache 2.0).

## License

    Copyright 2021 Alex Churkin.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.