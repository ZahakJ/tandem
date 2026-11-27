"""Port the wardrobe kit into Tandem: every part becomes a small display list (WardrobeData.kt) that AvatarArt draws.

The kit's colours are made symbolic first (cloth, hair, skin, iris, ink, accents and what deep/light/edge/lerp make of
them), so each region keeps following the user's choices; fixed colours stay as they are. Each part is drawn for the six
face shapes and kept once when they agree (hats, hair and tops are drawn on the default crown and body, so they do).

nix shell nixpkgs#python3 -c python3 gen.py OUT.kt
"""
import os, re, sys, math
import xml.etree.ElementTree as ET
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import base

# --- symbolic colours -------------------------------------------------------------------------------------------------

EXPRS = []          # expression tuples
EXPR_ID = {}


def mk(e):
    if e not in EXPR_ID:
        EXPR_ID[e] = len(EXPRS)
        EXPRS.append(e)
    return '@%d' % EXPR_ID[e]


def is_sym(c):
    return isinstance(c, str) and c.startswith('@')


def operand(c):
    return EXPRS[int(c[1:])] if is_sym(c) else ('const', c.upper())


_lerp, _deep, _light, _edge = base.lerp, base.deep, base.light, base.edge


def lerp(a, b, t):
    if not (is_sym(a) or is_sym(b)):
        return _lerp(a, b, t)
    return mk(('lerp', operand(a), operand(b), round(t, 4)))


def deep(c, f):
    return mk(('deep', operand(c), round(f, 4))) if is_sym(c) else _deep(c, f)


def light(c, f):
    return mk(('light', operand(c), round(f, 4))) if is_sym(c) else _light(c, f)


def edge(c, f=0.36):
    return mk(('edge', operand(c), round(f, 4))) if is_sym(c) else _edge(c, f)


base.lerp, base.deep, base.light, base.edge = lerp, deep, light, edge

import hats, tops, hair, face  # noqa: E402  (they take the symbolic helpers through `from base import *`)

KAFTAN = [False]


def accent(c, step=6):
    return mk(('accent_kaftan',)) if KAFTAN[0] else mk(('accent', step))


tops.accent = accent


def cuffs(col, cls=None, stripe=None):
    return '<xcuff fill="%s" stripe="%s"/>' % (col, stripe or 'none')


def arm_stripe(front, col, off=2.4, t0=0.3, t1=0.8, w=1.1):
    return '<xstripe side="%d" fill="%s" off="%s" w="%s" t0="%s" t1="%s"/>' % (1 if front else -1, col, off, w, t0, t1)


tops.cuffs = cuffs
tops.arm_stripe = arm_stripe

import overrides  # noqa: E402
overrides.cuffs = cuffs
overrides.accent = accent


def _astronaut(fn):
    def w(c):
        L = fn(c)
        L['behind_head_body'] = L.pop('behind_head')
        return L
    return w


def hijab(c):
    """The hijab with its face opening placed from this face's own brow and chin (in crown units)."""
    h = c.head
    sy = (h.mid - h.top) / 25
    sx = h.hw / 25
    top_abs, bot_abs = h.top + 7 * sy, h.chin + 1.2
    cy_abs, ry_abs = (top_abs + bot_abs) / 2, (bot_abs - top_abs) / 2
    cy, ry, rx = 16 + (cy_abs - h.top) / sy, ry_abs / sy, 21 * (0.84 * h.hw / 21) / sx * (21 / 21)
    rx = 21
    C = c.cloth
    bottom = max(88, cy + ry + 14)
    outer = ('M50 9C70 9 80.6 22 80.6 41L80.6 60C81.6 69 84.4 77 79 %sQ50 %s 21 %sC15.6 77 18.4 69 19.4 60L19.4 41C19.4 22 30 9 50 9Z'
             % (base.f(bottom - 3.6), base.f(bottom + 4), base.f(bottom - 3.6)))
    hole = 'M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ' % tuple(base.f(v) for v in (50 - rx, cy, rx, ry, 50 + rx, cy, rx, ry, 50 - rx, cy))
    d = outer + hole
    s = base.path(d, C, cls='cloth', extra='fill-rule="evenodd"') + base.path(d, None, edge(C), 1.1, cls='cloth-edge')
    s += base.stroke('M27.6 %sQ40 %s 56 %s' % (base.f(bottom - 15), base.f(bottom - 7.6), base.f(bottom - 8.4)), deep(C, 0.2), 1, op=0.7)
    s += base.stroke('M70.6 %sQ74 %s 70 %s' % (base.f(bottom - 18), base.f(bottom - 10.4), base.f(bottom - 5)), deep(C, 0.2), 1, op=0.6)
    s += hats.hl(26, 12, 40, 26, 205, 55, sw=2.4)
    return dict(front=s, hides_hair='all')


