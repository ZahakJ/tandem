"""Face parts: eye styles, eye colours, glasses, earrings and face extras (beards included).

Every face part is drawn in face coordinates: the eye centres are (c.head.cx +- c.head.dx, c.head.eyeY), the mouth at
c.head.mouthY, the nose at c.head.noseY; so each part follows the six head shapes without a crown transform.
"""
from base import *
import base


def X(ex, side):
    """x outward from an eye centre: X(v) is v units toward that eye's outer corner."""
    return lambda v: ex + side * v


def heart_d(x, y, s):
    return ('M%s %sC%s %s %s %s %s %sC%s %s %s %s %s %sZ' % (
        f(x), f(y + s * 0.95), f(x - s * 1.7), f(y - s * 0.1), f(x - s * 0.9), f(y - s * 1.45), f(x), f(y - s * 0.55),
        f(x + s * 0.9), f(y - s * 1.45), f(x + s * 1.7), f(y - s * 0.1), f(x), f(y + s * 0.95)))


def clipdef(ident, d):
    return '<defs><clipPath id="%s"><path d="%s"/></clipPath></defs>' % (ident, d)


SCLERA = '#FFFDF8'


def iris_disc(c, x, y, r, ry=None, pupil=0.48):
    ry = ry or r
    ink, ir = c.ink, c.iris
    return (oval(deep(ir, 0.18), x, y, r, ry, cls='iris-deep') + oval(light(ir, 0.2), x, y + ry * 0.34, r * 0.72, ry * 0.48, cls='iris-light') +
            oval(ink, x, y + 0.2, r * pupil, ry * pupil) + circle(WHITE, x + r * 0.34, y - ry * 0.34, r * 0.34) +
            circle(WHITE, x - r * 0.34, y + ry * 0.4, r * 0.15, op=0.85))


def sclera_eye(c, ident, d, ix, iy, r, lid_d, lid_w=1.5, line_w=0.9):
    """An eye with a white: its shape [d], the iris clipped inside, a fine outline and a heavier upper lid."""
    return (clipdef(ident, d) + path(d, SCLERA) + g(iris_disc(c, ix, iy, r, r * 1.08), clip=ident) +
            path(d, None, c.ink, line_w) + stroke(lid_d, c.ink, lid_w))


# --- eye styles ----------------------------------------------------------------------------------------------------

def both(fn):
    def draw(c):
        h = c.head
        return {'face': ''.join(fn(c, h.cx + s * h.dx, h.eyeY, s) for s in (-1, 1))}
    return draw


@item('eyes', 'sparkle_big', 'Big sparkle', colors=['iris'], layer='face',
      notes='Larger bright eye (rx 4.3, ry 5.1) with three catchlights and a tiny four-point twinkle; the lower iris band is lighter. '
            'Centred on (cx +- dx, eyeY). Port: base.bright() scaled, plus starAt(x+2.6, y-3.4, 1.3, 4 points).')
@both
def _sparkle(c, ex, y, s):
    rx, ry = 4.3, 5.1
    out = (oval(c.ink, ex, y, rx + 0.6, ry + 0.6) + oval(deep(c.iris, 0.2), ex, y, rx - 0.1, ry - 0.1, cls='iris-deep') +
           oval(light(c.iris, 0.3), ex, y + ry * 0.38, rx * 0.74, ry * 0.42, cls='iris-light') +
           oval(c.ink, ex, y + 0.3, rx * 0.42, ry * 0.44) +
           oval(WHITE, ex + 1.4, y - 1.9, 1.7, 1.9) + circle(WHITE, ex - 1.6, y + 2.2, 0.75, op=0.9) +
           circle(WHITE, ex + 1.9, y + 1.4, 0.45, op=0.8) +
           arc(c.ink, ex - rx - 0.6, y - ry - 0.6, rx * 2 + 1.2, ry * 2 + 1.2, 200, 140, 1.4))
    return out + star(ex + side_x(s, 4.6), y - 4.6, 1.5, WHITE, 4, 0.36)


def side_x(s, v):
    return s * v


@item('eyes', 'almond', 'Almond', colors=['iris'], layer='face',
      notes='An almond with a white (sclera #FFFDF8), the iris clipped inside, a heavier upper lid that runs past the outer corner. '
            'Half width 4.9, height ~7.4. Outer corner on the face side (mirror per side).')
@both
def _almond(c, ex, y, s):
    x = X(ex, s)
    d = 'M%s %sQ%s %s %s %sQ%s %s %s %sZ' % (f(x(-5.4)), f(y + 1.2), f(x(-0.8)), f(y - 7.6), f(x(5.4)), f(y - 0.4),
                                              f(x(0.8)), f(y + 6.6), f(x(-5.4)), f(y + 1.2))
    lid = 'M%s %sQ%s %s %s %sL%s %s' % (f(x(-5.4)), f(y + 1.2), f(x(-0.8)), f(y - 7.6), f(x(5.4)), f(y - 0.4), f(x(6.6)), f(y - 1.4))
    return sclera_eye(c, 'eyeAl%d' % (s + 1), d, ex + 0.1 * s, y + 0.2, 3.7, lid, 1.7)


@item('eyes', 'round_lash', 'Round with lashes', colors=['iris'], layer='face',
      notes='The bright round eye with three short curved lashes at the outer top. Lashes are ink, width 1.1.')
