"""New hairstyles, drawn on the default crown (top 16, sides 25..75 at y 41; eyes ~44.9, brows ~37.5, ears x 21..29 /
71..79 at y 43..52; neck and shoulders from y 62). Layers: back (behind the body), hanging (over the body, behind the
head), front (over the face) and front_d (the front shape's path, for the soft brow shadow).

Colour: everything in c.hair (class hair / hair-edge / hair-deep); shaved areas are hair lerp skin (class hair-shaved).
"""
from base import *


def P(c):
    return c.hair, edge(c.hair), deep(c.hair, 0.14), deep(c.hair, 0.22)


def shaved(c, t=0.5):
    return lerp(c.hair, c.skin, t)


def shine(op=0.24, w=2.4):
    return arc(WHITE, 29, 15, 38, 26, 200, 56, w, op=op)


BUZZ = (dome_d(0.9, 41, 1.4) + 'L74 41C73.8 32.5 70.5 26.5 64 25.2Q50 22 36 25.2C29.5 26.5 26.2 32.5 26 41Z')


def curls_svg(lst, color, texture=True, filler=None):
    e = edge(color)
    out = ''.join(circle(e, x, y, r + 0.6, cls='hair-edge') for x, y, r in lst)
    if filler:
        out += filler
    out += ''.join(circle(color, x, y, r - 0.5, cls='hair') for x, y, r in lst)
    if texture:
        sw = deep(color, 0.22)
        out += ''.join(arc(sw, x - r * 0.5, y - r * 0.5, r, r, 150, 150, 0.9) for x, y, r in lst)
    return out


def strand(x0, y0, x1, y1, w, color, ticks=0, tick_color=None, bead=None):
    """A thick rounded strand (a loc, a braid) from (x0, y0) to (x1, y1); ticks are braid/loc segment marks."""
    e = edge(color)
    d = 'M%s %sL%s %s' % (f(x0), f(y0), f(x1), f(y1))
    out = stroke(d, e, w + 1.2, cls='hair-edge') + stroke(d, color, w, cls='hair')
    if ticks:
        tc = tick_color or deep(color, 0.25)
        for i in range(1, ticks + 1):
            t = i / (ticks + 1)
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            out += stroke('M%s %sQ%s %s %s %s' % (f(x - w * 0.42), f(y - 0.5), f(x), f(y + 0.7), f(x + w * 0.42), f(y - 0.5)), tc, 0.6)
    if bead:
        out += dot(bead, x1, y1 + 0.6, w * 0.5 + 0.2, edge(bead), 0.6)
    return out


# --- short and shaved ---------------------------------------------------------------------------------------------

@item('hair', 'mohawk', 'Mohawk', colors=['hair'], layer='front (shaved cap + crest)',
      notes='Shaved sides (the Buzz cap filled hair lerp skin 0.5, class hair-shaved) and a spiky crest 15 units wide rising to '
            'y ~-1, centred. The crest sits over the hat line: hats with hair_clip_y hide it. Crest: polygon with round joins.')
def mohawk(c):
    col, e, dp, gr = P(c)
    sh = shaved(c)
    crest = 'M40.6 29.6L37.6 16.4L42.6 18.4L41.2 5.4L46.6 9.4L50 -2.6L53.4 9.4L58.8 5.4L57.4 18.4L62.4 16.4L59.4 29.6Q50 26.4 40.6 29.6Z'
    front = shape(BUZZ, sh, edge(sh, 0.25), cls='hair-shaved') + shape(crest, col, e, cls='hair') + \
        line(gr, 50, 4, 50, 25, 0.9) + line(gr, 44.6, 14, 46, 26, 0.7) + line(gr, 55.4, 14, 54, 26, 0.7) + line(WHITE, 45.2, 13, 44.2, 23, 1.6, op=0.25)
    return dict(front=front, front_d=BUZZ)


@item('hair', 'undercut', 'Undercut', colors=['hair'], layer='front',
      notes='Shaved sides below y ~32 (hair-shaved) and a longer top swept toward screen-left, its fringe dipping to y 41 over the '
            'left brow. Two grain lines follow the sweep.')
