Icon and Play Store graphics
============================
The steering wheel of the icon is made of a few primitives (a rim with a slightly flat bottom, a tonal disc, three
spokes and a pill-shaped hub), so the same geometry is written as SVG and as an Android vector; its geometry and colors
are in `icon.py`; the original icon is `media/ic_launcher-web.psd`. Requires Python 3 with Pillow and `cairosvg`
(or librsvg with PyGObject), and the Inter font (found with fontconfig) for the feature graphic.

Run from this folder with the paths of the repositories (the site is optional):

    python3 build.py ../.. ../../../TruckRemoteServer ../../../churkinapps.github.io   # Android icons, media/store/icon_512.png, server app_icon.ico and icon.svg, the card of the site
    python3 feature.py ../..                             # media/store/feature_graphic_1024x500.png and _ru.png

Outputs:
- `app/src/main/res/drawable/ic_launcher_{foreground,monochrome}.xml` and the background color: the adaptive icon
  with the themed (Material You) layer; `mipmap-*/ic_launcher*.png` are for Android 7.x.
- `media/store/icon_512.png` (Play Store icon, 512x512) and `media/store/feature_graphic_1024x500.png` (feature graphic;
  `feature_graphic_1024x500_ru.png` is the Russian one).
- Server: `src/TruckRemoteServer/app_icon.ico` (16-256 px) and `icon.svg`.
- Site: `img/TruckRemote_card.jpg` (the card of the app on the main page, 540x405).