@both
def _round_lash(c, ex, y, s):
    x = X(ex, s)
    out = base.bright(ex, y, 3.6, 4.4, c.iris, c.ink)
    for th in (38, 62, 86):
        t = math.radians(th)
        px, py = ex + s * math.sin(t) * 4.3, y - math.cos(t) * 5.0
        nx, ny = s * math.sin(t), -math.cos(t)
        l = 2.3
        out += stroke('M%s %sQ%s %s %s %s' % (f(px), f(py), f(px + nx * l * 0.6), f(py + ny * l * 0.6 - 0.4), f(px + nx * l + s * 0.5), f(py + ny * l - 0.6)), c.ink, 1.0)
    return out


@item('eyes', 'downturned', 'Gentle downturned', colors=['iris'], layer='face',
      notes='A soft, kind eye: the upper lid slopes down toward the outer corner and clips the iris top. Iris clipped by the lid shape.')
@both
def _down(c, ex, y, s):
    x = X(ex, s)
    d = 'M%s %sQ%s %s %s %sQ%s %s %s %sQ%s %s %s %sZ' % (
        f(x(-3.9)), f(y - 1.6), f(x(-0.6)), f(y - 4.4), f(x(4.3)), f(y + 0.6),
        f(x(4.4)), f(y + 4.8), f(x(0)), f(y + 5), f(x(-4.4)), f(y + 4.6), f(x(-3.9)), f(y - 1.6))
    lid = 'M%s %sQ%s %s %s %s' % (f(x(-4.4)), f(y - 1.4), f(x(-0.6)), f(y - 4.8), f(x(5)), f(y + 1.4))
    body = oval(c.ink, ex, y + 0.8, 4, 4.4) + g(iris_disc(c, ex, y + 0.8, 3.45, 3.8), clip='eyeDn%d' % (s + 1))
    return clipdef('eyeDn%d' % (s + 1), d) + g(body, clip='eyeDn%d' % (s + 1)) + stroke(lid, c.ink, 1.7)


@item('eyes', 'upturned_cat', 'Cat-eye flick', colors=['iris'], layer='face',
      notes='An almond eye whose upper lid ends in a small upward wing at the outer corner (a drawn flick, not makeup colour). '
            'The wing is an ink triangle from (outer 4.6, y-1.2) to (outer 7.2, y-3.6).')
@both
def _cat(c, ex, y, s):
    x = X(ex, s)
    d = 'M%s %sQ%s %s %s %sQ%s %s %s %sZ' % (f(x(-4.6)), f(y + 0.4), f(x(-0.4)), f(y - 5.6), f(x(4.8)), f(y - 1.6),
                                              f(x(1.4)), f(y + 5.0), f(x(-4.6)), f(y + 0.4))
    lid = 'M%s %sQ%s %s %s %s' % (f(x(-4.6)), f(y + 0.4), f(x(-0.4)), f(y - 5.6), f(x(4.8)), f(y - 1.6))
    wing = 'M%s %sL%s %sL%s %sZ' % (f(x(3.6)), f(y - 2.6), f(x(7.4)), f(y - 4.2), f(x(4.6)), f(y - 0.6))
    return sclera_eye(c, 'eyeCt%d' % (s + 1), d, ex + 0.1 * s, y - 0.1, 3.2, lid, 1.7) + path(wing, c.ink)


@item('eyes', 'star_pupil', 'Star pupil', colors=['iris'], layer='face',
      notes='The bright eye with a white five-point star in place of the pupil (r 1.9) and one small catchlight.')
@both
def _star_p(c, ex, y, s):
    rx, ry = 3.8, 4.5
    return (oval(c.ink, ex, y, rx + 0.55, ry + 0.55) + oval(deep(c.iris, 0.15), ex, y, rx - 0.1, ry - 0.1, cls='iris-deep') +
            oval(light(c.iris, 0.22), ex, y + ry * 0.34, rx * 0.72, ry * 0.46, cls='iris-light') +
            star(ex, y + 0.2, 2.2, WHITE, 5, 0.46) + circle(WHITE, ex - 2, y + 2.6, 0.6, op=0.85) +
            arc(c.ink, ex - rx - 0.5, y - ry - 0.5, rx * 2 + 1, ry * 2 + 1, 200, 140, 1.3))


@item('eyes', 'heart_pupil', 'Heart pupil', colors=['iris'], layer='face',
      notes='The bright eye with a dark heart pupil and a white catchlight; reads as delighted. Heart: two cubics, size 1.7.')
@both
def _heart_p(c, ex, y, s):
    rx, ry = 3.8, 4.5
    return (oval(c.ink, ex, y, rx + 0.55, ry + 0.55) + oval(deep(c.iris, 0.15), ex, y, rx - 0.1, ry - 0.1, cls='iris-deep') +
            oval(light(c.iris, 0.22), ex, y + ry * 0.34, rx * 0.72, ry * 0.46, cls='iris-light') +
            path(heart_d(ex, y + 0.6, 1.9), c.ink) + circle(WHITE, ex + 1.5, y - 1.9, 1.1) + circle(WHITE, ex - 1.7, y + 2.5, 0.55, op=0.85) +
            arc(c.ink, ex - rx - 0.5, y - ry - 0.5, rx * 2 + 1, ry * 2 + 1, 200, 140, 1.3))


@item('eyes', 'button', 'Button', colors=['iris'], layer='face',
      notes='A simple round eye: one solid iris-coloured disc with a dark rim and one big catchlight, no pupil. Friendly, toy-like; '
            'the simplest new style and the clearest at 32 px.')