def undercut(c):
    col, e, dp, gr = P(c)
    sh = shaved(c)
    top = ('M27.6 33.4C26.6 19.6 37 10.4 51 10.4C65.6 10.4 76 19 75.2 31.6Q73.4 29.4 70 29.2C63 30.4 55 32.2 47.6 34.8'
           'Q41 37.4 35.4 41.6Q33.4 38.2 32.4 34.8Q30 35.4 27.6 33.4Z')
    front = shape(BUZZ, sh, edge(sh, 0.25), cls='hair-shaved') + shape(top, col, e, cls='hair') + \
        stroke('M66 15Q56 20 44 30', gr, 0.8) + stroke('M58 13Q47 19 38 33', gr, 0.8) + shine()
    return dict(front=front, front_d=top)


@item('hair', 'man_bun', 'Top bun', colors=['hair'], layer='back (bun) + front (pulled-back crown)',
      notes='Smooth pulled-back crown with a gentle hairline (no fringe) and a small round bun on top (back layer, centre 50,8.4, '
            'r 6.8) tied with a darker band. Comb lines run back toward the bun.')
def man_bun(c):
    col, e, dp, gr = P(c)
    fr = dome_d(2.4, 41, 2.2) + 'L73.6 41C73.6 31.6 68.4 25.6 58 24.8Q54 26.4 50 25.2Q46 26.4 42 24.8C31.6 25.6 26.4 31.6 26.4 41Z'
    back = dot(lerp(col, '#000000', 0.04), 50, 8.4, 6.8, e, cls='hair') + arc(gr, 46, 4.4, 8, 8, 150, 140, 0.9) + \
        rrect(edge(dp), 44.6, 12.6, 10.8, 4, 2) + rrect(dp, 45.2, 13.1, 9.6, 3, 1.5, cls='hair-deep')
    front = shape(fr, col, e, cls='hair') + stroke('M42 25Q40 18 47 14', gr, 0.8) + stroke('M58 25Q60 18 53 14', gr, 0.8) + \
        stroke('M50 25V15', gr, 0.8) + shine()
    return dict(back=back, front=front, front_d=fr)


@item('hair', 'pixie', 'Pixie', colors=['hair'], layer='front',
      notes='Short, soft and side-swept: a wispy fringe sweeping to screen-left in three pointed wisps, short pointed sideburns in '
            'front of the ears down to y 50.')
def pixie(c):
    col, e, dp, gr = P(c)
    fr = ('M26.6 51C25.2 46 24.6 42 24.8 38C25 22 35.6 12.8 50 12.8C64.4 12.8 75 22 75.2 38C75.4 42 74.8 46 73.4 51'
          'Q72 46.6 71.8 42C71.6 35.6 70 31.6 67.4 29.6Q66 33 62.2 34.4Q62.6 31.8 61.6 30.6Q56.4 35.6 49.6 37.2'
          'Q50.6 35 50.4 33.6Q44 39 36.4 40.4Q38 38.2 38.4 36Q34.4 38.4 30.4 38.4Q28.6 43.4 28.4 46.6Q27.8 49 26.6 51Z')
    front = shape(fr, col, e, cls='hair') + stroke('M64 16Q58 24 50 32', gr, 0.8) + stroke('M55 14Q47 21 40 33', gr, 0.8) + shine()
    return dict(front=front, front_d=fr)


@item('hair', 'side_shave', 'Side shave', colors=['hair'], layer='back + front',
      notes='Asymmetric: screen-right is shaved (hair-shaved), the rest is parted high on the right and falls long over the left '
            'side to the shoulder (y 74), with a back layer behind. Mirror for the other side as a variant if wanted.')
def side_shave(c):
    col, e, dp, gr = P(c)
    sh = shaved(c)
    fr = ('M68 26.6C63.6 16.6 55 12.2 46 12.4C33 13 22.8 22 22.4 38C22.1 50 23.6 62 22 72.6Q25.2 76.8 28.6 72.8'
          'C28.4 62 27.4 52 28.6 44C30 37.2 34.4 33 40.4 31.2C48.6 29.4 58.6 28.8 68 26.6Z')
    back = shape('M24 30C17 44 19.6 60 18.2 74Q22 78.4 26.4 74.4L29 40Z', dp, e, cls='hair-deep')
    front = shape(BUZZ, sh, edge(sh, 0.25), cls='hair-shaved') + shape(fr, col, e, cls='hair') + \
        stroke('M60 19Q44 20 33 33', gr, 0.8) + stroke('M26 44Q24.6 58 25.4 70', gr, 0.8) + shine()
    return dict(back=back, front=front, front_d=fr)


