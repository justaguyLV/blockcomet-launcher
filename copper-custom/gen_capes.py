#!/usr/bin/env python3
"""Draws the three built-in capes (64x32 layout, scaled x8 = 512x256). Run once; the PNGs are committed in capes/."""
import math, random, os
from PIL import Image, ImageDraw, ImageFilter

S = 8                      # texture scale
W, H = 64 * S, 32 * S
FX, FY, FW, FH = S, S, 10 * S, 16 * S   # outside of the cape (what other players see from behind)

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
def grad(stops, t):
    t = max(0, min(1, t))
    for i in range(len(stops) - 1):
        (t0, c0), (t1, c1) = stops[i], stops[i + 1]
        if t <= t1: return lerp(c0, c1, (t - t0) / (t1 - t0 or 1))
    return stops[-1][1]

def base(fn):
    im = Image.new("RGBA", (W, H))
    px = im.load()
    for y in range(H):
        for x in range(W):
            px[x, y] = fn(x, y) + (255,)
    return im

def glow(im, draw_fn, radius, color_alpha=1.0):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    blur = layer.filter(ImageFilter.GaussianBlur(radius))
    im.alpha_composite(blur)
    im.alpha_composite(layer)

def face_pt(u, v):  # u,v in 0..1 over the outside face
    return (FX + u * FW, FY + v * FH)

