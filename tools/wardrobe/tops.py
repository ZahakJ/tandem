"""Tops and outfits for Tandem characters (the 'top' category). See base.compose for the layer keys."""
import math
from base import *
import base as B

# --- helpers -------------------------------------------------------------------------------------------------------

def accent(c, step=6):
    """A second colour that follows the user's choice: the palette entry `step` places on."""
    i = TOP.index(c.cloth) if c.cloth in TOP else 5
    return TOP[(i + step) % len(TOP)]


def hem(c, y=100.4, col=None, cls='cloth-deep'):
    return rrect(col or deep(c.cloth, 0.12), 28, y, 44, 4, 0, cls=cls)


def crew(col, w=1.6, cls=None):
    return arc(col, 44, 67, 12, 7.4, 15, 150, w, cls=cls)


def btn(x, y, r=1.0, col='#F7F3EA', e=None):
    return dot(col, x, y, r, e or edge(col, 0.4), 0.6)


def pts(*p):
    """'M x y L x y ... Z' from a flat list."""
    xy = ['%s %s' % (f(p[i]), f(p[i + 1])) for i in range(0, len(p), 2)]
    return 'M' + 'L'.join(xy) + 'Z'


def boots(leg, boot, top=104.6, leg_cls=None, boot_cls=None):
    out = ''
    for side in (-1, 1):
        x = 50 + side * 4.6
        out += rrect(edge(leg, 0.3), x - 4.1, 99, 8.2, 13.6, 3) + rrect(leg, x - 3.1, 99, 6.2, 12.8, 2.4, cls=leg_cls)
        be = edge(boot, 0.3)
        out += rrect(be, x - 4.6, top, 9.2, 10, 3) + rrect(boot, x - 3.8, top + 0.7, 7.6, 8.6, 2.6, cls=boot_cls)
        out += oval(be, x + side * 0.9, 113.4, 6, 3.9) + oval(boot, x + side * 0.9, 113.4, 5.2, 3.1, cls=boot_cls)
        out += oval(WHITE, x + side * 0.2, 112, 2.4, 0.9, op=0.22)
    return out


def bare_legs(c, sock=None, shoe=SHOE):
    out = legs(c.skin, shoe)
    if sock:
        for side in (-1, 1):
            x = 50 + side * 4.6
            out += rrect(sock, x - 3.1, 105.5, 6.2, 5.6, 1.2)
        # put the shoes back on top
        for side in (-1, 1):
            x = 50 + side * 4.6
            out += oval(shoe, x + side * 0.9, 113.4, 5.2, 3.3) + oval(WHITE, x + side * 0.2, 112.2, 2.4, 1, op=0.22)
    return out


def arm_stripe(front, col, off=2.4, t0=0.3, t1=0.8, w=1.1):
    """A thin stripe down the outer side of a long sleeve (only the part that shows beside the torso)."""
    sx, sy, qx, qy, wx, wy = B.arm_d(front)
    s = 1 if front else -1
    sx, qx, wx = sx + s * off, qx + s * off, wx + s * off
    def at(t):
        return ((1 - t) ** 2 * sx + 2 * (1 - t) * t * qx + t * t * wx, (1 - t) ** 2 * sy + 2 * (1 - t) * t * qy + t * t * wy)
    def d(t):
        return (2 * (1 - t) * (qx - sx) + 2 * t * (wx - qx), 2 * (1 - t) * (qy - sy) + 2 * t * (wy - qy))
    (x0, y0), (x1, y1) = at(t0), at(t1)
    (dx0, dy0), (dx1, dy1) = d(t0), d(t1)
    # the control point of the sub-curve: where the two tangents meet (the curve is a parabola)
    den = dx0 * dy1 - dy0 * dx1
    k = ((x1 - x0) * dy1 - (y1 - y0) * dx1) / den
    cx, cy = x0 + dx0 * k, y0 + dy0 * k
    return stroke('M%s %sQ%s %s %s %s' % (f(x0), f(y0), f(cx), f(cy), f(x1), f(y1)), col, w)


def cuffs(col, cls=None):
    """Sleeve cuffs just above the hands (long sleeves)."""
    out = ''
    for front in (False, True):
        _, _, _, _, x, y = B.arm_d(front)
        out += rrect(edge(col), x - 4.4, y - 5.6, 8.8, 3.4, 1.6) + rrect(col, x - 3.8, y - 5.1, 7.6, 2.4, 1.2, cls=cls)
    return out


COAT = ('M41 70.5C36 71 33.2 73.4 32.8 78C32.4 88 32.2 99 31.8 106.4Q31.6 109.2 34.6 109.2L65.4 109.2Q68.4 109.2 68.2 106.4'
        'C67.8 99 67.6 88 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')
# A gentle A-line to the ankle (the flare follows the Atelier klein concept: it is what tells a robe from a long tee).
ROBE = ('M41 70.5C36 71 33.2 73.4 32.8 78C32 88 30.6 100 29.6 108.8Q29.4 111.4 32.6 111.4L67.4 111.4Q70.6 111.4 70.4 108.8'
        'C69.4 100 68 88 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')
PUFFER = ('M40 70C34.5 70.5 31.2 73.4 30.8 78.6C30.2 87 31 96 32.2 101.4Q32.8 104.6 36.4 104.6L63.6 104.6Q67.2 104.6 67.8 101.4'
          'C69 96 69.8 87 69.2 78.6C68.8 73.4 65.5 70.5 60 70Q50 68.9 40 70Z')
SUNDRESS = ('M34.6 77.6Q50 81.4 65.4 77.6C66.6 81.4 66.8 85.4 65.4 89.5C68.6 95 72.4 101 73 104.6Q73 107.6 69.4 107.6L30.6 107.6'
            'Q27 107.6 27 104.6C27.6 101 31.4 95 34.6 89.5C33.2 85.4 33.4 81.4 34.6 77.6Z')
PARTY = ('M41 70.5C36 71 33.2 73.4 32.8 78C32.6 83 34 86.6 35 88.6C30 94 25 100 24 104.4Q23.6 108 27.6 108L72.4 108'
         'Q76.4 108 76 104.4C75 100 70 94 65 88.6C66 86.6 67.4 83 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')
BELL = ('M41 70.5C36 71 33.2 73.4 32.8 78C32.6 84 31.6 90 29.6 96C27.8 101 26.4 105 26.6 108Q27 110.6 30.4 110.6'
        'L69.6 110.6Q73 110.6 73.4 108C73.6 105 72.2 101 70.4 96C68.4 90 67.4 84 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')
TUNIC = ('M41 70.5C36 71 33.2 73.4 32.8 78C32.5 86 33 95 33.4 101.6L36 106.4L41.6 103.4L46 107.6L50 104L54 107.6L58.4 103.4'
         'L64 106.4L66.6 101.6C67 95 67.5 86 67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z')

TROUSERS = '#4A4A57'
CREAM = '#F2E8D5'
DENIM_L = '#6F8BC2'
MAIL = '#AEB4C4'
LEATHER = '#8A5A3B'
SUITW = '#F4F5F8'


def collar_points(col, y=70.2, cls=None):
    """Two shirt collar points either side of the neck."""
    e = edge(col, 0.3)
    l = pts(43.4, y, 48.6, y + 3.6, 44.4, y + 6.4, 40.6, y + 1.6)
    r = pts(56.6, y, 51.4, y + 3.6, 55.6, y + 6.4, 59.4, y + 1.6)
    return path(l, col, e, 0.9, cls=cls) + path(r, col, e, 0.9, cls=cls)


def v_panel(col, bottom=84, w=6.4, cls=None):
    """A V of shirt (or skin) showing at the neck of a jacket or vest."""
    return path(pts(50 - w, 69.6, 50 + w, 69.6, 50, bottom), col, cls=cls)


