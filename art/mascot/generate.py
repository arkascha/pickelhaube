#!/usr/bin/env python3
"""
The mascot of the welcome tour, of the dialer's title and of a call in the dialer: the app icon's helmet as a little character – the round icon with eyes,
its handset worn as a moustache, and white cartoon hands. One shape list per pose is written as an
SVG (art/mascot/*.svg, to look at) and as an Android vector drawable (res/drawable/mascot_*.xml).
Only plain shapes, no transforms: what ImageMagick renders is what Android draws.

Run from the repository root:  python3 art/mascot/generate.py
"""
import math
import os

BLUE, YELLOW, RED, WHITE, INK = "#005EA2", "#FEE400", "#F20400", "#FFFFFF", "#0B2540"
GLASS = "#CFE6F7"
SIZE = 108  # canvas, in the icon's units: the head is the round icon (radius 36 around 54,54)


def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0Z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:.2f},{cy:.2f}a{rx:.2f},{ry:.2f} 0 1,0 {2 * rx:.2f},0a{rx:.2f},{ry:.2f} 0 1,0 {-2 * rx:.2f},0Z"


def scaled(points):
    """The icon's helmet shapes are scaled to 92 % around the centre (as the launcher icon is)."""
    return [(0.92 * x + 4.32, 0.92 * y + 4.32) for x, y in points]


def handset(dy, down=False):
    """
    The icon's handset (Material "call" glyph) as a moustache: turned the other way round than on
    the icon, so both ends point up – a twirled moustache, and a smile – moved down by dy. With
    [down] as on the icon: ends down, a worried look.
    """
    d = ("M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 "
         "0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 "
         "0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z")
    import re
    angle = 135 if down else -45
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))

    def t(x, y):
        x, y = x - 12, y - 12
        x, y = 1.75 * (c * x - s * y), 1.75 * (s * x + c * y)
        return x + 54, y + (77 if down else 72) + dy

    toks = re.findall(r"[A-Za-z]|-?\d*\.?\d+", d)
    i, cmd, cx, cy, sx, sy, out = 0, None, 0.0, 0.0, 0.0, 0.0, []

    def num():
        nonlocal i
        v = float(toks[i]); i += 1
        return v

    f = lambda p: "%.2f,%.2f" % p
    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]; i += 1
        if cmd in "Mm":
            x, y = num(), num()
            if cmd == "m": x, y = cx + x, cy + y
            cx, cy, sx, sy = x, y, x, y; out.append("M" + f(t(x, y))); cmd = "l" if cmd == "m" else "L"
        elif cmd in "Ll":
            x, y = num(), num()
            if cmd == "l": x, y = cx + x, cy + y
            cx, cy = x, y; out.append("L" + f(t(x, y)))
        elif cmd in "Hh":
            x = num(); cx = cx + x if cmd == "h" else x; out.append("L" + f(t(cx, cy)))
        elif cmd in "Vv":
            y = num(); cy = cy + y if cmd == "v" else y; out.append("L" + f(t(cx, cy)))
        elif cmd in "Cc":
            p = [num() for _ in range(6)]
            if cmd == "c": p = [p[0] + cx, p[1] + cy, p[2] + cx, p[3] + cy, p[4] + cx, p[5] + cy]
            out.append("C" + " ".join(f(t(p[k], p[k + 1])) for k in (0, 2, 4))); cx, cy = p[4], p[5]
        elif cmd in "Zz":
            out.append("Z"); cx, cy = sx, sy; cmd = None
    return "".join(out)


def helmet():
    def poly(pts):
        return "M" + "L".join("%.2f,%.2f" % p for p in scaled(pts)) + "Z"
    # dome: the icon's curve, as a path in scaled coordinates
    (a, b), (c1, c2), (d1, d2), (e1, e2), (g1, g2), (h1, h2), (k1, k2) = scaled(
        [(30, 54), (30, 41), (41, 37), (54, 37), (67, 37), (78, 41), (78, 54)])
    dome = f"M{a:.2f},{b:.2f}C{c1:.2f},{c2:.2f} {d1:.2f},{d2:.2f} {e1:.2f},{e2:.2f}C{g1:.2f},{g2:.2f} {h1:.2f},{h2:.2f} {k1:.2f},{k2:.2f}Z"
    brim_l, brim_t = scaled([(28.75, 52.5)])[0]
    brim_r, brim_b = scaled([(79.25, 58)])[0]
    r = (brim_b - brim_t) / 2
    brim = (f"M{brim_l + r:.2f},{brim_t:.2f}H{brim_r - r:.2f}A{r:.2f},{r:.2f} 0 0,1 {brim_r - r:.2f},{brim_b:.2f}"
            f"H{brim_l + r:.2f}A{r:.2f},{r:.2f} 0 0,1 {brim_l + r:.2f},{brim_t:.2f}Z")
    spike = poly([(54, 20), (58, 35), (50, 35)])
    sb_l, sb_t = scaled([(48.5, 33.5)])[0]
    sb_r, sb_b = scaled([(59.5, 38.5)])[0]
    base = f"M{sb_l + 1.8:.2f},{sb_t:.2f}H{sb_r - 1.8:.2f}Q{sb_r:.2f},{sb_t:.2f} {sb_r:.2f},{sb_t + 1.8:.2f}V{sb_b - 1.8:.2f}Q{sb_r:.2f},{sb_b:.2f} {sb_r - 1.8:.2f},{sb_b:.2f}H{sb_l + 1.8:.2f}Q{sb_l:.2f},{sb_b:.2f} {sb_l:.2f},{sb_b - 1.8:.2f}V{sb_t + 1.8:.2f}Q{sb_l:.2f},{sb_t:.2f} {sb_l + 1.8:.2f},{sb_t:.2f}Z"
    return [("fill", YELLOW, dome), ("fill", RED, spike), ("fill", RED, base), ("fill", RED, brim)]


