"""Every hat on a few tricky hairstyles: sheets/check-hats-on-hair.png."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import render
from base import REG, Ctx, compose
hairs = ['space_buns', 'man_bun', 'high_pony', 'long_curly', 'locs']
hats = [it for (k, _), it in REG.items() if k == 'hat']
cell, box = 96, '-6 -12 112 104'
ch = cell * 104 / 112
W = 120 + len(hairs) * (cell + 6)
out = ['<rect width="%d" height="%d" fill="#2A2D3A"/>' % (W, len(hats) * (ch + 6) + 10)]
ctxs = [Ctx(0, 2, 1, 5, 1), Ctx(3, 8, 5, 0, 3), Ctx(1, 5, 0, 3, 0), Ctx(5, 0, 3, 7, 4), Ctx(2, 9, 2, 8, 1)]
for r, hat in enumerate(hats):
    y = 6 + r * (ch + 6)
    out.append('<text x="8" y="%s" font-family="sans-serif" font-size="13" fill="#EDEAF5">%s</text>' % (y + ch / 2, hat['id']))
    for c_, hid in enumerate(hairs):
        x = 110 + c_ * (cell + 6)
        body = render.uniq(compose(ctxs[c_], hair=REG[('hair', hid)]['fn'], hat=hat['fn']), r * 10 + c_)
        out.append('<rect x="%s" y="%s" width="%s" height="%s" rx="8" fill="#3A3F52"/>' % (x, y, cell, ch))
        out.append('<svg x="%s" y="%s" width="%s" height="%s" viewBox="%s">%s</svg>' % (x, y, cell, ch, box, body))
p = os.path.join(render.ROOT, 'sheets', 'check-hats-on-hair.svg')
open(p, 'w').write('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d">%s</svg>' % (W, len(hats) * (ch + 6) + 10, ''.join(out)))
render.png(p, p[:-4] + '.png'); os.remove(p)
