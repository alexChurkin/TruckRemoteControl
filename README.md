Truck Remote Control
====================
ETS2/ATS truck remote control app for Android based on Joystick and Keyboard emulation.
## How to use?
Launch [server app](https://github.com/alexChurkin/TruckRemoteServer) on your PC (follow instuction). Then: 
1) Run game
2) Run this Android app
3) Follow in-app instructions. 

That's all :) Enjoy it!

![Screenshot](https://github.com/alexChurkin/TruckRemoteControl/raw/master/Screenshot.png)


## Building

JDK 17+ and Android SDK are needed:

    ./gradlew ktlintCheck assembleDebug lintDebug testDebugUnitTest

Release build uses these values from `~/.gradle/gradle.properties` or environment variables
(in GitHub Actions they are repository secrets, the keystore is `TRUCKREMOTE_KEYSTORE_BASE64`):

- `TRUCKREMOTE_KEYSTORE_FILE`, `TRUCKREMOTE_KEYSTORE_PASSWORD`, `TRUCKREMOTE_KEY_ALIAS`, `TRUCKREMOTE_KEY_PASSWORD` —
  signing; without them the release APK is unsigned
- `TRUCKREMOTE_INTERSTITIAL_AD_ID` — Yandex Ads interstitial unit, the demo unit is used without it;
  `TRUCKREMOTE_APPMETRICA_API_KEY` — AppMetrica key, analytics is disabled without it.
  They can also be set in `app/ad.properties` (`interstitialAdId`, `appMetricaApiKey`); debug builds always use
  the demo ad unit and no analytics

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