def border(im, col, inner=None, width=None):
    d = ImageDraw.Draw(im)
    w = width or S // 2
    d.rectangle([FX, FY, FX + FW - 1, FY + FH - 1], outline=col, width=w)
    if inner:
        d.rectangle([FX + w, FY + w, FX + FW - 1 - w, FY + FH - 1 - w], outline=inner, width=max(1, w // 3))

# ---------------------------------------------------------------- 1. Phoenix
def phoenix():
    stops = [(0, hexc("#5c0606")), (0.45, hexc("#c4180c")), (0.8, hexc("#ff7a00")), (1, hexc("#ffd54a"))]
    def bg(x, y):
        fy = ((y - FY) / FH)
        wave = math.sin(x / S * 1.7 + y / S * 0.6) * 0.04
        return grad(stops, fy + wave)
    im = base(bg)
    d = ImageDraw.Draw(im)
    # rising flame tongues from the bottom
    rnd = random.Random(7)
    for i in range(9):
        cx = FX + (i + 0.5) * FW / 9
        h = rnd.uniform(0.18, 0.34) * FH
        pts = [(cx - FW / 20, FY + FH), (cx, FY + FH - h), (cx + FW / 20, FY + FH)]
        d.polygon(pts, fill=hexc("#ffe27a") + (120,))
    # phoenix: body, two sweeping wings, tail
    gold, hot, deep = hexc("#ffd54a"), hexc("#fff7c2"), hexc("#7a0c0c")
    def emblem(dd):
        # wings (mirrored)
        for sgn in (-1, 1):
            wing = [face_pt(0.5, 0.40), face_pt(0.5 + sgn * 0.10, 0.30), face_pt(0.5 + sgn * 0.30, 0.18),
                    face_pt(0.5 + sgn * 0.46, 0.10), face_pt(0.5 + sgn * 0.40, 0.26), face_pt(0.5 + sgn * 0.46, 0.34),
                    face_pt(0.5 + sgn * 0.34, 0.40), face_pt(0.5 + sgn * 0.36, 0.50), face_pt(0.5 + sgn * 0.22, 0.50),
                    face_pt(0.5 + sgn * 0.14, 0.56), face_pt(0.5, 0.58)]
            dd.polygon(wing, fill=gold + (255,), outline=deep + (255,))
        # body + head
        dd.polygon([face_pt(0.5, 0.26), face_pt(0.57, 0.40), face_pt(0.54, 0.62), face_pt(0.5, 0.70),
                    face_pt(0.46, 0.62), face_pt(0.43, 0.40)], fill=hot + (255,), outline=deep + (255,))
        # tail feathers
        for k, sx in enumerate((-0.14, 0, 0.14)):
            dd.polygon([face_pt(0.5, 0.64), face_pt(0.5 + sx - 0.05, 0.88), face_pt(0.5 + sx, 0.95), face_pt(0.5 + sx + 0.05, 0.88)],
                       fill=gold + (255,), outline=deep + (255,))
        # eye
        ex, ey = face_pt(0.5, 0.33)
        dd.ellipse([ex - S * 0.35, ey - S * 0.35, ex + S * 0.35, ey + S * 0.35], fill=deep + (255,))
    glow(im, emblem, S * 1.6)
    border(im, hexc("#3a0404"), hexc("#ffb300"))
    return im

# ---------------------------------------------------------------- 2. Void
def void():
    stops = [(0, hexc("#05030f")), (0.5, hexc("#150a33")), (1, hexc("#3b0f6b"))]
    rnd = random.Random(11)
    stars = [(rnd.randrange(W), rnd.randrange(H), rnd.choice((1, 1, 1, 2)), rnd.random()) for _ in range(260)]
    def bg(x, y): return grad(stops, y / H)
    im = base(bg)
    d = ImageDraw.Draw(im)
    for x, y, r, t in stars:
        c = (255, 255, 255) if t > 0.4 else hexc("#c9a7ff")
        d.ellipse([x - r, y - r, x + r, y + r], fill=c + (255,))
    # nebula swirls on the face
    glow(im, lambda dd: [dd.ellipse([*face_pt(0.1 + i * 0.07, 0.62 + (i % 2) * 0.1), *face_pt(0.5 + i * 0.09, 0.9)], fill=hexc("#7c3aed") + (50,)) for i in range(5)], S * 3)
    # the Eye of the Void: diamond with glowing core
    cx, cy = face_pt(0.5, 0.45)
    def eye(dd):
        r = FW * 0.40
        dd.polygon([(cx, cy - r * 1.35), (cx + r, cy), (cx, cy + r * 1.35), (cx - r, cy)], outline=hexc("#d8b4fe") + (255,), fill=hexc("#1e0a4a") + (255,), width=S // 3)
        r2 = r * 0.62
        dd.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=hexc("#a855f7") + (255,))
        r3 = r * 0.34
        dd.ellipse([cx - r3, cy - r3, cx + r3, cy + r3], fill=hexc("#f0abfc") + (255,))
        dd.ellipse([cx - r3 * 0.4, cy - r3 * 1.6, cx + r3 * 0.4, cy + r3 * 1.6], fill=hexc("#05030f") + (255,))  # slit pupil
    glow(im, eye, S * 1.4)
    # falling sparks below the eye
    for i in range(14):
        sx, sy = face_pt(0.15 + (i * 0.061) % 0.7, 0.78 + (i * 0.037) % 0.18)
        d.rectangle([sx, sy, sx + S * 0.4, sy + S * 0.4], fill=hexc("#e9d5ff") + (255,))
    border(im, hexc("#a855f7"), hexc("#3b0f6b"))
    return im

# ---------------------------------------------------------------- 3. Crown
def crown():
    stops = [(0, hexc("#8a6508")), (0.35, hexc("#e6b422")), (0.6, hexc("#fff0a0")), (1, hexc("#c99a14"))]
    def bg(x, y):
        return grad(stops, ((x + y * 0.8) % (W * 0.9)) / (W * 0.9) * 0.9 + 0.05)
    im = base(bg)
    d = ImageDraw.Draw(im)
    # royal blue field inside the face
    pad = S * 1.1
    d.rectangle([FX + pad, FY + pad, FX + FW - pad, FY + FH - pad], fill=hexc("#16307a"))
    for k in range(7):  # fine gold pinstripes
        yy = FY + pad + (k + 0.5) * (FH - 2 * pad) / 7
        d.line([FX + pad, yy, FX + FW - pad, yy], fill=hexc("#1d3f9c"), width=1)
    gold, shine, dark = hexc("#ffd700"), hexc("#fff7b0"), hexc("#8a6508")
    def crown_shape(dd):
        base_y = 0.62
        pts = [face_pt(0.17, base_y), face_pt(0.12, 0.30), face_pt(0.30, 0.44), face_pt(0.50, 0.22),
               face_pt(0.70, 0.44), face_pt(0.88, 0.30), face_pt(0.83, base_y)]
        dd.polygon(pts, fill=gold + (255,), outline=dark + (255,))
        dd.rectangle([*face_pt(0.17, base_y), *face_pt(0.83, 0.72)], fill=gold + (255,), outline=dark + (255,))
        dd.rectangle([*face_pt(0.17, 0.65), *face_pt(0.83, 0.67)], fill=shine + (255,))
        for (u, v, c) in ((0.12, 0.30, "#ff3b3b"), (0.50, 0.22, "#3bff8a"), (0.88, 0.30, "#ff3b3b")):
            x0, y0 = face_pt(u, v)
            dd.ellipse([x0 - S * 0.55, y0 - S * 0.55, x0 + S * 0.55, y0 + S * 0.55], fill=hexc(c) + (255,), outline=shine + (255,))
        for u in (0.30, 0.50, 0.70):
            x0, y0 = face_pt(u, 0.69)
            dd.ellipse([x0 - S * 0.4, y0 - S * 0.4, x0 + S * 0.4, y0 + S * 0.4], fill=hexc("#7dd3fc") + (255,))
    glow(im, crown_shape, S * 1.2)
    # laurel dots under the crown
    for i in range(7):
        x0, y0 = face_pt(0.2 + i * 0.1, 0.84)
        d.ellipse([x0 - S * 0.3, y0 - S * 0.3, x0 + S * 0.3, y0 + S * 0.3], fill=gold + (255,))
    border(im, hexc("#6b4a05"), hexc("#fff0a0"))
    return im

if __name__ == "__main__":
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "capes")
    os.makedirs(out, exist_ok=True)
    for name, fn in (("phoenix", phoenix), ("void", void), ("crown", crown)):
        im = fn()
        im.save(os.path.join(out, name + ".png"))
        print(name, im.size)
