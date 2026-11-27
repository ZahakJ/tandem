"""Headwear drafts, on the default crown (top 16, sides 25..75 at y 41, brows ~37.5, eyes ~44.9, ears x 21/79 y 45-50).

Every layer here is in crown units: the port draws it inside Head.crown() like AvatarArt's headwear(). Main fabric is
the user's top colour (c.cloth, class "cloth*"); metals, straw, fur and felt that read as one material are fixed.
"""
import math
from base import *

HL = 0.22


def hl(x, y, w, h, start=205, sweep=50, sw=2.2, op=HL):
    """The soft highlight arc every hat gets."""
    return arc(WHITE, x, y, w, h, start, sweep, sw, op=op)


def sh(d, fill, cls=None, e=None, w=1.1, ecls=None):
    """Filled shape + outline with explicit classes (cls for the fill, ecls for the line)."""
    return path(d, fill, cls=cls) + path(d, None, e or edge(fill), w, cls=ecls)


def cl(c, k=None):
    """The user colour and its class names: k None -> fill, else 'deep'/'light' variants."""
    return c.cloth


def lower_lip(cx, cy, rx, ry):
    """The near half of an elliptical brim (drawn over a crown so the brim wraps in front)."""
    return 'M%s %sA%s %s 0 0 0 %s %sQ%s %s %s %sZ' % (f(cx - rx), f(cy), f(rx), f(ry), f(cx + rx), f(cy), f(cx), f(cy + ry * 0.45), f(cx - rx), f(cy))


def ell_d(cx, cy, rx, ry):
    return 'M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ' % (f(cx - rx), f(cy), f(rx), f(ry), f(cx + rx), f(cy), f(rx), f(ry), f(cx - rx), f(cy))


def mirror(d):
    """Mirror an absolute path (M/L/C/Q/Z only, plain numbers) about x = 50."""
    out, nums, i = [], [], 0
    toks = []
    num = ''
    for ch in d:
        if ch.isalpha():
            if num:
                toks.append(num); num = ''
            toks.append(ch)
        elif ch in ' ,':
            if num:
                toks.append(num); num = ''
        elif ch == '-' and num:
            toks.append(num); num = ch
        else:
            num += ch
    if num:
        toks.append(num)
    res, k = [], 0
    for t in toks:
        if t.isalpha():
            res.append(t); k = 0
        else:
            v = float(t)
            res.append(f(100 - v) if k % 2 == 0 else f(v))
            k += 1
    s = ''
    for t in res:
        s += t if t.isalpha() else ((' ' if s and not s[-1].isalpha() else '') + t)
    return s


# --- caps and soft hats -------------------------------------------------------------------------------------------

CAP_CROWN = 'M23 30C22.6 5 77.4 5 77 30Q50 25.6 23 30Z'


@item('hat', 'cap_fwd', 'Cap', colors=['cloth: crown', 'cloth-deep: visor'], layer='front',
      notes='Crown dome y 11.2..30 over x 23..77, visor y 27..36.5 dips to just above the brows (37.5). front only; '
            'hair_clip_y 28 so tall hair (spiky) does not poke through. Front patch is a fixed cream disc.')
def cap_fwd(c):
    C = c.cloth
    s = sh(CAP_CROWN, C, 'cloth', ecls='cloth-edge')
    s += stroke('M38.5 27.6Q36 17 44.5 10.6', edge(C, 0.2), 0.8) + stroke('M61.5 27.6Q64 17 55.5 10.6', edge(C, 0.2), 0.8)
    s += dot(deep(C, 0.15), 50, 11.2, 1.8, cls='cloth-deep')
    s += dot('#FFF6E4', 50, 19.6, 3.4, edge(C, 0.25), 0.9)
    s += star(50, 19.8, 2, C, 5, 0.5, cls='cloth')
    s += hl(28, 11, 30, 22)
    v = 'M21.5 29.6Q50 24.6 78.5 29.6Q81 33.6 76.5 35Q50 39 23.5 35Q19 33.6 21.5 29.6Z'
    s += sh(v, deep(C, 0.18), 'cloth-deep', edge(C), ecls='cloth-edge')
    s += stroke('M25 33.2Q50 36.4 75 33.2', light(C, 0.25), 0.8, op=0.7)
    return dict(front=s, hair_clip_y=28)


@item('hat', 'cap_back', 'Backwards cap', colors=['cloth: crown', 'cloth-deep: visor'], layer='back + front',
      notes='Same crown as cap_fwd (y 11.2..30). back: the visor peeks above the crown (y ~1..14). front: crown with '
            'the snap-strap arch at the brow (x 43..57, y 21.5..28) filled with the HAIR colour (class hair). hair_clip_y 28.')
def cap_back(c):
    C = c.cloth
    back = sh('M31 15Q50 -4 69 15Q50 8 31 15Z', deep(C, 0.18), 'cloth-deep', edge(C), ecls='cloth-edge')
    s = sh(CAP_CROWN, C, 'cloth', ecls='cloth-edge')
    s += stroke('M38.5 27.6Q36 17 44.5 10.6', edge(C, 0.2), 0.8) + stroke('M61.5 27.6Q64 17 55.5 10.6', edge(C, 0.2), 0.8)
    s += dot(deep(C, 0.15), 50, 11.2, 1.8, cls='cloth-deep')
    s += sh('M43 28.2Q43 21.4 50 21.4Q57 21.4 57 28.2Q50 26.6 43 28.2Z', c.hair, 'hair', edge(C), ecls='cloth-edge')
    s += line(deep(C, 0.3), 42.6, 28, 57.4, 28, 1.8)
    s += dot('#D9DCE4', 47, 27.8, 0.8, '#8E93A3', 0.5) + dot('#D9DCE4', 53, 27.8, 0.8, '#8E93A3', 0.5)
    s += hl(28, 11, 30, 22)
    return dict(back=back, front=s, hair_clip_y=28)


@item('hat', 'beanie', 'Beanie', colors=['cloth: knit', 'cloth-deep: cuff'], layer='front',
      notes='Dome y 12.5..31 over x 22..78; ribbed cuff y 25.5..35 over x 20..80 (just above the brows). front; '
            'hair_clip_y 33 (only the fringe shows below the cuff). Small cream label tag on the cuff (right).')