@both
def _button(c, ex, y, s):
    return (circle(c.ink, ex, y + 0.3, 3.5) + circle(deep(c.iris, 0.1), ex, y + 0.3, 2.6, cls='iris-deep') +
            circle(WHITE, ex + 1.0, y - 0.8, 1.15) + circle(WHITE, ex - 1.1, y + 1.5, 0.45, op=0.8))


@item('eyes', 'determined', 'Determined', colors=['iris'], layer='face',
      notes='The bright eye with a flat upper lid that slopes down toward the nose (inner corner lower): focused, sporty, not angry. '
            'Iris clipped under the lid line.')
@both
def _det(c, ex, y, s):
    x = X(ex, s)
    d = 'M%s %sL%s %sQ%s %s %s %sQ%s %s %s %sZ' % (
        f(x(-4.4)), f(y - 0.8), f(x(4.4)), f(y - 3.2), f(x(5)), f(y + 5.6), f(x(0)), f(y + 5.4), f(x(-5)), f(y + 5.2), f(x(-4.4)), f(y - 0.8))
    body = oval(c.ink, ex, y + 0.6, 4.1, 4.8) + iris_disc(c, ex, y + 0.9, 3.5, 4.1)
    lid = 'M%s %sL%s %s' % (f(x(-4.8)), f(y - 0.6), f(x(4.8)), f(y - 3.4))
    return clipdef('eyeDt%d' % (s + 1), d) + g(body, clip='eyeDt%d' % (s + 1)) + stroke(lid, c.ink, 1.8)


@item('eyes', 'lashes_closed', 'Peaceful closed', colors=[], layer='face',
      notes='Closed, content eyes: a downward lid curve with two small lashes at the outer end. No iris (eye colour unused). '
            'Arc box (ex-3.8, y-2.6, 7.6, 5), 20..160 degrees, ink 1.7.')
@both
def _closed(c, ex, y, s):
    x = X(ex, s)
    out = arc(c.ink, ex - 3.8, y - 2.4, 7.6, 5, 20, 140, 1.7)
    out += line(c.ink, x(3.2), y + 1.3, x(4.9), y + 2.6, 1.1) + line(c.ink, x(2.1), y + 2.0, x(3.1), y + 3.7, 1.1)
    return out


@item('eyes', 'monolid_soft', 'Soft monolid', colors=['iris'], layer='face',
      notes='A wide, gently narrow eye with a straight-ish upper lid and no crease line; the lid covers the top third of the iris. '
            'Respectful, everyday East Asian eye shape; not slanted, not a slit. Half width 4.8, open height ~5.')
@both
def _mono(c, ex, y, s):
    x = X(ex, s)
    d = 'M%s %sC%s %s %s %s %s %sC%s %s %s %s %s %sZ' % (
        f(x(-4.8)), f(y + 0.2), f(x(-3)), f(y - 2.6), f(x(2.6)), f(y - 3.0), f(x(4.8)), f(y - 0.8),
        f(x(3.6)), f(y + 4.4), f(x(-3.4)), f(y + 4.6), f(x(-4.8)), f(y + 0.2))
    body = path(d, SCLERA) + iris_disc(c, ex + 0.15 * s, y + 0.8, 3.1, 3.4, 0.5)
    lid = 'M%s %sC%s %s %s %s %s %s' % (f(x(-4.8)), f(y + 0.2), f(x(-3)), f(y - 2.6), f(x(2.6)), f(y - 3.0), f(x(5.2)), f(y - 0.9))
    return clipdef('eyeMn%d' % (s + 1), d) + g(body, clip='eyeMn%d' % (s + 1)) + path(d, None, c.ink, 0.8) + stroke(lid, c.ink, 1.8)


@item('eyes', 'wide_shine', 'Wide-eyed', colors=['iris'], layer='face',
      notes='Big round eyes with a white ring around the iris (surprised, curious): white disc r 4.4, iris r 3.1, ink outline 0.9 '
            'and a heavier top arc.')
@both
def _wide(c, ex, y, s):
    d = 'M%s %sA4.4 4.7 0 1 1 %s %sA4.4 4.7 0 1 1 %s %sZ' % (f(ex - 4.4), f(y), f(ex + 4.4), f(y), f(ex - 4.4), f(y))
    return (path(d, SCLERA) + iris_disc(c, ex, y + 0.4, 2.9, 3.1, 0.5) + path(d, None, c.ink, 0.9) +
            arc(c.ink, ex - 4.6, y - 4.9, 9.2, 9.8, 205, 130, 1.5))


# --- eye colours ---------------------------------------------------------------------------------------------------

IRIS_NEW = [
    ('iris_teal', 'Teal', '#2AA19C'), ('iris_rose', 'Rose', '#D2607F'), ('iris_ice', 'Ice blue', '#86C3EA'),
    ('iris_indigo', 'Indigo', '#3E48A6'), ('iris_ruby', 'Ruby', '#B8363F'), ('iris_gold', 'Gold', '#E2B93B'),
    ('iris_silver', 'Silver', '#B6BECB'), ('iris_leaf', 'Leaf green', '#84B63A'),
]


def _iris_item(ident, name, hexv):
    def fn(c):
        old = c.iris
        c.iris = hexv
        out = {'face': default_eyes(c)}
        c.iris = old
        return out
    item('iris', ident, '%s %s' % (name, hexv), colors=[], layer='face',
         notes='New eye colour %s (append to AvatarColors.eyes). Drawn with the default bright eye: iris.deep(0.18) ring, '
               'iris.light(0.18) lower band.' % hexv)(fn)