def tie(col, y0=73.4, y1=92):
    e = edge(col)
    knot = pts(48.2, y0 - 0.4, 51.8, y0 - 0.4, 51, y0 + 2.6, 49, y0 + 2.6)
    blade = pts(49, y0 + 2.4, 51, y0 + 2.4, 52.8, y1 - 2.6, 50, y1, 47.2, y1 - 2.6)
    return path(blade, col, e, 0.9) + path(knot, deep(col, 0.08), e, 0.9)


def lapels(col, bottom=86, cls=None):
    e = edge(col)
    l = pts(43, 70.2, 48.6, 79, 46.8, bottom, 44.6, 80, 39.2, 72.4)
    r = pts(57, 70.2, 51.4, 79, 53.2, bottom, 55.4, 80, 60.8, 72.4)
    return path(l, col, e, 0.9, cls=cls) + path(r, col, e, 0.9, cls=cls)


def hood_rim(col, cls='cloth'):
    return oval(edge(col), 50, 71.6, 12.6, 5.4, cls=cls + '-edge') + oval(deep(col, 0.12), 50, 71.6, 11.6, 4.5, cls=cls + '-deep')


# --- the collection --------------------------------------------------------------------------------------------------

@item('top', 'tee', 'T-shirt', colors=['cloth (body, sleeves)', 'cloth-deep (hem, neckline)', 'cloth-light (star)'],
      layer='torso + short sleeves',
      notes='The current tee kept as the baseline. Default torso; sleeve 0.34; star r3.2 at (55.5,85); hem band y100.4..104.4.')
def tee(c):
    cl = c.cloth
    return dict(sleeve=0.34, inside=hem(c) + star(55.5, 85, 3.2, light(cl, 0.55), cls='cloth-light'),
                neck=crew(deep(cl, 0.15), cls='cloth-deep'))


@item('top', 'hoodie', 'Hoodie', colors=['cloth (body, sleeves)', 'cloth-deep (pocket, hood rim, hem)', 'fixed white cords'],
      layer='torso + long sleeves; hood rim behind the neck',
      notes='As today: kangaroo pocket (40..60, 91..101 r4.5), hood rim ellipse (50,71.6, 12.6x5.4) drawn behind the neck, '
            'white cords x46/54 from y76 to 84.4. Pairs with hat "hood" for hood-up.')
def hoodie(c):
    cl = c.cloth
    ins = (rrect(deep(cl, 0.1), 40, 91, 20, 10, 4.5, cls='cloth-deep') + line(edge(cl, 0.25), 40.5, 92, 59.5, 92, 0.8) + hem(c, 101))
    nk = (line(WHITE, 46.6, 76, 46, 84, 0.9, op=0.9) + line(WHITE, 53.4, 76, 54, 84, 0.9, op=0.9) +
          circle(WHITE, 46, 84.4, 0.9) + circle(WHITE, 54, 84.4, 0.9))
    return dict(sleeve=1, inside=ins, behind_neck=hood_rim(cl), over_torso='', neck='', front=nk)


@item('top', 'sweater', 'Knit sweater', colors=['cloth (body, sleeves)', 'cloth-deep (ribbing)', 'cloth-light (knit band)'],
      layer='torso + long sleeves',
      notes='Crew-neck knit: ribbed hem y99.6..104 with ribs every 2.6; a band of small light diamonds across the chest at y84; '
            'thick ribbed collar arc (w2.6). Cuffs over the wrists.')
def sweater(c):
    cl = c.cloth
    ins = rrect(deep(cl, 0.14), 28, 99.6, 44, 5, 0, cls='cloth-deep')
    ins += ''.join(line(edge(cl, 0.2), 35 + i * 2.6, 100.4, 35 + i * 2.6, 103.4, 0.6) for i in range(12))
    ins += rrect(light(cl, 0.28), 28, 81.2, 44, 6, 0, cls='cloth-light')
    ins += ''.join(path(pts(x, 82.2, x + 2.2, 84.2, x, 86.2, x - 2.2, 84.2), deep(cl, 0.08), cls='cloth-deep') for x in range(36, 66, 5))
    return dict(sleeve=1, inside=ins, neck=crew(deep(cl, 0.16), 2.8, cls='cloth-deep'), over_torso=cuffs(deep(cl, 0.14), 'cloth-deep'))


@item('top', 'cardigan', 'Cardigan', colors=['cloth (cardigan, sleeves)', 'fixed white tee', 'cloth-light (buttons)'],
      layer='torso + long sleeves',
      notes='Open V to y86 showing a white tee; placket edges x47.6/52.4, four buttons on the wearer-right edge (x53.6, '
            'y80..98), patch pockets at (36..43, 90..96) and (57..64, 90..96).')
def cardigan(c):
    cl = c.cloth
    ins = path(pts(45, 69, 55, 69, 52.6, 104, 47.4, 104), SHIRT)
    ins += path('M45 69L50 86L55 69', None, edge(SHIRT, 0.15), 1.4)
    ins += line(edge(cl), 47.6, 86, 47.4, 104, 0.9) + line(edge(cl), 52.4, 86, 52.6, 104, 0.9)
    ins += path(pts(44, 69, 50, 86.4, 47.4, 104, 30, 104, 30, 69), cl, cls='cloth') + path(pts(56, 69, 50, 86.4, 52.6, 104, 70, 104, 70, 69), cl, cls='cloth')
    ins += line(edge(cl), 44, 69, 50, 86.4, 1) + line(edge(cl), 56, 69, 50, 86.4, 1) + line(edge(cl), 50, 86.4, 50, 104, 1)
    ins += ''.join(btn(52.4, y, 1, light(cl, 0.6)) for y in (89, 94, 99))
    ins += rrect(deep(cl, 0.08), 36, 90, 7, 6, 1.4, cls='cloth-deep') + rrect(deep(cl, 0.08), 57, 90, 7, 6, 1.4, cls='cloth-deep')
    ins += hem(c, 101)
    return dict(sleeve=1, inside=ins, neck=crew(deep(SHIRT, 0.12), 1.4), over_torso=cuffs(deep(cl, 0.12), 'cloth-deep'))


@item('top', 'stripe_tee', 'Striped tee', colors=['cloth (body, sleeves)', 'cloth-light (stripes)'],
      layer='torso + short sleeves',
      notes='As today: four light stripes 2.8 tall every 6.4 from y79; striped crew neck.')
def stripe_tee(c):
    cl = c.cloth
    ins = ''.join(rrect(light(cl, 0.6), 28, 79 + i * 6.4, 44, 2.8, 0, cls='cloth-light') for i in range(4)) + hem(c)
    return dict(sleeve=0.34, inside=ins, neck=crew(light(cl, 0.6), cls='cloth-light'))


@item('top', 'polo', 'Polo shirt', colors=['cloth (body, sleeves)', 'cloth-light (collar)', 'fixed white buttons'],
      layer='torso + short sleeves; collar over the neck',
      notes='Short placket 48.6..51.4 y70..80 with two buttons; pointed collar (collar_points) in cloth-light; ribbed hem.')
def polo(c):
    cl = c.cloth
    ins = rrect(deep(cl, 0.08), 48.4, 69, 3.2, 11.4, 1, cls='cloth-deep') + btn(50, 75, 0.8) + btn(50, 78.4, 0.8) + hem(c)
    ins += rrect(light(cl, 0.5), 28, 98.2, 44, 1.2, 0, cls='cloth-light')
    return dict(sleeve=0.34, inside=ins, neck=collar_points(light(cl, 0.45), cls='cloth-light'))


@item('top', 'shirt_tie', 'Shirt and tie', colors=['cloth (tie)', 'fixed white shirt', 'fixed charcoal trousers'],
      layer='torso + long sleeves; collar and tie over the neck',
      notes='White long-sleeve shirt (#F7F3EA), button line x50, chest pocket (55..61, 79..84); tie in the user colour from '
            'y73 to 92 over the neck layer; charcoal trousers.')