def beanie(c):
    C = c.cloth
    s = sh('M22 31C21 5.5 79 5.5 78 31Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M38 14Q36 21 37 27', edge(C, 0.2), 0.8, op=0.7) + stroke('M62 14Q64 21 63 27', edge(C, 0.2), 0.8, op=0.7)
    s += hl(28, 10, 34, 24)
    s += sh('M24.5 25.5L75.5 25.5Q80 25.5 80 30.25Q80 35 75.5 35L24.5 35Q20 35 20 30.25Q20 25.5 24.5 25.5Z', deep(C, 0.12), 'cloth-deep', edge(C), ecls='cloth-edge')
    for i in range(9):
        x = 25 + i * 6.25
        s += line(edge(C, 0.25), x, 27.6, x, 33, 0.8)
    s += rrect('#FFF6E4', 64.2, 27.4, 5.2, 5, 1) + rrect(edge(C, 0.25), 64.2, 27.4, 5.2, 5, 1, st=True, sw=0.7)
    return dict(front=s, hair_clip_y=33)


@item('hat', 'bobble', 'Bobble hat', colors=['cloth: knit', 'cloth-light: cuff and bobble'], layer='front',
      notes='Taller knit dome y 7.5..31, contrasting cuff y 25..35, a fluffy bobble centred (50, 2) r 6.5. front; '
            'hair_clip_y 33. Knit grain = rows of small chevrons in the edge colour.')
def bobble(c):
    C = c.cloth
    L = light(C, 0.6)
    s = sh('M22.5 31C21 1 79 1 77.5 31Z', C, 'cloth', ecls='cloth-edge')
    for row, y in enumerate((12.5, 18, 23)):
        n = 4 + row
        for i in range(n):
            x = 50 + (i - (n - 1) / 2) * 7.2
            s += stroke('M%s %sL%s %sL%s %s' % (f(x - 1.8), f(y - 1.3), f(x), f(y + 0.4), f(x + 1.8), f(y - 1.3)), edge(C, 0.2), 0.8, op=0.75)
    s += hl(28, 7, 34, 26)
    s += sh('M25.5 25L74.5 25Q79.5 25 79.5 30Q79.5 35 74.5 35L25.5 35Q20.5 35 20.5 30Q20.5 25 25.5 25Z', L, 'cloth-light', edge(C, 0.25), ecls='cloth-edge')
    for i in range(9):
        x = 25.5 + i * 6.1
        s += line(edge(L, 0.18), x, 27, x, 33, 0.8)
    pts = [(50 + math.cos(math.radians(a)) * 5.6, 2 + math.sin(math.radians(a)) * 5.6) for a in range(0, 360, 45)]
    s += ''.join(circle(edge(L, 0.22), x, y, 2.6) for x, y in pts) + circle(edge(L, 0.22), 50, 2, 6.2)
    s += ''.join(circle(L, x, y, 2.0, cls='cloth-light') for x, y in pts) + circle(L, 50, 2, 5.7, cls='cloth-light')
    s += arc(edge(L, 0.2), 46, -1.5, 6, 6, 150, 140, 0.8) + arc(edge(L, 0.2), 49, 2, 5, 4, 180, 120, 0.8)
    return dict(front=s, hair_clip_y=33)


@item('hat', 'bucket', 'Bucket hat', colors=['cloth: crown', 'cloth-deep: brim'], layer='front',
      notes='Crown y 11.4..31 over x 25..75 on a sloped brim x 13..87, y 23..36.4. front; hair_clip_y 30. '
            'Two dashed stitch rows on the brim (stroke-dasharray 1.3 1.3, port as dots or skip below 48 px).')
def bucket(c):
    C = c.cloth
    D = deep(C, 0.14)
    brim = 'M13 31Q16 23.5 30 24Q50 26 70 24Q84 23.5 87 31Q84 36.6 70 35.4Q50 37.8 30 35.4Q16 36.6 13 31Z'
    s = sh(brim, D, 'cloth-deep', edge(C), ecls='cloth-edge')
    s += path('M17.5 31.4Q30 34.6 50 35.4Q70 34.6 82.5 31.4', None, edge(D, 0.2), 0.7, extra='stroke-dasharray="1.3 1.3"')
    s += sh('M25 30C25.4 4.5 74.6 4.5 75 30Q50 33 25 30Z', C, 'cloth', ecls='cloth-edge')
    s += path('M25.4 25Q50 28 74.6 25', None, light(C, 0.35), 2.4, cls='cloth-light')
    s += path('M27 21.6Q50 24.4 73 21.6', None, edge(C, 0.2), 0.7, extra='stroke-dasharray="1.3 1.3"')
    s += hl(29, 9, 32, 22)
    return dict(front=s, hair_clip_y=30)


@item('hat', 'beret', 'Beret', colors=['cloth'], layer='front',
      notes='A soft disc tilted toward the left, x 18..80, y 8..30.4, little stalk at (50, 5). Sits on the crown; '
            'the fringe shows below. front; hair_clip_y 24.')
def beret(c):
    C = c.cloth
    s = sh('M48.6 8.6L48.6 4.4Q50 3 51.4 4.4L51.4 8.6Z', deep(C, 0.15), 'cloth-deep', edge(C), ecls='cloth-edge')
    d = 'M21 27.6C12.6 22 21 8.4 46 8.4C68 8.4 82.4 13.6 79.4 23.2C77.4 29.2 64 30.4 50 30.4C37 30.4 26 30.4 21 27.6Z'
    s += sh(d, C, 'cloth', ecls='cloth-edge')
    s += stroke('M24 27.2Q50 32.4 76.6 25.2', deep(C, 0.18), 2, cls='cloth-deep')
    s += stroke('M33 15Q46 11.2 60 12.6', light(C, 0.3), 1, op=0.6)
    s += hl(22, 10, 34, 18, 200, 55)
    return dict(front=s, hair_clip_y=24)


@item('hat', 'sun_hat', 'Sun hat', colors=['cloth: ribbon'], layer='front',
      notes='Fixed straw (#F2D48A). Floppy brim x 6..94, y 19.6..35.8 (stops above the brows), crown x 28..72 '
            'y 11..30, ribbon band in the user colour with a bow on the right. front; hair_clip_y 28. '
            'Widest hat: at x 6..94 it clears long/afro hair.')
