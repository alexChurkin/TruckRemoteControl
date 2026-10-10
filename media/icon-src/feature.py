# The Play Store feature graphic (1024x500) in the colors of the icon (dark, as the app), in English and in Russian.
# Usage: python3 feature.py <TruckRemoteControl>
import base64
import io
import subprocess
import sys

from PIL import Image, ImageFont

from icon import BACKGROUND, DISC, HUB, WHEEL, png_bytes, wheel_svg

CLIENT = sys.argv[1]
TEXTS = {
    "": ("Your phone is the steering wheel", "and the pedals of your truck", "for truck simulators"),
    "_ru": ("Ваш телефон — руль и педали", "вашего грузовика", "для симуляторов грузовиков"),
}
W, H = 1024, 500
shot = base64.b64encode(open(f"{CLIENT}/Screenshot.png", "rb").read()).decode()

# The phone: the screenshot (1170x540) in a dark frame, tilted to the right as when steering
SW, SH, BEZEL = 500, 500 * 540 / 1170, 14
PW, PH = SW + 2 * BEZEL, SH + 2 * BEZEL
phone = (f'<g transform="translate(760 262) rotate(8) translate({-PW / 2} {-PH / 2})">'
         f'<rect x="-4" y="22" width="{PW}" height="{PH}" rx="34" fill="#000000" fill-opacity="0.7" filter="url(#shadow)"/>'
         f'<rect width="{PW}" height="{PH}" rx="34" fill="#111418"/>'
         f'<rect x="2" y="2" width="{PW - 4}" height="{PH - 4}" rx="32" fill="none" stroke="#3a4250" stroke-width="2"/>'
         f'<image x="{BEZEL}" y="{BEZEL}" width="{SW}" height="{SH}" clip-path="url(#screen)" '
         f'href="data:image/png;base64,{shot}"/></g>')
# A big wheel behind the phone, barely seen
backdrop = (f'<g transform="translate(760 250) scale(7.4) translate(-54 -54)" opacity="0.08">'
            f'{wheel_svg(mono=WHEEL)}</g>')
# The icon itself beside the title
icon = (f'<g transform="translate(64 84)">'
        f'<rect width="96" height="96" rx="22" fill="{BACKGROUND}"/>'
        f'<rect x="0.75" y="0.75" width="94.5" height="94.5" rx="21.25" fill="none" stroke="{WHEEL}" '
        f'stroke-opacity="0.22" stroke-width="1.5"/>'
        f'<g transform="scale({96 / 72}) translate(-18 -18)">{wheel_svg()}</g></g>')
font = "font-family:Inter;"
# Inter, wherever fontconfig finds it
bold = ImageFont.truetype(subprocess.run(["fc-match", "-f", "%{file}", "Inter:weight=bold"], capture_output=True,
                                         text=True, check=True).stdout, 19)


def page(line1, line2, badge):
    # The badge fits its text
    badge_width = round(bold.getlength(badge)) + 46
    text = (f'<text x="64" y="244" style="{font}font-weight:800;font-size:56px;letter-spacing:-1px" fill="#fff">Truck Remote</text>'
            f'<text x="66" y="292" style="{font}font-weight:500;font-size:25px" fill="#fff" fill-opacity="0.92">{line1}</text>'
            f'<text x="66" y="326" style="{font}font-weight:500;font-size:25px" fill="#fff" fill-opacity="0.92">{line2}</text>'
            f'<rect x="64" y="358" width="{badge_width}" height="40" rx="20" fill="{HUB}" fill-opacity="0.16"/>'
            f'<text x="{64 + badge_width / 2}" y="385" text-anchor="middle" style="{font}font-weight:700;font-size:19px" fill="{HUB}">{badge}</text>')
    defs = ('<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="{DISC}"/><stop offset="1" stop-color="{BACKGROUND}"/></linearGradient>'
            f'<clipPath id="screen"><rect x="{BEZEL}" y="{BEZEL}" width="{SW}" height="{SH}" rx="10"/></clipPath>'
            '<filter id="shadow" x="-20%" y="-20%" width="140%" height="160%"><feGaussianBlur stdDeviation="14"/></filter>')
    graphic = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><defs>{defs}</defs>'
            f'<rect width="{W}" height="{H}" fill="url(#bg)"/>{backdrop}{phone}{icon}{text}</svg>')
    return graphic


for suffix, (line1, line2, badge) in TEXTS.items():
    out = f"{CLIENT}/media/store/feature_graphic_1024x500{suffix}.png"
    Image.open(io.BytesIO(png_bytes(page(line1, line2, badge)))).convert("RGB").save(out, optimize=True)
