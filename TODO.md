# TODO

- **Tune the vibration in the game.** The thresholds of the server (`HapticDetector`: suspension speeds of a rough
  road and of a bump, the damage and the acceleration of a collision) and the patterns of the app (`HapticPatterns`)
  are chosen by reason, not measured: drive with the telemetry logged and adjust them. Android 16 envelope effects
  (`Vibrator.areEnvelopeEffectsSupported`) could make the road vibration smoother on phones that have them.