def sun_hat(c):
    C = c.cloth
    S = '#F2D48A'
    brim = 'M6 29.6Q8 21.6 28 20.6Q50 18.8 72 20.6Q92 21.6 94 29.6Q90 35.6 72 34.8Q50 36.8 28 34.8Q10 35.6 6 29.6Z'
    s = sh(brim, S)
    for k in (0, 1):
        s += stroke('M%s 30Q50 %s %s 30' % (f(12 + k * 7), f(33.4 + k * 0.6 - k * 2.2), f(88 - k * 7)), edge(S, 0.2), 0.7, op=0.55)
    s += sh('M28 29.2C28 5.6 72 5.6 72 29.2Q50 31.4 28 29.2Z', S)
    s += sh('M28.3 25.2Q50 27.6 71.7 25.2L72 29.2Q50 31.4 28 29.2Z', C, 'cloth', ecls='cloth-edge', w=0.9)
    s += sh('M70 27.4C74 22.4 79.6 24 78.2 27.8C79.6 31.6 74 32.6 70 27.4Z', C, 'cloth', ecls='cloth-edge', w=0.9)
    s += sh('M70 27.4C66 22.4 61 24 62 27.8C61 31.6 66 32.6 70 27.4Z', C, 'cloth', ecls='cloth-edge', w=0.9)
    s += dot(deep(C, 0.1), 70, 27.6, 1.7, edge(C), 0.8, cls='cloth-deep')
    s += hl(31, 10, 30, 20)
    return dict(front=s, hair_clip_y=28)


@item('hat', 'cowboy', 'Cowboy hat', colors=['cloth: felt', 'cloth-deep: brim'], layer='front',
      notes='Pinched crown x 28.5..71.5, y 6.6..29 on an up-curled brim x 7..93, y 22.6..33.6. Drawn brim, crown, '
            'dark band (deep 0.45) with a silver concho, then the brim\'s near lip again so it wraps the crown. '
            'front; hair_clip_y 28.')
def cowboy(c):
    C = c.cloth
    D = deep(C, 0.14)
    brim = 'M7 23.4Q10 33 26 32Q50 35.2 74 32Q90 33 93 23.4Q88 28.4 74 27.4Q50 25.8 26 27.4Q12 28.4 7 23.4Z'
    lip = 'M7 23.4Q10 33 26 32Q50 35.2 74 32Q90 33 93 23.4Q88 30.4 74 30Q50 29 26 30Q12 30.4 7 23.4Z'
    s = sh(brim, D, 'cloth-deep', edge(C), ecls='cloth-edge')
    s += sh('M28.5 29C28 18 29 7.6 37 6.8Q44 6.4 50 10.8Q56 6.4 63 6.8C71 7.6 72 18 71.5 29Q50 26.6 28.5 29Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M50 10.8Q49 15 50 19', edge(C, 0.25), 0.9)
    s += sh('M29 24.2Q50 22.2 71 24.2L71.5 28.8Q50 26.6 28.5 28.8Z', deep(C, 0.45), 'cloth-deep', w=0.8)
    s += dot('#DCE1EA', 50, 25.6, 1.5, '#8E93A3', 0.6)
    s += sh(lip, D, 'cloth-deep', edge(C), ecls='cloth-edge')
    s += hl(31, 9, 26, 20)
    return dict(front=s, hair_clip_y=28)


@item('hat', 'top_hat', 'Top hat', colors=['cloth: band'], layer='front',
      notes='Fixed charcoal (#3B3445) silk, tall: body x 31.5..68.5 from y -4.5 to 27, brim x 20..80 y 23..33.5, '
            'band (user colour) y 18.6..26.5. Rises 20 units above the crown: check sheet crops allow y -6. '
            'front; hair_clip_y 28.')
def top_hat(c):
    C = c.cloth
    K = '#3B3445'
    e = '#1F1A28'
    s = sh('M33 27L31.5 -2Q50 -4.6 68.5 -2L67 27Z', K, e=e)
    s += sh(ell_d(50, -2.2, 18.5, 3), '#4B4358', e=e)
    s += sh('M32.6 19.6Q50 18 67.4 19.6L67 26.4Q50 24.8 33 26.4Z', C, 'cloth', ecls='cloth-edge', w=0.9)
    s += sh('M20 27Q20 23 33 25.6Q50 27.2 67 25.6Q80 23 80 27Q80 33.4 50 33.6Q20 33.4 20 27Z', K, e=e)
    s += line(WHITE, 36.4, 2, 36.8, 17, 2, op=0.16)
    s += stroke('M24 28.4Q50 32 76 28.4', '#5A5268', 0.8)
    return dict(front=s, hair_clip_y=28)


@item('hat', 'wizard', 'Wizard hat', colors=['cloth: cone and brim'], layer='front',
      notes='Cone x 29..71 from y 27 up to a soft tip that flops right at (66, -4); brim ellipse c(50, 28) '
            'rx 33 ry 5.5 and its near lip redrawn over the cone. Fixed gold stars and moon. front; hair_clip_y 28.')
def wizard(c):
    C = c.cloth
    D = deep(C, 0.14)
    s = sh(ell_d(50, 28, 33, 5.5), D, 'cloth-deep', edge(C), ecls='cloth-edge')
    s += sh('M29 28C36 17 42 4 50 -3C55 -7.4 62.4 -6.6 66.4 -3.4C60.4 -3.4 56.4 1.6 57.2 9C60 16 66 22 71 28Z', C, 'cloth', ecls='cloth-edge')
    s += sh(lower_lip(50, 28, 33, 5.5), D, 'cloth-deep', edge(C), ecls='cloth-edge')
    gold, ge = '#FFD86B', '#D99A2E'
    s += star(41.5, 16, 3.2, gold, 5, 0.45, ge) + star(53, 6, 2.2, gold, 5, 0.45, ge) + star(46, 24.4, 1.4, gold, 4, 0.4)
    s += circle(ge, 58, 19, 3.3) + circle(gold, 58, 19, 2.7) + circle(C, 59.6, 17.8, 2.6, cls='cloth')
    s += dot(gold, 66.4, -3.2, 1.6, ge, 0.7)
    s += hl(34, 4, 20, 26, 215, 40)
    return dict(front=s, hair_clip_y=28)


@item('hat', 'witch', 'Witch hat', colors=['cloth: cone and brim'], layer='front',
      notes='A crooked cone x 31..69 rising to a kinked tip at (52, -8), dark band y 22..27 with a gold buckle, '
            'flat brim c(50, 28) rx 35 ry 5. front; hair_clip_y 28.')