def shirt_tie(c):
    ins = line(edge(SHIRT, 0.15), 50, 72, 50, 104, 0.8) + rrect(deep(SHIRT, 0.06), 55, 79, 6, 5, 1, ) + line(edge(SHIRT, 0.2), 55, 80.4, 61, 80.4, 0.6)
    ins += rrect(deep(SHIRT, 0.08), 28, 100.4, 44, 4, 0)
    return dict(sleeve=1, sleeve_color=SHIRT, sleeve_cls='fixed', base=SHIRT, base_cls='fixed', legs=TROUSERS, inside=ins,
                neck=collar_points(SHIRT) + tie(c.cloth).replace('<path', '<path class="cloth"', 1),
                over_torso=cuffs(deep(SHIRT, 0.06)))


@item('top', 'blazer', 'Blazer', colors=['cloth (jacket, sleeves, lapels lighter)', 'fixed white shirt', 'accent (pocket square)'],
      layer='torso + long sleeves; lapels over the torso',
      notes='Open jacket over a white shirt V (to y86); lapels light(cloth,0.15); two buttons at x50,(91,97); hip pocket '
            'lines; pocket square in accent at (38..42, 79..81).')
def blazer(c):
    cl = c.cloth
    ins = v_panel(SHIRT, 90, 6.6) + line(edge(cl), 50, 90, 50, 104, 1)
    ins += btn(50, 92, 1.1, deep(cl, 0.3)) + btn(50, 97.4, 1.1, deep(cl, 0.3))
    ins += line(edge(cl), 37, 95, 44, 95, 0.9) + line(edge(cl), 56, 95, 63, 95, 0.9) + line(edge(cl), 37, 81, 43, 81, 0.9)
    ins += path(pts(38, 81, 39.6, 78.4, 41, 80, 42.4, 78.6, 43, 81), accent(c, 4))
    return dict(sleeve=1, inside=ins, over_torso=lapels(light(cl, 0.12), 88, 'cloth-light'), neck=collar_points(SHIRT, 70.6))


@item('top', 'wrap_jacket', 'Wrap jacket (kimono-style)', colors=['cloth (jacket, sleeve panels)', 'accent (collar band, sash)'],
      layer='back (wide sleeve panels) + torso + long sleeves; sash over the torso',
      notes='Generic wrap jacket, no costume details. Wide square sleeve panels in back (x24..36 / 64..76, y73..95, r3) '
            'behind the arms; the outer collar band runs top-right to bottom-left ("y" shape, wearer\'s left over right): '
            'from (56,69.6) to (41,104); a plain sash y87..92 in accent. Id formerly kimono_jacket.')
def wrap_jacket(c):
    cl, ac = c.cloth, accent(c, 3)
    bk = ''
    for x in (24.6, 64.4):
        sl = 'M%s 76Q%s 75 %s 76L%s 98Q%s 101 %s 101Q%s 101 %s 98Z' % (f(x + 1), f(x + 5.5), f(x + 10), f(x + 11), f(x + 11), f(x + 6), f(x), f(x))
        bk += path(sl, deep(cl, 0.08), edge(cl), 1.1, cls='cloth-deep')
    ins = path('M45 69.2L51.6 79.4', None, edge(ac), 3.8) + path('M45 69.2L51.6 79.4', None, ac, 2.4)
    ins += path('M56.2 69.2L43.4 88', None, edge(ac), 3.8) + path('M56.2 69.2L43.4 88', None, ac, 2.4)
    ins += line(edge(cl), 43.6, 91, 42.6, 104, 0.9)
    ins += rrect(edge(ac), 28, 86.6, 44, 5.8, 0) + rrect(ac, 28, 87.3, 44, 4.4, 0)
    return dict(sleeve=1, back=bk, inside=ins, legs=TROUSERS)


@item('top', 'hanbok', 'Hanbok-inspired jacket and skirt', colors=['cloth (short jacket, sleeves)', 'accent (full skirt)',
                                                                   'accent-deep (ribbon)', 'fixed white collar strip'],
      layer='torso = bell skirt to y110.6; jacket drawn inside the top; ribbon over the torso',
      notes='Dignified everyday-hanbok silhouette: a short jacket (to y81..84, curved hem) over a high, full skirt '
            '(BELL torso, flare 26.6..73.4). White collar strip in the crossing "y" shape, a bow with two long ribbons on '
            'the wearer\'s right chest (screen x54..60, y79..96). Bare legs hidden; shoes peek out. Long sleeves in cloth.')
def hanbok(c):
    cl, sk = c.cloth, accent(c, 7)
    rib = deep(sk, 0.22)
    ins = path('M26 66L74 66L74 82Q62 86.6 50 84.6Q38 86.6 26 82Z', cl, cls='cloth')
    ins += path('M26 82Q38 86.6 50 84.6Q62 86.6 74 82', None, edge(cl), 1)
    ins += path('M45 69.4L52.6 78', None, '#FBF8F2', 2.4) + path('M56.4 69.4L44 83.6', None, '#FBF8F2', 2.6)
    ins += path('M56.4 69.4L44 83.6', None, edge(cl, 0.25), 0.5)
    ins += line(edge(sk, 0.25), 40, 90, 38.6, 108, 0.6) + line(edge(sk, 0.25), 50, 90, 50, 110, 0.6) + line(edge(sk, 0.25), 60, 90, 61.4, 108, 0.6)
    ot = path('M54.4 80.4Q49 77 49.6 81Q50.4 84 54.4 80.4Z', rib, edge(rib), 0.8)
    ot += path('M54.4 80.4C55 86 54.2 91 53 97L55.4 97C56.4 91 56.6 86 55.4 80.6Z', rib, edge(rib), 0.8)
    ot += path('M55 80.6C57.6 86 59 90 60.6 94L62.6 93C61 89 59 85 56 80.2Z', rib, edge(rib), 0.8)
    ot += dot(rib, 55, 80.6, 1.4, edge(rib), 0.7)
    return dict(sleeve=1, torso=BELL, base=sk, base_cls='accent', inside=ins, over_torso=ot,
                legs_svg=legs(c.skin))


@item('top', 'sundress', 'Sundress', colors=['cloth (dress, straps)', 'cloth-light (flowers)'],
      layer='DRESS torso, no sleeves; skin shows above the neckline',
      notes='Sleeveless (sleeve 0): skin fills the torso above a gentle neckline (y76..78) with two straps x41/59; '
            'little light five-dot flowers scattered on the skirt; bare legs.')
def sundress(c):
    cl = c.cloth
    ins = ''
    for x, y in ((40, 92), (50, 99), (60, 92), (45, 104), (56, 105), (44, 86)):
        ins += ''.join(circle(light(cl, 0.6), x + 1.3 * math.cos(a * 1.2566), y + 1.3 * math.sin(a * 1.2566), 0.9) for a in range(5))
        ins += circle(accent(c, 2), x, y, 0.6)
    ins += rrect(deep(cl, 0.12), 28, 87.4, 44, 2, 0, cls='cloth-deep')
    sk = c.skin
    bk = path('M41 70.5C36 71 33.2 73.4 32.8 78L33.4 84L66.6 84L67.2 78C66.8 73.4 64 71 59 70.5Q50 69.6 41 70.5Z', sk, edge(sk, 0.3), 1.1, cls='skin')
    straps = (line(edge(cl), 40.8, 70.6, 40.4, 79, 3.4) + line(cl, 40.8, 70.6, 40.4, 79, 2.2) +
              line(edge(cl), 59.2, 70.6, 59.6, 79, 3.4) + line(cl, 59.2, 70.6, 59.6, 79, 2.2))
    return dict(sleeve=0, torso=SUNDRESS, inside=ins, legs=c.skin, back=bk, neck=straps)


