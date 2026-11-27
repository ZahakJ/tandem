"""Render the wardrobe: per-item SVG drafts, previews on the base character, labelled contact sheets.

nix shell nixpkgs#python3 nixpkgs#librsvg -c python3 tools/render.py [cat ...]
"""
import importlib, os, subprocess, sys
sys.path.insert(0, os.path.dirname(__file__))
import base
from base import Ctx, compose, svg, g, REG

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES = ['hats', 'tops', 'hair', 'face']
for m in MODULES:
    if os.path.exists(os.path.join(os.path.dirname(__file__), m + '.py')):
        importlib.import_module(m)

# category -> (compose keyword, crop viewBox for sheets)
CATS = {
    'hat': ('hat', '2 -10 96 90'),
    'top': ('top', '0 0 100 120'),
    'hair': ('hair', '0 -6 100 126'),
    'eyes': ('eyes', '22 28 56 40'),
    'iris': ('eyes', '22 28 56 40'),
    'glasses': ('glasses', '18 26 64 42'),
    'earrings': ('earrings', '14 30 72 44'),
    'extra': ('extras', '18 26 64 48'),
}
DEMO = [Ctx(0, 2, 1, 5, 1), Ctx(3, 7, 3, 0, 3), Ctx(5, 9, 6, 3, 0), Ctx(1, 0, 5, 8, 4)]


def guide(c):
    h = c.head
    return g(base.path(h.d(), None, '#7A7A9A', 0.6) + base.path(base.TORSO, None, '#7A7A9A', 0.6), cls='guide') \
        .replace('<g class="guide">', '<g id="guide" opacity="0.25">')


def part_svg(it, c):
    """The part alone, its layers as named groups, on a faint guide."""
    L = it['fn'](c)
    crown = c.head.crown()
    parts = [guide(c)]
    order = ['back', 'behind_head', 'hanging', 'inside', 'over_torso', 'behind_neck', 'neck', 'front', 'face']
    if it['cat'] == 'top':
        tor = L.get('torso', base.TORSO)
        parts.append('<defs><clipPath id="torso"><path d="%s"/></clipPath></defs>' % tor)
        parts.append(g(L.get('back', ''), ident='back'))
        sleeve = L.get('sleeve', 0.34)
        scol = L.get('sleeve_color', c.cloth)
        parts.append(g(base.arm(False, scol, c.skin, sleeve, cloth_cls=L.get('sleeve_cls', 'cloth')) +
                       base.arm(True, scol, c.skin, sleeve, cloth_cls=L.get('sleeve_cls', 'cloth')), ident='arms'))
        parts.append(g(L.get('legs_svg') or base.legs(L.get('legs', base.DENIM)), ident='legs'))
        b = L.get('base', c.cloth)
        parts.append(g(base.path(tor, b, cls=L.get('base_cls', 'cloth')) + g(L.get('inside', ''), clip='torso') +
                       base.path(tor, None, base.edge(b), 1.1), ident='torso'))
        for k in ('over_torso', 'behind_neck', 'neck', 'front'):
            if L.get(k):
                parts.append(g(L[k], ident=k))
    else:
        for k in order:
            if L.get(k):
                tr = crown if it['cat'] in ('hat', 'hair') and k in ('back', 'behind_head', 'hanging', 'front') else None
                parts.append(g(L[k], tr, ident=k))
    meta = '<!-- %s/%s: %s. layers: %s. user colours: %s. %s -->' % (
        it['cat'], it['id'], it['name'], ', '.join(k for k in order + ['torso'] if L.get(k)), ', '.join(it['colors']) or 'none',
        it.get('notes', ''))
    return svg(meta + ''.join(parts))


def preview(it, c, wave=False):
    kw = {CATS[it['cat']][0]: it['fn']}
    if it['cat'] == 'hair':
        pass
    return compose(c, wave=wave, **kw)


def sheet(cat, items, cols=6, cell=180, label_h=26, demo=False):
    key, box = CATS[cat]
    vx, vy, vw, vh = map(float, box.split())
    ch = cell * vh / vw
    rows = (len(items) + cols - 1) // cols
    W = cols * (cell + 12) + 12
    Hh = rows * (ch + label_h + 12) + 12 + 40
    out = ['<rect width="%d" height="%d" fill="#2A2D3A"/>' % (W, Hh),
           '<text x="14" y="30" font-family="sans-serif" font-size="20" font-weight="700" fill="#EDEAF5">%s (%d)</text>' % (cat, len(items))]
    for i, it in enumerate(items):
        r, col = divmod(i, cols)
        x = 12 + col * (cell + 12)
        y = 52 + r * (ch + label_h + 12)
        c = DEMO[i % len(DEMO)] if demo else DEMO[0]
        out.append('<rect x="%s" y="%s" width="%s" height="%s" rx="12" fill="#3A3F52"/>' % (x, y, cell, ch + label_h))
        out.append('<svg x="%s" y="%s" width="%s" height="%s" viewBox="%s">%s</svg>' % (x, y, cell, ch, box, uniq(preview(it, c), i)))
        out.append('<text x="%s" y="%s" font-family="sans-serif" font-size="13" fill="#EDEAF5" text-anchor="middle">%s</text>' % (
            x + cell / 2, y + ch + label_h - 9, it['id']))
    return '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d">%s</svg>' % (W, Hh, ''.join(out))


def uniq(s, i):
    """Clip ids must be unique in one sheet."""
    for name in ('torso', 'head', 'hairclipb', 'hairclip'):
        s = s.replace('id="%s"' % name, 'id="%s%d"' % (name, i)).replace('url(#%s)' % name, 'url(#%s%d)' % (name, i))
    return s


def png(svg_path, png_path, width=None):
    cmd = ['rsvg-convert', svg_path, '-o', png_path]
    if width:
        cmd[1:1] = ['-w', str(width)]
    subprocess.run(cmd, check=True)


def main(cats):
    os.makedirs(os.path.join(ROOT, 'sheets'), exist_ok=True)
    for cat in cats:
        items = [it for (k, _), it in REG.items() if k == cat]
        if not items:
            continue
        d = os.path.join(ROOT, 'svg', cat)
        os.makedirs(d, exist_ok=True)
        for it in items:
            open(os.path.join(d, it['id'] + '.svg'), 'w').write(part_svg(it, DEMO[0]))
        for demo in (False, True):
            name = 'sheet-%s%s' % (cat, '-mixed' if demo else '')
            p = os.path.join(ROOT, 'sheets', name + '.svg')
            open(p, 'w').write(sheet(cat, items, demo=demo))
            png(p, p[:-4] + '.png')
            os.remove(p)
        # 64 px readability strip
        small = sheet(cat, items, cols=10, cell=64, label_h=16)
        p = os.path.join(ROOT, 'sheets', 'sheet-%s-64px.svg' % cat)
        open(p, 'w').write(small)
        png(p, p[:-4] + '.png')
        os.remove(p)
        print(cat, len(items))


if __name__ == '__main__':
    args = sys.argv[1:]
    if args and args[0] == 'base':
        out = os.path.join(ROOT, 'atelier')
        os.makedirs(out, exist_ok=True)
        # a bald, plain base for reference edits (Atelier klein) and a dressed one to compare with the app
        for name, kw in (('base_plain', dict(bald=False)), ('base_bald', dict(bald=True))):
            p = os.path.join(out, name + '.svg')
            open(p, 'w').write(svg(compose(DEMO[0], **kw), box='-10 -6 120 132', bg='#E9E6EF'))
            png(p, p[:-4] + '.png', 1024)
        sys.exit()
    main(args or list(CATS))
