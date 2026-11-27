"""The owner's review of the phase 1 drafts, applied on top of the kit (same ids, so the order is kept)."""
import math
from base import *
import base as B
import hats, hair, tops, face
from hats import sh, hl, band_arc
from hair import P, shine
from tops import accent, pts, cuffs


def _re(cat, ident, fn, **kw):
    it = REG[(cat, ident)]
    it['fn'] = fn
    it.update(kw)


# --- headwear ------------------------------------------------------------------------------------------------------

def bunny_ears(c):
    """Both ears upright, splayed a little (the Atelier klein reference)."""
    E, EE, P_ = '#F6F1E7', '#BDB3C4', '#FFB3C8'
    s = ''
    for x, a in ((40, -12), (60, 12)):
        s += '<g transform="rotate(%d %d 18)">' % (a, x) + sh('M%s 18L%s -2Q%s -7.4 %s -7.4Q%s -7.4 %s -2L%s 18Z' % (
            x - 5, x - 5, x - 5, x, x + 5, x + 5, x + 5), E, e=EE) + rrect(P_, x - 2.4, -3.8, 4.8, 18, 2.4) + '</g>'
    s += band_arc(c)
    return dict(front=s)


def pilot(c):
    """Fewer, bolder pieces so it reads at 64 px: the leather cap, a thick fleece trim, two big goggles."""
    C = c.cloth
    Lr, LE, F = '#8B5E3C', '#5C3B25', '#F3E6CF'
    d = 'M21 44C19 6.6 81 6.6 79 44L79 55Q79 59.4 75 59.4L72.6 59.4Q72 50 72 44Q70 29.6 50 28Q30 29.6 28 44Q28 50 27.4 59.4L25 59.4Q21 59.4 21 55Z'
    s = sh(d, Lr, e=LE) + stroke('M30 15Q38 10.8 48 10', WHITE, 2.2, op=0.2)
    trim = 'M27.6 58.6Q28 50 28 44Q30 29.6 50 28Q70 29.6 72 44Q72 50 72.4 58.6'
    s += stroke(trim, edge(F, 0.25), 5.6) + stroke(trim, F, 4.2)
    s += stroke('M21 23.6Q50 17.6 79 23.6', edge(C), 4.6, cls='cloth-edge') + stroke('M21 23.6Q50 17.6 79 23.6', C, 3.2, cls='cloth')
    for x in (40.2, 59.8):
        s += circle('#5E5A68', x, 21.8, 6.6) + circle('#9FD9F2', x, 21.8, 4.6)
        s += arc(WHITE, x - 3, 18.6, 6, 6, 200, 70, 1.4, op=0.85)
    s += rrect('#5E5A68', 45.6, 20.4, 8.8, 2.8, 1.2)
    return dict(front=s, hair_clip_y=29)


def hood(c):
    """The hood with enough of its own shape to read without the hoodie: the cowl under the chin, a deeper lining
    inside the rim and two drawstrings."""
    C = c.cloth
    back = sh('M50 3C74 3 87 20 86 42C86 56 80.6 66 71 70.4L29 70.4C19.4 66 14 56 14 42C13 20 26 3 50 3Z', deep(C, 0.1), 'cloth-deep', edge(C), ecls='cloth-edge')
    back += hl(20, 5, 40, 30, 210, 45)
    # the cowl: the hood's fabric gathering round the neck, under the chin
    back += sh('M27 60Q29.6 70.6 38 73.4Q50 77 62 73.4Q70.4 70.6 73 60L73 66Q71 75.6 62 77.6Q50 80.4 38 77.6Q29 75.6 27 66Z', C, 'cloth', ecls='cloth-edge')
    rim = 'M23.6 61L23.6 40C23.6 22 34 12.6 50 12.6C66 12.6 76.4 22 76.4 40L76.4 61'
    inner = 'M27.4 60L27.4 40.6C27.4 25 36.6 16.4 50 16.4C63.4 16.4 72.6 25 72.6 40.6L72.6 60'
    front = stroke(inner, deep(C, 0.2), 2.2, cls='cloth-deep')
    front += stroke(rim, edge(C), 7.6, cls='cloth-edge') + stroke(rim, C, 5.8, cls='cloth')
    front += stroke('M28 24Q36 14.6 48 13.4', WHITE, 1.4, op=0.28)
    for x, bend in ((25.4, -1.2), (74.6, 1.2)):
        front += stroke('M%s 62Q%s 68 %s 74' % (x, x + bend, x + bend * 0.4), WHITE, 1)
        front += rrect('#E9E4F0', x + bend * 0.4 - 0.9, 73.4, 1.8, 3.2, 0.8)
    return dict(behind_head=back, front=front, hair_clip_y=16)


