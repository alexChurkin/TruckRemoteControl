import base64, sys, cairosvg
from PIL import Image
from mark import *
CLIENT = sys.argv[1]
shot = base64.b64encode(open(f"{CLIENT}/Screenshot.png", "rb").read()).decode()
W, H = 1024, 500
# phone: screenshot 1170x540 scaled to 560 wide
sw, sh = 500, 500 * 540 / 1170
bz = 14
defs = (BG_DEFS + f'<linearGradient id="fg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{BG_TOP}"/>'
        f'<stop offset="1" stop-color="#0D3F86"/></linearGradient>'
        f'<clipPath id="scr"><rect x="{bz}" y="{bz}" width="{sw}" height="{sh}" rx="10"/></clipPath>'
        '<filter id="sh" x="-20%" y="-20%" width="140%" height="160%"><feGaussianBlur stdDeviation="14"/></filter>')
tile = f'<g transform="translate(64 92) scale(0.8)"><rect x="4" y="4" width="100" height="100" rx="24" fill="#fff" fill-opacity="0.14"/>' \
       f'<g transform="translate(54 54) scale(1.22) translate(-54 -54)">{wheel_svg()}</g></g>'
phone_w, phone_h = sw + 2 * bz, sh + 2 * bz
phone = (f'<g transform="translate(752 262) rotate(-8) translate({-phone_w/2} {-phone_h/2})">'
         f'<rect x="6" y="22" width="{phone_w}" height="{phone_h}" rx="34" fill="#061c3d" fill-opacity="0.55" filter="url(#sh)"/>'
         f'<rect width="{phone_w}" height="{phone_h}" rx="34" fill="#111418"/>'
         f'<rect x="2" y="2" width="{phone_w-4}" height="{phone_h-4}" rx="32" fill="none" stroke="#3a4250" stroke-width="2"/>'
         f'<image x="{bz}" y="{bz}" width="{sw}" height="{sh}" clip-path="url(#scr)" href="data:image/png;base64,{shot}"/></g>')
# big faint wheel ring behind the phone
deco = (f'<g transform="translate(752 250) scale(6.6) translate(-54 -54)" opacity="0.10">'
        f'<path d="{RING}" fill="#fff" fill-rule="evenodd"/><path d="{SPOKES}" fill="#fff"/></g>')
font = "font-family:Inter;"
text = (f'<text x="64" y="232" style="{font}font-weight:800;font-size:56px;letter-spacing:-1px" fill="#fff">Truck Remote</text>'
        f'<text x="66" y="282" style="{font}font-weight:500;font-size:25px" fill="#fff" fill-opacity="0.9">Your phone is the steering wheel</text>'
        f'<text x="66" y="316" style="{font}font-weight:500;font-size:25px" fill="#fff" fill-opacity="0.9">and the pedals of your truck</text>'
        f'<rect x="64" y="350" width="226" height="40" rx="20" fill="#fff" fill-opacity="0.16"/>'
        f'<text x="177" y="377" text-anchor="middle" style="{font}font-weight:700;font-size:19px" fill="#fff">for truck simulators</text>')
s = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><defs>{defs}</defs>'
     f'<rect width="{W}" height="{H}" fill="url(#fg)"/>{deco}{phone}{tile}{text}</svg>')
out = f"{CLIENT}/media/store/feature_graphic_1024x500.png"
cairosvg.svg2png(bytestring=s.encode(), write_to=out)
Image.open(out).convert("RGB").save(out, optimize=True)
