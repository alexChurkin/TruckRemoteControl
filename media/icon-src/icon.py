# The steering wheel of the original icon (media/ic_launcher-web.psd), traced to a vector and turned to the right.
# 108x108 is the space of an adaptive icon: its safe zone is the 66-unit circle at the centre.
from pathlib import Path

WHEEL_PATH = " ".join(Path(__file__).with_name("wheel.path").read_text().split())

BACKGROUND = "#185EA2"
INNER = "#3EB3FD"   # the fill inside the rim
HUB = "#2F8BFF"
WHITE = "#FFFFFF"

TURN = 20           # degrees clockwise: the wheel turns right
SIZE = 56           # the wheel's diameter, as on the original icon
LEFT = 54 - SIZE / 2
SCALE = SIZE / 10000  # the traced path is in 0.1 px of a 1000 px image, upside down
INNER_R = 0.39 * SIZE
HUB_R = 0.105 * SIZE

def wheel_svg(mono=None):
    """The turned wheel; mono: one color, without the inner fill (a themed icon)"""
    inner = "" if mono else f'<circle cx="54" cy="54" r="{INNER_R:.2f}" fill="{INNER}"/>'
    hub = "" if mono else f'<circle cx="54" cy="54" r="{HUB_R:.2f}" fill="{HUB}"/>'
    return (f'<g transform="rotate({TURN} 54 54)">{inner}'
            f'<path transform="translate({LEFT} {LEFT + SIZE}) scale({SCALE} {-SCALE})" d="{WHEEL_PATH}" '
            f'fill="{mono or WHITE}"/>{hub}</g>')

def svg(inner, size, view="0 0 108 108", width=None, height=None):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width or size}" height="{height or size}" '
            f'viewBox="{view}">{inner}</svg>')
