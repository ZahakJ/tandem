"""sheets/00-overview.png: complete characters wearing the new parts together."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import render
from base import REG, Ctx, compose
P = lambda cat, i: REG[(cat, i)]['fn']
looks = [
    (Ctx(0, 2, 1, 5, 1), dict(hair='wolf_cut', hat='headphones', top='hoodie', glasses='round_wire')),
    (Ctx(3, 8, 0, 11, 0), dict(hair='locs', top='denim_jacket', earrings='hoop_small')),
    (Ctx(1, 0, 5, 8, 4), dict(hair='space_buns', top='party_dress', eyes='sparkle_big', extras='blush_hearts')),
    (Ctx(5, 6, 0, 3, 1), dict(hair='slicked_back', top='suit', extras='beard_short')),
    (Ctx(0, 4, 0, 4, 3), dict(hat='hijab', top='cardigan', eyes='almond')),
    (Ctx(2, 3, 3, 2, 3), dict(hair='long_curly', hat='sun_hat', top='sundress', extras='freckles_band')),
    (Ctx(0, 1, 7, 6, 4), dict(hair='bald_shine', top='lab_coat', glasses='half_rim', extras='moustache')),
    (Ctx(4, 7, 1, 9, 1), dict(hair='cornrows', top='jersey', hat='headband')),
    (Ctx(1, 2, 8, 7, 6), dict(hair='hime', hat='witch', top='cape', eyes='upturned_cat')),
    (Ctx(0, 2, 2, 2, 1), dict(hat='bear_hood', top='bear_onesie', eyes='button')),
    (Ctx(3, 9, 6, 0, 2), dict(hair='box_braids', top='overalls', glasses='heart_shades')),
    (Ctx(0, 5, 4, 3, 3), dict(hair='mullet', hat='cowboy', top='scarf_sweater', extras='bandaid')),
    (Ctx(5, 3, 1, 5, 1), dict(hat='astronaut', top='space_suit')),
    (Ctx(2, 1, 9, 10, 5), dict(hair='mohawk', top='bomber', earrings='cuff', eyes='determined')),
    (Ctx(0, 6, 0, 1, 1), dict(hair='high_pony', hat='bow', top='hanbok', eyes='round_lash')),
    (Ctx(4, 2, 2, 9, 1), dict(hair='man_bun', hat='chef', top='chef_coat', extras='beard_full')),
]
CAT = dict(hair='hair', hat='hat', top='top', glasses='glasses', earrings='earrings', eyes='eyes', extras='extra')
cols, cell = 8, 150
ch = cell * 1.26
out = ['<rect width="%d" height="%d" fill="#2A2D3A"/>' % (cols * (cell + 10) + 10, 2 * (ch + 10) + 10)]
for i, (c, kw) in enumerate(looks):
    r, k = divmod(i, cols)
    x, y = 10 + k * (cell + 10), 10 + r * (ch + 10)
    args = {key: P(CAT[key], v) for key, v in kw.items()}
    out.append('<rect x="%s" y="%s" width="%s" height="%s" rx="12" fill="#3A3F52"/>' % (x, y, cell, ch))
    out.append('<svg x="%s" y="%s" width="%s" height="%s" viewBox="-4 -12 108 136">%s</svg>' % (x, y, cell, ch, render.uniq(compose(c, **args), i)))
W, H = cols * (cell + 10) + 10, 2 * (ch + 10) + 10
p = os.path.join(render.ROOT, 'sheets', '00-overview.svg')
open(p, 'w').write('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d">%s</svg>' % (W, H, ''.join(out)))
render.png(p, p[:-4] + '.png'); os.remove(p)
