# TODO

- **Tune the vibration in the game.** The thresholds of the server (`HapticDetector`: suspension speeds of a rough
  road and of a bump, the damage and the acceleration of a collision) and the patterns of the app (`HapticPatterns`)
  are chosen by reason, not measured: drive with the telemetry logged and adjust them. Android 16 envelope effects
  (`Vibrator.areEnvelopeEffectsSupported`) could make the road vibration smoother on phones that have them.
- **Release.** The app is `versionName` 1.4.1 / `versionCode` 38, the server 1.4.1 (protocol revision 8: the app asks
  to update an older server once). Publish Truck Remote Server 1.4 as a GitHub release first (tag `1.4` or `v1.4`,
  the Release workflow attaches the exe), then the app: its release notes (`version_changes_text`) ask to update the
  server.
- **Test on devices.** What CI can't check: the vibration on a phone with haptic primitives (Pixel, Galaxy S), on an
  amplitude-only and on an on/off one; the dashboard mode on a tablet beside a driving phone; the server on Windows
  with ETS2 and ATS (the setup wizard on a clean PC, the player's keys, a new profile, the auto-update); the purchase
  restore and the ads.
- **Baseline Profile.** The Compose screens (settings, guide, dashboard) are compiled on the phone only after they
  are used: a Baseline Profile (a `:baselineprofile` module with the Macrobenchmark plugin and a generator that opens
  the screens, `androidx.profileinstaller` in the app) makes the first start and the first frames of a fresh install
  faster. Needs a device or an emulator to generate it.