for _i in IRIS_NEW:
    _iris_item(*_i)


# --- glasses -------------------------------------------------------------------------------------------------------

def gl(fn):
    def draw(c):
        h = c.head
        return {'face': fn(c, h.cx, h.eyeY, h.dx)}
    return draw


@item('glasses', 'round_wire', 'Round wire', colors=[], layer='face, over the eyes and brows-free zone',
      notes='Thin gold wire rounds (r 5.2, stroke 0.9, #C9973A) with a faint lens tint and a keyhole bridge arc. Lighter than the '
            'existing dark rounds.')
@gl
def _round_wire(c, cx, y, dx):
    gold = '#C9973A'
    out = ''
    for s in (-1, 1):
        out += circle(WHITE, cx + s * dx, y, 5.2, op=0.16) + circle(gold, cx + s * dx, y, 5.2, st=True, w=0.9)
        out += arc(WHITE, cx + s * dx - 3.6, y - 3.6, 7.2, 7.2, 200, 50, 0.8, op=0.7)
    out += arc(gold, cx - dx + 5.1, y - 2.2, 2 * dx - 10.2, 3.2, 200, 140, 0.9)
    return out


@item('glasses', 'square_thick', 'Bold square', colors=['cloth?'], layer='face',
      notes='Thick rounded-square frames (stroke 2.2) in tortoiseshell #6B3F2A with a lighter flake; the frame colour could follow '
            'the outfit colour as an option. Lenses 12 x 9.4, corner 2.4.')
@gl
def _sq(c, cx, y, dx):
    fr = '#6B3F2A'
    out = ''
    for s in (-1, 1):
        x = cx + s * dx
        out += rrect(WHITE, x - 6, y - 4.6, 12, 9.4, 2.4, op=0.15)
        out += rrect(fr, x - 6, y - 4.6, 12, 9.4, 2.4, st=True, sw=2.2)
        out += line('#A8693F', x - 4.6, y - 4.6, x - 1.8, y - 4.6, 0.9)
    out += line(fr, cx - dx + 6, y - 1.6, cx + dx - 6, y - 1.6, 1.8)
    return out


@item('glasses', 'cat_eye', 'Cat-eye', colors=[], layer='face',
      notes='Frames with upswept outer top corners, cherry red #D9484F, stroke 1.4, faint lens tint; small gold dot at each wing.')
@gl
def _cat_g(c, cx, y, dx):
    fr = '#D9484F'
    out = ''
    for s in (-1, 1):
        ex = cx + s * dx
        x = X(ex, s)
        d = 'M%s %sQ%s %s %s %sQ%s %s %s %sQ%s %s %s %sQ%s %s %s %sZ' % (
            f(x(-5.2)), f(y - 2.6), f(x(0)), f(y - 4.6), f(x(6.8)), f(y - 5.4),
            f(x(6.4)), f(y + 1), f(x(3.6)), f(y + 4.2), f(x(-1)), f(y + 5.4), f(x(-5.0)), f(y + 3.4),
            f(x(-6)), f(y + 0), f(x(-5.2)), f(y - 2.6))
        out += path(d, WHITE, op=0.15) + path(d, None, fr, 1.5) + circle(GOLD, x(6.4), y - 4.6, 0.7)
    out += arc(fr, cx - dx + 5, y - 2.6, 2 * dx - 10, 3, 200, 140, 1.2)
    return out


@item('glasses', 'aviator', 'Aviator', colors=[], layer='face',
      notes='Teardrop lenses tinted warm brown (#5B4636 at 0.6) in a thin gold frame, a double bridge, one white glint per lens.')
@gl
def _aviator(c, cx, y, dx):
    gold = '#C9973A'
    out = ''
    for s in (-1, 1):
        ex = cx + s * dx
        x = X(ex, s)
        d = 'M%s %sQ%s %s %s %sQ%s %s %s %sQ%s %s %s %sZ' % (
            f(x(-5)), f(y - 3.6), f(x(1)), f(y - 4.6), f(x(5.8)), f(y - 3.2),
            f(x(6.2)), f(y + 4.4), f(x(0.6)), f(y + 5.4), f(x(-5.6)), f(y + 3.2), f(x(-5)), f(y - 3.6))
        out += path(d, '#5B4636', op=0.6) + path(d, None, gold, 1)
        out += line(WHITE, x(-2.8), y - 1.8, x(-0.4), y - 2.6, 1.1, op=0.6)
    out += line(gold, cx - dx + 5, y - 3.2, cx + dx - 5, y - 3.2, 0.9)
    out += arc(gold, cx - dx + 5.4, y - 2.4, 2 * dx - 10.8, 3, 200, 140, 0.9)
    return out


@item('glasses', 'half_rim', 'Browline', colors=[], layer='face',
      notes='Browline frames: a thick dark top bar (#2B2433, 2.2) over each lens, a thin silver wire (#B6BECB) for the lower rim.')
