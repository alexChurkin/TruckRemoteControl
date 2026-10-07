import os, sys, io, cairosvg
from PIL import Image
from mark import *

CLIENT, SERVER = sys.argv[1], sys.argv[2]
RES = f"{CLIENT}/app/src/main/res"
MEDIA = f"{CLIENT}/media/store"
os.makedirs(MEDIA, exist_ok=True)

def png(svgtext, path=None):
    data = cairosvg.svg2png(bytestring=svgtext.encode())
    if path: open(path, "wb").write(data)
    return data

# ---------- Android adaptive icon (vectors) ----------
VEC = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
       '    android:width="108dp"\n    android:height="108dp"\n'
       '    android:viewportWidth="108"\n    android:viewportHeight="108">\n{body}</vector>\n')

def vpath(d, color, evenodd=False):
    fr = '\n        android:fillType="evenOdd"' if evenodd else ''
    return f'    <path\n        android:fillColor="{color}"{fr}\n        android:pathData="{d}" />\n'

def vstroke(d, color):
    return (f'    <path\n        android:strokeColor="{color}"\n        android:strokeWidth="{ARC_W}"\n'
            f'        android:strokeLineCap="round"\n        android:pathData="{d}" />\n')

def fg(body, accent):
    return (vpath(RING, body, True) + vpath(SPOKES, body) + vpath(HUB, accent)
            + "".join(vstroke(a, accent) for a in ARCS))

os.makedirs(f"{RES}/drawable", exist_ok=True)
open(f"{RES}/drawable/ic_launcher_foreground.xml", "w").write(VEC.format(ns="", body=fg(WHITE, AMBER)))
open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w").write(VEC.format(ns="", body=fg("#FF000000", "#FF000000")))
bg = ('    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
      '            <gradient\n                android:type="linear"\n'
      '                android:startX="54" android:startY="0"\n                android:endX="54" android:endY="108"\n'
      f'                android:startColor="{BG_TOP}"\n                android:endColor="{BG_BOTTOM}" />\n'
      '        </aapt:attr>\n    </path>\n')
open(f"{RES}/drawable/ic_launcher_background.xml", "w").write(
    VEC.format(ns='\n    xmlns:aapt="http://schemas.android.com/aapt"', body=bg))
ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')
for n in ("ic_launcher", "ic_launcher_round"):
    open(f"{RES}/mipmap-anydpi-v26/{n}.xml", "w").write(ADAPTIVE)

# ---------- legacy PNGs (Android 7.x) ----------
# the 108 canvas cropped to its 72-unit visible part, like a launcher shows it
BGRECT = '<rect width="108" height="108" fill="url(#bg)"/>'
for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    d = f"{RES}/mipmap-{dpi}"
    m = 4 * px / 48  # transparent margin, as the legacy grid has
    inner = px - 2 * m
    def tile(shape):
        s = svg(f'<clipPath id="c">{shape}</clipPath><g clip-path="url(#c)">{BGRECT}{wheel_svg()}</g>',
                inner, vb="18 18 72 72")
        im = Image.open(io.BytesIO(png(s))).convert("RGBA")
        out = Image.new("RGBA", (px, px)); out.paste(im, (round(m), round(m)), im)
        return out
    tile('<rect x="18" y="18" width="72" height="72" rx="16"/>').save(f"{d}/ic_launcher.png", optimize=True)
    tile('<circle cx="54" cy="54" r="36"/>').save(f"{d}/ic_launcher_round.png", optimize=True)
    if os.path.exists(f"{d}/ic_launcher_foreground.png"):
        os.remove(f"{d}/ic_launcher_foreground.png")

# ---------- Play Store icon 512 (full square, Play applies the mask) ----------
full = svg(BGRECT + wheel_svg(), 512)
open(f"{MEDIA}/icon.svg", "w").write(full)
png(full, f"{MEDIA}/icon_512.png")
Image.open(f"{MEDIA}/icon_512.png").convert("RGB").save(f"{MEDIA}/icon_512.png", optimize=True)

# ---------- Windows icon: the mark on a rounded tile ----------
def win(px):
    small = px <= 24
    inner = (f'<rect x="4" y="4" width="100" height="100" rx="22" fill="url(#bg)"/>'
             f'<g transform="translate(54 54) scale({1.32 if small else 1.22}) translate(-54 -54)">'
             + (wheel_svg().split("\n<path d=\"M")[0] + f'\n<path d="{SPOKES}" fill="#fff"/><path d="{HUB}" fill="{AMBER}"/>'
                if small else wheel_svg()) + '</g>')
    return Image.open(io.BytesIO(png(svg(inner, px)))).convert("RGBA")
open(f"{SERVER}/icon.svg", "w").write(svg('<rect x="4" y="4" width="100" height="100" rx="22" fill="url(#bg)"/>'
    '<g transform="translate(54 54) scale(1.22) translate(-54 -54)">' + wheel_svg() + '</g>', 256))
sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
imgs = [win(s) for s in sizes]
imgs[-1].save(f"{SERVER}/src/TruckRemoteServer/app_icon.ico", format="ICO",
              sizes=[(s, s) for s in sizes], append_images=imgs[:-1])
