"""Write ~/tandem-wardrobe/spec.md from the registered parts (their names, colours, layers and notes)."""
import importlib, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import base
from base import REG

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for m in ('hats', 'tops', 'hair', 'face'):
    if os.path.exists(os.path.join(os.path.dirname(__file__), m + '.py')):
        importlib.import_module(m)

TITLES = {'hat': 'Headwear', 'top': 'Outfits', 'hair': 'Hairstyles', 'eyes': 'Eye styles', 'iris': 'Eye colours',
          'glasses': 'Glasses', 'earrings': 'Earrings', 'extra': 'Face extras'}

INTRO = open(os.path.join(os.path.dirname(__file__), 'spec_intro.md')).read() if os.path.exists(
    os.path.join(os.path.dirname(__file__), 'spec_intro.md')) else ''


def layers_of(it):
    L = it['fn'](base.Ctx())
    keys = [k for k in ('back', 'behind_head', 'hanging', 'inside', 'over_torso', 'behind_neck', 'neck', 'front', 'face',
                        'torso', 'legs_svg') if L.get(k)]
    flags = []
    if L.get('hides_hair'):
        flags.append('hides hair: %s' % L['hides_hair'])
    if L.get('hair_clip_y') is not None:
        flags.append('hair front clipped above y %s' % base.f(L['hair_clip_y']))
    if 'sleeve' in L:
        flags.append('sleeve %s' % {0: 'none', 1: 'long'}.get(L['sleeve'], 'short (%s)' % L['sleeve']))
    return ', '.join(keys), '; '.join(flags)


def cell(s):
    return str(s).replace('|', '/').replace('\n', ' ')


def main():
    out = [INTRO]
    counts = []
    for cat, title in TITLES.items():
        items = [it for (k, _), it in REG.items() if k == cat]
        if not items:
            continue
        counts.append('%s %d' % (title.lower(), len(items)))
        out.append('\n## %s (%d)\n' % (title, len(items)))
        out.append('Sheets: `sheets/sheet-%s.png` (default character), `-mixed.png` (four characters), `-64px.png`. '
                   'Drafts: `svg/%s/<id>.svg`.\n' % (cat, cat))
        out.append('| id | name | user colour regions | layers | flags | placement and notes |')
        out.append('|---|---|---|---|---|---|')
        for it in items:
            lay, flags = layers_of(it)
            out.append('| `%s` | %s | %s | %s | %s | %s |' % (it['id'], cell(it['name']), cell(', '.join(it['colors']) or 'none'),
                                                           lay, cell(flags), cell(' '.join(x for x in (it.get('layer'), it.get('notes')) if x))))
    out.insert(1, '\nCounts: ' + ', '.join(counts) + '.\n')
    open(os.path.join(ROOT, 'spec.md'), 'w').write('\n'.join(out) + '\n')
    print('\n'.join(counts))


if __name__ == '__main__':
    main()