@gl
def _half(c, cx, y, dx):
    out = ''
    for s in (-1, 1):
        x = cx + s * dx
        out += rrect(WHITE, x - 5.8, y - 4, 11.6, 8.6, 3.4, op=0.14)
        out += stroke('M%s %sQ%s %s %s %sQ%s %s %s %s' % (f(x - 5.8), f(y - 1), f(x - 5.8), f(y + 4.6), f(x), f(y + 4.6),
                                                       f(x + 5.8), f(y + 4.6), f(x + 5.8), f(y - 1)), '#B6BECB', 0.8)
        out += stroke('M%s %sQ%s %s %s %sQ%s %s %s %s' % (f(x - 6), f(y - 1), f(x - 6), f(y - 4.2), f(x - 2), f(y - 4.2),
                                                       f(x + 6), f(y - 4.6), f(x + 6), f(y - 1)), '#2B2433', 2.2)
    out += line('#2B2433', cx - dx + 5.8, y - 3, cx + dx - 5.8, y - 3, 1.4)
    return out


@item('glasses', 'oval_tint', 'Tinted ovals', colors=[], layer='face',
      notes='Small oval lenses with a rose tint (#FF8FB8 at 0.45), thin gold rims; a bit retro. Lenses rx 5, ry 3.8, sit a touch low.')
@gl
def _oval(c, cx, y, dx):
    out = ''
    for s in (-1, 1):
        x = cx + s * dx
        out += oval('#FF8FB8', x, y + 0.6, 5, 3.9, op=0.45) + oval('#C9973A', x, y + 0.6, 5, 3.9, st=True, w=0.9)
        out += line(WHITE, x - 2.6, y - 0.8, x - 0.6, y - 1.6, 1, op=0.6)
    out += arc('#C9973A', cx - dx + 5, y - 1.6, 2 * dx - 10, 3, 200, 140, 0.9)
    return out


@item('glasses', 'flower_party', 'Daisy party', colors=[], layer='face',
      notes='Party frames: each lens ringed by eight white petals with a sunny yellow rim (#F2C14E); a clear lens in the middle. '
            'Petals: dots r 1.9 on a ring r 6.2.')
@gl
def _flower(c, cx, y, dx):
    out = ''
    for s in (-1, 1):
        x = cx + s * dx
        for i in range(8):
            a = math.radians(i * 45 + 22.5)
            out += dot('#FFFFFF', x + math.cos(a) * 6.1, y + math.sin(a) * 6.1, 2.0, '#D9C9E0', 0.8)
        out += circle(WHITE, x, y, 4.4, op=0.2) + circle('#F2C14E', x, y, 4.4, st=True, w=1.5)
    out += line('#F2C14E', cx - dx + 4.4, y - 1.2, cx + dx - 4.4, y - 1.2, 1.3)
    return out


@item('glasses', 'heart_shades', 'Heart shades', colors=[], layer='face',
      notes='Heart-shaped lenses in a raspberry tint (#E0457B at 0.85) with a darker rim and a glint; a fun pair. Heart size 4.1.')
@gl
def _hearts(c, cx, y, dx):
    out = ''
    for s in (-1, 1):
        x = cx + s * dx
        d = heart_d(x, y + 0.6, 4.2)
        out += path(d, '#E0457B', op=0.85) + path(d, None, '#A02858', 1.2)
        out += line(WHITE, x - 3.4, y - 2.4, x - 1.8, y - 3.2, 1.1, op=0.7)
    out += line('#A02858', cx - dx + 4.6, y - 1.4, cx + dx - 4.6, y - 1.4, 1.3)
    return out


@item('glasses', 'goggles', 'Adventure goggles', colors=['cloth'], layer='face (band runs to the head edge)',
      notes='Big rounded goggles, sky lenses (#8FD3F4 at 0.75), a chunky grey rim and a strap in the outfit colour reaching the '
            'sides of the head (x 25/75). The strap follows the head width (cx +- hw).')
@gl
def _goggles(c, cx, y, dx):
    h = c.head
    strap = c.cloth
    out = rrect(edge(strap), cx - h.hw - 0.4, y - 3.2, 2 * h.hw + 0.8, 6.4, 3.2) + rrect(strap, cx - h.hw, y - 2.5, 2 * h.hw, 5, 2.5, cls='cloth')
    for s in (-1, 1):
        x = cx + s * dx
        out += rrect('#6E6A7C', x - 7, y - 5.6, 14, 11.2, 5.2) + rrect('#8FD3F4', x - 5.4, y - 4.1, 10.8, 8.2, 4, op=0.85)
        out += line(WHITE, x - 3.2, y - 1.8, x - 0.6, y - 3, 1.3, op=0.75)
    out += rrect('#6E6A7C', cx - 3.4, y - 1.8, 6.8, 3.4, 1.6)
    return out


@item('glasses', 'monocle', 'Monocle', colors=[], layer='face',
      notes='One gold round lens over the right eye (the character\'s left on screen right), r 5.6, with a fine chain that loops '
            'down to the jaw. Asymmetric: draw on the +x eye only.')
@gl
def _monocle(c, cx, y, dx):
    gold = '#C9973A'
    x = cx + dx
    h = c.head
    out = circle(WHITE, x, y, 5.6, op=0.18) + circle(gold, x, y, 5.6, st=True, w=1.4)
    out += arc(WHITE, x - 3.8, y - 3.8, 7.6, 7.6, 200, 50, 0.9, op=0.75)
    out += stroke('M%s %sQ%s %s %s %s' % (f(x + 4.2), f(y + 3.8), f(x + 7), f(y + 12), f(h.cx + h.jawW - 1), f(h.jawY + 1)), gold, 0.6)
    return out


# --- earrings ------------------------------------------------------------------------------------------------------

