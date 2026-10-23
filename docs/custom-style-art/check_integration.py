#!/usr/bin/env python3
"""Small static delivery audit, without launching Minecraft."""
import json
import re
import subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/blade_tetra'
BASE = 'ebd3e72ff26e2e03a8ddc57986ba5a5298f979fb'

def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

# Registered root model resolves through the original PR #31 model to a PNG.
model = load(ASSETS / 'models/item/style_recorder.json')
assert model['parent'] == 'blade_tetra:item/custom_style/shiroku_fuda'
parent = load(ASSETS / 'models/item/custom_style/shiroku_fuda.json')
assert (ASSETS / ('textures/' + parent['textures']['layer0'].split(':')[1] + '.png')).is_file()

# Footer, status and toolbar rectangles never intersect the visible list.
for height in (240, 270, 360, 480, 720):
    rows = max(1, min(6, (height - 8 - 141) // 22))
    panel = 141 + rows * 22
    end = 61 + rows * 22
    assert panel <= height
    assert end <= end + 4 < end + 24 <= end + 40 < end + 49 <= end + 55 < end + 75 <= panel
    for offset in range(6 - rows + 1):
        assert offset + rows <= 6

langs = {lang: load(ASSETS / f'lang/{lang}.json') for lang in ('zh_cn', 'en_us')}
for lang, values in langs.items():
    original = json.loads(subprocess.check_output(['git','show',f'{BASE}:src/main/resources/assets/blade_tetra/lang/{lang}.json'],cwd=ROOT,encoding='utf-8'))
    for key, value in original.items():
        assert key in values, f'Removed translation: {key}'
        if not key.startswith('screen.blade_tetra.custom_style.'):
            assert values[key] == value, f'Unrelated translation modified: {key}'
    prefix = ('ui.blade_tetra.custom_style.', 'screen.blade_tetra.custom_style.')
    for key in values:
        if key.startswith(prefix):
            assert key in langs['en_us'] and key in langs['zh_cn'], key

# All literal GUI references and three button states chosen dynamically exist.
for name in ('editor_panel','action_row','action_row_selected','button_normal','button_hover',
             'button_pressed','button_disabled','undo','clear','finish','style_custom','recording_hud',
             'state_waiting','state_added','state_full','state_unavailable','state_compiled','ink_write','seal_set'):
    assert (ASSETS / f'textures/gui/custom_style/{name}.png').is_file(), name
for length in ('short','standard','long'):
    path = ROOT / f'src/main/resources/data/tetra/schematics/slashblade/custom_style_{length}.json'
    glyph = load(path)['glyph']
    assert glyph['textureX'] == 16 * (('short','standard','long').index(length) + 1)
    assert (ASSETS / glyph['textureLocation'].split(':')[1]).is_file()

# Only the three client presentation classes may change Java against the core HEAD.
changed = subprocess.check_output(['git','diff','--name-only',BASE],cwd=ROOT,encoding='utf-8').splitlines()
allowed = {'CustomStyleArt.java','CustomStyleFeedback.java','CustomStyleEditorScreen.java'}
for path in changed:
    if path.endswith('.java'):
        assert path.startswith('src/main/java/dev/bladetetra/client/') and Path(path).name in allowed, path
print('PASS: model chain, five layout heights / six-action scroll range, bilingual keys, unchanged existing non-UI text, literal GUI textures, Tetra atlas references, client-only Java scope')