def grad_cap(c):
    """Reads on dark themes: a light edge round the board and a gold tassel."""
    K, KE = '#2F2A3A', '#17121F'
    EDGE = '#9A92AE'
    G, GE = '#F2C14E', '#B98F2E'
    s = sh('M28 30C28 17 72 17 72 30Q50 27 28 30Z', K, e=EDGE)
    s += sh('M50 3.6L82.4 13L50 22.4L17.6 13Z', '#3D3649', e=EDGE, w=1.3)
    s += stroke('M18.6 13.4L50 22.4L81.4 13.4', '#B9B2CB', 0.9, op=0.9)
    s += stroke('M50 13Q66 12.6 76.4 15.4L76.4 26', GE, 1.6) + stroke('M50 13Q66 12.6 76.4 15.4L76.4 26', G, 0.9)
    s += sh('M74.2 25.4L78.6 25.4L79.8 34Q76.4 35.4 73 34Z', G, e=GE, w=0.8)
    s += line(GE, 75.2, 28, 75, 33.6, 0.5) + line(GE, 77.6, 28, 78, 33.6, 0.5)
    s += dot(G, 50, 13, 1.7, GE, 0.8)
    s += stroke('M30 12Q40 8.4 50 6', WHITE, 1.2, op=0.25)
    return dict(front=s, hair_clip_y=26)


_re('hat', 'bunny_ears', bunny_ears, notes='Both ears upright, splayed 12 deg each way (klein reference).')
_re('hat', 'pilot', pilot)
_re('hat', 'hood', hood)
_re('hat', 'grad_cap', grad_cap, colors=[])


# --- hair ----------------------------------------------------------------------------------------------------------

def man_bun(c):
    """Hair pulled back tight to the skull: a clear hairline with the forehead and temples showing, thin sideburns,
    comb lines sweeping back, and a loose knot (not a ball) on top. Reads as hair in light colours, not a bobble hat."""
    col, e, dp, gr = P(c)
    hi = light(col, 0.4)
    fr = (dome_d(1.2, 44, 1.6) + 'L72.6 44C72.4 37 71.4 31.6 67.6 28.4Q63.6 25.6 58 25.4Q54 26.8 50 25.8Q46 26.8 42 25.4'
          'Q36.4 25.6 32.4 28.4C28.6 31.6 27.6 37 27.4 44Z')
    knot = 'M44.2 13.4C40.6 9.6 42.6 3.4 48.6 2.4C54.6 1.4 59 5.6 57.6 10.2C57 12.4 55.4 13.6 53.4 13.8Q50 15 46.6 14.4Z'
    back = path(knot, lerp(col, '#000000', 0.04), cls='hair') + path(knot, None, e, 1.1, cls='hair-edge')
    back += stroke('M45.6 11.4C44 7.6 47 4.4 51 4.6', gr, 0.9) + stroke('M49 12.4C48.4 9.4 50.6 7.4 53.6 8', gr, 0.9)
    back += stroke('M53.6 3.6C56.4 4.8 57.4 7.6 56.4 10', gr, 0.8)
    back += rrect(edge(dp), 44.4, 12.4, 11.2, 3.8, 1.9) + rrect(dp, 45, 12.9, 10, 2.8, 1.4, cls='hair-deep')
    combs = ['M40 26.4Q38.6 19 45.8 14.4', 'M60 26.4Q61.4 19 54.2 14.4', 'M46.6 26Q45.4 19.6 48.6 14.8',
             'M53.4 26Q54.6 19.6 51.4 14.8', 'M33.6 29.4Q32 21 42.6 15.6', 'M66.4 29.4Q68 21 57.4 15.6',
             'M29 36Q27.8 25.4 38.6 17.6', 'M71 36Q72.2 25.4 61.4 17.6']
    front = shape(fr, col, e, cls='hair')
    front += ''.join(stroke(d, gr, 0.85) for d in combs)
    front += stroke('M43.4 25Q42.2 19.4 47 15.4', hi, 0.9, op=0.7) + stroke('M56.6 25Q57.8 19.4 53 15.4', hi, 0.9, op=0.7)
    front += shine(0.28)
    return dict(back=back, front=front, front_d=fr)


def bowl_cut(c):
    """A rounded cut with a heavy fringe, drawn as hair: strands fan out from a whorl on the crown, the fringe and the
    sides end in soft pointed wisps, and there is a deeper shade under the fringe."""
    col, e, dp, gr = P(c)
    hi = light(col, 0.4)
    tips = [(71.4, 36), (69.4, 38.6), (66.6, 36.2), (63.6, 39), (60.6, 36.4), (57.4, 39.2), (54, 36.6), (50.6, 39.4),
            (47.2, 36.6), (43.8, 39.2), (40.6, 36.4), (37.6, 39), (34.6, 36.2), (31.6, 38.6), (29.2, 36)]
    fringe = 'L76.6 49.6L74.2 46.6L74 38.6Q73.8 36.2 71.4 36' + ''.join('L%s %s' % (f(x), f(y)) for x, y in tips[1:]) + \
             'Q26.2 36.2 26 38.6L25.8 46.6L23.4 49.6Z'
    fr = dome_d(3.4, 47) + fringe
    front = shape(fr, col, e, cls='hair')
    front += path('M74.2 46.6L74 38.6Q73.8 36.2 71.4 36L70.6 44Z', dp) + path('M25.8 46.6L26 38.6Q26.2 36.2 28.6 36L29.4 44Z', dp)
    # strands from the whorl, each a little different, ending above the fringe's tips
    wx, wy = 54, 15.6
    for (x, y), bend in zip(tips[1::2], (-2, 1.6, -1.2, 2, -1.6, 1.2, -2)):
        sx, sy = wx + (x - wx) * 0.22, wy + (y - wy) * 0.22
        mx, my = (sx + x) / 2 + bend, (sy + y) / 2
        front += stroke('M%s %sQ%s %s %s %s' % (f(sx), f(sy), f(mx), f(my), f(x), f(y - 3.4)), gr, 0.85)
    front += arc(gr, wx - 2.6, wy - 2, 5.2, 4, 160, 250, 0.85)
    front += stroke('M38 19Q35.6 25 36.6 31', hi, 1, op=0.65) + stroke('M62 18.4Q65 24 64 30.6', hi, 1, op=0.65)
    front += shine(0.28)
    return dict(front=front, front_d=fr)