def ear(fn):
    def draw(c):
        h = c.head
        return {'face': ''.join(fn(c, h.cx + s * (h.hw - 0.2), h.eyeY + 7, s) for s in (-1, 1))}
    return draw


@item('earrings', 'stud_pearl', 'Pearl studs', colors=[], layer='face (after the head, before eyes)',
      notes='A cream pearl (#F7F1E6, rim #CFC3B4) r 1.7 at the lobe (x = cx +- (hw-0.2), y = eyeY+7) with a white glint.')
@ear
def _pearl(c, x, y, s):
    return dot('#F7F1E6', x, y, 1.7, '#CFC3B4', 0.7) + circle(WHITE, x - 0.5, y - 0.6, 0.55)


@item('earrings', 'hoop_small', 'Huggie hoops', colors=[], layer='face',
      notes='A small gold hoop hugging the lobe: ring r 1.9 centred 1.6 below the lobe, stroke 1.2, gold #F2C14E with a dark rim.')
@ear
def _hoop_s(c, x, y, s):
    return circle(edge(GOLD), x, y + 1.6, 1.9, st=True, w=1.8) + circle(GOLD, x, y + 1.6, 1.9, st=True, w=1.0)


@item('earrings', 'hoop_big', 'Big hoops', colors=[], layer='face',
      notes='Large gold hoops: ring r 4.4 hanging from the lobe (centre y+4.2), stroke 1.3 with a darker rim stroke under it.')
@ear
def _hoop_b(c, x, y, s):
    return circle(edge(GOLD), x, y + 4.2, 4.4, st=True, w=2.0) + circle(GOLD, x, y + 4.2, 4.4, st=True, w=1.2)


@item('earrings', 'drop_gem', 'Gem drops', colors=['cloth'], layer='face',
      notes='A gold stud and a teardrop gem below it in the outfit colour (class cloth), with a white facet glint. Drop 5 units long.')
@ear
def _drop(c, x, y, s):
    gem = c.cloth
    d = 'M%s %sQ%s %s %s %sA2.2 2.2 0 1 1 %s %sQ%s %s %s %sZ' % (f(x), f(y + 1.6), f(x + 0.6), f(y + 3.4), f(x + 2.1), f(y + 5.2),
                                                             f(x - 2.1), f(y + 5.2), f(x - 0.6), f(y + 3.4), f(x), f(y + 1.6))
    return dot(GOLD, x, y, 1.0, edge(GOLD), 0.6) + shape(d, gem, cls='cloth', w=0.8) + circle(WHITE, x - 0.7, y + 5.4, 0.55, op=0.85)


@item('earrings', 'moon_dangle', 'Moon dangles', colors=[], layer='face',
      notes='A short chain and a golden crescent moon (#FFE27A, rim #D9A63A) with a tiny star beside it. Crescent r 2.6.')
@ear
def _moon(c, x, y, s):
    my = y + 4.6
    d = 'M%s %sA2.7 2.7 0 1 0 %s %sA2.0 2.0 0 1 1 %s %sZ' % (f(x + 0.6), f(my - 2.6), f(x + 0.6), f(my + 2.6), f(x + 0.6), f(my - 2.6))
    return (line('#D9A63A', x, y, x, my - 2.2, 0.6) + path(d, '#FFE27A') + path(d, None, '#D9A63A', 0.7))


@item('earrings', 'cuff', 'Ear cuff', colors=[], layer='face (on the outer ear rim)',
      notes='Two small silver bars clipped on the outer upper rim of each ear (ear centre cx +- (hw-0.8), eyeY+2.6, r 4.4), plus a '
            'silver stud on the lobe. Reads at 64 px as a glint on the ear.')
def _cuff(c):
    h = c.head
    out = ''
    sil = '#C6CCD8'
    for s in (-1, 1):
        ex, ey = h.cx + s * (h.hw - 0.8), h.eyeY + 2.6
        for a in (-50, -15):
            r = math.radians(a if s > 0 else 180 - a)
            x, y = ex + math.cos(r) * 4.6, ey + math.sin(r) * 4.6
            out += circle(edge(sil), x, y, 1.25) + circle(sil, x, y, 0.85)
        out += dot(sil, h.cx + s * (h.hw - 0.2), h.eyeY + 7, 0.9, edge(sil), 0.6)
    return {'face': out}



# --- face extras ---------------------------------------------------------------------------------------------------

@item('extra', 'freckles_band', 'Freckle band', colors=['skin'], layer='face (after cheeks, before brows)',
      notes='Freckles across both cheeks and over the bridge of the nose (11 dots, r 0.6, skin.edge(0.34)). Differs from the '
            'existing cheek freckles by crossing the nose.')
def _freckles(c):
    h = c.head
    col = edge(c.skin, 0.36)
    pts = [(-11.6, 5.4), (-9.4, 6.8), (-7.4, 5.2), (-5.4, 6.6), (-2.4, 4.6), (0, 5.6), (2.4, 4.6), (5.4, 6.6), (7.4, 5.2), (9.4, 6.8), (11.6, 5.4)]
    return {'face': ''.join(circle(col, h.cx + x, h.eyeY + y, 0.62, cls='skin-edge') for x, y in pts)}


@item('extra', 'beauty_mark', 'Beauty mark', colors=[], layer='face',
      notes='One small dark mark (r 0.75, #4A2E33) beside the mouth on the right (cx+7, mouthY-2).')
def _mark(c):
    h = c.head
    return {'face': circle('#4A2E33', h.cx + 7, h.mouthY - 2.2, 0.8)}


