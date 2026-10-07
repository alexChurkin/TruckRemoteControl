# Generates the icons of the Android app and of the server, and the Play Store icon, from icon.py.
# Usage: python3 build.py <TruckRemoteControl> <TruckRemoteServer>
import io
import os
import sys

import cairosvg
from PIL import Image

from icon import BACKGROUND, HUB, HUB_R, INNER, INNER_R, LEFT, SCALE, SIZE, TURN, WHEEL_PATH, WHITE, svg, wheel_svg

CLIENT, SERVER = sys.argv[1], sys.argv[2]
RES = f"{CLIENT}/app/src/main/res"
STORE = f"{CLIENT}/media/store"


def png(text):
    return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=text.encode()))).convert("RGBA")


# ---------- Android: the adaptive icon as vectors ----------
def vector(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            f'    <group\n        android:pivotX="54"\n        android:pivotY="54"\n        android:rotation="{TURN}">\n'
            f'{body}    </group>\n</vector>\n')


def circle(r, color):
    return (f'        <path\n            android:fillColor="{color}"\n'
            f'            android:pathData="M54,{54 - r:.2f}a{r:.2f},{r:.2f} 0 1,1 0,{2 * r:.2f}'
            f'a{r:.2f},{r:.2f} 0 1,1 0,{-2 * r:.2f}z" />\n')


def wheel(color):
    return (f'        <group\n            android:translateX="{LEFT}"\n            android:translateY="{LEFT + SIZE}"\n'
            f'            android:scaleX="{SCALE}"\n            android:scaleY="{-SCALE}">\n'
            f'            <path\n                android:fillColor="{color}"\n'
            f'                android:pathData="{WHEEL_PATH}" />\n        </group>\n')


open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(
    vector(circle(INNER_R, INNER) + wheel(WHITE) + circle(HUB_R, HUB)))
# A themed icon is one color: the rim and the spokes
open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w").write(vector(wheel("#FF000000")))
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
tile = (f'<rect x="20" y="20" width="68" height="68" rx="13" fill="{BACKGROUND}"/>'
        f'<g transform="translate(54 54) scale(1.08) translate(-54 -54)">{wheel_svg()}</g>')
open(f"{SERVER}/icon.svg", "w").write(svg(tile, 256, view="18 18 72 72"))
sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
images = [png(svg(tile, s, view="18 18 72 72")) for s in sizes]
images[-1].save(f"{SERVER}/src/TruckRemoteServer/app_icon.ico", format="ICO",
                sizes=[(s, s) for s in sizes], append_images=images[:-1])