@item('top', 'party_dress', 'Party dress', colors=['cloth (dress, puffs)', 'accent (sash, bow)', 'cloth-light (tiers)'],
      layer='full PARTY torso (flare 24..76 to y108); puff sleeves over the shoulders',
      notes='A wide twirly skirt with two light tier lines, an accent sash y86..90.4 with a bow at centre, round puff '
            'sleeves (ovals at 35/65, 75.6, 5x4) over the shoulders; bare legs.')
def party_dress(c):
    cl, ac = c.cloth, accent(c, 4)
    ins = path('M20 97Q50 101 80 97', None, light(cl, 0.45), 1.6) + path('M18 103.4Q50 107.4 82 103.4', None, light(cl, 0.45), 1.6)
    ins += rrect(ac, 28, 86, 44, 4.4, 0)
    ot = ''.join(oval(edge(cl), x, 75.8, 5.6, 4.6) + oval(cl, x, 75.8, 4.8, 3.8, cls='cloth') + oval(WHITE, x - 1, 74.4, 2, 1, op=0.25) for x in (35, 65))
    ot += path('M50 88.2C46 84.4 42.4 85 43 88.2C42.4 91.4 46 92 50 88.2Z', ac, edge(ac), 0.9)
    ot += path('M50 88.2C54 84.4 57.6 85 57 88.2C57.6 91.4 54 92 50 88.2Z', ac, edge(ac), 0.9) + dot(deep(ac, 0.1), 50, 88.2, 1.4, edge(ac), 0.8)
    return dict(sleeve=0.2, torso=PARTY, inside=ins, over_torso=ot, legs=c.skin, neck=crew(deep(cl, 0.15)))


@item('top', 'overalls', 'Overalls', colors=['cloth (bib, straps, legs)', 'cloth-deep (pocket)', 'fixed white shirt', 'fixed gold buttons'],
      layer='torso base white shirt; bib inside; short white sleeves',
      notes='As today: white tee base, bib from y85 with straps to the shoulders, gold buttons at (40.6,86)/(59.4,86), '
            'legs in the user colour.')
def overalls(c):
    cl = c.cloth
    bib = 'M39.5 85L60.5 85L62 92Q66 92 67 98L67 106L33 106L33 98Q34 92 38 92Z'
    ins = path(bib, cl, cls='cloth') + rrect(deep(cl, 0.12), 45, 88.5, 10, 6, 2, cls='cloth-deep')
    ins += line(cl, 38.5, 71.5, 40.5, 86, 3.4) + line(cl, 61.5, 71.5, 59.5, 86, 3.4)
    ins += dot('#F2CF5B', 40.6, 86, 1.3, '#B98F2E', 0.7) + dot('#F2CF5B', 59.4, 86, 1.3, '#B98F2E', 0.7)
    ins += path(bib, None, edge(cl), 0.9)
    return dict(sleeve=0.34, sleeve_color=SHIRT, sleeve_cls='fixed', base=SHIRT, base_cls='fixed', legs=c.cloth, inside=ins,
                neck=crew(deep(SHIRT, 0.12), 1.4))


@item('top', 'dungaree_skirt', 'Dungaree skirt', colors=['cloth (bib and skirt)', 'fixed white shirt', 'fixed gold buttons'],
      layer='DRESS torso, white base, bib and A-line skirt inside',
      notes='The overalls\' bib on an A-line skirt (DRESS shape): skirt from y89, bib 40..60 from y84, straps, gold buttons, '
            'a front patch pocket; short white sleeves; bare legs.')
def dungaree_skirt(c):
    cl = c.cloth
    ins = path('M24 89.4L76 89.4L76 110L24 110Z', cl, cls='cloth') + path(pts(40.4, 84, 59.6, 84, 60.6, 90, 39.4, 90), cl, cls='cloth')
    ins += path('M40.4 84L39.4 89.6M59.6 84L60.6 89.6M40.4 84L59.6 84', None, edge(cl), 0.9)
    ins += rrect(deep(cl, 0.1), 45.4, 92, 9.2, 6.4, 2, cls='cloth-deep') + line(cl, 38.5, 71.5, 40.8, 85, 3.4) + line(cl, 61.5, 71.5, 59.2, 85, 3.4)
    ins += dot('#F2CF5B', 41, 84.8, 1.3, '#B98F2E', 0.7) + dot('#F2CF5B', 59, 84.8, 1.3, '#B98F2E', 0.7)
    ins += path('M34.6 89.6L65.4 89.6', None, edge(cl), 0.9)
    return dict(sleeve=0.34, sleeve_color=SHIRT, sleeve_cls='fixed', base=SHIRT, base_cls='fixed', torso=DRESS, legs=c.skin,
                inside=ins, neck=crew(deep(SHIRT, 0.12), 1.4))


@item('top', 'raincoat', 'Raincoat', colors=['cloth (coat, sleeves, hood rim, boots)', 'fixed cream toggles'],
      layer='COAT torso to y109.2; hood rim behind the neck; boots in legs_svg',
      notes='Longer A-line coat, centre opening x50, three cream toggle bars (47..53 at y80/88/96), flap pockets at '
            '(35..43, 95) and (57..65, 95), the hood down behind the neck, rain boots in cloth-deep (top y104.6).')
def raincoat(c):
    cl = c.cloth
    ins = line(edge(cl), 50, 72, 50, 110, 1)
    for y in (80, 88, 96):
        ins += rrect(edge(CREAM, 0.3), 46.4, y - 1.3, 7.2, 2.6, 1.3) + rrect(CREAM, 47, y - 0.8, 6, 1.6, 0.8)
    ins += rrect(deep(cl, 0.1), 35, 94, 8, 2.6, 1, cls='cloth-deep') + rrect(deep(cl, 0.1), 57, 94, 8, 2.6, 1, cls='cloth-deep')
    ins += rrect(deep(cl, 0.12), 28, 105.6, 44, 4, 0, cls='cloth-deep')
    return dict(sleeve=1, torso=COAT, inside=ins, behind_neck=hood_rim(cl), neck=collar_points(light(cl, 0.15), 70.2, 'cloth-light'),
                legs_svg=boots(DENIM, deep(cl, 0.18), boot_cls='cloth-deep'))


@item('top', 'puffer', 'Puffer jacket', colors=['cloth (jacket, sleeves)', 'cloth-deep (quilting, collar)'],
      layer='wider PUFFER torso (30.8..69.2); stand collar over the neck',
      notes='Wider, rounder silhouette, four horizontal quilt seams (y80.4/87/93.6/99.6) with a soft light on each '
            'puff, a zip line x50 with a pull, a padded stand collar (42..58, y66.4..74) over the neck.')
def puffer(c):
    cl = c.cloth
    ins = ''
    for y in (80.4, 87, 93.6, 100):
        ins += path('M28 %sQ50 %s 72 %s' % (f(y), f(y + 1.4), f(y)), None, edge(cl, 0.3), 1)
        ins += oval(WHITE, 41, y - 3, 6, 1.2, op=0.16) + oval(WHITE, 59, y - 3, 6, 1.2, op=0.16)
    ins += line(edge(cl, 0.3), 50, 72, 50, 106, 1) + rrect(SHOE, 48.6, 76, 2.8, 4, 1)
    nk = rrect(edge(cl), 41.4, 65.6, 17.2, 9.4, 4.4) + rrect(cl, 42.2, 66.4, 15.6, 7.8, 3.8, cls='cloth')
    nk += line(edge(cl, 0.3), 50, 66.6, 50, 74, 1) + oval(WHITE, 46, 68, 2.6, 1, op=0.2)
    return dict(sleeve=1, torso=PUFFER, inside=ins, neck=nk, over_torso=cuffs(deep(cl, 0.15), 'cloth-deep'))


@item('top', 'jersey', 'Sports jersey', colors=['cloth (jersey, shorts)', 'accent (chest band, V, trims)', 'fixed white socks'],
      layer='torso + short sleeves; shorts and socks in legs_svg',
      notes='Number-free kit: a broad accent chest band y82..88, accent V-neck, accent side panels; shorts in cloth-deep '
            '(36..64 to y106.6); bare legs with white socks.')
