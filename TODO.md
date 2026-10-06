# TODO

- **Tune the vibration in the game.** The thresholds of the server (`HapticDetector`: suspension speeds of a rough
  road and of a bump, the damage and the acceleration of a collision) and the patterns of the app (`HapticPatterns`)
  are chosen by reason, not measured: drive with the telemetry logged and adjust them. Android 16 envelope effects
  (`Vibrator.areEnvelopeEffectsSupported`) could make the road vibration smoother on phones that have them.
- **Privacy policies on the site.** The pages of `ChurkinApps/churkinapps.github.io` (`en|ru/truckremote/privacy-policy.html`,
  new `en|ru/truckremote/third-party-software.html`, links from `index.html`) still describe the advertising identifier.
  They are rewritten in a separate commit "Truck Remote: new privacy policies and third-party software pages" (not pushed:
  no access to the organization): no advertising identifier and no location, AppMetrica and Yandex Mobile Ads without
  personalisation, what the app sends to the server on the local network, the list of components and licenses. Apply
  it, check the pages and publish them before the release; the app links to them from Settings → About
  (`privacy_policy_link`).
- **Release.** The app is still `versionName` 1.22 / `versionCode` 34: raise them (1.3 / 35) for the release. Publish
  Truck Remote Server 1.3 as a GitHub release (tag `1.3` or `v1.3`, the Release workflow attaches the exe): the
  servers check it for updates from 1.3 on, a 1.2 server has to be downloaded once by hand. The release notes of the
  app (`version_changes_text`) ask to update the server.
- **Test on devices.** What CI can't check: the vibration on a phone with haptic primitives (Pixel, Galaxy S), on an
  amplitude-only and on an on/off one; the dashboard mode on a tablet beside a driving phone; the server on Windows
  with ETS2 and ATS (the setup wizard on a clean PC, the player's keys, a new profile, the auto-update); the purchase
  restore and the ads.