@item('hair', 'slicked_back', 'Slicked back', colors=['hair'], layer='front',
      notes='Combed straight back with a glossy finish: high, softly widow-peaked hairline (y ~24), comb lines running up and '
            'back, a strong shine arc (white 0.32).')
def slicked_back(c):
    col, e, dp, gr = P(c)
    fr = dome_d(2.6, 41, 2.4) + 'L73.8 41C74 31 70.4 25.4 62.6 23.6Q57 25.6 50 26Q43 25.6 37.4 23.6C29.6 25.4 26 31 26.2 41Z'
    front = shape(fr, col, e, cls='hair') + ''.join(
        stroke('M%s 25Q%s 17 %s 12.6' % (f(x), f(x + (x - 50) * 0.1), f(50 + (x - 50) * 0.55)), gr, 0.8) for x in (36, 43, 50, 57, 64)) + \
        shine(0.32, 2.6)
    return dict(front=front, front_d=fr)


@item('hair', 'bowl_cut', 'Bowl cut', colors=['hair'], layer='front',
      notes='A rounded helmet with a straight blunt fringe at y 36 and level sides ending at y 47 just over the ears; fine '
            'vertical grain on the fringe.')
def bowl_cut(c):
    col, e, dp, gr = P(c)
    fr = dome_d(3.4, 47) + 'L74 47L74 38.6Q73.6 36.2 71 36.2L29 36.2Q26.4 36.2 26 38.6L26 47Z'
    front = shape(fr, col, e, cls='hair') + ''.join(line(gr, x, 31.2, x, 35, 0.7) for x in (34, 40, 46, 52, 58, 64)) + shine()
    return dict(front=front, front_d=fr)


@item('hair', 'mullet', 'Mullet', colors=['hair'], layer='back (long nape) + front (short top)',
      notes='Short, textured top with a choppy fringe; the back grows long and flicks out under the jaw beside the neck '
            '(back layer x 24..76 down to y 74). Business up front.')
def mullet(c):
    col, e, dp, gr = P(c)
    fr = (dome_d(2.6, 44, 2.6) + 'L73.8 44C74 36 72 30.6 67.4 28.6L64 32L61 28.8L57 32.4L53.6 29L50 33L46.4 29L43 32.4L39 28.8'
          'L36 32L32.6 28.6C28 30.6 26 36 26.2 44Z')
    back = shape('M29 40L27.6 62Q25.4 70 22.6 74.2Q29 75.6 33.4 72.4Q38 76.4 43.6 73Q50 76.6 56.4 73Q62 76.4 66.6 72.4'
                 'Q71 75.6 77.4 74.2Q74.6 70 72.4 62L71 40Z', dp, e, cls='hair-deep')
    front = shape(fr, col, e, cls='hair') + shine()
    return dict(back=back, front=front, front_d=fr)


@item('hair', 'wolf_cut', 'Wolf cut', colors=['hair'], layer='back + front',
      notes='Shaggy and layered: a choppy curtain fringe parted in the middle, pointed layers framing the face down to y 62, '
            'more pointed layers behind (back layer to y 70).')
def wolf_cut(c):
    col, e, dp, gr = P(c)
    fr = ('M22 44C21 26 33 12.6 50 12.6C67 12.6 79 26 78 44L79.4 53L76 51L77 60L73.2 56.6L72.6 64L70.2 58Q72 50 71.6 42'
          'C71 36 68.6 32.4 65.6 30.6L64 36L60.4 31.6L58 36.2L54.6 31L50 33.4L45.4 31L42 36.2L39.6 31.6L36 36L34.4 30.6'
          'C31.4 32.4 29 36 28.4 42Q28 50 29.8 58L27.4 64L26.8 56.6L23 60L24 51L20.6 53Z')
    back = shape('M20 40L17 56L20.6 55L18.4 66L23.4 63L24 71L29 64L50 66L71 64L76 71L76.6 63L81.6 66L79.4 55L83 56L80 40Z',
                 dp, e, cls='hair-deep')
    front = shape(fr, col, e, cls='hair') + stroke('M50 13.2V24', gr, 0.8) + shine()
    return dict(back=back, front=front, front_d=fr)


@item('hair', 'hime', 'Hime cut', colors=['hair'], layer='back (long) + front (blunt fringe, cheek locks)',
      notes='Straight long hair to y 96 behind, a blunt fringe at y 35 and blunt cheek-length side locks (x 22..27.6 / 72.4..78) '
            'cut level at y 63. Clean, graphic; the cut lines are exactly horizontal.')
