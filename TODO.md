# TODO

- **Tune the vibration in the game.** The thresholds of the server (`HapticDetector`: suspension speeds of a rough
  road and of a bump, the damage and the acceleration of a collision) and the patterns of the app (`HapticPatterns`)
  are chosen by reason, not measured: drive with the telemetry logged and adjust them. Android 16 envelope effects
  (`Vibrator.areEnvelopeEffectsSupported`) could make the road vibration smoother on phones that have them.
- **Update the guides.** The start guide of the app (`ui/guide`, `guide_*.xml`) and the README files of the app and
  the server describe the previous versions. Since then: analog pedals are the default and digital ones are an option,
  the gas is locked by a sideways swipe (from 5%), the press force is shown over the pedals, auto pause also works
  with the screen up and can be turned off, the quick actions panel replaces the middle controls while it is open,
  the steering check in the settings locks the screen rotation, the server adds the cruise control keys to the game
  bindings. The release notes (`version_changes_text`) need the same update.