base.REG[('hat', 'astronaut')]['fn'] = _astronaut(base.REG[('hat', 'astronaut')]['fn'])
base.REG[('hat', 'hijab')]['fn'] = hijab

# --- which kit part fills which choice number ---------------------------------------------------------------------------
# Existing choices keep their numbers: the drafts that redraw one of them take its place, the rest are appended.

HAT = [None, 'bobble', 'cap_fwd', 'headphones', 'bow', None, 'crown', 'bunny_ears', 'cat_ears', 'bucket', 'headband', None,
       'beanie', 'cap_back', 'beret', 'sun_hat', 'cowboy', 'top_hat', 'wizard', 'witch', 'tiara', 'flower_crown', 'bear_ears',
       'chef', 'hard_hat', 'pilot', 'explorer', 'grad_cap', 'party_hat', 'bandana', 'hood', 'viking', 'astronaut', 'halo',
       'antlers', 'hijab', 'bear_hood']
TOP = [None, None, None, None, None, 'blazer', 'scarf_sweater',
       'sweater', 'cardigan', 'polo', 'shirt_tie', 'wrap_jacket', 'hanbok', 'sundress', 'party_dress', 'dungaree_skirt',
       'raincoat', 'puffer', 'jersey', 'sailor', 'chef_coat', 'lab_coat', 'scrubs', 'bear_onesie', 'suit', 'tracksuit',
       'denim_jacket', 'apron', 'cape', 'knight_tunic', 'space_suit', 'turtleneck', 'vest_shirt', 'thobe', 'kaftan', 'varsity',
       'pajamas', 'bomber']
HAIR = [None] * 14 + ['mohawk', 'undercut', 'man_bun', 'pixie', 'side_shave', 'slicked_back', 'bowl_cut', 'mullet', 'wolf_cut',
                      'hime', 'high_pony', 'space_buns', 'half_up', 'long_curly', 'fade_curls', 'locs', 'cornrows', 'box_braids',
                      'waves', 'bald_shine']
EYES = [None] * 8 + ['sparkle_big', 'almond', 'round_lash', 'downturned', 'upturned_cat', 'star_pupil', 'heart_pupil', 'button',
                     'determined', 'lashes_closed', 'monolid_soft', 'wide_shine']
GLASSES = [None] * 5 + ['round_wire', 'square_thick', 'cat_eye', 'aviator', 'half_rim', 'oval_tint', 'flower_party',
                        'heart_shades', 'goggles', 'monocle']
EARRINGS = [None] * 4 + ['stud_pearl', 'hoop_small', 'hoop_big', 'drop_gem', 'moon_dangle', 'cuff']
EXTRAS = [None, 'freckles_band', 'beauty_mark', 'bandaid', 'paint_star', 'paint_whiskers', 'blush_hearts', 'sweat_drop']
BEARDS = [None, 'moustache', 'beard_short', 'beard_full', 'goatee']
IRIS = [it for (k, _), it in base.REG.items() if k == 'iris']