def hime(c):
    col, e, dp, gr = P(c)
    back = shape('M50 12C70 12 80.4 22 80.8 40L82 92Q82 96 77.6 96L22.4 96Q18 96 18 92L19.2 40C19.6 22 30 12 50 12Z', dp, e, cls='hair-deep')
    fr = dome_d(3, 63) + 'L72.6 63L72.6 37Q72.4 35 70.4 35L29.6 35Q27.6 35 27.4 37L27.4 63Z'
    front = shape(fr, col, e, cls='hair') + ''.join(line(gr, x, 30, x, 34.2, 0.7) for x in (36, 43, 50, 57, 64)) + \
        line(gr, 24.8, 45, 24.8, 60, 0.7) + line(gr, 75.2, 45, 75.2, 60, 0.7) + shine()
    return dict(back=back, front=front, front_d=fr)


# --- tied up ------------------------------------------------------------------------------------------------------

@item('hair', 'high_pony', 'High ponytail', colors=['hair'], layer='back (tail) + front (pulled-back crown)',
      notes='Hair swept up from a clean hairline to a tie on the crown (TIE pink band at 50,9) and a long tail that arcs over to '
            'screen-right and falls behind the shoulder to y 72. The band could take the outfit colour.')
def high_pony(c):
    col, e, dp, gr = P(c)
    fr = dome_d(2.2, 41, 2) + 'L73.6 41C73.6 32 68.6 26.4 59 25.6Q50 27.4 41 25.6C31.4 26.4 26.4 32 26.4 41Z'
    tail = ('M46 10C46 2 58 -1.6 66 4C78 11 87.6 26 86 44C85 56 87.4 64 91.6 71.6Q81.4 71 79 59C76.6 47 79.4 34 70 23'
            'Q62 16 54 14Z')
    back = shape(tail, lerp(col, '#000000', 0.04), e, cls='hair') + stroke('M62 6Q78 14 82 34', gr, 0.8) + \
        stroke('M80 46Q80 58 86 66', gr, 0.8)
    front = shape(fr, col, e, cls='hair') + stroke('M40 26Q42 18 48 13', gr, 0.8) + stroke('M60 26Q58 18 52 13', gr, 0.8) + \
        rrect(edge(TIE), 44.4, 8, 11.2, 4.6, 2.3) + rrect(TIE, 45, 8.6, 10, 3.4, 1.7) + shine()
    return dict(back=back, front=front, front_d=fr)


@item('hair', 'space_buns', 'Space buns', colors=['hair'], layer='back (two buns) + front (wispy fringe)',
      notes='Two round buns high on each side (centres 30.6,14 and 69.4,14, r 8.4) with spiral grain and pink ties at their bases, '
            'and a light, wispy fringe with side wisps in front of the ears.')
def space_buns(c):
    col, e, dp, gr = P(c)
    back = ''
    for s in (-1, 1):
        x = 50 + s * 19.4
        back += dot(lerp(col, '#000000', 0.04), x, 14, 8.4, e, cls='hair') + arc(gr, x - 4.4, 9.6, 8.8, 8.8, 140, 200, 0.9) + \
            arc(gr, x - 2.2, 11.8, 4.4, 4.4, 160, 180, 0.8)
    fr = (dome_d(2.6, 41) + 'L77.6 47Q75.8 49.4 74 46.4C74 38 72.6 33 70 31Q67 34.6 63.4 33.2Q60 36.6 55.4 34.2Q52.4 36.8 50 34.4'
          'Q47.6 36.8 44.6 34.2Q40 36.6 36.6 33.2Q33 34.6 30 31C27.4 33 26 38 26 46.4Q24.2 49.4 22.4 47Z')
    ties = ''.join(rrect(edge(TIE), 50 + s * 18.4 - 4.6, 19.6, 9.2, 3.8, 1.9) + rrect(TIE, 50 + s * 18.4 - 4, 20.1, 8, 2.8, 1.4)
                   for s in (-1, 1))
    front = shape(fr, col, e, cls='hair') + ties + shine()
    return dict(back=back, front=front, front_d=fr)


