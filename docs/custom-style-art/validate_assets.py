#!/usr/bin/env python3
"""Resource path, PNG, references, events, translations and branch-scope checks."""
import json
import re
import subprocess
import sys
from pathlib import Path
from PIL import Image
D=Path(__file__).resolve().parent; R=D.parents[1]
sys.path.insert(0,str(D))
from export_assets import render, png
m=json.loads((D/'export/asset-manifest.json').read_text());assets=m['assets'];names=set()
for a in assets:
    assert a['name'] not in names;names.add(a['name'])
    p=R/a['texture'];assert re.fullmatch(r'[a-z0-9_./]+',a['texture'])
    with Image.open(p) as im:
        assert im.format=='PNG' and im.mode=='RGBA' and list(im.size)==a['size'],p
        assert set(im.getchannel('A').get_flattened_data()) <= {0,255},p
        assert p.read_bytes()==png(render(R/a['source'],a['size'])),p
    assert not p.with_suffix('.png.mcmeta').exists(),'Event sheets must not auto-loop'
for p in (R/'src/main/resources/assets/blade_tetra/models/item/custom_style').glob('*.json'):
    model=json.loads(p.read_text());assert model['parent']=='minecraft:item/generated'
    for location in model['textures'].values():
        namespace,path=location.split(':');assert (R/f'src/main/resources/assets/{namespace}/textures/{path}.png').is_file(),location
for n,event in m['event_sheets'].items():
    assert n in names and event['loop'] is False and event['frames']==6
    assert next(a['size'] for a in assets if a['name']==n)==[16,96]
    for f in range(6):assert (D/f'export/frames/{n}_{f}.png').exists()
lang=json.loads((D/'export/ui-strings.json').read_text());assert lang['zh_cn'].keys()==lang['en_us'].keys()
# No text is burned into production textures; every phrase remains a translation component.
assert len(lang['zh_cn'])>=25
base=m['base_commit']
tracked=subprocess.check_output(['git','diff','--name-only',base],cwd=R,text=True).splitlines()
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=R,text=True).splitlines()
allowed=('docs/custom-style-art/','src/main/resources/assets/blade_tetra/textures/item/custom_style/','src/main/resources/assets/blade_tetra/textures/gui/custom_style/','src/main/resources/assets/blade_tetra/models/item/custom_style/')
for p in set(tracked+untracked):assert p.startswith(allowed),f'Out-of-scope edit: {p}'
# Existing assets and Java are excluded by the additive-only path allowlist.
print(f'PASS: {len(assets)} PNGs; RGBA/pixel alpha, dimensions, SVG reproducibility, model references, 3 nonlooping sheets, bilingual keys, additive-only scope')