def jersey(c):
    cl, ac = c.cloth, accent(c)
    ins = rrect(ac, 28, 82, 44, 6.2, 0) + rrect(WHITE, 28, 81, 44, 1, 0, op=0.7) + rrect(WHITE, 28, 88.2, 44, 1, 0, op=0.7)
    ins += path(pts(28, 70, 35, 70, 35.8, 104, 28, 104), ac) + path(pts(72, 70, 65, 70, 64.2, 104, 72, 104), ac)
    ins += path('M44.4 69.6L50 76.6L55.6 69.6', None, ac, 2.4)
    lg = bare_legs(c, WHITE)
    sh = deep(cl, 0.12)
    lg += path('M35 99L65 99L64.4 106.4Q64.2 107.6 63 107.6L52 107.6L50 105L48 107.6L37 107.6Q35.8 107.6 35.6 106.4Z', sh, edge(sh), 1)
    return dict(sleeve=0.34, inside=ins, legs_svg=lg)


@item('top', 'sailor', 'Sailor top', colors=['cloth (collar, trousers)', 'accent (bow)', 'fixed white top'],
      layer='white torso; the square sailor collar over the torso; bow on the neck',
      notes='White short-sleeve top with the big square collar in the user colour (two white piping lines) dropping to a V '
            'at (50,84); an accent bow at the V; trousers in cloth.')
def sailor(c):
    cl, ac = c.cloth, accent(c, 6)
    col = 'M38.4 70.4L61.6 70.4L64.6 78.4L50 85.6L35.4 78.4Z'
    ot = path(col, cl, edge(cl), 1, cls='cloth')
    ot += path('M37.8 72.2L36.8 77.8L50 84M62.2 72.2L63.2 77.8L50 84', None, WHITE, 0.8)
    ot += path(pts(45, 70, 55, 70, 50, 80.4), c.skin) + path('M45 70L50 80.4L55 70', None, edge(cl), 0.8)
    ot += path('M50 83C46 80 43 81 43.6 83.6C44 86 47 86 50 83Z', ac, edge(ac), 0.8)
    ot += path('M50 83C54 80 57 81 56.4 83.6C56 86 53 86 50 83Z', ac, edge(ac), 0.8)
    ot += path('M49 83.4L46.4 90L48.6 90L50 85ZM51 83.4L53.6 90L51.4 90L50 85Z', ac, edge(ac), 0.7) + dot(deep(ac, 0.1), 50, 83.2, 1.2, edge(ac), 0.6)
    return dict(sleeve=0.34, sleeve_color=SHIRT, sleeve_cls='fixed', base=SHIRT, base_cls='fixed', legs=cl,
                inside=rrect(deep(SHIRT, 0.08), 28, 100.4, 44, 4, 0), over_torso=ot)


@item('top', 'chef_coat', 'Chef coat', colors=['cloth (neckerchief)', 'fixed white coat', 'fixed grey buttons', 'fixed charcoal trousers'],
      layer='white torso + long sleeves; neckerchief on the neck',
      notes='Double-breasted white coat: two button columns x45.4/54.6 at y80/87/94, the overlap seam x55.6; a small '
            'neckerchief knot in the user colour; charcoal trousers.')
def chef_coat(c):
    w = SUITW
    ins = line(edge(w, 0.2), 56.6, 71, 56.6, 104, 0.9)
    for y in (81, 88, 95):
        ins += btn(45, y, 1.05, '#C7CBD6') + btn(54, y, 1.05, '#C7CBD6')
    ins += rrect(deep(w, 0.06), 28, 100.4, 44, 4, 0)
    cl = c.cloth
    nk = path('M43.6 69.8Q50 74 56.4 69.8L55.6 73.6Q50 76.6 44.4 73.6Z', cl, edge(cl), 0.9, cls='cloth')
    nk += path(pts(48, 74.4, 52, 74.4, 53.4, 79.4, 50, 78, 46.6, 79.4), deep(cl, 0.08), edge(cl), 0.9)
    nk += collar_points(w, 69.4)
    return dict(sleeve=1, sleeve_color=w, sleeve_cls='fixed', base=w, base_cls='fixed', legs=TROUSERS, inside=ins, neck=nk,
                over_torso=cuffs(deep(w, 0.08)))


@item('top', 'lab_coat', 'Lab coat', colors=['cloth (shirt underneath)', 'fixed white coat', 'fixed blue pen'],
      layer='COAT torso in white; shirt V in the user colour; lapels over the torso',
      notes='Long white coat (to y109.2) open over a shirt in the user colour (V to y90), white lapels, chest pocket with '
            'a pen at (57..62, 80..86), hip pockets, centre opening x50.')
def lab_coat(c):
    w = SUITW
    cl = c.cloth
    ins = path(pts(43.6, 69.4, 56.4, 69.4, 51.6, 110, 48.4, 110), cl, cls='cloth') + line(edge(cl), 50, 72, 50, 110, 0.7)
    ins += path(pts(42.4, 69.4, 50.4, 90.6, 48.6, 110, 28, 110, 28, 69.4), w) + path(pts(57.6, 69.4, 49.6, 90.6, 51.4, 110, 72, 110, 72, 69.4), w)
    ins += path('M42.4 69.4L50.2 90.6L48.6 110M57.6 69.4L49.8 90.6L51.4 110', None, edge(w, 0.25), 0.9)
    ins += line('#4A7FD9', 59.4, 77.6, 59.4, 82, 1.2) + rrect(deep(w, 0.06), 56.4, 80, 6.6, 6, 1) + line(edge(w, 0.2), 56.4, 80, 63, 80, 0.7)
    ins += rrect(deep(w, 0.06), 34.6, 96, 8, 7, 1.4) + rrect(deep(w, 0.06), 57.4, 96, 8, 7, 1.4)
    return dict(sleeve=1, sleeve_color=w, sleeve_cls='fixed', base=w, base_cls='fixed', torso=COAT, inside=ins,
                over_torso=lapels(w, 86), neck=collar_points(light(cl, 0.2), 70.6, 'cloth-light'))


@item('top', 'scrubs', 'Scrubs', colors=['cloth (top, trousers)', 'cloth-deep (V trim, pocket)'],
      layer='torso + short sleeves; matching trousers',
      notes='V-neck to y78 (skin in the V, deep trim), a chest pocket at (36..43, 80..86) with a pen, matching trousers.')
def scrubs(c):
    cl = c.cloth
    ins = path(pts(45, 69, 55, 69, 50, 78.4), c.skin, cls='skin') + path('M44.6 69.4L50 78.4L55.4 69.4', None, deep(cl, 0.2), 1.6)
    ins += rrect(deep(cl, 0.1), 36, 80, 7, 6, 1, cls='cloth-deep') + line('#F7F3EA', 38, 78, 38, 81.6, 1)
    ins += hem(c)
    return dict(sleeve=0.34, inside=ins, legs=deep(cl, 0.04))


@item('top', 'bear_onesie', 'Bear onesie', colors=['cloth (onesie, legs, sleeves)', 'fixed cream belly and paw pads'],
      layer='torso + long sleeves + cloth legs with paw feet; a hood rim behind the neck',
      notes='Animal onesie body only: cream belly oval (50, 91, 10x10), cloth legs ending in round paw feet with cream '
            'pads. Pairs with hats "hood" or "bear_ears" (a dedicated bear hood with ears would be a good later add, see spec). Kept as one animal so a hat can pick the animal.')
