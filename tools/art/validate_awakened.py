#!/usr/bin/env python3
"""Validate published atlas contract, alpha, source safety, frames, previews and scope."""
from pathlib import Path
from PIL import Image
import json,hashlib,xml.etree.ElementTree as ET,subprocess
R=Path(__file__).resolve().parents[2]; D=R/'docs/awakened-rework';m=json.loads((D/'assets.json').read_text())
def need(ok,msg):
 if not ok:raise ValueError(msg)
def texture(x):return R/'src/main/resources/assets'/x.replace(':','/')
need(len({a['id'] for a in m['assets']+m['animations']})==len(m['assets'])+len(m['animations']),'duplicate ids')
records={}; cells=set();frames=0
for a in m['assets']:
 p=R/a['source'];xml=ET.parse(p);need(xml.getroot().attrib['viewBox']=='0 0 128 128',str(p))
 need(not any(x.tag.split('}')[-1] in ['image','script','foreignObject','text','filter'] for x in xml.iter()),'nonportable SVG')
 im=Image.open(texture(a['texture']));need(im.mode=='RGBA' and list(im.size)==a["canvas_size"],'atlas format')
 x,y,w,h=a['rect'];need(w==h==128 and x%128==y%128==0 and 0<=x<=im.width-128 and 0<=y<=im.height-128,'tile rect')
 key=(a['texture'],x,y);need(key not in cells,'overlap');cells.add(key)
 q=im.crop((x,y,x+w,y+h));lo,hi=q.getchannel('A').getextrema();need(lo==0 and hi>0,'invalid alpha '+a['id']);box=q.getchannel('A').getbbox();need(box[0]>=2 and box[1]>=2 and box[2]<=126 and box[3]<=126,'tile gutter '+a['id']+str(box))
 need(a['uv']==[(x+.5)/im.width,(y+.5)/im.height,(x+127.5)/im.width,(y+127.5)/im.height],'uv mismatch')
for a in m['animations']:
 im=Image.open(texture(a['texture']));need(im.mode=='RGBA' and im.size==(512,512),'animation format')
 for i in range(16):
  q=im.crop((i%4*128,i//4*128,i%4*128+128,i//4*128+128));alpha=q.getchannel('A');box=alpha.getbbox()
  if i<a['frames']:
   need((R/a['source_directory']/f'{i:02}.svg').exists(),'missing frame');frames+=1
   # Release-fold endpoints and material final frames may intentionally be blank.
   if box:need(box[0]>=2 and box[1]>=2 and box[2]<=126 and box[3]<=126,'frame gutter '+a['id']+str(i)+str(box))
  else:need(box is None,'unused cell not transparent')
for p in sorted((R/'src/main/resources/assets/blade_tetra/textures/effect/awakened').rglob('*.png')):
 im=Image.open(p);records[str(p.relative_to(R))]={'size':list(im.size),'rgba_sha256':hashlib.sha256(im.tobytes()).hexdigest()}
for p in sorted((R/'art/svg/awakened').rglob('*.svg')):records[str(p.relative_to(R))]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
need(len(list((D/'previews').glob('*.mp4')))==11,'missing movies')
for p in (D/'previews').glob('*.mp4'):
 r=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(p)]));s=r['streams'][0];need(s['width']==960 and s['height']==540 and s['r_frame_rate']=='20/1' and s['codec_name']=='h264','movie encoding')
files=subprocess.check_output(['git','diff','--name-only','HEAD'],cwd=R,text=True).splitlines()
need(not any(p.endswith('.java') for p in files),'Java modified')
report={'components':len(m['assets']),'material_frames':frames,'svg_sources':len(list((R/'art/svg/awakened').rglob('*.svg'))),'png_sheets':len(list((R/'src/main/resources/assets/blade_tetra/textures/effect/awakened').rglob('*.png'))),'offline_mp4':11,'pixel_and_source_hashes':records}
(D/'validation.json').write_text(json.dumps(report,indent=2)+'\n');print({k:v for k,v in report.items() if k!='pixel_and_source_hashes'})