# Eyes sit between the brim (bottom ≈ 57.7) and the moustache.
EYE_L, EYE_R, EYE_Y = 46.5, 61.5, 63.0


def eyes(look=(0.0, 0.0), happy=False, squint_right=False, closed=False):
    if closed:  # a blink: both eyes a short line
        line = lambda x: ("stroke", INK, f"M{x - 3.2:.2f},{EYE_Y + 0.5:.2f}Q{x:.2f},{EYE_Y + 1.6:.2f} {x + 3.2:.2f},{EYE_Y + 0.5:.2f}", 1.6)
        return [line(EYE_L), line(EYE_R)]
    if happy:  # closed, smiling: ^ ^
        arc = lambda x: ("stroke", INK, f"M{x - 3.2:.2f},{EYE_Y + 1:.2f}Q{x:.2f},{EYE_Y - 3.2:.2f} {x + 3.2:.2f},{EYE_Y + 1:.2f}", 1.6)
        return [arc(EYE_L), arc(EYE_R)]
    shapes = []
    for x in (EYE_L, EYE_R):
        if squint_right and x == EYE_R:
            shapes.append(("stroke", INK, f"M{x - 3:.2f},{EYE_Y:.2f}H{x + 3:.2f}", 1.6))
            continue
        shapes.append(("fill", WHITE, ellipse(x, EYE_Y, 3.6, 4.0)))
        shapes.append(("fill", INK, circle(x + look[0], EYE_Y + look[1], 1.9)))
        shapes.append(("fill", WHITE, circle(x + look[0] + 0.7, EYE_Y + look[1] - 0.8, 0.6)))
    return shapes


def hand(cx, cy, r=5.6):
    return [("fill", WHITE, circle(cx, cy, r)), ("stroke", BLUE, circle(cx, cy, r), 1.4)]


def head(moustache_dy=0.0, worried=False):
    return [("fill", BLUE, circle(54, 54, 36))] + helmet() + [("fill", WHITE, handset(moustache_dy, down=worried))]


def sparkle(cx, cy, r, color=YELLOW):
    k = r * 0.28
    d = (f"M{cx:.2f},{cy - r:.2f}L{cx + k:.2f},{cy - k:.2f}L{cx + r:.2f},{cy:.2f}L{cx + k:.2f},{cy + k:.2f}"
         f"L{cx:.2f},{cy + r:.2f}L{cx - k:.2f},{cy + k:.2f}L{cx - r:.2f},{cy:.2f}L{cx - k:.2f},{cy - k:.2f}Z")
    return [("fill", color, d)]