@item('hair', 'half_up', 'Half-up', colors=['hair'], layer='back (long + small knot) + front',
      notes='Long hair to y 92 behind; the top half gathered into a small knot at the back of the crown (back layer, 50,10 r 5); '
            'a soft side-swept fringe and long front pieces over the shoulders.')
def half_up(c):
    col, e, dp, gr = P(c)
    back = shape('M50 12C70 12 80.4 22 80.8 40L82 88Q82 92 77.6 92L22.4 92Q18 92 18 88L19.2 40C19.6 22 30 12 50 12Z', dp, e, cls='hair-deep') + \
        dot(lerp(col, '#000000', 0.04), 50, 10.2, 5.2, e, cls='hair') + arc(gr, 47.4, 7.6, 5.2, 5.2, 150, 160, 0.8)
    fr = (dome_d(3, 41) + 'L78 84Q77 87 74.4 86C73.4 74 74.4 56 73.6 44C73 36 70.6 31.6 67 29.4Q58 33.4 46 33.6Q38 34.4 31.2 38.6'
          'Q28 41 26.6 44C25.6 56 26.6 74 25.6 86Q23 87 22 84Z')
    front = shape(fr, col, e, cls='hair') + stroke('M64 18Q54 26 40 32', gr, 0.8) + shine()
    return dict(back=back, front=front, front_d=fr)


# --- curls, coils, locs and braids --------------------------------------------------------------------------------

LONG_CURLS_BACK = [(x, y, r) for x, y, r in (
    (19.6, 44, 6.4), (80.4, 44, 6.4), (17.6, 53, 6.6), (82.4, 53, 6.6), (17.4, 62.6, 6.6), (82.6, 62.6, 6.6),
    (18.6, 72, 6.4), (81.4, 72, 6.4), (21, 81, 6.2), (79, 81, 6.2), (27, 87.4, 6), (73, 87.4, 6), (34.6, 89, 5.8), (65.4, 89, 5.8),
    (42.6, 89.6, 5.6), (57.4, 89.6, 5.6), (50, 90, 5.6), (24, 31, 6.4), (76, 31, 6.4))]
LONG_CURLS_FRONT = [(x, y, r) for x, y, r in (
    (25, 30, 6.8), (31.4, 21.6, 7.2), (40, 16.2, 7.4), (50, 14.4, 7.4), (60, 16.2, 7.4), (68.6, 21.6, 7.2), (75, 30, 6.8),
    (33.8, 28.6, 5.4), (42, 29.6, 5.6), (50.4, 30, 5.6), (58.6, 29.4, 5.6), (66.4, 28.4, 5.2),
    (22.4, 39, 5.6), (77.6, 39, 5.6), (22.2, 48, 5.2), (77.8, 48, 5.2), (23.4, 56.4, 4.8), (76.6, 56.4, 4.8))]


@item('hair', 'long_curly', 'Long curls', colors=['hair'], layer='back (cascade of curls to y 96) + front (hairline curls)',
      notes='Big, bouncy long curls: a cascade of overlapping curl circles behind the body to y ~96 over a filled mass, and a '
            'curly hairline with curls framing the cheeks. Each curl: edge circle r+0.6, fill r-0.5, a swirl arc (deep 0.22).')
def long_curly(c):
    col, e, dp, gr = P(c)
    mass = path('M22 30C30 14 70 14 78 30L84 60L82 86Q70 94 50 94Q30 94 18 86L16 60Z', dp, cls='hair-deep')
    back = curls_svg(LONG_CURLS_BACK, dp, True, mass)
    front = curls_svg(LONG_CURLS_FRONT, col, True, oval(col, 50, 23, 21, 9, cls='hair')) + shine(0.18)
    return dict(back=back, front=front)


@item('hair', 'fade_curls', 'Curly top, faded sides', colors=['hair'], layer='front',
      notes='A high-top of tight curls on the crown (curl circles r 4..5 between x 30..70, y 9..30) over faded, shaved sides '
            '(hair-shaved). Common, contemporary Black men\'s cut; the coils are small and dense, not loose waves.')
