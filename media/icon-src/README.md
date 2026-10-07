Icon and Play Store graphics
============================
Generated from the geometry in `mark.py` (a steering wheel with a signal over it, 108×108 adaptive-icon space).
Requires Python 3 with `cairosvg` and `Pillow`, and the Inter font for the feature graphic.

Run from this folder with the paths of both repositories:

    python3 build.py ../.. ../../../TruckRemoteServer   # Android icons, media/store/icon_512.png, server app_icon.ico and icon.svg
    python3 feature.py ../..                             # media/store/feature_graphic_1024x500.png

Outputs:
- `app/src/main/res/drawable/ic_launcher_{foreground,background,monochrome}.xml`: the adaptive icon with the themed
  (Material You) layer; `mipmap-*/ic_launcher*.png` are for Android 7.x.
- `media/store/icon_512.png` (Play Store icon, 512×512) and `media/store/feature_graphic_1024x500.png` (feature graphic).
- Server: `src/TruckRemoteServer/app_icon.ico` (16–256 px, the smallest sizes without the signal) and `icon.svg`.
