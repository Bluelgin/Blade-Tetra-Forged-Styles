#!/usr/bin/env python3
"""Pack unchanged PR #31 icons for Tetra's fixed 256px glyph UVs."""
import argparse
import io
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
NAMES = ('style_custom', 'length_short', 'length_standard', 'length_long')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    folder = ROOT / 'src/main/resources/assets/blade_tetra/textures/gui/custom_style'
    atlas = Image.new('RGBA', (256, 256))
    for index, name in enumerate(NAMES):
        with Image.open(folder / (name + '.png')) as icon:
            assert icon.size == (16, 16)
            atlas.paste(icon, (16 * index, 0))
    output = io.BytesIO()
    atlas.save(output, format='PNG', compress_level=9)
    path = folder / 'workbench_glyphs.png'
    if args.check:
        assert path.read_bytes() == output.getvalue(), 'Stale workbench glyph atlas'
    else:
        path.write_bytes(output.getvalue())
    print('PASS: 256px Tetra atlas; four original icons copied without resampling')

if __name__ == '__main__':
    main()
