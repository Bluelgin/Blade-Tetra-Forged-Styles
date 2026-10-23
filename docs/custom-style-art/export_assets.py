#!/usr/bin/env python3
"""Render the deliberately small pixel-SVG vocabulary without rasterizer ambiguity.
Python 3.10+, Pillow 10+. Paths resolve from this script, never the caller's cwd.
"""
import argparse
import hashlib
import io
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageDraw

DOC = Path(__file__).resolve().parent
ROOT = DOC.parents[1]
MANIFEST = DOC / 'export/asset-manifest.json'

def render(path, size):
    root = ET.parse(path).getroot()
    assert tuple(map(int, (root.attrib['width'], root.attrib['height']))) == tuple(size), path
    assert root.attrib['viewBox'] == f'0 0 {size[0]} {size[1]}', path
    im = Image.new('RGBA', tuple(size))
    draw = ImageDraw.Draw(im)
    for node in root:
        tag = node.tag.rsplit('}', 1)[-1]
        if tag in ('title', 'desc'):
            continue
        fill = node.attrib['fill']
        assert re.fullmatch(r'#[0-9a-fA-F]{6}', fill), path
        if tag == 'rect':
            x, y, w, h = (int(node.attrib[k]) for k in ('x','y','width','height'))
            assert w > 0 and h > 0 and x >= 0 and y >= 0 and x+w <= size[0] and y+h <= size[1], path
            draw.rectangle((x,y,x+w-1,y+h-1), fill=fill)
        elif tag == 'polygon':
            points = [tuple(map(int,p.split(','))) for p in node.attrib['points'].split()]
            assert all(0 <= x < size[0] and 0 <= y < size[1] for x,y in points), path
            # Test pixel centres. Match SVG fill coverage instead of Pillow's inclusive edges.
            for y in range(size[1]):
                for x in range(size[0]):
                    px, py, inside = x+.5, y+.5, False
                    for (ax,ay),(bx,by) in zip(points, points[1:]+points[:1]):
                        if (ay > py) != (by > py) and px < (bx-ax)*(py-ay)/(by-ay)+ax:
                            inside = not inside
                    if inside: draw.point((x,y), fill=fill)
        else:
            raise ValueError(f'Unsupported SVG element {tag}: {path}; flatten to pixel rects/polygons')
    return im

def png(im):
    out=io.BytesIO();im.save(out,format='PNG',compress_level=9);return out.getvalue()

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    m=json.loads(MANIFEST.read_text());outputs={}
    for a in m['assets']:
        im=render(ROOT/a['source'],a['size']);outputs[ROOT/a['texture']]=png(im)
        if a['name'] in m['event_sheets']:
            for f in range(6):outputs[DOC/f'export/frames/{a["name"]}_{f}.png']=png(im.crop((0,f*16,16,(f+1)*16)))
        if '/item/' in a['texture']:
            model={'parent':'minecraft:item/generated','textures':{'layer0':'blade_tetra:item/custom_style/'+a['name']}}
            outputs[ROOT/f'src/main/resources/assets/blade_tetra/models/item/custom_style/{a["name"]}.json']=(json.dumps(model,indent=2)+'\n').encode()
    for p,b in outputs.items():
        if args.check:
            assert p.exists(), f'Missing output: {p.relative_to(ROOT)}'
            if p.suffix == '.png':
                # zlib streams can differ between Windows/Linux with identical pixels.
                with Image.open(p) as actual, Image.open(io.BytesIO(b)) as expected:
                    assert actual.mode == expected.mode and actual.size == expected.size and actual.tobytes() == expected.tobytes(), f'Stale pixels: {p}'
            else:
                assert p.read_bytes().replace(b'\r\n',b'\n') == b, f'Stale output: {p}'
        else:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
    checksums={p.relative_to(ROOT).as_posix():hashlib.sha256(
        (p.read_bytes().replace(b'\r\n',b'\n') if p.suffix == '.json' else p.read_bytes()) if args.check else b
        ).hexdigest() for p,b in outputs.items()}
    checksum=DOC/'export/sha256.json';expected=(json.dumps(checksums,indent=2)+'\n').encode()
    if args.check:assert checksum.read_bytes().replace(b'\r\n',b'\n')==expected,'Stale checksum manifest'
    else:checksum.write_bytes(expected)
    print(f'{"Verified" if args.check else "Exported"} {len(m["assets"])} SVGs, {len(outputs)} outputs, deterministic SHA-256 match')

if __name__=='__main__':main()
