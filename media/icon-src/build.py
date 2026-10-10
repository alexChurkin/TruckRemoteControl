# Generates the icons of the Android app and of the server, the Play Store icon and the card of the site, from icon.py.
# Usage: python3 build.py <TruckRemoteControl> <TruckRemoteServer> [<churkinapps.github.io>]
import io
import os
import sys

from PIL import Image

from icon import BACKGROUND, png_bytes, svg, wheel_svg, wheel_vector

CLIENT, SERVER = sys.argv[1], sys.argv[2]
SITE = sys.argv[3] if len(sys.argv) > 3 else None
RES = f"{CLIENT}/app/src/main/res"
STORE = f"{CLIENT}/media/store"


def png(text):
    return Image.open(io.BytesIO(png_bytes(text))).convert("RGBA")


# ---------- Android: the adaptive icon as vectors ----------
def vector(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            f'{body}</vector>\n')


open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(vector(wheel_vector()))
# A themed icon is one color: the rim, the spokes and the hub
open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w").write(vector(wheel_vector(mono="#FF000000")))
open(f"{RES}/values/ic_launcher_background.xml", "w").write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
    f'    <color name="ic_launcher_background">{BACKGROUND}</color>\n</resources>\n')
ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')
for name in ("ic_launcher", "ic_launcher_round"):
    open(f"{RES}/mipmap-anydpi-v26/{name}.xml", "w").write(ADAPTIVE)
if os.path.exists(f"{RES}/drawable/ic_launcher_background.xml"):
    os.remove(f"{RES}/drawable/ic_launcher_background.xml")

# ---------- Android 7.x: PNGs as the original ones (a rounded square, a circle) ----------
# The 72-unit visible part of the adaptive icon, in the 48dp grid with its 4dp margin
for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    for name, shape in (("ic_launcher", '<rect x="18" y="18" width="72" height="72" rx="11"/>'),
                        ("ic_launcher_round", '<circle cx="54" cy="54" r="36"/>')):
        inner = (f'<clipPath id="c">{shape}</clipPath><g clip-path="url(#c)">'
                 f'<rect width="108" height="108" fill="{BACKGROUND}"/>{wheel_svg()}</g>')
        margin = px // 12
        icon = png(svg(inner, px - 2 * margin, view="18 18 72 72"))
        out = Image.new("RGBA", (px, px))
        out.paste(icon, (margin, margin), icon)
        out.save(f"{RES}/mipmap-{dpi}/{name}.png", optimize=True)

# ---------- Play Store: 512x512, a full square (Google Play rounds it itself) ----------
os.makedirs(STORE, exist_ok=True)
store = svg(f'<rect x="18" y="18" width="72" height="72" fill="{BACKGROUND}"/>{wheel_svg()}', 512, view="18 18 72 72")
open(f"{STORE}/icon.svg", "w").write(store)
png(store).convert("RGB").save(f"{STORE}/icon_512.png", optimize=True)

# ---------- Windows: the same rounded square, 16-256 px ----------
tile = f'<rect x="20" y="20" width="68" height="68" rx="13" fill="{BACKGROUND}"/>{wheel_svg()}'
open(f"{SERVER}/icon.svg", "w").write(svg(tile, 256, view="18 18 72 72"))
sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
images = [png(svg(tile, s, view="18 18 72 72")) for s in sizes]
images[-1].save(f"{SERVER}/src/TruckRemoteServer/app_icon.ico", format="ICO",
                sizes=[(s, s) for s in sizes], append_images=images[:-1])

# ---------- The site: the card of the app, 540x405, the icon on white as the cards of the other apps ----------
if SITE:
    card = ('<rect width="540" height="405" fill="#FFFFFF"/>'
            f'<g transform="translate(111 44) scale({318 / 72}) translate(-18 -18)">'
            '<clipPath id="c"><rect x="18" y="18" width="72" height="72" rx="11"/></clipPath><g clip-path="url(#c)">'
            f'<rect width="108" height="108" fill="{BACKGROUND}"/>{wheel_svg()}</g></g>')
    png(svg(card, 540, view="0 0 540 405", height=405)).convert("RGB").save(
        f"{SITE}/img/TruckRemote_card.jpg", quality=92, progressive=True, optimize=True)