def fade_curls(c):
    col, e, dp, gr = P(c)
    sh = shaved(c, 0.45)
    pts = []
    for row, (y, xs, r) in enumerate(((10.6, (38, 44.6, 51.2, 57.8, 63), 4.6), (16.4, (31.6, 37.8, 44, 50.2, 56.4, 62.6, 68.4), 4.8),
                                      (22.6, (29.2, 35.4, 41.6, 47.8, 54, 60.2, 66.4, 71.4), 4.6), (28, (32.6, 39, 45.4, 51.8, 58.2, 64.6, 69.6), 4.2))):
        pts += [(x, y, r) for x in xs]
    filler = path('M30 30C28 12 72 12 70 30Z', col, cls='hair')
    front = shape(BUZZ, sh, edge(sh, 0.25), cls='hair-shaved') + curls_svg(pts, col, True, filler) + shine(0.16)
    return dict(front=front, front_d=BUZZ)


@item('hair', 'locs', 'Locs', colors=['hair'], layer='back (locs behind, to y ~90) + hanging (front locs) + front (crown)',
      notes='Shoulder-to-chest length locs: rounded strands (width 4.2) with gentle segment marks, a fan of them behind the body, '
            'and four framing the face in front of the shoulders; the crown is a smooth cap parted off-centre with the locs\' roots '
            'shown as soft lines. Ends rounded, a couple with gold cuffs.')
def locs(c):
    col, e, dp, gr = P(c)
    back = ''
    for i, x in enumerate((16.6, 21, 25.4, 74.6, 79, 83.4, 30, 70, 35, 65, 40.6, 59.4, 45.4, 54.6, 50)):
        ln = 84 + (i * 7 % 10)
        back += strand(x, 30, x + (x - 50) * 0.06, ln, 4.4, dp, 5)
    hang = ''
    for x0, x1, ln in ((23.6, 22.2, 82), (28.4, 27.4, 77), (76.4, 77.8, 82), (71.6, 72.6, 77)):
        hang += strand(x0, 36, x1, ln, 4.4, col, 6, bead=GOLD if ln == 82 else None)
    cap = dome_d(3.6, 41) + 'L73.8 41C73.4 32 68 26.4 60 25.6Q55 28.4 49 26.2Q44 28.6 40 25.6C32 26.4 26.6 32 26.2 41Z'
    front = shape(cap, col, e, cls='hair')
    # the loc roots over the crown: rounded tips in rows, then the framing locs' tops
    for x, y in ((31, 31.4), (36.6, 28.2), (43.4, 29.2), (49.4, 28.4), (55.6, 29.6), (62.6, 28.2), (68.6, 31.2)):
        front += strand(x, y - 8, x + (x - 50) * 0.04, y, 4.2, col, 1)
    front += ''.join(dot(col, x, 36.4, 2.5, e, 0.9, cls='hair') for x in (23.6, 28.4, 71.6, 76.4)) + shine(0.18)
    return dict(back=back, hanging=hang, front=front, front_d=cap)


@item('hair', 'cornrows', 'Cornrows', colors=['hair'], layer='front (+ back: two short braids behind the shoulders)',
      notes='Straight-back cornrows: seven raised braided rows (stroke 4, edge 5.2) running from the hairline up and over the '
            'crown, with the scalp (hair lerp skin 0.5) showing as clean partings between them and small chevron ticks for the '
            'plait. Two braid ends show behind the shoulders. Accurate, tidy partings matter more than detail.')
def cornrows(c):
    col, e, dp, gr = P(c)
    scalp = shaved(c, 0.55)
    cap = dome_d(1.4, 41, 1.4) + 'L73.4 41C73.2 33 70.4 27.6 64.4 26Q50 23.4 35.6 26C29.6 27.6 26.8 33 26.6 41Z'
    rows = ''
    for x0 in (29, 35.6, 42.6, 50, 57.4, 64.4, 71):
        y0 = 27.6 + abs(x0 - 50) ** 1.6 * 0.07
        x1 = 50 + (x0 - 50) * 0.55
        cxq = 50 + (x0 - 50) * 1.02
        d = 'M%s %sQ%s %s %s %s' % (f(x0), f(y0), f(cxq), 17, f(x1), 13.4)
        rows += stroke(d, e, 5.0, cls='hair-edge') + stroke(d, col, 3.8, cls='hair')
        for t in (0.12, 0.3, 0.48, 0.66, 0.84):
            x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * cxq + t * t * x1
            y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * 17 + t * t * 13.4
            rows += stroke('M%s %sL%s %sL%s %s' % (f(x - 1.3), f(y + 0.9), f(x), f(y - 0.2), f(x + 1.3), f(y + 0.9)), gr, 0.55)
    cid = 'cornrowcap'
    front = ('<defs><clipPath id="%s"><path d="%s"/></clipPath></defs>' % (cid, cap) + path(cap, scalp, cls='hair-shaved') +
             g(rows, clip=cid) + path(cap, None, edge(scalp, 0.3), 1.0) + shine(0.14))
    back = strand(38, 56, 37, 78, 3.8, dp, 5, bead=GOLD) + strand(62, 56, 63, 78, 3.8, dp, 5, bead=GOLD)
    return dict(back=back, front=front, front_d=cap)


