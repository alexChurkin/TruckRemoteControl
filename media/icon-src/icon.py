# The steering wheel of the icon: a rim with a slightly flat bottom, a tonal disc inside it, three spokes (the one down
# is wide) and a pill-shaped hub, turned right. It is made of a few primitives, so the same geometry is written as SVG
# and as an Android vector. 108x108 is the space of an adaptive icon: its safe zone is the 66-unit circle at the centre.
import io
import math

BACKGROUND = "#101010"  # the black of the app
DISC = "#1B2533"        # a tonal step from the black towards the blue
WHEEL = "#A6C8FF"       # the light blue (tone 80 of the brand blue #185EA2)
HUB = "#7EDB8A"         # the green of the app

C = 54
TURN = 14           # degrees clockwise: the wheel turns right
SCALE = 1.08        # the whole wheel
R, W = 22.5, 7      # the centre line of the rim and its weight
FLAT = 0.84         # the flat bottom: the chord at FLAT * R below the centre
SPOKES = ((168, 6), (12, 6), (90, 9.5))  # (degrees, width): two slightly down, a wide one down
PILL_W, PILL_H = 16.5, 12


def _pt(r, deg):
    a = math.radians(deg)
    return C + r * math.cos(a), C + r * math.sin(a)


def _flat_circle(r, chord_y):
    """A circle of radius r cut by the horizontal chord at chord_y (below the centre): the path, clockwise"""
    hx = math.sqrt(r * r - (chord_y - C) ** 2)
    return f"M{C - hx:.2f},{chord_y:.2f}A{r},{r} 0 1,1 {C + hx:.2f},{chord_y:.2f}Z"


RIM_PATH = _flat_circle(R, C + FLAT * R)
# The disc fills the rim; its edge goes 0.5 under the rim
DISC_PATH = _flat_circle(R - W / 2 + 0.5, C + FLAT * R - W / 2 + 0.5)


def _spoke_end(deg, width):
    """The spoke ends under the rim, so its round cap never shows outside"""
    s = math.sin(math.radians(deg))
    centre = min(R, FLAT * R / s) if s > 0 else R
    return centre - 1 - max(0, width / 2 - W / 2)


SPOKE_PATHS = [(f"M{C},{C}L{_pt(_spoke_end(d, w), d)[0]:.2f},{_pt(_spoke_end(d, w), d)[1]:.2f}", w)
               for d, w in SPOKES]
PILL_PATH = (f"M{C - PILL_W / 2 + PILL_H / 2:.2f},{C - PILL_H / 2:.2f}h{PILL_W - PILL_H:.2f}"
             f"a{PILL_H / 2},{PILL_H / 2} 0 0,1 0,{PILL_H}h{PILL_H - PILL_W:.2f}"
             f"a{PILL_H / 2},{PILL_H / 2} 0 0,1 0,{-PILL_H}z")
TRANSFORM = f"translate({C} {C}) scale({SCALE}) rotate({TURN}) translate({-C} {-C})"


def wheel_svg(mono=None):
    """The wheel as SVG; mono: one color, without the disc (a themed icon)"""
    disc, wheel, hub = (None, mono, mono) if mono else (DISC, WHEEL, HUB)
    parts = [f'<path d="{DISC_PATH}" fill="{disc}"/>'] if disc else []
    parts += [f'<path d="{d}" stroke="{wheel}" stroke-width="{w}" stroke-linecap="round"/>' for d, w in SPOKE_PATHS]
    parts.append(f'<path d="{RIM_PATH}" fill="none" stroke="{wheel}" stroke-width="{W}" stroke-linejoin="round"/>')
    parts.append(f'<path d="{PILL_PATH}" fill="{hub}"/>')
    return f'<g transform="{TRANSFORM}">{"".join(parts)}</g>'


def wheel_vector(mono=None):
    """The wheel as the body of an Android vector (108x108); mono: one color, without the disc"""
    disc, wheel, hub = (None, mono, mono) if mono else (DISC, WHEEL, HUB)

    def fill(d, color):
        return f'        <path\n            android:fillColor="{color}"\n            android:pathData="{d}" />\n'

    def stroke(d, color, width, cap=None, join=None):
        extra = (f'\n            android:strokeLineCap="{cap}"' if cap else "") + \
                (f'\n            android:strokeLineJoin="{join}"' if join else "")
        return (f'        <path\n            android:pathData="{d}"\n            android:strokeColor="{color}"\n'
                f'            android:strokeWidth="{width}"{extra} />\n')

    body = fill(DISC_PATH, disc) if disc else ""
    body += "".join(stroke(d, wheel, w, cap="round") for d, w in SPOKE_PATHS)
    body += stroke(RIM_PATH, wheel, W, join="round")
    body += fill(PILL_PATH, hub)
    return (f'    <group\n        android:pivotX="{C}"\n        android:pivotY="{C}"\n'
            f'        android:rotation="{TURN}"\n        android:scaleX="{SCALE}"\n        android:scaleY="{SCALE}">\n'
            f'{body}    </group>\n')


def svg(inner, size, view="0 0 108 108", width=None, height=None):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width or size}" height="{height or size}" '
            f'viewBox="{view}">{inner}</svg>')


def png_bytes(text):
    """SVG to PNG: cairosvg, or librsvg through PyGObject where cairosvg is not installed"""
    try:
        import cairosvg
        return cairosvg.svg2png(bytestring=text.encode())
    except ImportError:
        import gi
        gi.require_version("Rsvg", "2.0")
        from gi.repository import Rsvg
        import cairo
        handle = Rsvg.Handle.new_from_data(text.encode())
        dim = handle.get_dimensions()
        surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, dim.width, dim.height)
        handle.render_cairo(cairo.Context(surface))
        out = io.BytesIO()
        surface.write_to_png(out)
        return out.getvalue()
