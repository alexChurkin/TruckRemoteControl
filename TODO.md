# TODO

- **Force feedback.** Now the phone only vibrates for the duration the server sends, and the server takes it from
  the constant force effects of the game (`VJoyJoystick.OnForceFeedback`), ignoring the strength and the other
  effects vJoy reports (spring, damper, bumps, collisions). Support the force feedback properly: pass the strength
  and the kind of the effect to the phone and turn them into vibration of matching amplitude and pattern
  (`VibrationEffect`), with a strength setting in the app.
- **Update the guides.** The start guide of the app (`ui/guide`, `guide_*.xml`) and the README files of the app and
  the server describe the previous versions. Since then: analog pedals are the default and digital ones are an option,
  the gas is locked by a sideways swipe (from 5%), the press force is shown over the pedals, auto pause also works
  with the screen up and can be turned off, the quick actions panel replaces the middle controls while it is open,
  the steering check in the settings locks the screen rotation, the server adds the cruise control keys to the game
  bindings. The release notes (`version_changes_text`) need the same update.
- **Update the privacy policy on the site.** It must match what the app does now (ads, analytics, purchases,
  the QR code scanner of Google Play services, the local network).