def witch(c):
    C = c.cloth
    D = deep(C, 0.14)
    s = sh(ell_d(50, 28, 35, 5), D, 'cloth-deep', edge(C), ecls='cloth-edge')
    s += sh('M31 28C38 18 42 9 44 1L41 -6L52 -8.4C51 0 56 13 69 28Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M44 1Q47 0.4 50 1.6', edge(C, 0.25), 0.8)
    s += sh('M33.4 23Q50 20.8 66.6 23L69 27.6Q50 25.4 31 27.6Z', deep(C, 0.45), 'cloth-deep', w=0.8)
    s += rrect('#F7C948', 45.8, 21.4, 8.4, 5.6, 1.2) + rrect('#C9952A', 45.8, 21.4, 8.4, 5.6, 1.2, st=True, sw=0.9)
    s += rrect(deep(C, 0.45), 48.2, 23, 3.6, 2.6, 0.6)
    s += sh(lower_lip(50, 28, 35, 5), D, 'cloth-deep', edge(C), ecls='cloth-edge')
    s += hl(36, 2, 18, 26, 215, 40)
    return dict(front=s, hair_clip_y=28)


# --- jewellery and things worn on the hair --------------------------------------------------------------------------

@item('hat', 'crown', 'Crown', colors=[], layer='front',
      notes='Fixed gold (#F7C948) five-point crown x 31..69, y 0..26 with ball tips and three gems on the band '
            '(y 21..26). Sits on top of any hair (no clip).')
def crown(c):
    G, GE = '#F7C948', '#B98A22'
    s = sh('M33 26.4L31 6.4L40.6 14.6L50 2.4L59.4 14.6L69 6.4L67 26.4Q50 23.8 33 26.4Z', G, e=GE)
    s += sh('M33.5 21.2Q50 18.8 66.5 21.2L67 26.4Q50 23.8 33 26.4Z', '#E5AE2F', e=GE, w=0.9)
    s += dot(G, 31, 6.2, 1.8, GE, 0.8) + dot(G, 50, 2.2, 2, GE, 0.8) + dot(G, 69, 6.2, 1.8, GE, 0.8)
    s += dot('#FF6F86', 50, 22.6, 2.1, '#C2405A', 0.8) + dot('#7FC8FF', 41.6, 23.2, 1.5, '#3F86C2', 0.7) + dot('#8FE3B0', 58.4, 23.2, 1.5, '#3F9E66', 0.7)
    s += line(WHITE, 36, 10, 37, 18, 1.4, op=0.4)
    return dict(front=s)


@item('hat', 'tiara', 'Tiara', colors=[], layer='front',
      notes='Fixed silver arch from (29.5, 27) over (50, 19) to (70.5, 27) with a tall centre peak to y 7.6 and a '
            'pink gem. front, no clip. The band follows the crown at ~y 21-27.')
def tiara(c):
    S, SE = '#E3E8F2', '#8E97AE'
    s = stroke('M29.5 27.4Q50 15 70.5 27.4', SE, 4)
    s += stroke('M29.5 27.4Q50 15 70.5 27.4', S, 2.4)
    s += sh('M42.6 21.4Q50 4 57.4 21.4Q50 19 42.6 21.4Z', S, e=SE, w=0.9)
    s += sh('M34 25Q36.2 14.6 41.6 22.6Q37.6 23.2 34 25Z', S, e=SE, w=0.9)
    s += sh('M66 25Q63.8 14.6 58.4 22.6Q62.4 23.2 66 25Z', S, e=SE, w=0.9)
    s += sh('M50 10.4Q53.4 14.4 50 17.6Q46.6 14.4 50 10.4Z', '#FF8FC0', e='#C4487E', w=0.8)
    s += dot('#9FD9F2', 37.4, 20.6, 0.9, '#4A8FB5', 0.5) + dot('#9FD9F2', 62.6, 20.6, 0.9, '#4A8FB5', 0.5)
    s += circle(WHITE, 49.2, 12.8, 0.7, op=0.9)
    return dict(front=s)


def flower(x, y, r, petal, centre='#FFD86B'):
    pe = edge(petal, 0.28)
    s = ''
    pts = [(x + math.cos(math.radians(a - 90)) * r * 0.95, y + math.sin(math.radians(a - 90)) * r * 0.95) for a in range(0, 360, 72)]
    s += ''.join(circle(pe, px, py, r * 0.78 + 0.45) for px, py in pts)
    s += ''.join(circle(petal, px, py, r * 0.78 - 0.3) for px, py in pts)
    s += dot(centre, x, y, r * 0.5, edge(centre, 0.3), 0.6)
    return s


@item('hat', 'flower_crown', 'Flower crown', colors=['cloth: every third bloom'], layer='front',
      notes='Seven blooms on the arc c(50, 30) rx 26 ry 11 (from x 24 to 76, top y 19), leaves between. '
            'Pink, cream and the user colour. front, no clip (hair shows through the gaps).')
def flower_crown(c):
    leaf = '#7CC98F'
    s = ''
    angs = [196, 218, 240, 270, 300, 322, 344]
    for a in [207, 229, 255, 285, 311, 333]:
        x, y = 50 + math.cos(math.radians(a)) * 26, 30 + math.sin(math.radians(a)) * 11
        s += '<ellipse cx="%s" cy="%s" rx="3.4" ry="1.7" fill="%s" stroke="%s" stroke-width="0.7" transform="rotate(%s %s %s)"/>' % (
            f(x), f(y - 1), leaf, edge(leaf), f(a + 90 + 25), f(x), f(y - 1))
    cols = ['#FFB3D1', '#FFF6E4', c.cloth, '#FFB3D1', '#FFF6E4', c.cloth, '#FFB3D1']
    for i, a in enumerate(angs):
        x, y = 50 + math.cos(math.radians(a)) * 26, 30 + math.sin(math.radians(a)) * 11
        r = 3.6 if i == 3 else 3.1
        s += flower(x, y, r, cols[i])
    return dict(front=s)


@item('hat', 'bow', 'Big bow', colors=['cloth'], layer='front',
      notes='A big ribbon bow on the top-right of the head, knot at (64, 17), loops x 49..79, y 9..25, short tails '
            'to y 29. front, no clip. Pairs well with any long style.')
def bow(c):
    C = c.cloth
    e = edge(C)
    s = sh('M62 18.4L57.4 28.6L60.6 27.6L62.6 30L65 19.4Z', deep(C, 0.08), 'cloth-deep', e, w=0.9, ecls='cloth-edge')
    s += sh('M66 18.4L70.6 28.6L67.4 27.6L65.4 30L63 19.4Z', deep(C, 0.08), 'cloth-deep', e, w=0.9, ecls='cloth-edge')
    s += sh('M64 17C56 6.6 48 8.6 50 17C48 25.4 56 27.4 64 17Z', C, 'cloth', ecls='cloth-edge')
    s += sh('M64 17C72 6.6 80 8.6 78 17C80 25.4 72 27.4 64 17Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M54 13Q57 15.6 59.6 16.4', edge(C, 0.2), 0.8) + stroke('M74 13Q71 15.6 68.4 16.4', edge(C, 0.2), 0.8)
    s += rrect(e, 60.8, 13.6, 6.4, 7, 2.4) + rrect(deep(C, 0.1), 61.5, 14.3, 5, 5.6, 2, cls='cloth-deep')
    s += arc(WHITE, 51, 10, 10, 10, 200, 70, 1.6, op=0.3)
    return dict(front=s)


