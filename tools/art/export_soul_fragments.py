"""Export approved B2 soul echoes from editable pixel SVG masters.

The SVGs, not the proposal scripts, are the source of truth. Only integer opaque
pixel rectangles are accepted so export preserves the approved art exactly.
Use --check to verify shipped textures without rewriting anything.
"""
from pathlib import Path
import argparse
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'art/soul-fragments'
TARGET = ROOT / 'src/main/resources/assets/blade_tetra/textures/item'
PREVIEW = ROOT / 'art-previews/soul-fragments'
SOULS = ['akatsuki', 'senbonzakura', 'raikiri', 'kyouka']
NS = '{http://www.w3.org/2000/svg}'


def render(path):
    svg = ET.parse(path).getroot()
    if svg.tag != NS + 'svg' or svg.get('viewBox') != '0 0 32 32':
        raise ValueError(f'{path}: expected a 32px SVG master')
    image = Image.new('RGBA', (32, 32))
    draw = ImageDraw.Draw(image)
    for rect in svg:
        if rect.tag != NS + 'rect':
            raise ValueError(f'{path}: unsupported pixel primitive {rect.tag}')
        x, y, w, h = [int(rect.get(k, '0')) for k in ['x', 'y', 'width', 'height']]
        color = rect.get('fill', '')
        if not (0 <= x < x+w <= 32 and 0 <= y < y+h <= 32
                and len(color) == 7 and color.startswith('#')):
            raise ValueError(f'{path}: invalid pixel rectangle')
        draw.rectangle((x, y, x+w-1, y+h-1), fill=color)
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    icons = []
    for soul in SOULS:
        image = render(SOURCE / f'{soul}.svg')
        path = TARGET / f'{soul}_soul_fragment.png'
        if args.check:
            with Image.open(path) as shipped:
                if shipped.size != image.size or shipped.mode != 'RGBA' or shipped.tobytes() != image.tobytes():
                    raise ValueError(f'SVG / runtime PNG mismatch: {soul}')
        else:
            TARGET.mkdir(parents=True, exist_ok=True)
            image.save(path)
        icons.append(image)
    if not args.check:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        canvas = Image.new('RGBA', (512, 148), '#201e28')
        draw = ImageDraw.Draw(canvas)
        for i, (name, image) in enumerate(zip(['AKATSUKI', 'SAKURA', 'RAIKIRI', 'KYOUKA'], icons)):
            canvas.alpha_composite(image.resize((96, 96), Image.Resampling.NEAREST), (i*128+16, 8))
            canvas.alpha_composite(image.resize((16, 16), Image.Resampling.NEAREST), (i*128+20, 118))
            draw.text((i*128+42, 120), name, fill='#cbbdb7')
        canvas.convert('RGB').save(PREVIEW / 'fragments.png')
    print('Four B2 soul echo SVG / runtime PNG pairs ' + ('verified.' if args.check else 'exported.'))


if __name__ == '__main__':
    main()