def bear_onesie(c):
    cl = c.cloth
    ins = oval(edge(CREAM, 0.2), 50, 92, 10.6, 10.8) + oval(CREAM, 50, 92, 10, 10.2) + hem(c, 101.6)
    ins += line(edge(cl), 50, 71, 50, 80.6, 0.9)
    lg = ''
    for side in (-1, 1):
        x = 50 + side * 4.6
        lg += rrect(edge(cl, 0.3), x - 4.3, 99, 8.6, 14, 3.2) + rrect(cl, x - 3.4, 99, 6.8, 13.2, 2.6, cls='cloth')
        lg += oval(edge(cl, 0.3), x + side * 0.6, 113, 5.6, 3.6) + oval(cl, x + side * 0.6, 113, 4.8, 2.9, cls='cloth')
        lg += oval(CREAM, x + side * 0.6, 113.6, 2.4, 1.4)
    return dict(sleeve=1, inside=ins, legs_svg=lg, behind_neck=hood_rim(cl), over_torso=cuffs(deep(cl, 0.08), 'cloth-deep'))


@item('top', 'suit', 'Suit', colors=['cloth (jacket, sleeves, trousers)', 'fixed white shirt', 'accent (tie)'],
      layer='torso + long sleeves; lapels over the torso; tie on the neck',
      notes='Matching jacket and trousers in the user colour (dark choices look formal), white shirt V to y88, accent '
            'tie, lapels slightly deeper than the jacket, one button at (50,92).')
def suit(c):
    cl = c.cloth
    ins = v_panel(SHIRT, 90, 6.6) + line(edge(cl), 50, 90, 50, 104, 1) + btn(50, 92.6, 1.1, deep(cl, 0.35))
    ins += line(edge(cl), 37, 96, 44, 96, 0.9) + line(edge(cl), 56, 96, 63, 96, 0.9)
    return dict(sleeve=1, inside=ins, legs=cl, over_torso=lapels(deep(cl, 0.1), 89, 'cloth-deep'),
                neck=collar_points(SHIRT, 70.6) + tie(accent(c, 6), 73.6, 88.6))


@item('top', 'tracksuit', 'Tracksuit', colors=['cloth (jacket, sleeves, trousers)', 'fixed white stripes, zip'],
      layer='torso + long sleeves (white stripes along each arm); stand collar; trousers with side stripes',
      notes='Zip-up track top: zip line x50 with a pull, a stand collar, two white stripes running from shoulder to cuff '
            'along the outer sleeve (arm_stripe, offset 2.3), white piping down the trousers.')
def tracksuit(c):
    cl = c.cloth
    ins = line(WHITE, 50, 72, 50, 104, 0.9) + rrect('#E8E8EE', 48.8, 75, 2.4, 3.6, 0.8) + hem(c)
    nk = rrect(edge(cl), 42.6, 67.4, 14.8, 6.8, 3.2) + rrect(cl, 43.4, 68.1, 13.2, 5.4, 2.6, cls='cloth') + line(WHITE, 50, 68, 50, 74, 0.9)
    ot = arm_stripe(False, WHITE) + arm_stripe(True, WHITE)
    lg = legs(cl, SHOE, 'cloth') + ''.join(line(WHITE, 50 + s * 7.4, 100, 50 + s * 7.4, 110, 0.9) for s in (-1, 1))
    return dict(sleeve=1, inside=ins, neck=nk, over_torso=ot, legs_svg=lg)


@item('top', 'denim_jacket', 'Denim jacket', colors=['cloth (tee underneath)', 'fixed denim blue', 'fixed copper buttons'],
      layer='torso + long sleeves in denim; tee in the user colour in the opening',
      notes='Fixed denim (#6F8BC2) with light stitching: yoke seam y80, two flap chest pockets with copper buttons, '
            'shirt-style collar, open front showing a tee in the user colour.')
def denim_jacket(c):
    d, cl = DENIM_L, c.cloth
    st = light(d, 0.45)
    ins = path(pts(45, 69, 55, 69, 53, 104, 47, 104), cl, cls='cloth') + crew(deep(cl, 0.15)).replace('44 67', '44 67')
    ins += line(edge(d), 46.2, 71, 46.6, 104, 1) + line(edge(d), 53.8, 71, 53.4, 104, 1)
    ins += path('M30 80Q38 82 45.6 80M54.4 80Q62 82 70 80', None, st, 0.6, extra='stroke-dasharray="1.2 1"')
    for x in (39.6, 60.4):
        ins += path(pts(x - 4.4, 82, x + 4.4, 82, x + 4.4, 85, x, 86.4, x - 4.4, 85), deep(d, 0.08), edge(d), 0.7) + btn(x, 84.6, 0.8, '#D9944E')
    ins += rrect(deep(d, 0.12), 28, 99.6, 44, 4.6, 0) + btn(42, 101.8, 0.8, '#D9944E') + btn(58, 101.8, 0.8, '#D9944E')
    return dict(sleeve=1, sleeve_color=d, sleeve_cls='fixed', base=d, base_cls='fixed', inside=ins,
                neck=collar_points(light(d, 0.12), 69.8) , over_torso=cuffs(deep(d, 0.12)))


@item('top', 'apron', 'Apron', colors=['cloth (apron)', 'fixed white shirt', 'cloth-light (pocket trim)'],
      layer='white torso + long sleeves; apron over the torso (unclipped, to y108)',
      notes='A bib apron in the user colour over a white shirt: bib 41..59 from y76, widening to 35..65 at y108, neck '
            'strap round the neck, waist ties peeking out at the sides (y89), a big front pocket with a light trim.')
def apron(c):
    cl = c.cloth
    ap = 'M41 76L59 76L59.6 88Q65 88.6 65.6 92L66 105.6Q66 108.2 63.4 108.2L36.6 108.2Q34 108.2 34 105.6L34.4 92Q35 88.6 40.4 88Z'
    ot = path(ap, cl, edge(cl), 1.1, cls='cloth')
    ot += rrect(deep(cl, 0.08), 42, 94, 16, 8, 2, cls='cloth-deep') + line(light(cl, 0.5), 42.6, 95.2, 57.4, 95.2, 1)
    ot += line(edge(cl), 50, 94.4, 50, 102, 0.7)
    ot += path('M34.6 89.4Q31 90 29.8 92.4M65.4 89.4Q69 90 70.2 92.4', None, cl, 1.8)
    nk = path('M41.6 76.4Q42 68.6 45.6 66.6M58.4 76.4Q58 68.6 54.4 66.6', None, cl, 1.8)
    return dict(sleeve=1, sleeve_color=SHIRT, sleeve_cls='fixed', base=SHIRT, base_cls='fixed', legs=TROUSERS,
                inside=rrect(deep(SHIRT, 0.08), 28, 100.4, 44, 4, 0), over_torso=ot, neck=collar_points(SHIRT, 69.8) + nk)


@item('top', 'cape', 'Hero cape', colors=['cloth (cape)', 'accent (tee underneath)', 'fixed gold clasp'],
      layer='back (the cape, flaring 22..78 to y108) + tee torso in accent; cape ties over the shoulders',
      notes='A cape behind the body seen from the front (its edges show beside the arms, flaring out), a deeper inner '
            'lining strip, ties over the shoulders to a gold clasp at (50,72.6); a tee in the accent colour with a '
            'simple light star.')
def cape(c):
    cl, ac = c.cloth, accent(c, 6)
    cp = 'M38 71Q30 74 28.4 82L21.6 104.6Q21 109 25.4 109L74.6 109Q79 109 78.4 104.6L71.6 82Q70 74 62 71Z'
    bk = path(cp, cl, edge(cl), 1.2, cls='cloth') + path('M27.4 86L23.6 104M72.6 86L76.4 104', None, deep(cl, 0.2), 1.6)
    ins = hem(c, 100.4, deep(ac, 0.12), cls='accent') + star(50, 87, 4.6, light(ac, 0.5))
    ot = path('M39.4 70.8Q43 72.6 48.4 72.6M60.6 70.8Q57 72.6 51.6 72.6', None, edge(cl), 3.6) + path('M39.4 70.8Q43 72.6 48.4 72.6M60.6 70.8Q57 72.6 51.6 72.6', None, cl, 2.4)
    nk = dot('#F2C14E', 50, 72.8, 2, '#B98F2E', 0.8)
    return dict(sleeve=0.34, sleeve_color=ac, sleeve_cls='accent', base=ac, base_cls='accent', back=bk, inside=ins,
                neck=ot + nk)


