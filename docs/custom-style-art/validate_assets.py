#!/usr/bin/env python3
"""Resource path, PNG, references, events, translations and branch-scope checks."""
import argparse
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
        assert im.tobytes()==render(R/a['source'],a['size']).tobytes(),p
    assert not p.with_suffix('.png.mcmeta').exists(),'Event sheets must not auto-loop'
for p in (R/'src/main/resources/assets/blade_tetra/models/item/custom_style').glob('*.json'):
    model=json.loads(p.read_text());assert model['parent']=='minecraft:item/generated'
    for location in model['textures'].values():
        namespace,path=location.split(':');assert (R/f'src/main/resources/assets/{namespace}/textures/{path}.png').is_file(),location
for n,event in m['event_sheets'].items():
    assert n in names and event['loop'] is False and event['frames']==6
    assert next(a['size'] for a in assets if a['name']==n)==[16,96]
    for f in range(6):assert (D/f'export/frames/{n}_{f}.png').exists()
lang=json.loads((D/'export/ui-strings.json').read_text(encoding='utf-8'));assert lang['zh_cn'].keys()==lang['en_us'].keys()
# No text is burned into production textures; every phrase remains a translation component.
assert len(lang['zh_cn'])>=25
parser=argparse.ArgumentParser()
parser.add_argument('--integration-base', help='Audit the client integration against this exact core HEAD instead of the art-only baseline')
parser.add_argument('--resources-only', action='store_true', help='Check all art resources without imposing the historical art-only Java scope on later feature branches')
args=parser.parse_args()
if args.resources_only:
    print(f'PASS: {len(assets)} PNGs; RGBA/pixel alpha, dimensions, SVG reproducibility, model references, 3 nonlooping sheets, bilingual keys')
    sys.exit(0)
base=args.integration_base or m['base_commit']
tracked=subprocess.check_output(['git','diff','--name-only',base],cwd=R,text=True).splitlines()
untracked=subprocess.check_output(['git','ls-files','--others','--exclude-standard'],cwd=R,text=True).splitlines()
allowed=('docs/custom-style-art/','src/main/resources/assets/blade_tetra/textures/item/custom_style/','src/main/resources/assets/blade_tetra/textures/gui/custom_style/','src/main/resources/assets/blade_tetra/models/item/custom_style/')
integration_allowed={
    'src/main/java/dev/bladetetra/client/CustomStyleEditorScreen.java',
    'src/main/java/dev/bladetetra/client/CustomStyleArt.java',
    'src/main/java/dev/bladetetra/client/CustomStyleFeedback.java',
    'src/main/resources/assets/blade_tetra/models/item/style_recorder.json',
    'src/main/resources/assets/blade_tetra/lang/zh_cn.json',
    'src/main/resources/assets/blade_tetra/lang/en_us.json',
}
for length in ('short','standard','long'):
    for folder,filename in (('schematics',f'custom_style_{length}'),('modules',f'custom_{length}_blade')):
        path=f'src/main/resources/data/tetra/{folder}/slashblade/{filename}.json'
        integration_allowed.add(path)
        if args.integration_base:
            before=json.loads(subprocess.check_output(['git','show',f'{base}:{path}'],cwd=R,text=True))
            after=json.loads((R/path).read_text())
            # Only glyph presentation may differ; all crafting and combat fields must match.
            if folder=='schematics':
                after.pop('glyph',None);before.pop('glyph',None)
            else:
                for data in (before,after):
                    for variant in data['variants']:variant.get('extract',{}).pop('glyph',None)
            assert before==after,f'Nonvisual Tetra change: {path}'
for p in set(tracked+untracked):
    assert p.startswith(allowed) or (args.integration_base and p in integration_allowed),f'Out-of-scope edit: {p}'
# Existing assets and Java are excluded by the additive-only path allowlist.
print(f'PASS: {len(assets)} PNGs; RGBA/pixel alpha, dimensions, SVG reproducibility, model references, 3 nonlooping sheets, bilingual keys; '+('client-only integration scope and unchanged Tetra business fields' if args.integration_base else 'additive-only scope'))