@item('hat', 'headphones', 'Headphones', colors=['cloth: ear cups'], layer='front',
      notes='Dark band arching from the cups to y ~8 above the crown; cups x 17.5..28.5 / 71.5..82.5, y 35..54 '
            'covering the ears. front, no clip.')
def headphones(c):
    C = c.cloth
    band = '#3A3346'
    s = stroke('M21.6 40C18 1 82 1 78.4 40', band, 4.4) + stroke('M21.6 40C18 1 82 1 78.4 40', '#5A5268', 1.6)
    s += stroke('M33 10Q40 6.6 47 6', WHITE, 1, op=0.3)
    for side in (-1, 1):
        x = 50 + side * 27
        s += rrect(band, x - 5.6, 34.6, 11.2, 19.6, 5.6)
        s += rrect(C, x - 4, 36.4, 8, 16, 4, cls='cloth')
        s += rrect(light(C, 0.35), x - 2.4, 39, 4.8, 10.8, 2.4, cls='cloth-light')
        s += line(WHITE, x - 1.2, 39.4, x - 1.2, 43, 1.1, op=0.5)
        s += rrect(band, x - 2.2, 31.6, 4.4, 4.6, 1.4)
    return dict(front=s)


def band_arc(c, w=3.0, y=34):
    C = c.cloth
    d = 'M23.4 %sC24.6 8.6 75.4 8.6 76.6 %s' % (f(y), f(y))
    return stroke(d, edge(C), w + 1.6, cls='cloth-edge') + stroke(d, C, w, cls='cloth') + stroke(d, WHITE, 0.9, op=0.3)


@item('hat', 'cat_ears', 'Cat ears', colors=['cloth: band and ears'], layer='front',
      notes='Headband arc from (23.4, 34) over y ~15 to (76.6, 34); pointed ears x 25..43 / 57..75 rising to y 1.6 '
            'with pink inner ears. front, no clip.')
def cat_ears(c):
    C = c.cloth
    s = ''
    for d in ('M25.4 21.6Q25 7 28.6 1.6Q37 5.2 43 14.4Z',):
        for dd in (d, mirror(d)):
            s += sh(dd, C, 'cloth', ecls='cloth-edge')
    for d in ('M28.4 17Q28.2 9.4 29.8 6.6Q34 9.2 37.6 13.8Z',):
        for dd in (d, mirror(d)):
            s += path(dd, '#FFB3C8')
    s += band_arc(c)
    return dict(front=s)


@item('hat', 'bunny_ears', 'Bunny ears', colors=['cloth: band'], layer='front',
      notes='Fixed cream ears (#F6F1E7, pink inner) on a user-colour headband; left ear upright (tilted -12 deg about '
            '(40, 18)), right ear flopped forward at its tip. Tops reach y -7. front, no clip.')
def bunny_ears(c):
    E, EE, P = '#F6F1E7', '#BDB3C4', '#FFB3C8'
    s = '<g transform="rotate(-12 40 18)">' + sh('M35 18L35 -2Q35 -7.4 40 -7.4Q45 -7.4 45 -2L45 18Z', E, e=EE) + \
        rrect(P, 37.6, -3.8, 4.8, 18, 2.4) + '</g>'
    s += '<g transform="rotate(12 60 18)">' + sh('M55 18L55 4Q55 -1 60 -1Q65 -1 65 4L65 18Z', E, e=EE) + \
        rrect(P, 57.6, 3, 4.8, 13, 2.4) + sh('M55.4 3.6Q60 -3 66.4 -1.2Q72 1 71.6 8Q67 5 60 6.2Z', E, e=EE) + '</g>'
    s += band_arc(c)
    return dict(front=s)


@item('hat', 'bear_ears', 'Bear ears', colors=['cloth: band and ears', 'cloth-light: inner ears'], layer='front',
      notes='Round ears r 7 at (30, 15) and (70, 15) with lighter inner ears, on the same band arc as cat_ears. '
            'front, no clip.')
def bear_ears(c):
    C = c.cloth
    s = ''
    for x in (30, 70):
        s += dot(C, x, 15, 7, cls='cloth') + circle(light(C, 0.45), x + (1 if x < 50 else -1), 16, 3.8, cls='cloth-light')
    s += band_arc(c)
    return dict(front=s)


# --- work and play ---------------------------------------------------------------------------------------------------

@item('hat', 'chef', 'Chef hat', colors=[], layer='front',
      notes='Fixed white toque: cloud puff from three circles (centres y 8..14, r 9-10.5) over a pleated band '
            'x 27..73, y 21.6..31. front; hair_clip_y 29.')
def chef(c):
    W, E = '#FFFFFF', '#C3BCCF'
    puff = [(35.6, 14, 9.2), (50, 7.6, 10.6), (64.4, 14, 9.2), (43, 16, 7), (57, 16, 7)]
    s = ''.join(circle(E, x, y, r + 0.6) for x, y, r in puff) + ''.join(circle(W, x, y, r - 0.5) for x, y, r in puff)
    s += arc(E, 40, 4, 10, 10, 160, 100, 0.8, op=0.8) + arc(E, 57, 8, 10, 10, 200, 90, 0.8, op=0.8)
    s += sh('M27 22.4Q50 20 73 22.4L73 30Q50 27.8 27 30Z', '#F7F5FA', e=E)
    for x in (35, 42.5, 50, 57.5, 65):
        s += line(E, x, 23.2, x, 28.4, 0.7, op=0.8)
    return dict(front=s, hair_clip_y=29)


@item('hat', 'hard_hat', 'Hard hat', colors=['cloth: shell', 'cloth-deep: brim'], layer='front',
      notes='Shell dome x 24..76, y 11.4..29 with a raised centre ridge, a front brim x 18..82, y 25..33. Classic '
            'yellow when the user picks yellow. front; hair_clip_y 31.')