CATS = [('hats', 'hat', HAT), ('tops', 'top', TOP), ('hair', 'hair', HAIR), ('eyes', 'eyes', EYES), ('glasses', 'glasses', GLASSES),
        ('earrings', 'earrings', EARRINGS), ('extras', 'extra', EXTRAS), ('beards', 'extra', BEARDS)]

SKIRTS = {tops.COAT, tops.ROBE, base.DRESS, tops.SUNDRESS, tops.PARTY, tops.BELL, tops.TUNIC}

# --- svg fragments to ops ---------------------------------------------------------------------------------------------

NUM = re.compile(r'-?\d*\.?\d+(?:e-?\d+)?')


def fmt(v):
    v = float(v)
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def mat_mul(a, b):
    return (a[0] * b[0] + a[2] * b[1], a[1] * b[0] + a[3] * b[1], a[0] * b[2] + a[2] * b[3], a[1] * b[2] + a[3] * b[3],
            a[0] * b[4] + a[2] * b[5] + a[4], a[1] * b[4] + a[3] * b[5] + a[5])


def parse_transform(t):
    m = (1, 0, 0, 1, 0, 0)
    for name, args in re.findall(r'(\w+)\(([^)]*)\)', t):
        v = [float(x) for x in NUM.findall(args)]
        if name == 'translate':
            n = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0)
        elif name == 'scale':
            n = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == 'rotate':
            a = math.radians(v[0])
            cx, cy = (v[1], v[2]) if len(v) > 2 else (0, 0)
            ca, sa = math.cos(a), math.sin(a)
            n = (ca, sa, -sa, ca, cx - ca * cx + sa * cy, cy - sa * cx - ca * cy)
        elif name == 'matrix':
            n = tuple(v)
        else:
            raise ValueError(t)
        m = mat_mul(m, n)
    return m


def colour(v):
    if v is None or v == 'none':
        return None
    if is_sym(v):
        return 'k%d' % int(v[1:])
    assert re.fullmatch(r'#[0-9A-Fa-f]{6}', v), v
    return v.upper()


USED = set()


def ctok(v):
    c = colour(v)
    if c and c.startswith('k'):
        USED.add(int(c[1:]))
    return c


def shape_d(el):
    tag = el.tag
    if tag == 'path':
        return el.get('d')
    if tag in ('circle', 'ellipse'):
        cx, cy = float(el.get('cx')), float(el.get('cy'))
        rx = float(el.get('r') or el.get('rx'))
        ry = float(el.get('r') or el.get('ry'))
        return 'M%s %sA%s %s 0 1 0 %s %sA%s %s 0 1 0 %s %sZ' % tuple(fmt(v) for v in (cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy))
    if tag == 'rect':
        x, y, w, h = (float(el.get(k)) for k in ('x', 'y', 'width', 'height'))
        return 'M%s %sH%s V%s H%s Z' % tuple(fmt(v) for v in (x, y, x + w, y + h, x))
    raise ValueError(tag)


def rep_x(el):
    tag = el.tag
    if tag == 'path':
        n = NUM.findall(el.get('d'))
        return float(n[0])
    if tag in ('circle', 'ellipse'):
        return float(el.get('cx'))
    if tag == 'rect':
        return float(el.get('x')) + float(el.get('width')) / 2
    if tag == 'g':
        for ch in el:
            if ch.tag != 'defs':
                return rep_x(ch)
    return 50.0


class Out:
    def __init__(self):
        self.lines = []
        self.clips = {}

    def add(self, *a):
        self.lines.append(' '.join(str(x) for x in a))


def alpha(el):
    o = el.get('opacity')
    return fmt(o) if o is not None else '1'


