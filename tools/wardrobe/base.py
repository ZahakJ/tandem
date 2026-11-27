"""A faithful SVG copy of Tandem's character (ui/AvatarArt.kt) in its 100 x 120 unit box, plus the part registry.

Parts are drawn as SVG fragments. Colours a user picks are passed in; every fragment marks its recolourable regions
with class="cloth" / "hair" / "skin" / "iris" (and -edge / -deep / -light variants) so the Kotlin port knows which
fill follows which choice. Hats and hair are drawn on the default crown (top 16, sides 25..75 at y 41, eyes ~y 44.9,
brows ~37.5, ears 21..29), exactly as AvatarArt's Head.crown() expects.
"""
import math

# --- colours (AvatarArt's helpers) -------------------------------------------------------------------------------

def _rgb(h):
    h = h.lstrip('#')
    if len(h) == 8:
        h = h[2:]
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def _hex(c):
    return '#%02X%02X%02X' % tuple(max(0, min(255, round(v * 255))) for v in c)


def lerp(a, b, t):
    a, b = _rgb(a), _rgb(b)
    return _hex(tuple(x + (y - x) * t for x, y in zip(a, b)))


def deep(c, f):
    return lerp(c, '#231C2E', f)


def light(c, f):
    return lerp(c, '#FFFFFF', f)


def edge(c, f=0.36):
    r, g, b = _rgb(c)
    return _hex((r * (1 - f), g * (1 - f * 1.04), b * (1 - f * 0.82)))


def lum(c):
    def ch(v):
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(v) for v in _rgb(c))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


SKIN = ['#FDE3D0', '#F8D2B5', '#F1C09A', '#E3A87F', '#D39168', '#BB7A52', '#9C6240', '#7E4B31', '#633826', '#4A291C',
        '#B8EBD3', '#D8C8F5', '#BFDDF7', '#FFC9D6']
HAIR = ['#2B2530', '#4A3226', '#7A5134', '#9A3F2B', '#D9772F', '#F0CD6E', '#F4EBD6', '#B9B4BE', '#F59BC0', '#7FD8B5',
        '#7DB8F0', '#B59AEE']
EYES = ['#2E2433', '#6B4226', '#8C7A3A', '#3F8F5E', '#3F7FC9', '#7A8794', '#7E5BC9', '#C98A2E']
TOP = ['#F27D72', '#F6A96B', '#F2CF5B', '#6FCF9F', '#3FB6B0', '#63A8EE', '#3D5A9E', '#A98BE8', '#EE8BB8', '#F2E8D5',
       '#4A4A57', '#D9484F']
BLUSH = '#FF8A9B'
MOUTH_IN = '#8E3248'
TONGUE = '#FF97A8'
TIE = '#FF7FA2'
DENIM = '#55618A'
SHIRT = '#F7F3EA'
SHOE = '#3F3752'
GOLD = '#F2C14E'
FRAME = '#3A3346'
WHITE = '#FFFFFF'

# --- svg helpers -----------------------------------------------------------------------------------------------------