def hard_hat(c):
    C = c.cloth
    s = sh('M24 29C23 5.6 77 5.6 76 29Z', C, 'cloth', ecls='cloth-edge')
    s += sh('M46.4 28L46.4 10.6Q50 9 53.6 10.6L53.6 28Z', light(C, 0.22), 'cloth-light', edge(C), w=0.9, ecls='cloth-edge')
    s += stroke('M33 27Q32 18 37 12.6', edge(C, 0.2), 0.8) + stroke('M67 27Q68 18 63 12.6', edge(C, 0.2), 0.8)
    s += hl(27, 10, 18, 22, 205, 55)
    s += sh('M18 28.6Q50 24.6 82 28.6Q83.2 32.4 79 33Q50 30.4 21 33Q16.8 32.4 18 28.6Z', deep(C, 0.12), 'cloth-deep', edge(C), ecls='cloth-edge')
    s += dot('#FFFFFF', 60.4, 21, 2.4, edge(C, 0.3), 0.7) + line('#4CAF7A', 59, 21, 61.8, 21, 1) + line('#4CAF7A', 60.4, 19.6, 60.4, 22.4, 1)
    return dict(front=s, hair_clip_y=31)


@item('hat', 'pilot', 'Pilot cap', colors=['cloth: goggle strap'], layer='front',
      notes='Fixed brown leather (#8B5E3C) aviator cap covering the head to the ear flaps (x 21..79, down to y 58), '
            'cream fleece trim round the face opening (top y 28), goggles on the forehead at y 22.6 (lenses r 5.4 at '
            'x 40.6/59.4) on a user-colour strap. front; hair_clip_y 29 (the fringe shows in the opening). '
            'Long hair keeps its back layer below the flaps.')
def pilot(c):
    C = c.cloth
    Lr, LE, F = '#8B5E3C', '#5C3B25', '#F3E6CF'
    d = 'M21 44C19 6.6 81 6.6 79 44L79 54Q79 58.4 75 58.4L72.6 58.4Q72 50 72 44Q70 29.6 50 28Q30 29.6 28 44Q28 50 27.4 58.4L25 58.4Q21 58.4 21 54Z'
    s = sh(d, Lr, e=LE)
    s += stroke('M50 9.4L50 20', LE, 0.8, op=0.8) + stroke('M30 15Q38 10.8 48 10', WHITE, 2, op=0.18)
    s += stroke('M27.6 57.6Q28 50 28 44Q30 29.6 50 28Q70 29.6 72 44Q72 50 72.4 57.6', edge(F, 0.2), 3.6)
    s += stroke('M27.6 57.6Q28 50 28 44Q30 29.6 50 28Q70 29.6 72 44Q72 50 72.4 57.6', F, 2.4)
    s += stroke('M22 24Q50 18.6 78 24', edge(C), 3.8, cls='cloth-edge') + stroke('M22 24Q50 18.6 78 24', C, 2.4, cls='cloth')
    for x in (40.6, 59.4):
        s += circle('#6E6A78', x, 22.6, 6) + circle('#B9C2D0', x, 22.6, 5.1) + circle('#9FD9F2', x, 22.6, 4)
        s += arc(WHITE, x - 3, 19.6, 6, 6, 200, 70, 1.2, op=0.8)
    s += rrect('#6E6A78', 46, 21.2, 8, 2.8, 1.2)
    return dict(front=s, hair_clip_y=29)


@item('hat', 'explorer', 'Explorer hat', colors=['cloth: band'], layer='front',
      notes='Fixed khaki pith helmet (#D8C08C): dome x 27..73, y 10.8..28, all-round brim x 15..85, y 24..34, '
            'user-colour band y 22.4..29. front; hair_clip_y 28.')
def explorer(c):
    C = c.cloth
    K = '#D8C08C'
    s = sh('M15 29Q16 24 28 25.6Q50 27 72 25.6Q84 24 85 29Q80 34.2 50 34.2Q20 34.2 15 29Z', deep(K, 0.08))
    s += sh('M27 28C26 5 74 5 73 28Z', K)
    s += stroke('M38 27Q36.4 16 43 11.6', edge(K, 0.2), 0.8) + stroke('M62 27Q63.6 16 57 11.6', edge(K, 0.2), 0.8)
    s += sh('M27.2 22.6Q50 24.6 72.8 22.6L73 27.6Q50 29.8 27 27.6Z', C, 'cloth', ecls='cloth-edge', w=0.9)
    s += sh(lower_lip(50, 29, 35, 5.2), deep(K, 0.08))
    s += dot(K, 50, 10.4, 1.8, edge(K), 0.8)
    s += hl(29, 9, 26, 20)
    return dict(front=s, hair_clip_y=28)


@item('hat', 'grad_cap', 'Graduation cap', colors=['cloth: tassel'], layer='front',
      notes='Fixed near-black skull cap (y 18..30) and a flat board drawn as a diamond (50,4)-(82,13)-(50,22)-(18,13); '
            'user-colour tassel hangs from the button to (76, 33). front; hair_clip_y 26.')
def grad_cap(c):
    C = c.cloth
    K, KE = '#2F2A3A', '#17121F'
    s = sh('M28 30C28 17 72 17 72 30Q50 27 28 30Z', K, e=KE)
    s += sh('M50 3.6L82.4 13L50 22.4L17.6 13Z', '#3D3649', e=KE)
    s += stroke('M50 22.4L17.6 13', '#4E465C', 1, op=0.8)
    s += stroke('M50 13Q66 12.6 76.4 15.4L76.4 26', edge(C), 1.4, cls='cloth-edge') + stroke('M50 13Q66 12.6 76.4 15.4L76.4 26', C, 0.8, cls='cloth')
    s += sh('M74.4 25.6L78.4 25.6L79.4 33.4Q76.4 34.6 73.4 33.4Z', C, 'cloth', ecls='cloth-edge', w=0.8)
    s += dot(C, 50, 13, 1.6, cls='cloth')
    s += stroke('M30 12Q40 8.4 50 6', WHITE, 1.2, op=0.2)
    return dict(front=s, hair_clip_y=26)


@item('hat', 'party_hat', 'Party hat', colors=['cloth: cone', 'cloth-light: dots'], layer='front',
      notes='A cone tilted 12 deg to the right (rotate(12 56 27)), base x 42..70 at y 27, tip with a yellow pompom '
            'at (56, -4). Sits off-centre on the crown; no clip.')
