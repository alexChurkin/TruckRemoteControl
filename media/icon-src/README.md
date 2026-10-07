Icon and Play Store graphics
============================
The steering wheel of the original icon (`media/ic_launcher-web.psd`), traced to a vector (`wheel.path`, potrace)
and turned to the right; its geometry and colors are in `icon.py`. Requires Python 3 with `cairosvg` and `Pillow`,
and the Inter font for the feature graphic.

Run from this folder with the paths of both repositories:

    python3 build.py ../.. ../../../TruckRemoteServer   # Android icons, media/store/icon_512.png, server app_icon.ico and icon.svg
    python3 feature.py ../..                             # media/store/feature_graphic_1024x500.png and _ru.png

Outputs:
- `app/src/main/res/drawable/ic_launcher_{foreground,monochrome}.xml` and the background color: the adaptive icon
  with the themed (Material You) layer; `mipmap-*/ic_launcher*.png` are for Android 7.x.
- `media/store/icon_512.png` (Play Store icon, 512x512) and `media/store/feature_graphic_1024x500.png` (feature graphic;
  `feature_graphic_1024x500_ru.png` is the Russian one).
- Server: `src/TruckRemoteServer/app_icon.ico` (16-256 px) and `icon.svg`.