@item('hair', 'box_braids', 'Long box braids', colors=['hair'], layer='back (braids to y 98) + hanging (front braids) + front (crown)',
      notes='Long box braids: thin plaits (width 3) with chevron ticks, a full curtain behind the body to y ~98, three plaits each '
            'side hanging in front of the shoulders with gold cuffs at the ends; the crown shows a centre part and the boxed '
            'parting as a faint grid of short lines.')
def box_braids(c):
    col, e, dp, gr = P(c)
    back = ''
    for i, x in enumerate([18 + k * 3.6 for k in range(18)]):
        back += strand(x, 32, x + (x - 50) * 0.06, 94 + (i % 3) * 2, 3.0, dp, 9)
    hang = ''
    for x0 in (23, 26.6, 30.2, 69.8, 73.4, 77):
        hang += strand(x0, 40, x0 + (x0 - 50) * 0.04, 86, 3.0, col, 9, bead=GOLD)
    cap = dome_d(3.2, 41) + 'L73.6 41C73 32.6 67.4 27.4 58 26.2Q53 26 50.8 24.4L49.2 24.4Q47 26 42 26.2C32.6 27.4 27 32.6 26.4 41Z'
    front = shape(cap, col, e, cls='hair') + line(gr, 50, 12.6, 50, 24.2, 0.9)
    for x in (32, 38, 44, 56, 62, 68):
        y0 = 15.6 + abs(x - 50) * 0.34
        front += line(gr, x, y0, x + (x - 50) * 0.08, y0 + 8.4, 0.6) + line(gr, x - 2.6 if x < 50 else x, y0 + 4.2, x if x < 50 else x + 2.6, y0 + 4.2, 0.6)
    front += ''.join(strand(x, 27.4, x + (x - 50) * 0.02, 33.6, 3.0, col, 1) for x in (29, 33, 67, 71)) + shine(0.2)
    return dict(back=back, hanging=hang, front=front, front_d=cap)


@item('hair', 'waves', 'Short waves', colors=['hair'], layer='front',
      notes='Close-cropped 360 waves: a short cap with a crisp lined-up hairline (sharp temple corners) and concentric rippled '
            'rings around the crown (deep 0.28 strokes, sin ripple 9 lobes) clipped to the cap. Must stay neat and fine.')
def waves(c):
    col, e, dp, gr = P(c)
    cap = dome_d(1.3, 41, 0) + 'L73.6 41L73.6 32Q73.4 27.6 69.4 27.2L60 26Q50 25.2 40 26L30.6 27.2Q26.6 27.6 26.4 32L26.4 41Z'
    rings = ''
    wc = deep(col, 0.42)
    for r in (5, 10, 15, 20, 25, 30):
        pts = []
        for k in range(73):
            th = math.radians(k * 5)
            rr = r + 0.7 * math.sin(9 * th + r)
            pts.append('%s %s' % (f(50 + math.cos(th) * rr * 1.05), f(19 + math.sin(th) * rr * 0.9)))
        rings += stroke('M' + 'L'.join(pts) + 'Z', wc, 0.95)
    cid = 'wavecap'
    front = ('<defs><clipPath id="%s"><path d="%s"/></clipPath></defs>' % (cid, cap) + path(cap, col, cls='hair') +
             g(rings, clip=cid) + path(cap, None, e, 1.1) + shine(0.2, 2))
    return dict(front=front, front_d=cap)


@item('hair', 'bald_shine', 'Bald', colors=[], layer='front (a highlight only)',
      notes='A true bald option: no hair at all, just a soft shine on the scalp (white 0.3 arc) and a faint second glint. Brows '
            'still take the hair colour. In the codec, a Hair value whose front/back are empty.')
def bald_shine(c):
    return dict(front=arc(WHITE, 30, 17, 34, 22, 205, 55, 2.6, op=0.3) + circle(WHITE, 62.6, 20.4, 1.1, op=0.3))