@item('extra', 'bandaid', 'Plaster', colors=[], layer='face',
      notes='A little plaster on the left cheek, tilted -20 degrees: beige strip 9 x 3.6 (#F3D3A6, rim #C9A579), a paler pad, '
            'four tiny holes. Centre (cx-dx-2, eyeY+7).')
def _bandaid(c):
    h = c.head
    x, y = h.cx - h.dx - 2, h.eyeY + 7.6
    s = (rrect('#C9A579', -4.9, -2.2, 9.8, 4.4, 2.2) + rrect('#F3D3A6', -4.4, -1.7, 8.8, 3.4, 1.7) +
         rrect('#FBE9CF', -1.6, -1.7, 3.2, 3.4, 0.6) + ''.join(circle('#C9A579', px, py, 0.3) for px in (-3.1, 3.1) for py in (-0.6, 0.6)))
    return {'face': g(s, 'translate(%s %s) rotate(-20)' % (f(x), f(y)))}


@item('extra', 'paint_star', 'Star face paint', colors=['cloth'], layer='face',
      notes='A painted five-point star on the right cheek in the outfit colour (class cloth), a white sparkle dot beside it. '
            'Star r 3, centre (cx+dx+3, eyeY+7.4).')
def _pstar(c):
    h = c.head
    x, y = h.cx + h.dx + 3, h.eyeY + 7.6
    return {'face': star(x, y, 3.1, c.cloth, 5, 0.5, edge(c.cloth), cls='cloth') + circle(WHITE, x + 3.4, y - 2.6, 0.7)}


@item('extra', 'paint_whiskers', 'Whiskers', colors=[], layer='face',
      notes='Face-paint whiskers: three short strokes fanning out on each cheek (ink-dark #4A3646, 0.9) and a pink nose dot '
            '(#FF8FAE) on the nose tip. Pairs with the cat / bunny / bear ears.')
def _whisk(c):
    h = c.head
    out = ''
    col = '#4A3646'
    for s in (-1, 1):
        bx = h.cx + s * (h.dx + 1.2)
        for dy, ang in ((5.0, -0.25), (6.8, 0), (8.6, 0.25)):
            out += line(col, bx, h.eyeY + dy, bx + s * 5.4, h.eyeY + dy + ang * 5.4 * 1.2 - 0.2, 0.9)
    out += oval('#FF8FAE', h.cx, h.noseY - 0.1, 1.7, 1.25)
    return {'face': out}


@item('extra', 'blush_hearts', 'Heart blush', colors=[], layer='face (replaces or sits over the blush ovals)',
      notes='A small pink heart on each cheek (#FF7FA2 at 0.75), size 1.8, where the blush sits (cx +- (dx+4.2), eyeY+6.4).')
def _bhearts(c):
    h = c.head
    return {'face': ''.join(path(heart_d(h.cx + s * (h.dx + 4.2), h.eyeY + 6.6, 1.9), '#FF7FA2', op=0.8) for s in (-1, 1))}


@item('extra', 'scar_tiny', 'Little scar', colors=['skin'], layer='face',
      notes='A gentle healed nick on the left cheekbone: a short pale-and-dark double line (skin.edge 0.3 and skin.light 0.3), '
            'with two tiny stitch marks. Kept small and soft.')
def _scar(c):
    h = c.head
    x, y = h.cx - h.dx - 4.6, h.eyeY + 2.4
    e = edge(c.skin, 0.32)
    return {'face': line(light(c.skin, 0.35), x - 2.4, y + 1.6, x + 2.4, y - 1.4, 1.5) + line(e, x - 2.4, y + 1.6, x + 2.4, y - 1.4, 0.7, cls='skin-edge') +
            line(e, x - 1.2, y - 0.4, x - 0.2, y + 1.6, 0.55) + line(e, x + 0.6, y - 1.4, x + 1.6, y + 0.6, 0.55)}


@item('extra', 'sweat_drop', 'Sweat drop', colors=[], layer='face (over the hair edge too; draw last)',
      notes='A cartoon sweat drop on the right temple (cx+hw-5, top+18): light blue #9FD8F5, rim #5AA8D6, white glint. Expressive, '
            'good for a "phew" mood.')
def _sweat(c):
    h = c.head
    x, y = h.cx + h.hw - 4.4, h.eyeY - 9
    d = 'M%s %sQ%s %s %s %sA2.4 2.4 0 1 1 %s %sQ%s %s %s %sZ' % (f(x), f(y - 4), f(x + 0.6), f(y - 2), f(x + 2.3), f(y + 0.4),
                                                             f(x - 2.3), f(y + 0.4), f(x - 0.6), f(y - 2), f(x), f(y - 4))
    return {'face': path(d, '#9FD8F5') + path(d, None, '#5AA8D6', 0.8) + circle(WHITE, x - 0.8, y + 0.6, 0.6)}


@item('extra', 'moustache', 'Moustache', colors=['hair'], layer='face (after nose, before mouth)',
      notes='A soft moustache in the hair colour (class hair) between the nose and the mouth: two rounded wings meeting at the '
            'centre with curled tips. Centre (cx, noseY+3.4), width 13.')