def waves(c):
    """Close-cropped: the scalp shows a little through the cut, so it never reads as a cap, and the ripples are drawn
    in the hair's own deeper shade on top."""
    col, e, dp, gr = P(c)
    cap = dome_d(1.3, 41, 0) + 'L73.6 41L73.6 32Q73.4 27.6 69.4 27.2L60 26Q50 25.2 40 26L30.6 27.2Q26.6 27.6 26.4 32L26.4 41Z'
    base = lerp(col, c.skin, 0.32)
    rings = ''
    wc = deep(col, 0.3)
    for r in (4.6, 9, 13.4, 17.8, 22.2, 26.6, 31):
        pts_ = []
        for k in range(37):
            th = math.radians(k * 10)
            rr = r + 0.8 * math.sin(9 * th + r)
            pts_.append('%s %s' % (f(50 + math.cos(th) * rr * 1.05), f(19 + math.sin(th) * rr * 0.9)))
        rings += stroke('M' + 'L'.join(pts_) + 'Z', col, 2) + stroke('M' + 'L'.join(pts_) + 'Z', wc, 0.8)
    cid = 'wavecap'
    front = ('<defs><clipPath id="%s"><path d="%s"/></clipPath></defs>' % (cid, cap) + path(cap, base, cls='hair') +
             g(rings, clip=cid) + path(cap, None, edge(base, 0.3), 1) +
             stroke('M26.4 33Q26.6 27.6 30.6 27.2L40 26Q50 25.2 60 26L69.4 27.2Q73.4 27.6 73.6 33', deep(col, 0.35), 1.3) +
             shine(0.2, 2))
    return dict(front=front, front_d=cap)


_re('hair', 'man_bun', man_bun)
_re('hair', 'bowl_cut', bowl_cut)
_re('hair', 'waves', waves)


# --- tops ----------------------------------------------------------------------------------------------------------

def bomber(c):
    """Told apart from the tracksuit and the puffer: a knit trim with two stripes in the second colour, a bright lining
    flash, slanted welt pockets and a blouson hem gathered into the band."""
    cl = c.cloth
    ac = accent(c, 6)
    rib = '#3E3E4A'
    ins = line(edge(cl, 0.3), 50, 72, 50, 98.6, 1) + rrect('#C7CBD6', 48.8, 76, 2.4, 3.6, 0.8)
    ins += path('M28 97.6Q50 95.6 72 97.6L72 99L28 99Z', deep(cl, 0.12), cls='cloth-deep')
    ins += rrect(rib, 28, 98.6, 44, 5.6, 0)
    ins += line(ac, 28, 100.4, 72, 100.4, 0.9) + line(ac, 28, 102.2, 72, 102.2, 0.9)
    ins += line(edge(cl, 0.3), 36.4, 87, 40, 95, 1.6) + line(edge(cl, 0.3), 63.6, 87, 60, 95, 1.6)
    ins += path(pts(44.6, 69.4, 55.4, 69.4, 50, 75.6), '#F28A3A')
    nk = path('M42.4 69Q50 75.6 57.6 69', None, rib, 3.6) + path('M42.8 68.4Q50 74.6 57.2 68.4', None, ac, 0.8)
    return dict(sleeve=1, inside=ins, neck=nk, over_torso=cuffs(rib, stripe=ac))


_re('top', 'bomber', bomber)


# --- earrings ------------------------------------------------------------------------------------------------------

def cuff(c):
    """A silver band along the outer rim of each ear, with a stud on the lobe: bold enough to show at 64 px."""
    h = c.head
    sil, se = '#E3E8F1', '#7E8596'
    out = ''
    for s in (-1, 1):
        ex, ey = h.cx + s * (h.hw - 0.8), h.eyeY + 2.6
        a0, sw = (-80, 75) if s > 0 else (185, 75)
        out += arc(se, ex - 4.4, ey - 4.4, 8.8, 8.8, a0, sw, 2.6) + arc(sil, ex - 4.4, ey - 4.4, 8.8, 8.8, a0, sw, 1.5)
        out += dot(sil, h.cx + s * (h.hw - 0.2), h.eyeY + 7, 1.1, se, 0.6)
    return {'face': out}


_re('earrings', 'cuff', cuff)