@item('top', 'knight_tunic', 'Knight tunic', colors=['cloth (tabard)', 'fixed grey mail (sleeves, legs)', 'fixed gold emblem',
                                                     'fixed brown belt, boots'],
      layer='mail torso + long mail sleeves; the tabard inside the torso; belt over',
      notes='A storybook knight: grey mail under a tabard in the user colour (TUNIC torso with a zigzag hem to y107.6), '
            'a round gold emblem with a simple star at (50,85), a brown belt y92..95.4 with a buckle, brown boots.')
def knight_tunic(c):
    cl = c.cloth
    ins = ''.join(path('M28 %s' % y + ''.join('Q%s %s %s %s' % (x + 1.5, y + 1.4, x + 3, y) for x in range(28, 72, 3)), None, deep(MAIL, 0.12), 0.6) for y in range(74, 104, 3))
    ins += path(pts(39.6, 69, 60.4, 69, 61.6, 110, 38.4, 110), cl, edge(cl), 1, cls='cloth')
    ins += line(light(cl, 0.35), 41.4, 70, 41, 108, 0.9) + line(light(cl, 0.35), 58.6, 70, 59, 108, 0.9)
    ins += dot('#F2C14E', 50, 83.6, 4.2, '#B98F2E', 0.9) + star(50, 83.8, 2.6, '#FFF3C4')
    ot = rrect(edge(LEATHER), 33.6, 91.6, 32.8, 4.4, 1.6) + rrect(LEATHER, 34.2, 92.2, 31.6, 3.2, 1.2) + rrect('#F2C14E', 47.6, 91.6, 4.8, 4.4, 1, )
    ot += rrect(LEATHER, 48.8, 92.8, 2.4, 2, 0.5)
    return dict(sleeve=1, sleeve_color=MAIL, sleeve_cls='fixed', base=MAIL, base_cls='fixed', torso=TUNIC, inside=ins,
                over_torso=ot, neck=crew(deep(MAIL, 0.2), 2), legs_svg=boots(MAIL, LEATHER, 103.6))


@item('top', 'space_suit', 'Space suit', colors=['cloth (chest panel, belt, boots, sleeve bands)', 'fixed white suit'],
      layer='white torso + long white sleeves; collar ring on the neck; boots in legs_svg',
      notes='Chunky white suit: a chest control panel in the user colour (41..59, 78..88) with three lights, a belt '
            'band y95..98.6, a grey collar ring (pairs with hat astronaut), sleeve bands at the cuffs, coloured boots.')
def space_suit(c):
    w = SUITW
    cl = c.cloth
    ins = rrect(edge(cl), 40.4, 77.4, 19.2, 11.2, 2.4) + rrect(cl, 41, 78, 18, 10, 2, cls='cloth')
    ins += circle('#FF8A7A', 44.6, 81.6, 1.4) + circle('#8FE3B0', 50, 81.6, 1.4) + circle('#FFE27A', 55.4, 81.6, 1.4)
    ins += rrect(deep(cl, 0.2), 43, 85, 14, 1.2, 0.6)
    ins += rrect(cl, 28, 95, 44, 3.6, 0, cls='cloth') + rrect('#C7CBD6', 47.6, 94.6, 4.8, 4.4, 1)
    ins += rrect(deep(w, 0.08), 28, 100.4, 44, 4, 0)
    nk = path('M41 67.6Q50 64.4 59 67.6L59.8 73Q50 76.4 40.2 73Z', '#C7CBD6', edge('#C7CBD6'), 1) + path('M43 69Q50 67 57 69', None, WHITE, 0.8, op=0.6)
    return dict(sleeve=1, sleeve_color=w, sleeve_cls='fixed', base=w, base_cls='fixed', inside=ins, neck=nk,
                over_torso=cuffs(cl, 'cloth'), legs_svg=boots(w, deep(cl, 0.05), 104, boot_cls='cloth'))


@item('top', 'scarf_sweater', 'Sweater and scarf', colors=['cloth (sweater, sleeves)', 'accent (scarf)'],
      layer='torso + long sleeves; scarf on the neck and over the torso',
      notes='Today\'s scarf top, refined: a ribbed sweater with a chunky knit scarf (accent) wrapped at the neck '
            '(38..62, 67.5..75.5), one tail hanging to y92 with three light stripes and a little fringe.')
def scarf_sweater(c):
    cl, sc = c.cloth, accent(c, 6)
    ins = rrect(deep(cl, 0.14), 28, 99.6, 44, 5, 0, cls='cloth-deep') + ''.join(line(edge(cl, 0.2), 35 + i * 2.6, 100.4, 35 + i * 2.6, 103.4, 0.6) for i in range(12))
    nk = rrect(edge(sc), 37.4, 67, 25.2, 9, 4.4) + rrect(sc, 38, 67.6, 24, 7.8, 3.9) + oval(WHITE, 44, 69.4, 4, 1, op=0.2)
    tl = pts(53, 73, 60, 73, 61.4, 90, 54.6, 90)
    nk += path(tl, deep(sc, 0.06), edge(sc), 1) + ''.join(line(light(sc, 0.45), 55, 78 + i * 4, 60.2, 78 + i * 4, 1) for i in range(3))
    nk += ''.join(line(deep(sc, 0.1), x, 90, x, 92.6, 0.9) for x in (55.4, 57.2, 59, 60.8))
    return dict(sleeve=1, inside=ins, neck=nk, over_torso=cuffs(deep(cl, 0.14), 'cloth-deep'))


@item('top', 'turtleneck', 'Turtleneck', colors=['cloth (sweater, sleeves, roll neck)', 'cloth-deep (ribs)'],
      layer='torso + long sleeves; the roll neck covers the neck',
      notes='A fine-knit turtleneck: a tall folded collar over the neck (43.4..56.6, y63.6..74.6) with vertical ribs, '
            'plain body, ribbed hem and cuffs. Sleek and grown-up.')
def turtleneck(c):
    cl = c.cloth
    nk = rrect(edge(cl), 42.8, 63.4, 14.4, 11.8, 4) + rrect(cl, 43.6, 64.2, 12.8, 10.2, 3.4, cls='cloth')
    nk += ''.join(line(deep(cl, 0.14), x, 65.4, x, 73.6, 0.7) for x in (46.4, 48.8, 51.2, 53.6))
    nk += path('M43.6 69Q50 71.4 56.4 69', None, edge(cl), 0.9)
    ins = rrect(deep(cl, 0.12), 28, 99.6, 44, 5, 0, cls='cloth-deep') + ''.join(line(edge(cl, 0.2), 35 + i * 2.6, 100.4, 35 + i * 2.6, 103.4, 0.6) for i in range(12))
    return dict(sleeve=1, inside=ins, neck=nk, over_torso=cuffs(deep(cl, 0.12), 'cloth-deep'))


@item('top', 'vest_shirt', 'Sweater vest', colors=['cloth (vest)', 'cloth-light (diamond)', 'fixed white shirt'],
      layer='white torso + long white sleeves; the vest inside the torso',
      notes='A V-neck knit vest over a white shirt: vest panel with armholes (inset 3.6 at the shoulders), V to y82, one '
            'light diamond motif, ribbed hem; shirt collar points; charcoal trousers.')