def emit(el, out):
    tag = el.tag
    if tag == 'defs':
        for cp in el:
            assert cp.tag == 'clipPath'
            out.clips[cp.get('id')] = ' '.join(shape_d(x) for x in cp)
        return
    if tag == 'g':
        assert el.get('opacity') is None, 'group opacity'
        for ch in el:
            if ch.tag == 'defs':
                emit(ch, out)
        n = 0
        if el.get('transform'):
            m = parse_transform(el.get('transform'))
            if m != (1, 0, 0, 1, 0, 0):
                out.add('G', *(fmt(v) for v in m))
                n += 1
        if el.get('clip-path'):
            cid = re.match(r'url\(#(.*)\)', el.get('clip-path')).group(1)
            out.add('C', out.clips[cid])
            n += 1
        for ch in el:
            if ch.tag != 'defs':
                emit(ch, out)
        for _ in range(n):
            out.add('}')
        return
    if tag == 'xcuff':
        out.add('X', 'cuff', ctok(el.get('fill')), ctok(el.get('stripe')) or '-')
        return
    if tag == 'xstripe':
        out.add('X', 'stripe', ctok(el.get('fill')), el.get('side'), fmt(el.get('off')), fmt(el.get('w')), fmt(el.get('t0')), fmt(el.get('t1')))
        return
    if el.get('clip-path'):
        raise ValueError('clip on a shape')
    fill, stroke = ctok(el.get('fill')), ctok(el.get('stroke'))
    a = alpha(el)
    w = fmt(el.get('stroke-width') or 1.1)
    dashed = el.get('stroke-dasharray') is not None
    if tag == 'path':
        d = el.get('d')
        if fill:
            out.add('E' if el.get('fill-rule') == 'evenodd' else 'F', fill, a, d)
        if stroke:
            out.add('D' if dashed else 'S', stroke, a, w, d)
    elif tag in ('circle', 'ellipse'):
        cx, cy = fmt(el.get('cx')), fmt(el.get('cy'))
        rx = fmt(el.get('r') or el.get('rx'))
        ry = fmt(el.get('r') or el.get('ry'))
        if fill:
            out.add('O', fill, a, cx, cy, rx, ry)
        if stroke:
            out.add('P', stroke, a, w, cx, cy, rx, ry)
    elif tag == 'rect':
        x, y, ww, hh = (fmt(el.get(k)) for k in ('x', 'y', 'width', 'height'))
        r = fmt(el.get('rx') or 0)
        if fill:
            out.add('R', fill, a, x, y, ww, hh, r)
        if stroke:
            out.add('Q', stroke, a, w, x, y, ww, hh, r)
    else:
        raise ValueError(tag)


def parse(fragment):
    return ET.fromstring('<g>%s</g>' % fragment)


def layer(name, fragment, out, sides=False):
    if not fragment:
        return
    out.add('L', name)
    root = parse(fragment)
    for ch in root:
        if ch.tag == 'defs':
            emit(ch, out)
    for ch in root:
        if ch.tag == 'defs':
            continue
        if sides:
            x = rep_x(ch)
            s = -1 if x < 49.5 else (1 if x > 50.5 else 0)
            out.add('H', s)
            emit(ch, out)
            out.add('}')
        else:
            emit(ch, out)


def item_ops(cat, it, c):
    L = it['fn'](c)
    out = Out()
    if cat == 'hat':
        for k in ('back', 'behind_head', 'behind_head_body', 'front'):
            layer(k, L.get(k), out)
        if L.get('hair_clip_y') is not None:
            out.add('M', 'clip', fmt(L['hair_clip_y']))
        if L.get('hides_hair') == 'all':
            out.add('M', 'hides', '1')
    elif cat == 'hair':
        for k in ('back', 'hanging', 'front'):
            layer(k, L.get(k), out)
        if L.get('front_d'):
            out.add('M', 'shadow', L['front_d'])
    elif cat == 'top':
        for k in ('back', 'inside', 'over_torso', 'behind_neck', 'neck', 'front'):
            layer(k, L.get(k), out)
        if L.get('legs_svg'):
            layer('legs', L['legs_svg'], out, sides=True)
        out.add('M', 'sleeve', fmt(L.get('sleeve', 0.34)))
        out.add('M', 'sleeveColor', ctok(L.get('sleeve_color', c.cloth)))
        b = L.get('base', c.cloth)
        out.add('M', 'base', ctok(b))
        tor = L.get('torso', base.TORSO)
        if tor != base.TORSO:
            out.add('M', 'torso', tor)
        legs = L.get('legs')
        if not L.get('legs_svg'):
            out.add('M', 'legs', ctok(legs or base.DENIM))
        lap = b if tor in SKIRTS else (legs or b)
        out.add('M', 'lap', ctok(lap))
    elif cat == 'eyes':
        layer('face', L.get('face'), out, sides=True)
        if it['id'] == 'lashes_closed':
            out.add('M', 'closed', '1')
    else:
        layer('face', L.get('face'), out)
        if it['id'] == 'sweat_drop':
            out.add('M', 'last', '1')
    return '\n'.join(out.lines)