def _moust(c):
    h = c.head
    cx, y = h.cx, (h.noseY + h.mouthY) / 2 + 0.6
    right = ('M%s %sC%s %s %s %s %s %sC%s %s %s %s %s %sQ%s %s %s %sC%s %s %s %s %s %sZ' % (
        f(cx), f(y - 1.8), f(cx + 2.4), f(y - 3), f(cx + 5.6), f(y - 2.6), f(cx + 6.6), f(y + 0.2),
        f(cx + 7.6), f(y + 1.8), f(cx + 8.6), f(y + 0.4), f(cx + 8.4), f(y - 0.6),
        f(cx + 7.2), f(y + 3.4), f(cx + 4.2), f(y + 2.4),
        f(cx + 2.2), f(y + 1.8), f(cx + 0.8), f(y + 1.2), f(cx), f(y + 0.6)))
    left = ('M%s %sC%s %s %s %s %s %sC%s %s %s %s %s %sQ%s %s %s %sC%s %s %s %s %s %sZ' % (
        f(cx), f(y - 1.8), f(cx - 2.4), f(y - 3), f(cx - 5.6), f(y - 2.6), f(cx - 6.6), f(y + 0.2),
        f(cx - 7.6), f(y + 1.8), f(cx - 8.6), f(y + 0.4), f(cx - 8.4), f(y - 0.6),
        f(cx - 7.2), f(y + 3.4), f(cx - 4.2), f(y + 2.4),
        f(cx - 2.2), f(y + 1.8), f(cx - 0.8), f(y + 1.2), f(cx), f(y + 0.6)))
    col = c.hair
    return {'face': shape(left, col, cls='hair', w=0.9) + shape(right, col, cls='hair', w=0.9)}


def _beard(c, ident, inner_y, chin_drop, tone, with_moustache):
    """A beard: the face below a curve that runs from the ears around the mouth, clipped to the head, plus a little
    below the chin. The mouth keeps a skin-coloured opening so every mouth style still reads."""
    h = c.head
    cx, my = h.cx, h.mouthY
    col = lerp(c.hair, c.skin, tone)
    e = edge(col)
    ey = h.eyeY + inner_y
    region = ('M0 %sL%s %sC%s %s %s %s %s %sQ%s %s %s %sC%s %s %s %s %s %sL100 %sL100 130L0 130Z' % (
        f(ey), f(cx - h.hw + 2.2), f(ey), f(cx - h.hw + 3.4), f(my - 2), f(cx - 9), f(my - 3.4), f(cx - 4), f(my - 3.2),
        f(cx), f(my - 4.2), f(cx + 4), f(my - 3.2), f(cx + 9), f(my - 3.4), f(cx + h.hw - 3.4), f(my - 2), f(cx + h.hw - 2.2), f(ey), f(ey)))
    cid = 'beard_' + ident
    chin = ('M%s %sQ%s %s %s %sQ%s %s %s %sZ' % (f(cx - h.chinW - 6), f(h.chin - 3), f(cx - h.chinW - 2), f(h.chin + chin_drop),
                                               f(cx), f(h.chin + chin_drop + 0.8), f(cx + h.chinW + 2), f(h.chin + chin_drop),
                                               f(cx + h.chinW + 6), f(h.chin - 3)))
    mouth_hole = oval(lerp(c.skin, col, 0.25), cx, my + 1.2, 5.6, 2.8)
    body = ('<defs><clipPath id="%s"><path d="%s"/></clipPath></defs>' % (cid, h.d()) +
            (shape(chin, col, e, 1.0, cls='hair') if chin_drop > 0 else '') +
            g(path(region, col, cls='hair') + path(region, None, e, 1.1) + mouth_hole, clip=cid))
    tex = deep(col, 0.18)
    for dx_, dy_ in ((-12, 2), (-9, 7), (-4, 9.5), (4, 9.5), (9, 7), (12, 2)):
        body += arc(tex, cx + dx_ - 1, my + dy_ - 1, 2, 2, 200, 140, 0.6)
    if with_moustache:
        body += _moust(type('C', (), {'head': h, 'hair': col})())['face']
    return {'face': body}


@item('extra', 'beard_short', 'Short beard', colors=['hair'], layer='face (after cheeks, before nose and mouth)',
      notes='A short, close beard on the jaw and chin in a softened hair colour (hair lerp skin 0.22, class hair), clipped to the '
            'head path, leaving a skin-tone opening round the mouth so every mouth still reads. With a short moustache.')
def _bshort(c):
    return _beard(c, 'short', 5.5, 0, 0.22, True)


@item('extra', 'beard_full', 'Full beard', colors=['hair'], layer='face (after cheeks, before nose and mouth)',
      notes='A full, rounded beard in the hair colour from the sideburns down, spilling 4.5 units below the chin (a soft U shape '
            'drawn over the neck), mouth opening kept, moustache included, curl marks for texture.')
def _bfull(c):
    return _beard(c, 'full', 2.0, 5, 0.0, True)


@item('extra', 'goatee', 'Goatee', colors=['hair'], layer='face',
      notes='A small rounded goatee under the lower lip in the hair colour (class hair) with a short moustache.')
def _goatee(c):
    h = c.head
    cx, my = h.cx, h.mouthY
    d = 'M%s %sQ%s %s %s %sQ%s %s %s %sQ%s %s %s %sZ' % (f(cx - 4), f(my + 3.4), f(cx - 4.4), f(h.chin + 2), f(cx), f(h.chin + 2.4),
                                                      f(cx + 4.4), f(h.chin + 2), f(cx + 4), f(my + 3.4), f(cx), f(my + 2), f(cx - 4), f(my + 3.4))
    return {'face': shape(d, c.hair, cls='hair', w=0.9) + _moust(c)['face']}