def vest_shirt(c):
    cl = c.cloth
    v = 'M37.6 72Q41 76 41 79L45.6 70.4L50 82L54.4 70.4L59 79Q59 76 62.4 72L66 74L68 104L32 104L34 74Z'
    ins = path(v, cl, edge(cl), 1, cls='cloth')
    ins += path('M45.6 70.4L50 82L54.4 70.4', None, deep(cl, 0.18), 1.6)
    ins += path(pts(50, 85, 54.4, 91, 50, 97, 45.6, 91), light(cl, 0.45), cls='cloth-light') + path(pts(50, 88.6, 51.8, 91, 50, 93.4, 48.2, 91), deep(cl, 0.12))
    ins += rrect(deep(cl, 0.14), 28, 99.6, 44, 5, 0, cls='cloth-deep')
    return dict(sleeve=1, sleeve_color=SHIRT, sleeve_cls='fixed', base=SHIRT, base_cls='fixed', inside=ins, legs=TROUSERS,
                neck=collar_points(SHIRT, 69.8), over_torso=cuffs(deep(SHIRT, 0.06)))


@item('top', 'thobe', 'Long robe (thobe)', colors=['cloth (robe, sleeves)', 'cloth-deep (placket, collar)'],
      layer='ROBE torso to y111.2 (legs hidden, shoes show); long sleeves',
      notes='A simple, respectful ankle-length robe: a neat band collar (crew w2), a placket x48.6..51.4 from the neck '
            'to y86 with three small buttons, a chest pocket on the wearer\'s left (screen 56..62, 80..85). '
            'Defaults look best in white/cream (TOP 9). Legs are hidden by the robe; shoes peek out.')
def thobe(c):
    cl = c.cloth
    ins = rrect(deep(cl, 0.06), 48.6, 69, 2.8, 17.6, 1.2, cls='cloth-deep') + ''.join(btn(50, y, 0.75, deep(cl, 0.18)) for y in (74, 78.4, 82.8))
    ins += path('M56 80L62 80L62 84.4Q59 85.6 56 84.4Z', None, edge(cl, 0.3), 0.8)
    ins += line(edge(cl, 0.25), 33, 102, 31.6, 111, 0.6) + line(edge(cl, 0.25), 67, 102, 68.4, 111, 0.6)
    return dict(sleeve=1, torso=ROBE, inside=ins, neck=crew(deep(cl, 0.14), 2.2, cls='cloth-deep'),
                legs_svg=legs(TROUSERS))


@item('top', 'kaftan', 'Kaftan', colors=['cloth (kaftan, sleeves)', 'accent (embroidered trim)'],
      layer='ROBE torso to y111.2; embroidered V and hem band; long sleeves',
      notes='A long kaftan with an embroidered keyhole neckline: a V to y82 edged with an accent band and small dots, a '
            'matching hem band y106..109 and cuffs. Respectful, festive.')
def kaftan(c):
    cl, ac = c.cloth, accent(c, 2 if c.cloth not in (TOP[0], TOP[1], TOP[11]) else 4)
    ins = path(pts(45.6, 69, 54.4, 69, 50, 81.6), c.skin, cls='skin')
    ins += path('M44.6 69.4L50 82.6L55.4 69.4', None, ac, 2.6)
    ins += ''.join(circle(ac, 50 + s * (5.4 - k * 1.1), 72.8 + k * 3.2, 0.7) for s in (-1, 1) for k in range(3))
    ins += line(ac, 50, 83, 50, 92, 1.6) + ''.join(circle(ac, 50 + dx, 88, 0.7) for dx in (-2, 2))
    ins += rrect(ac, 26, 106, 48, 2.6, 0) + ''.join(circle(light(ac, 0.5), x, 107.3, 0.6) for x in range(35, 66, 3))
    return dict(sleeve=1, torso=ROBE, inside=ins, over_torso=cuffs(ac), legs_svg=legs(TROUSERS))


@item('top', 'varsity', 'Varsity jacket', colors=['cloth (body)', 'fixed cream sleeves', 'accent (ribbing)'],
      layer='torso + long cream sleeves; striped rib collar, hem and cuffs',
      notes='Letter-free varsity jacket: body in the user colour, cream sleeves, accent-and-cream striped rib at the '
            'collar, hem and cuffs, snap buttons down x50, a small cream star patch on the chest.')
def varsity(c):
    cl, ac = c.cloth, accent(c, 6)
    ins = line(edge(cl), 50, 72, 50, 104, 0.9) + ''.join(btn(50, y, 0.9, CREAM) for y in (80, 86, 92, 98))
    ins += rrect(ac, 28, 99.4, 44, 5, 0) + rrect(CREAM, 28, 100.8, 44, 0.9, 0) + rrect(CREAM, 28, 102.6, 44, 0.9, 0)
    ins += star(40, 83, 3.4, CREAM, e=edge(CREAM, 0.2))
    nk = path('M42.6 69.4Q50 74.4 57.4 69.4', None, edge(ac), 3.6) + path('M42.6 69.4Q50 74.4 57.4 69.4', None, ac, 2.6) + path('M43 69.8Q50 74.2 57 69.8', None, CREAM, 0.6)
    return dict(sleeve=1, sleeve_color=CREAM, sleeve_cls='fixed', inside=ins, neck=nk, over_torso=cuffs(ac))


@item('top', 'pajamas', 'Pyjamas', colors=['cloth (pyjamas, trousers)', 'cloth-light (dots, piping)'],
      layer='torso + long sleeves; matching trousers with dots',
      notes='Cosy button-up pyjamas: piped notch collar, three buttons, a scatter of light dots and little moons, '
            'matching trousers; soft slippers (cloth-light shoes).')
def pajamas(c):
    cl = c.cloth
    lt = light(cl, 0.55)
    ins = ''.join(circle(lt, x, y, 0.9) for x, y in ((37, 80), (44, 92), (61, 84), (57, 97), (38, 98), (63, 76), (47, 80)))
    ins += ''.join(path('M%s %sA2.2 2.2 0 1 0 %s %sA1.7 1.7 0 1 1 %s %sZ' % (f(x), f(y), f(x + 1.4), f(y + 3.6), f(x), f(y)), lt) for x, y in ((56, 88), (36, 87)))
    ins += line(edge(cl), 50, 76, 50, 104, 0.8) + ''.join(btn(51.6, y, 0.9, lt) for y in (82, 89, 96)) + hem(c)
    nk = collar_points(lt, 70, 'cloth-light')
    lg = legs(cl, lt, 'cloth') + ''.join(circle(lt, 50 + s * 4.6 + dx, y, 0.7) for s in (-1, 1) for dx, y in ((-1, 103), (1.2, 108)))
    return dict(sleeve=1, inside=ins, neck=nk, legs_svg=lg, over_torso=cuffs(lt, 'cloth-light'))


@item('top', 'bomber', 'Bomber jacket', colors=['cloth (jacket, sleeves)', 'fixed charcoal ribbing', 'fixed orange lining'],
      layer='torso + long sleeves; ribbed collar, hem and cuffs; zip',
      notes='Short, rounded bomber: charcoal ribbed collar (with an orange lining flash at the neck), ribbed hem band y98.6..104 '
            'and cuffs, a zip x50 with a pull, a sleeve pocket hinted on the left arm (small zip at the shoulder).')
def bomber(c):
    cl = c.cloth
    rib = '#3E3E4A'
    ins = line(edge(cl, 0.3), 50, 72, 50, 104, 1) + rrect('#C7CBD6', 48.8, 76, 2.4, 3.6, 0.8)
    ins += rrect(rib, 28, 98.6, 44, 5.6, 0) + ''.join(line('#55556A', 31 + i * 2.6, 99.4, 31 + i * 2.6, 103.4, 0.6) for i in range(15))
    ins += path(pts(44.6, 69.4, 55.4, 69.4, 50, 75), '#F28A3A')
    nk = path('M42.4 69Q50 75.6 57.6 69', None, rib, 3.4)
    return dict(sleeve=1, inside=ins, neck=nk, over_torso=cuffs(rib))