def sym_ctx(shape):
    c = base.Ctx(shape=shape)
    c.cloth, c.hair, c.skin, c.iris, c.ink = (mk(('base', n)) for n in ('cloth', 'hair', 'skin', 'iris', 'ink'))
    return c


def variants(cat, it):
    heads = [0] if cat == 'eyes' else range(6)
    KAFTAN[0] = it['id'] == 'kaftan'
    vs = [item_ops(cat, it, sym_ctx(i)) for i in heads]
    KAFTAN[0] = False
    return vs[:1] if all(v == vs[0] for v in vs) else vs


# --- Kotlin -------------------------------------------------------------------------------------------------------------

def kt_expr(e):
    k = e[0]
    if k == 'const':
        return 'Color(0xFF%s)' % e[1][1:]
    if k == 'base':
        return 'k.' + e[1]
    if k == 'accent':
        return 'k.accent(%d)' % e[1]
    if k == 'accent_kaftan':
        return 'k.kaftanAccent()'
    if k in ('deep', 'light', 'edge'):
        return '%s.%s(%sf)' % (kt_expr(e[1]), k, fmt(e[2]))
    if k == 'lerp':
        return 'lerp(%s, %s, %sf)' % (kt_expr(e[1]), kt_expr(e[2]), fmt(e[3]))
    raise ValueError(e)


def kt_string(s):
    assert '"""' not in s and '$' not in s
    return '"""' + s + '"""'


def main(path_out):
    body = []
    counts = {}
    for name, cat, ids in CATS:
        entries = []
        n = 0
        for ident in ids:
            if ident is None:
                entries.append('        null,')
                continue
            it = base.REG[(cat, ident)]
            vs = variants(cat, it)
            n += 1
            entries.append('        // %s: %s\n        arrayOf(%s),' % (ident, it['name'], ', '.join(kt_string(v) for v in vs)))
        counts[name] = n
        body.append('    /** %s by choice number (null: drawn by AvatarArt itself, or nothing). */\n    val %s: Array<Array<String>?> = arrayOf(\n%s\n    )\n'
                    % (name.capitalize(), name, '\n'.join(entries)))
    iris = ', '.join('0xFF%s' % it['id'] for it in [])
    # the colour table: only the expressions some part uses, keeping their numbers
    used = sorted(USED)
    cases = '\n'.join('    %d -> %s' % (i, kt_expr(EXPRS[i])) for i in used)
    src = '''package io.github.zahakj.tandem.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Generated from the wardrobe kit (tools/wardrobe/gen.py): the parts' drawings as small display lists, read by
// Wardrobe in AvatarArt.kt. Do not edit by hand; change the kit and generate again.

internal object WardrobeData {
%s}

/** A colour of a part, from the user's choices in [k] (number k<n> in the lists above). */
internal fun wardrobeColor(i: Int, k: WardrobeInk): Color = when (i) {
%s
    else -> Color.Magenta
}
''' % ('\n'.join(body), cases)
    open(path_out, 'w').write(src)
    print(counts, 'colours', len(used), 'bytes', len(src))


if __name__ == '__main__':
    main(sys.argv[1])