def party_hat(c):
    C = c.cloth
    s = sh('M42 27L56 -3L70 27Q56 30.4 42 27Z', C, 'cloth', ecls='cloth-edge')
    for x, y, r in ((55, 6, 1.4), (52, 14, 1.6), (60, 15.5, 1.6), (48, 22.4, 1.7), (56.4, 23.4, 1.7), (64, 23, 1.6)):
        s += circle(light(C, 0.6), x, y, r, cls='cloth-light')
    s += stroke('M42 27Q56 30.4 70 27', '#FFFFFF', 2.2) + stroke('M42 27Q56 30.4 70 27', '#FFFFFF', 0.6)
    pts = [(56 + math.cos(math.radians(a)) * 2.6, -4 + math.sin(math.radians(a)) * 2.6) for a in range(0, 360, 60)]
    s += ''.join(circle('#D9A63A', x, y, 2) for x, y in pts) + ''.join(circle('#FFE27A', x, y, 1.5) for x, y in pts)
    s += circle('#FFE27A', 56, -4, 2.6)
    s += stroke('M51 6Q48 13 45.6 21', WHITE, 1.6, op=0.3)
    return dict(front='<g transform="rotate(12 56 27)">%s</g>' % s)


@item('hat', 'headband', 'Sports headband', colors=['cloth: band'], layer='front',
      notes='A towelling sweatband across the brow, from (22.6, 26) curving up to y 21.6 at the centre and down to '
            '(77.4, 26), 6.5 deep (bottom at y 28.4..32.6), with a white stripe. Hair shows above; no clip.')
def headband(c):
    C = c.cloth
    s = sh('M22.6 26Q50 18.4 77.4 26L77.4 32.6Q50 25 22.6 32.6Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M22.8 29.3Q50 21.8 77.2 29.3', WHITE, 1.4, op=0.95)
    s += stroke('M28 26.6Q38 23.4 46 22.6', WHITE, 1, op=0.3)
    return dict(front=s)


@item('hat', 'bandana', 'Bandana', colors=['cloth: kerchief'], layer='front',
      notes='A kerchief over the crown (x 22..78, y 12.6..34, hem dips to y ~30.5 at centre) with white polka dots '
            'and a knot plus two tails at the right temple (x 77..88, y 28..44). front; hair_clip_y 32.')
def bandana(c):
    C = c.cloth
    s = sh('M77 30Q86 27.6 88 34Q83 33 79 34.4Z', deep(C, 0.08), 'cloth-deep', edge(C), ecls='cloth-edge')
    s += sh('M77.4 32Q84.4 38 82.4 44.4Q79 39.6 76.6 35.6Z', deep(C, 0.08), 'cloth-deep', edge(C), ecls='cloth-edge')
    s += sh('M22 34C20 7.6 80 7.6 78 34Q50 27.4 22 34Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M23 32.2Q50 26 77 32.2', deep(C, 0.2), 1.2, cls='cloth-deep')
    for x, y in ((33, 21), (43, 15.6), (54, 14), (64, 18.6), (38, 27.6), (49, 22.6), (59, 25.4), (70, 25), (28.4, 28.6)):
        s += circle(WHITE, x, y, 1.15, op=0.92)
    s += dot(deep(C, 0.06), 77.6, 31.4, 2.8, edge(C), cls='cloth-deep')
    s += hl(28, 11, 30, 22)
    return dict(front=s, hair_clip_y=32)


@item('hat', 'hood', 'Hood up', colors=['cloth: hood', 'cloth-deep: lining'], layer='behind_head + front',
      notes='behind_head: the hood\'s back, x 14..86, y 3..70 (over the body, under the head: it hides long hair\'s '
            'back layer, as a real hood would). front: a thick rim (stroke 5.8 over 7.4 edge) along the face from '
            '(23.5, 60) over y 12.5 to (76.5, 60), covering the ears. hair_clip_y 16: only the fringe shows. Port: draw '
            'the rim as a stroked path like the arms. Best paired with the hoodie top.')
def hood(c):
    C = c.cloth
    back = sh('M50 3C74 3 87 20 86 42C86 56 80.6 66 71 70.4L29 70.4C19.4 66 14 56 14 42C13 20 26 3 50 3Z', deep(C, 0.1), 'cloth-deep', edge(C), ecls='cloth-edge')
    back += hl(20, 5, 40, 30, 210, 45)
    rim = 'M23.6 61L23.6 40C23.6 22 34 12.6 50 12.6C66 12.6 76.4 22 76.4 40L76.4 61'
    front = stroke(rim, edge(C), 7.6, cls='cloth-edge') + stroke(rim, C, 5.8, cls='cloth')
    front += stroke('M28 24Q36 14.6 48 13.4', WHITE, 1.4, op=0.28)
    return dict(behind_head=back, front=front, hair_clip_y=16)


# --- costume ---------------------------------------------------------------------------------------------------------

@item('hat', 'viking', 'Viking helmet', colors=['cloth: brow band'], layer='front',
      notes='Fixed steel dome (#B8BCC8) x 23..77, y 12.6..31 with a centre ridge and a short nose guard (to y 40, '
            'between the brows), user-colour riveted band y 25.6..32, cream horns sweeping up-out to (12, 2)/(88, 2). '
            'front; hair_clip_y 31. Horns are friendly (no realism).')
def viking(c):
    C = c.cloth
    M, ME, H = '#B8BCC8', '#6F7487', '#F3E6CF'
    horns = ''
    for dd in ('M25.6 22.6C17 21 10.4 12.6 12 1.6C15.4 9 20.4 12.6 28.4 14.6Z', mirror('M25.6 22.6C17 21 10.4 12.6 12 1.6C15.4 9 20.4 12.6 28.4 14.6Z')):
        horns += sh(dd, H, e='#B8A27E')
    horns += stroke('M17.4 16Q19.4 13.6 19 10.4', '#D9C7A6', 0.8) + stroke('M82.6 16Q80.6 13.6 81 10.4', '#D9C7A6', 0.8)
    s = horns + sh('M23 31C22 5.6 78 5.6 77 31Z', M, e=ME)
    s += sh('M47.4 28L47.4 10Q50 8.6 52.6 10L52.6 28Z', '#CDD1DB', e=ME, w=0.9)
    s += sh('M47.6 31L52.4 31L51.6 40L48.4 40Z', M, e=ME, w=0.9)
    s += sh('M24.6 25.6L75.4 25.6Q79 25.6 79 28.8Q79 32 75.4 32L24.6 32Q21 32 21 28.8Q21 25.6 24.6 25.6Z', C, 'cloth', ecls='cloth-edge')
    for x in (27, 35, 43, 57, 65, 73):
        s += dot('#E3E6EE', x, 28.8, 0.9, ME, 0.5)
    s += hl(27, 10, 18, 22, 205, 55, op=0.4)
    return dict(front=s, hair_clip_y=31)