def f(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return '0' if s == '-0' else s


def attrs(fill=None, stroke=None, w=None, op=None, cls=None, extra=''):
    a = []
    a.append('fill="%s"' % (fill or 'none'))
    if stroke:
        a.append('stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"' % (stroke, f(w or 1.1)))
    if op is not None and op < 1:
        a.append('opacity="%s"' % f(op))
    if cls:
        a.append('class="%s"' % cls)
    if extra:
        a.append(extra)
    return ' '.join(a)


def path(d, fill=None, stroke=None, w=None, op=None, cls=None, extra=''):
    return '<path d="%s" %s/>' % (d, attrs(fill, stroke, w, op, cls, extra))


def shape(d, fill, e=None, w=1.1, cls=None, op=None):
    """A filled shape with its outline (AvatarArt's shape())."""
    e = e or edge(fill)
    ecls = (cls + '-edge') if cls and ' ' not in cls else None
    return path(d, fill, op=op, cls=cls) + path(d, None, e, w, op=op, cls=ecls)


def stroke(d, color, w, op=None, cls=None):
    return path(d, None, color, w, op, cls)


def oval(color, cx, cy, rx, ry, op=None, cls=None, st=None, w=None):
    return '<ellipse cx="%s" cy="%s" rx="%s" ry="%s" %s/>' % (f(cx), f(cy), f(rx), f(ry), attrs(None if st else color, color if st else None, w, op, cls))


def circle(color, cx, cy, r, op=None, cls=None, st=False, w=1.1):
    return '<circle cx="%s" cy="%s" r="%s" %s/>' % (f(cx), f(cy), f(r), attrs(None if st else color, color if st else None, w if st else None, op, cls))


def dot(fill, x, y, r, e=None, w=1.1, cls=None):
    e = e or edge(fill)
    return circle(e, x, y, r + w / 2, cls=(cls + '-edge') if cls else None) + circle(fill, x, y, r - w / 2 + 0.01, cls=cls)


def rrect(color, x, y, w, h, r, op=None, cls=None, st=None, sw=None):
    return '<rect x="%s" y="%s" width="%s" height="%s" rx="%s" %s/>' % (f(x), f(y), f(w), f(h), f(r), attrs(None if st else color, color if st else None, sw, op, cls))


def line(color, x1, y1, x2, y2, w, op=None, cls=None):
    return stroke('M%s %sL%s %s' % (f(x1), f(y1), f(x2), f(y2)), color, w, op, cls)


def arc_d(x, y, w, h, start, sweep):
    """Compose drawArc(start, sweep, topLeft=(x, y), size=(w, h)) as an SVG path (angles clockwise from +x)."""
    rx, ry = w / 2, h / 2
    cx, cy = x + rx, y + ry
    a0 = math.radians(start)
    a1 = math.radians(start + sweep)
    x0, y0 = cx + rx * math.cos(a0), cy + ry * math.sin(a0)
    x1, y1 = cx + rx * math.cos(a1), cy + ry * math.sin(a1)
    large = 1 if abs(sweep) > 180 else 0
    sw = 1 if sweep > 0 else 0
    return 'M%s %sA%s %s 0 %d %d %s %s' % (f(x0), f(y0), f(rx), f(ry), large, sw, f(x1), f(y1))


def arc(color, x, y, w, h, start, sweep, sw, op=None, cls=None):
    return stroke(arc_d(x, y, w, h, start, sweep), color, sw, op, cls)


def star_d(cx, cy, r, inner, points):
    pts = []
    for i in range(points * 2):
        rr = r if i % 2 == 0 else r * inner
        a = math.radians(-90 + i * 180 / points)
        pts.append('%s %s' % (f(cx + math.cos(a) * rr), f(cy + math.sin(a) * rr)))
    return 'M' + 'L'.join(pts) + 'Z'


def star(x, y, r, color, points=5, inner=0.45, e=None, cls=None):
    s = path(star_d(x, y, r, inner, points), color, cls=cls)
    if e:
        s += path(star_d(x, y, r, inner, points), None, e, 1.1)
    return s


def mirror_x(d_fn):
    """Build both sides: d_fn(x) gets a function mapping a left-side x to the side's x."""
    return [d_fn(lambda v: v), d_fn(lambda v: 100 - v)]


def g(content, transform=None, clip=None, cls=None, ident=None):
    a = ''
    if ident:
        a += ' id="%s"' % ident
    if cls:
        a += ' class="%s"' % cls
    if transform:
        a += ' transform="%s"' % transform
    if clip:
        a += ' clip-path="url(#%s)"' % clip
    return '<g%s>%s</g>' % (a, content)


# --- the head ------------------------------------------------------------------------------------------------------

class Head:
    def __init__(self, shape, hw, top, mid, jawW, jawY, chinW, chin):
        self.shape, self.hw, self.top, self.mid, self.jawW, self.jawY, self.chinW, self.chin = shape, hw, top, mid, jawW, jawY, chinW, chin
        self.cx = 50.0

    @property
    def eyeY(self):
        return self.top + (self.chin - self.top) * 0.545

    @property
    def dx(self):
        return 8.6 + self.hw * 0.07

    @property
    def mouthY(self):
        return self.eyeY + (self.chin - self.eyeY) * 0.6

    @property
    def noseY(self):
        return self.eyeY + (self.mouthY - self.eyeY) * 0.5

    def crown(self):
        """The transform hair and hats get (they are drawn for the default crown)."""
        sx, sy = self.hw / 25, (self.mid - self.top) / 25
        ty = self.top - 16
        # translate(0, ty) then scale about (50, 16)
        return 'translate(0 %s) translate(50 16) scale(%s %s) translate(-50 -16)' % (f(ty), f(sx), f(sy))

    def d(self):
        cx, w, t, mid = self.cx, self.hw, self.top, self.mid
        dX, dY = self.chinW - self.jawW, self.chin - self.jawY + 5
        n = math.hypot(dX, dY)
        dX /= n
        dY /= n
        a = (self.jawY - mid) * 0.38
        b = (self.chin - self.jawY) * 0.62
        p = [cx, t,
             cx + w * 0.56, t, cx + w, t + 0.44 * (mid - t), cx + w, mid,
             cx + w, mid + (self.jawY - mid) * 0.42, cx + self.jawW - dX * a, self.jawY - dY * a, cx + self.jawW, self.jawY,
             cx + self.jawW + dX * b, self.jawY + dY * b, cx + self.chinW, self.chin, cx, self.chin]
        x = lambda i: f(p[i * 2])
        y = lambda i: f(p[i * 2 + 1])
        mx = lambda i: f(2 * cx - p[i * 2])
        return ('M%s %s' % (x(0), y(0)) +
                'C%s %s %s %s %s %s' % (x(1), y(1), x(2), y(2), x(3), y(3)) +
                'C%s %s %s %s %s %s' % (x(4), y(4), x(5), y(5), x(6), y(6)) +
                'C%s %s %s %s %s %s' % (x(7), y(7), x(8), y(8), x(9), y(9)) +
                'C%s %s %s %s %s %s' % (mx(8), y(8), mx(7), y(7), mx(6), y(6)) +
                'C%s %s %s %s %s %s' % (mx(5), y(5), mx(4), y(4), mx(3), y(3)) +
                'C%s %s %s %s %s %sZ' % (mx(2), y(2), mx(1), y(1), x(0), y(0)))


HEADS = [Head(0, 25, 16, 41, 20.5, 60.5, 9, 69), Head(1, 23, 14.5, 40, 17, 61.5, 5.5, 71),
         Head(2, 25.5, 16, 41, 24.5, 62, 14.5, 69), Head(3, 26.5, 15.5, 39.5, 15.5, 61, 2, 70.5),
         Head(4, 22, 12.5, 39, 19.5, 65, 8, 74), Head(5, 28.5, 18, 42, 25.5, 59.5, 12, 67.5)]


class Ctx:
    """What a part is drawn with: the head, the user's colours and the face's ink."""

    def __init__(self, shape=0, skin=2, hair=1, cloth=5, iris=1):
        self.head = HEADS[shape]
        self.skin = SKIN[skin] if isinstance(skin, int) else skin
        self.hair = HAIR[hair] if isinstance(hair, int) else hair
        self.cloth = TOP[cloth] if isinstance(cloth, int) else cloth
        self.iris = EYES[iris] if isinstance(iris, int) else iris
        self.ink = '#12090E' if lum(self.skin) < 0.1 else lerp('#2A1D2A', self.skin, 0.12)


# --- registry ------------------------------------------------------------------------------------------------------
# Each item: dict(id, name, cat, fn, colors=[...], layer=..., notes=...). fn(ctx) returns a dict of layers:
#   back      behind everything (long hair, capes, hat brims seen behind the head, a hijab's drape)
#   hanging   over the body, behind the head (braids, scarf tails)
#   body      replaces nothing; drawn over the torso (tops only, see tops)
#   face      on the face, under the hair (eyes, face extras, glasses)
#   front     over the hair (hair front, hats)
# plus flags: hides_hair ('all' | 'top' | None) for headwear.

REG = {}


def item(cat, ident, name, colors=(), notes='', layer='', **kw):
    def deco(fn):
        REG[(cat, ident)] = dict(id=ident, name=name, cat=cat, fn=fn, colors=list(colors), notes=notes, layer=layer, **kw)
        return fn
    return deco


# --- the body ------------------------------------------------------------------------------------------------------

TORSO = ('M41 70.5C36 71 33.2 73.4 32.8 78C32.5 86 33.6 95 34.6 100.6Q35 103.6 38.2 103.6L61.8 103.6Q65 103.6 65.4 100.6'
         'C66.4 95 67.5 86 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')
DRESS = ('M41 70.5C36 71 33.2 73.4 32.8 78C32.6 83 34 87 34.6 89.5C31.4 95 27.6 101 27 104.6Q27 107.6 30.6 107.6L69.4 107.6'
         'Q73 107.6 73 104.6C72.4 101 68.6 95 65.4 89.5C66 87 67.4 83 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')


def arm_d(front, wave=False):
    sx, sy = (60, 76.5) if front else (40, 76.5)
    side = 1 if front else -1
    ex, ey = 50 + side * 17.8, 87.5
    wx, wy = 50 + side * 18.6, 97.2
    if wave and front:
        ex, ey = 79, 85
        a = math.radians(8)
        wx, wy = ex + math.sin(a) * 21, ey - math.cos(a) * 21
    qx, qy = 2 * ex - (sx + wx) / 2, 2 * ey - (sy + wy) / 2
    return (sx, sy, qx, qy, wx, wy)


def arm(front, cloth, skin, sleeve, wave=False, cloth_cls='cloth'):
    sx, sy, qx, qy, wx, wy = arm_d(front, wave)
    d = 'M%s %sQ%s %s %s %s' % (f(sx), f(sy), f(qx), f(qy), f(wx), f(wy))
    if sleeve >= 1:
        return stroke(d, edge(cloth), 9.2, cls=cloth_cls + '-edge') + stroke(d, cloth, 7, cls=cloth_cls)
    out = stroke(d, edge(skin, 0.3), 7.6) + stroke(d, skin, 5.4, cls='skin')
    if sleeve <= 0:
        return out
    t = sleeve
    cx, cy = sx + (qx - sx) * t, sy + (qy - sy) * t
    bx = (1 - t) ** 2 * sx + 2 * (1 - t) * t * qx + t * t * wx
    by = (1 - t) ** 2 * sy + 2 * (1 - t) * t * qy + t * t * wy
    d2 = 'M%s %sQ%s %s %s %s' % (f(sx), f(sy), f(cx), f(cy), f(bx), f(by))
    return out + stroke(d2, edge(cloth), 10, cls=cloth_cls + '-edge') + stroke(d2, cloth, 7.8, cls=cloth_cls)


def hand(front, skin, wave=False):
    _, _, _, _, x, y = arm_d(front, wave)
    e = edge(skin, 0.3)
    if wave and front:
        return (oval(e, x, y - 1, 4.4, 5.1) + oval(e, x - 4, y + 0.8, 2.6, 1.75) + oval(skin, x, y - 1, 3.8, 4.5) +
                oval(skin, x - 4, y + 0.8, 2, 1.15))
    tx = x + 2.6 if x < 50 else x - 2.6
    return (circle(e, x, y + 0.6, 4.15) + circle(e, tx, y - 2, 2.05) + circle(skin, x, y + 0.6, 3.55, cls='skin') +
            circle(skin, tx, y - 2, 1.45, cls='skin'))


def legs(color, shoe=SHOE, cls=None):
    out = ''
    for side in (-1, 1):
        x = 50 + side * 4.6
        out += rrect(edge(color, 0.3), x - 4.1, 99, 8.2, 13.6, 3)
        out += rrect(color, x - 3.1, 99, 6.2, 12.8, 2.4, cls=cls)
        out += oval(shoe, x + side * 0.9, 113.4, 5.2, 3.3)
        out += oval(WHITE, x + side * 0.2, 112.2, 2.4, 1, op=0.22)
    return out


def neck(skin):
    return (rrect(edge(skin, 0.3), 44.6, 62, 10.8, 12.6, 5) + rrect(skin, 45.4, 62, 9.2, 11.8, 4.4, cls='skin') +
            oval(edge(skin, 0.25), 50, 68.5, 4.6, 1.8, op=0.35))


# --- the face defaults (AvatarArt eyes style 1, brows 1, mouth 0, cheeks 1) -----------------------------------------

def bright(ex, ey, rx, ry, iris, ink):
    x, y = ex, ey
    return (oval(ink, x, y, rx + 0.55, ry + 0.55) + oval(deep(iris, 0.18), x, y, rx - 0.1, ry - 0.1, cls='iris-deep') +
            oval(light(iris, 0.18), x, y + ry * 0.32, rx * 0.72, ry * 0.5, cls='iris-light') +
            oval(ink, x, y + 0.2, rx * 0.46, ry * 0.48) + circle(WHITE, x + rx * 0.32, y - ry * 0.36, rx * 0.36) +
            circle(WHITE, x - rx * 0.36, y + ry * 0.42, rx * 0.16, op=0.85) +
            arc(ink, x - rx - 0.5, y - ry - 0.5, rx * 2 + 1, ry * 2 + 1, 200, 140, 1.3))


def default_eyes(c):
    h = c.head
    return ''.join(bright(h.cx + s * h.dx, h.eyeY, 3.5, 4.3, c.iris, c.ink) for s in (-1, 1))


def default_brows(c, color):
    h = c.head
    out = ''
    for s in (-1, 1):
        bx = h.cx + s * (h.dx + 0.4)
        by = h.eyeY - 7.4
        out += stroke('M%s %sQ%s %s %s %s' % (f(bx - 3.4), f(by + 0.7), f(bx), f(by - 1.9), f(bx + 3.4), f(by + 0.5)), color, 1.5)
    return out


def default_mouth(c):
    h = c.head
    cx, y = h.cx, h.mouthY
    return stroke('M%s %sQ%s %s %s %s' % (f(cx - 4.4), f(y - 0.8), f(cx), f(y + 3.4), f(cx + 4.4), f(y - 0.8)), c.ink, 1.5)


def cheeks(c):
    h = c.head
    return ''.join(oval(BLUSH, h.cx + s * (h.dx + 4.2), h.eyeY + 6.2, 3.9, 2.3, op=0.38) for s in (-1, 1))


def nose(c):
    h = c.head
    return arc(edge(c.skin, 0.28), h.cx - 1.9, h.noseY - 1.8, 3.8, 3, 25, 130, 1.2)


# --- the default hair (Crop) so hats and tops are shown on a familiar head ------------------------------------------

def dome_d(pad, y0, taper=0.0):
    w = 25 + pad
    t = 16 - pad
    m = min(41, y0 - taper * 1.6)
    c = t + 0.44 * (41 - t)
    s = ''
    if taper > 0:
        s += 'M%s %sQ%s %s %s %s' % (f(50 - w + taper), f(y0), f(50 - w), f(y0), f(50 - w), f(m))
    else:
        s += 'M%s %sL%s %s' % (f(50 - w), f(y0), f(50 - w), f(m))
    s += 'C%s %s %s %s 50 %s' % (f(50 - w), f(c), f(50 - w * 0.56), f(t), f(t))
    s += 'C%s %s %s %s %s %s' % (f(50 + w * 0.56), f(t), f(50 + w), f(c), f(50 + w), f(m))
    if taper > 0:
        s += 'Q%s %s %s %s' % (f(50 + w), f(y0), f(50 + w - taper), f(y0))
    else:
        s += 'L%s %s' % (f(50 + w), f(y0))
    return s


CROP = (dome_d(2.6, 44, 2.6) + 'L73.8 44C74 35 71.5 29.5 67 27.6Q63.5 28.4 61 31.2Q58.5 28.6 55 28.4Q52 29 49.5 31.6'
        'Q47 28.8 43.5 28.4Q40.5 28.8 38.2 31Q36 28.4 33 27.8C28.5 29.5 26.2 35 26.2 44Z')
SHORT_TOP = (dome_d(2.4, 30, 0) + 'L74.6 30C72 24 64 21.6 50 21.4C36 21.6 28 24 25.4 30Z')  # a low cap of hair for hats


def default_hair(c):
    """Crop hair: a front layer only."""
    return dict(front=shape(CROP, c.hair, cls='hair') + arc(WHITE, 29, 15, 38, 26, 200, 56, 2.4, op=0.24),
                front_d=CROP)


# --- composition ---------------------------------------------------------------------------------------------------

def compose(c, hair=None, hat=None, top=None, eyes=None, glasses=None, earrings=None, extras=None, brows=True,
            mouth=None, wave=False, bald=False):
    """The whole character as SVG body (no <svg> wrapper). Each argument is a part fn(ctx) -> layers dict."""
    h = c.head
    H = (hair or default_hair)(c) if not bald else {}
    T = (top or tee_top)(c)
    W = hat(c) if hat else {}
    if W.get('hides_hair') == 'all':
        H = {}
    clip_y = W.get('hair_clip_y')
    out = []
    defs = []
    out.append(oval('#000000', 50, 115.75, 21, 3.25, op=0.16))
    # behind everything
    out.append(T.get('back', ''))
    if W.get('back'):
        out.append(g(W['back'], h.crown()))
    if H.get('back'):
        if clip_y is not None:
            # a hat that covers the crown also hides what sits on top of it (buns, top-knots, a pony's root)
            defs.append('<clipPath id="hairclipb"><rect x="-40" y="%s" width="180" height="200"/></clipPath>' % f(clip_y))
            out.append(g(g(H['back'], clip='hairclipb'), h.crown()))
        else:
            out.append(g(H['back'], h.crown()))
    # arms, legs, torso
    sleeve = T.get('sleeve', 0.34)
    scol = T.get('sleeve_color', c.cloth)
    scls = T.get('sleeve_cls', 'cloth')
    out.append(arm(False, scol, c.skin, sleeve, cloth_cls=scls))
    out.append(arm(True, scol, c.skin, sleeve, wave, cloth_cls=scls))
    out.append(T.get('legs_svg') or legs(T.get('legs', DENIM)))
    tor = T.get('torso', TORSO)
    base = T.get('base', c.cloth)
    bcls = T.get('base_cls', 'cloth')
    defs.append('<clipPath id="torso"><path d="%s"/></clipPath>' % tor)
    out.append(path(tor, base, cls=bcls))
    out.append(g(T.get('inside', '') + oval(WHITE, 41, 76, 7, 3.2, op=0.13), clip='torso'))
    out.append(path(tor, None, edge(base), 1.1, cls=bcls + '-edge'))
    out.append(T.get('over_torso', ''))
    out.append(T.get('behind_neck', ''))
    out.append(neck(c.skin))
    out.append(T.get('neck', ''))
    out.append(hand(False, c.skin))
    if not wave:
        out.append(hand(True, c.skin))
    out.append(T.get('front', ''))
    if H.get('hanging'):
        out.append(g(H['hanging'], h.crown()))
    if W.get('behind_head'):
        out.append(g(W['behind_head'], h.crown()))
    # ears and head
    se = edge(c.skin, 0.3)
    for s in (-1, 1):
        ex = h.cx + s * (h.hw - 0.8)
        out.append(dot(c.skin, ex, h.eyeY + 2.6, 4.4, se))
        out.append(arc(deep(c.skin, 0.16), ex - 2.2, h.eyeY + 0.2, 4.4, 5, 110 if s < 0 else -70, 140, 1.1))
    defs.append('<clipPath id="head"><path d="%s"/></clipPath>' % h.d())
    out.append(path(h.d(), c.skin, cls='skin'))
    shadow = ''
    if H.get('front_d'):
        shadow = g(path(H['front_d'], edge(c.skin, 0.2), op=0.45), 'translate(0 1.8)')
    out.append(g(g(shadow, h.crown()) + oval(WHITE, h.cx, h.eyeY - 12, h.hw * 0.6, 6, op=0.10), clip='head'))
    out.append(path(h.d(), None, se, 1.2))
    if earrings:
        out.append(earrings(c).get('face', ''))
    out.append((eyes(c).get('face', '') if eyes else default_eyes(c)))
    out.append(cheeks(c))
    if extras:
        out.append(extras(c).get('face', ''))
    if brows:
        bc = lerp(deep(c.hair, 0.3), c.ink, 0.15)
        out.append(default_brows(c, bc))
    out.append(nose(c))
    out.append(mouth(c) if mouth else default_mouth(c))
    if glasses:
        out.append(glasses(c).get('face', ''))
    if H.get('front'):
        if clip_y is not None:
            defs.append('<clipPath id="hairclip"><rect x="-20" y="%s" width="140" height="160"/></clipPath>' % f(clip_y))
            out.append(g(g(H['front'], clip='hairclip'), h.crown()))
        else:
            out.append(g(H['front'], h.crown()))
    if W.get('front'):
        out.append(g(W['front'], h.crown()))
    if wave:
        out.append(hand(True, c.skin, wave=True))
    return '<defs>%s</defs>%s' % (''.join(defs), ''.join(out))


def svg(body, box='0 0 100 120', w=None, h=None, bg=None):
    size = ''
    if w:
        size = ' width="%d" height="%d"' % (w, h)
    b = '<rect x="-50" y="-50" width="300" height="300" fill="%s"/>' % bg if bg else ''
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="%s"%s>%s%s</svg>' % (box, size, b, body)


def tee_top(c):
    cl = c.cloth
    return dict(sleeve=0.34, inside=rrect(deep(cl, 0.1), 30, 100.4, 40, 4, 0, cls='cloth-deep'),
                neck=arc(deep(cl, 0.15), 44, 67, 12, 7.4, 15, 150, 1.6, cls='cloth-deep'))
