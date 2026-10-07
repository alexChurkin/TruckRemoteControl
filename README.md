Truck Remote Control
====================
An Android app that turns a phone into the steering wheel, the pedals and the dashboard of a truck in
Euro Truck Simulator 2 and American Truck Simulator. It works with
[Truck Remote Server](https://github.com/alexChurkin/TruckRemoteServer) on the PC with the game, over the local
network. Android 7.0 or newer is needed.

![The controller screen](https://github.com/alexChurkin/TruckRemoteControl/raw/master/Screenshot.png)

## What it does

- **Steering by tilting the phone**, smooth and without a lag (gyroscope and accelerometer together); sensitivity,
  dead zone and curve are set in the app with a live preview.
- **Analog pedals**: drag a pedal up to press it harder, it springs back when released; swipe left on the gas
  to keep its level, double-tap the gas for cruise control.
- **Instruments of the truck** at the top of the screen: speed, rpm, speed limit, gear, cruise control, warnings,
  the job and the distance left, in the speed units of the game. Over the speed limit the speed turns amber and then
  red (can be turned off in the settings).
- **Quick actions**: six pages of buttons (engine, lights, retarder, cameras, map, radio and more) that can be
  moved and replaced; "Activate" is held to refuel.
- **Buttons without a key in the game** are marked: with Truck Remote Server 1.4+ a button whose action has no key
  in the player's game profile is dimmed with an amber dot, and its press tells to bind a key in
  the game controls.
- **Shortcuts** of the launcher icon (a long press): settings, the dashboard mode and the guide.
- **Vibration** with the truck: bumps, rough roads, collisions, the engine, the blinkers, gear shifts.
- **Dashboard mode**: a tablet or a second phone shows the instruments beside the phone that drives; its night mode
  dims the screen.
- English, Russian, Belarusian and Ukrainian.

![The quick actions panel](https://github.com/alexChurkin/TruckRemoteControl/raw/master/Screenshot_actions.png)

![The dashboard mode](https://github.com/alexChurkin/TruckRemoteControl/raw/master/Screenshot_dashboard.png)

## How to use?

1) Download and run [Truck Remote Server](https://github.com/alexChurkin/TruckRemoteServer/releases/latest) on the PC
   with the game: its setup wizard installs and sets up everything the phone needs
2) Run the game
3) Connect the phone to the same network as the PC and run this app: it finds the server by itself
   (or scan the QR code of the server window in Settings → Connection)
4) Tilt the phone to steer and press the pedals. "Straighten the wheel" in the menu (the gear icon) sets
   the straight position; the steering is set up in Settings → Steering
5) The button at the bottom center opens the quick actions; long press a button to edit the panel: drag
   the buttons to move them, tap a place to put another action there
6) For a tablet or a second phone choose **Dashboard** when the app asks how the device will be used at its
   first start (or later in Settings → Mode). It only shows, so it works beside the phone that drives

The guide in the app menu describes the rest.

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
  They can also be set in `app/ad.properties` (`interstitialAdId`, `appMetricaApiKey`, see `app/ad.properties.example`);
  debug builds always use
  the demo ad unit and no analytics

## Architecture

The app follows the [Android app architecture guide](https://developer.android.com/topic/architecture):

- `data` — data layer: `controller` (UDP client of the server, `ControllerRepository` with the connection
  and truck state as flows), `settings`, `sensor` (tilt sensor as a flow), `device` (vibration: `Haptics` plays the events of the server as
  `HapticPatterns` — composition primitives where the phone has them, amplitude waveforms or plain on/off otherwise —
  and Wi-Fi),
  `billing`, `ads`, `analytics`, `region` (`DataRegion`: the country of the device, from the mobile network,
  the SIM card or the system language, decides where ads are shown and where statistics are sent),
  `viewer` (`ViewerClient`: the truck state for the dashboard mode, which doesn't control anything).
- `domain` — logic without Android dependencies: `SteeringProcessor` (the steering angle from the fused
  gyroscope and accelerometer is smoothed by the adaptive `OneEuroFilter`, then `SteeringCurve` applies the settings),
  `PedalHandler`.
- `ui` — screens: a view model holds the UI state (`StateFlow`) and one-off effects, the activity
  only renders the state and passes user actions (unidirectional data flow). The controller screen (`ui/main`)
  uses views (landscape, touches of several pedals at once), a dialog fragment for the menu and a Compose pager
  for the quick actions panel (`ActionsPanel`, buttons by pages in `ActionButton.kt`), the dashboard mode
  (`ui/dashboard`), the settings screen (`ui/settings`) and the guide (`ui/guide`) are Jetpack Compose
  with Material 3.
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