@item('hat', 'astronaut', 'Space helmet', colors=['cloth: comms cap'], layer='behind_head + front',
      notes='A clear bubble c(50, 42) r 35 (x 15..85, y 7..77; fill #CFEFFF at 0.22) over a snug user-colour comms cap '
            '(y 13..34) with ear pucks, a white neck ring y 68.6..78 in behind_head (under the chin) and a little antenna. hides_hair all (hair would '
            'poke out of the glass). Port: the neck ring sits on the body, so draw it outside Head.crown().')
def astronaut(c):
    C = c.cloth
    s = sh('M25 35C23.6 12.4 76.4 12.4 75 35Q50 27 25 35Z', C, 'cloth', ecls='cloth-edge')
    s += stroke('M50 15.6L50 30', edge(C, 0.2), 0.8)
    for x in (24, 76):
        s += dot(C, x, 44, 3.6, cls='cloth') + circle(light(C, 0.4), x, 44, 1.6, cls='cloth-light')
    ring = rrect('#A5AEBF', 30.4, 68.6, 39.2, 9.4, 4.7) + rrect('#F4F6FA', 31.2, 69.4, 37.6, 7.8, 3.9)
    ring += line('#C9D0DC', 35, 74.6, 65, 74.6, 0.8)
    s += line('#8FA6BF', 72, 16, 79, 5, 1.2) + dot('#FF6F86', 79.4, 4.4, 1.8, '#C2405A', 0.7)
    s += circle('#CFEFFF', 50, 42, 35, op=0.22)
    s += circle('#8FA6BF', 50, 42, 35.9, st=True, w=1.1) + circle('#EAF6FF', 50, 42, 34.9, st=True, w=1.8)
    s += arc(WHITE, 20, 11, 50, 50, 200, 55, 3, op=0.7) + circle(WHITE, 72, 26, 1.6, op=0.8)
    return dict(behind_head=ring, front=s, hides_hair='all')


@item('hat', 'halo', 'Halo', colors=[], layer='front',
      notes='A gold ring floating above the head, ellipse c(50, 3) rx 16 ry 4, stroked (4.4 edge, 2.8 gold) with two '
            'sparkles. front, no clip. It floats on purpose.')
def halo(c):
    s = oval('#D9A63A', 50, 3, 16, 4, st=True, w=4.4) + oval('#FFE27A', 50, 3, 16, 4, st=True, w=2.8)
    s += arc(WHITE, 36, -0.6, 18, 7, 200, 60, 1, op=0.9)
    s += star(70, -3, 2.4, '#FFE27A', 4, 0.4) + star(30, 7.6, 1.6, '#FFFFFF', 4, 0.4)
    return dict(front=s)


@item('hat', 'antlers', 'Antlers', colors=['cloth: band'], layer='front',
      notes='Soft brown antlers (stroked beams w 3 over 4.6 edge) from the band at x 34/66 up to y -2, two tines each, '
            'on the cat_ears band arc. front, no clip.')
def antlers(c):
    B, BE = '#A0714C', '#6C4A30'
    beams = ['M35 20Q30.6 10 25.4 2', 'M30.6 11.6Q24.6 10.4 21 6', 'M28 7Q31.6 3 31.4 -1.6']
    s = ''
    for d in beams + [mirror(b) for b in beams]:
        s += stroke(d, BE, 4.6)
    for d in beams + [mirror(b) for b in beams]:
        s += stroke(d, B, 3)
    s += stroke('M33 16Q30.4 10 27.6 5.6', WHITE, 0.8, op=0.3) + stroke('M67 16Q69.6 10 72.4 5.6', WHITE, 0.8, op=0.3)
    s += band_arc(c)
    return dict(front=s)


@item('hat', 'hijab', 'Hijab', colors=['cloth: wrap'], layer='front',
      notes='A smooth wrap: outer from (50, 9) round the head (x 19.5..80.5) and down over the shoulders to y ~88, '
            'with an oval face opening c(50, 46.5) rx 21 ry 23.5 (fill-rule evenodd), so the face, brows and blush show '
            'and ears, hair and neck are covered. One soft fold on the drape, a highlight on the crown. hides_hair all. '
            'Port: place the face opening from the Head\'s own eyeY/chin (not the crown stretch) so long and wide faces '
            'stay framed, and draw the drape over the top (it covers the neckline).')
def hijab(c):
    C = c.cloth
    outer = 'M50 9C70 9 80.6 22 80.6 41L80.6 60C81.6 69 84.4 77 79 84.4Q50 92 21 84.4C15.6 77 18.4 69 19.4 60L19.4 41C19.4 22 30 9 50 9Z'
    hole = 'M29 46.5A21 23.5 0 1 0 71 46.5A21 23.5 0 1 0 29 46.5Z'
    d = outer + hole
    s = path(d, C, cls='cloth', extra='fill-rule="evenodd"') + path(d, None, edge(C), 1.1, cls='cloth-edge')
    s += stroke('M27.6 73Q40 80.4 56 79.6', deep(C, 0.2), 1, op=0.7) + stroke('M70.6 70Q74 77.6 70 83', deep(C, 0.2), 1, op=0.6)
    s += hl(26, 12, 40, 26, 205, 55, sw=2.4)
    return dict(front=s, hides_hair='all')


@item('hat', 'bear_hood', 'Bear hood', colors=['cloth: hood and ears', 'cloth-deep: hood back', 'cloth-light: inner ears'],
      layer='behind_head + front',
      notes='The hood (see hood: back x 14..86 y 3..70 in behind_head, a stroked rim from (23.6, 61) over y 12.6 to '
            '(76.6, 61) in front) with two round ears r 6.4 at (28.6, 12.4) and (71.4, 12.4) drawn in front before the '
            'rim, lighter inner ears r 3.4. hair_clip_y 16. The onesie partner of the bear_onesie top.')
def bear_hood(c):
    C = c.cloth
    base_ = hood(c)
    ears = ''
    for x in (28.6, 71.4):
        ears += dot(C, x, 12.4, 6.4, cls='cloth') + circle(light(C, 0.45), x + (1 if x < 50 else -1), 13.4, 3.4, cls='cloth-light')
    return dict(behind_head=base_['behind_head'] + ears, front=base_['front'], hair_clip_y=16)