POSES = {
    # Hello: the right hand up, waving (two small motion lines).
    "waving": head() + eyes((0.4, -0.3)) + [
        ("stroke", BLUE, "M95,25Q99,30 98,36", 1.4), ("stroke", BLUE, "M99,22Q104,29 103,37", 1.4),
    ] + hand(90, 32),
    # What the box needs: a clipboard held at the lower right.
    "clipboard": head() + eyes((1.2, 0.8)) + [
        ("fill", WHITE, "M78,62H100Q102,62 102,64V96Q102,98 100,98H78Q76,98 76,96V64Q76,62 78,62Z"),
        ("stroke", BLUE, "M78,62H100Q102,62 102,64V96Q102,98 100,98H78Q76,98 76,96V64Q76,62 78,62Z", 1.4),
        ("fill", RED, "M84,59H94V65H84Z"),
        ("stroke", BLUE, "M81,72H97M81,79H97M81,86H93", 1.4),
        ("stroke", RED, "M80,71.5l1.5,1.5 3,-3", 1.2),
    ] + hand(77, 88),
    # Where is the box: a magnifying glass in front of the right eye.
    "searching": head() + eyes((1.0, 0.0)) + [
        ("stroke", INK, "M70,71L84,88", 3.2),
        ("fill", GLASS, circle(64, 63, 8.5)),
        ("stroke", INK, circle(64, 63, 8.5), 2.2),
        ("fill", WHITE, "M59.5,59.5Q61,57 64,56.6L64,58.4Q61.8,58.8 60.9,60.4Z"),
    ] + hand(85, 89),
    # Who are you: typing on a smartphone held low, eyes down.
    "typing": head() + eyes((0.0, 1.6)) + [
        ("fill", INK, "M44,84H66Q68,84 68,86V100Q68,102 66,102H44Q42,102 42,100V86Q42,84 44,84Z"),
        ("fill", GLASS, "M45,87H65V99H45Z"),
        ("stroke", BLUE, "M48,91H62M48,95H58", 1.2),
    ] + hand(41, 96) + hand(69, 96),
    # Found it, all set: both hands up, happy eyes, sparkles.
    "cheering": head() + eyes(happy=True) + hand(16, 30) + hand(92, 30)
        + sparkle(8, 14, 5) + sparkle(100, 12, 4) + sparkle(28, 8, 3.2, RED),
    # Did not work: scratching the head, one eye squinted, a question mark.
    "puzzled": head(worried=True) + eyes((-0.6, -0.4), squint_right=True) + [
        ("fill", "#7FC4EE", "M27,52Q24,57 27,59Q30,57 27,52Z"),
        ("stroke", RED, "M90,8Q90,3 95,3Q100,3 100,8Q100,12 95,14V18", 2.4),
        ("fill", RED, circle(95, 23.5, 1.6)),
    ] + hand(89, 42),
    # At rest, in the dialer's title: just the head, eyes front.
    "idle": head() + eyes((0.0, 0.0)),
    # Nodding, as the dialer is pulled down: eyes down.
    "nodding": head() + eyes((0.0, 1.9)),
    # The box rings your phone: eyes down to the phone, ringing marks on both sides of the head.
    "ringing": head() + eyes((-0.8, 1.4)) + [
        ("stroke", BLUE, "M13,40Q8,47 10,55", 1.6), ("stroke", BLUE, "M7,36Q1,46 4,58", 1.6),
        ("stroke", BLUE, "M95,40Q100,47 98,55", 1.6), ("stroke", BLUE, "M101,36Q107,46 104,58", 1.6),
    ],
    # The other side is being called: a hand cupped behind the ear, listening, eyes to the side.
    "listening": head() + eyes((-1.4, -0.2)) + [
        ("stroke", BLUE, "M10,52Q5,58 10,66", 1.4),
    ] + hand(19, 59),
    # Connected: happy eyes, and what is said going out on the right.
    "talking": head() + eyes(happy=True) + [
        ("stroke", BLUE, "M94,50Q98,54 94,58", 1.6), ("stroke", BLUE, "M99,46Q105,54 99,62", 1.6),
    ],
}


def blink(name):
    """The pose [name] with its eyes closed – shown for a moment now and then (see Mascot in WelcomeScreen)."""
    look = {"waving": (0.4, -0.3), "clipboard": (1.2, 0.8), "searching": (1.0, 0.0), "typing": (0.0, 1.6),
            "ringing": (-0.8, 1.4), "listening": (-1.4, -0.2), "idle": (0.0, 0.0)}[name]
    shapes = POSES[name]
    open_eyes = eyes(look)
    start = next(i for i in range(len(shapes)) if shapes[i:i + len(open_eyes)] == open_eyes)
    return shapes[:start] + eyes(closed=True) + shapes[start + len(open_eyes):]


BLINKING = ["waving", "clipboard", "searching", "typing", "ringing", "listening", "idle"]


def svg(shapes):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 {SIZE} {SIZE}">']
    for s in shapes:
        if s[0] == "fill":
            out.append(f'  <path fill="{s[1]}" d="{s[2]}"/>')
        else:
            out.append(f'  <path fill="none" stroke="{s[1]}" stroke-width="{s[3]}" stroke-linecap="round" stroke-linejoin="round" d="{s[2]}"/>')
    out.append("</svg>")
    return "\n".join(out) + "\n"


def vector(name, shapes):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f'<!-- The welcome tour\'s mascot, pose "{name}". Generated by art/mascot/generate.py – edit that, not this. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{SIZE}dp"', f'    android:height="{SIZE}dp"',
           f'    android:viewportWidth="{SIZE}"', f'    android:viewportHeight="{SIZE}">']
    for s in shapes:
        if s[0] == "fill":
            out.append(f'    <path android:fillColor="{s[1]}" android:pathData="{s[2]}" />')
        else:
            out.append(f'    <path android:strokeColor="{s[1]}" android:strokeWidth="{s[3]}" android:strokeLineCap="round" '
                       f'android:strokeLineJoin="round" android:pathData="{s[2]}" />')
    out.append("</vector>")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    all_poses = dict(POSES)
    all_poses.update({f"{name}_blink": blink(name) for name in BLINKING})
    for name, shapes in all_poses.items():
        with open(f"art/mascot/{name}.svg", "w") as f:
            f.write(svg(shapes))
        with open(f"app/src/main/res/drawable/mascot_{name}.xml", "w") as f:
            f.write(vector(name, shapes))
    print("written:", ", ".join(all_poses))
