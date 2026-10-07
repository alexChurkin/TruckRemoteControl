# Steering wheel mark, 108x108 adaptive-icon space (safe zone: 66-unit circle at the centre).
# Plain path data, so the same geometry goes to SVG and to Android VectorDrawables.
import math

def arc(cx, cy, r, a0, a1):
    p = lambda a: (cx + r*math.cos(math.radians(a)), cy - r*math.sin(math.radians(a)))
    (x0, y0), (x1, y1) = p(a0), p(a1)
    return f"M{x0:.2f},{y0:.2f}A{r},{r} 0 0 0 {x1:.2f},{y1:.2f}"

def ring(cx, cy, ro, ri):
    return (f"M{cx-ro},{cy}a{ro},{ro} 0 1 0 {2*ro},0a{ro},{ro} 0 1 0 -{2*ro},0z"
            f"M{cx-ri},{cy}a{ri},{ri} 0 1 1 {2*ri},0a{ri},{ri} 0 1 1 -{2*ri},0z")

C = 54
RING = ring(C, C, 30, 23.5)
# spokes: left/right bar that dips to the hub, and a wide lower spoke
SPOKES = ("M24.6,55 C34,54 38,52 44,51 L64,51 C70,52 74,54 83.4,55 L83.4,60 C74,60 70,61 64,63"
          " L60,83 L48,83 L44,63 C38,61 34,60 24.6,60 Z")
HUB = "M54,48a8.5,8.5 0 1 1 0,17a8.5,8.5 0 1 1 0,-17z"
ARCS = [arc(C, 46, r, 45, 135) for r in (5.5, 11)]
ARC_W = 3.6

WHITE = "#FFFFFF"; AMBER = "#A9D2FF"  # light tonal blue accent
BG_TOP, BG_BOTTOM = "#2F80E4", "#1257B0"
BG_DEFS = (f'<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">'
           f'<stop offset="0" stop-color="{BG_TOP}"/><stop offset="1" stop-color="{BG_BOTTOM}"/></linearGradient>')

def wheel_svg(body=WHITE, accent=AMBER):
    s = [f'<path d="{RING}" fill="{body}" fill-rule="evenodd"/>', f'<path d="{SPOKES}" fill="{body}"/>',
         f'<path d="{HUB}" fill="{accent}"/>']
    s += [f'<path d="{a}" fill="none" stroke="{accent}" stroke-width="{ARC_W}" stroke-linecap="round"/>' for a in ARCS]
    return "\n".join(s)

def svg(inner, w, h=None, vb="0 0 108 108", defs=BG_DEFS):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h or w}" viewBox="{vb}"><defs>{defs}</defs>{inner}</svg>'
