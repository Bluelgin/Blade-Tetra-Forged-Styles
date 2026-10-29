#!/usr/bin/env python3
"""Offline compositing of actual exported atlas regions; never game capture.
Requires Pillow 12.3.0, ffmpeg 5+. No random scene state. Times in 20Hz ticks.
"""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math,subprocess
R=Path(__file__).resolve().parents[2];D=R/'docs/awakened-rework'; O=D/'previews';O.mkdir(exist_ok=True)
M=json.loads((D/'assets.json').read_text()); A={a['id']:a for a in M['assets']};cache={}
F='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';B='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(n,b=False):return ImageFont.truetype(B if b else F,n)
C={'akatsuki':'#ff5368','kyouka':'#a2e7ef','senbonzakura':'#ffb7d5','raikiri':'#88dfff','shared':'#d6c8f0'}
TITLE={'akatsuki':'AKATSUKI / CAUSAL SEVERANCE','kyouka':'KYOUKA / MIRROR REPLAY','senbonzakura':'SENBONZAKURA / BLOSSOM ARRAY','raikiri':'RAIKIRI / THUNDER STEP','shared':'SHARED / RELEASE UTILITIES'}
def texture(id):return R/'src/main/resources/assets'/id.replace(':','/')
def crop(id):
 if id not in cache:
  a=A[id];x,y,w,h=a['rect'];cache[id]=Image.open(texture(a['texture'])).convert('RGBA').crop((x,y,x+w,y+h))
 return cache[id].copy()
def place(im,id,x,y,w=128,h=None,angle=0,alpha=1):
 if alpha<=0:return
 q=crop(id) if isinstance(id,str) else id.copy();q=q.resize((max(1,int(w)),max(1,int(h or w))),Image.Resampling.LANCZOS)
 if angle:q=q.rotate(angle,resample=Image.Resampling.BICUBIC,expand=True)
 if alpha<1:q.putalpha(q.getchannel('A').point(lambda v:int(v*max(0,alpha))))
 im.alpha_composite(q,(int(x-q.width/2),int(y-q.height/2)))
def bg(soul,label,light=False,w=960,h=540):
 im=Image.new('RGBA',(w,h),'#e7e7df' if light else '#10151f');d=ImageDraw.Draw(im)
 for i in range(-12,13):d.line((w/2+i*80,170,w/2+i*175,h),fill='#d7d9d5' if light else '#202b3a')
 for y in [210,260,325,405,500]:d.line((0,y,w,y),fill='#d7d9d5' if light else '#202b3a')
 d.rectangle((0,0,w,88),fill='#0b0f18');d.rectangle((24,25,28,67),fill=C[soul]);d.text((44,22),TITLE[soul],font=font(21,True),fill='white');d.text((44,54),label,font=font(13),fill='#a6b4c8')
 d.rectangle((0,h-38,w,h),fill='#0b0f18');d.text((24,h-27),'OFFLINE ART PREVIEW  /  ATLAS COMPOSITE  /  NOT MINECRAFT CAPTURE',font=font(12),fill='#a6b4c8')
 return im

def actor(im,x=480,y=365,alpha=1,color='#758296'):
 q=Image.new('RGBA',im.size);d=ImageDraw.Draw(q);d.rectangle((x-12,y-99,x+12,y-75),fill=color);d.rectangle((x-18,y-73,x+18,y-28),fill=color);d.rectangle((x-17,y-27,x-3,y+12),fill=color);d.rectangle((x+3,y-27,x+17,y+12),fill=color);d.line((x+20,y-65,x+69,y-99),fill='#e5eef8',width=3)
 if alpha<1:q.putalpha(q.getchannel('A').point(lambda v:int(v*alpha)))
 im.alpha_composite(q)

def animated(soul,name,t):
 a=next(x for x in M['animations'] if x['id']==soul+'/'+name);i=min(a['frames']-1,max(0,int(t/a['ticks_per_frame'])));x=i%4*128;y=i//4*128
 return Image.open(texture(a['texture'])).convert('RGBA').crop((x,y,x+128,y+128))
def phase(t,lo,hi):return max(0,min(1,(t-lo)/(hi-lo)))
def scene(soul,kind,t,quality='standard',light=False):
 im=bg(soul,kind.upper().replace('_',' ')+'   /   '+quality.upper(),light);d=ImageDraw.Draw(im);p=soul+'/'
 if kind=='awakening':
  fade=min(phase(t,0,6),1-phase(t,32,44));actor(im)
  if soul=='akatsuki':
   place(im,p+'crescent',480,270,250,angle=-8,alpha=fade);place(im,p+'blade_trace',536,290,80,20,angle=34,alpha=fade)
  elif soul=='kyouka':
   place(im,animated(soul,'mirror_lifecycle',t if t<8 else 8+(t-8)*.8 if t<18 else 18+(t-18)*1.75),480,386,285,90,alpha=fade)
   if t<25:actor(im,480,430,alpha=fade*.18)
  elif soul=='senbonzakura':
   for j in range(24 if quality=='standard' else 10):
    a=j*2.4+t*.13;r=140*(1-phase(t,16,27))+phase(t,32,42)*140
    place(im,p+'falling_petal',480+math.cos(a)*r,315+math.sin(a)*r*.55,28,angle=j*31+t*4,alpha=fade)
   place(im,p+'phantom_blade',480,297,70,180,alpha=phase(t,20,26)*(1-phase(t,30,40)))
  else:
   place(im,p+'direction_arc',525,290,100,120,angle=t*13,alpha=fade)
   place(im,p+'piercing_light',480,285,320,42,angle=90,alpha=phase(t,12,15)*(1-phase(t,17,26)))
  place(im,p+'true_name_seal',480,210,100,alpha=phase(t,23,27)*(1-phase(t,36,44)))
  if t>25:d.text((355,442),soul.upper()+'  /  TRUE NAME',font=font(18,True),fill=C[soul])
 elif soul=='akatsuki':
  actor(im,275,375);actor(im,640,370,color='#455063')
  for j in range(6):
   a=phase(t,j*2,j*2+3)*(1-phase(t,40,56));scale=1-phase(t,24,32)*.65
   place(im,p+f'scar_{j}_stable',640,300,300*scale,alpha=a)
  for j in range(4):place(im,p+'causal_node',640+math.cos(j*1.57)*28,300+math.sin(j*1.57)*28,35,alpha=phase(t,10,18)*(1-phase(t,34,44)))
  place(im,p+'rift',640,300,170,320,alpha=phase(t,14,22)*(1-phase(t,37,48)))
  place(im,p+'execution_cross',640,300,420,angle=18,alpha=phase(t,32,34)*(1-phase(t,36,43)))
 elif soul=='kyouka':
  actor(im,405,375)
  n=8 if quality=='standard' else 3
  for j in range(n):
   a=j*math.tau/n;r=220;x=480+math.cos(a)*r;y=280+math.sin(a)*85
   place(im,p+'water_mirror',x,y,104,158,angle=math.sin(a)*12,alpha=phase(t,j,10+j)*(1-phase(t,42,50))*.8)
   if 14<t<40:actor(im,int(x),int(y+39),alpha=.16)
   f=phase(t,24+j,30+j)
   if 0<f<1:place(im,p+'replay_cut',x+(640-x)*f,y+(300-y)*f,100,angle=j*44,alpha=math.sin(f*math.pi))
   if t>=40:
    for k in range(3):place(im,p+f'shard_{k}',x+(t-40)*math.cos(k*2.1+j)*5,y+(t-40)*math.sin(k*2.1+j)*3,32,angle=j*43+t*3,alpha=1-phase(t,40,56))
  place(im,p+'replay_cut',640,300,280,angle=30,alpha=phase(t,36,39)*(1-phase(t,41,48)))
 elif soul=='senbonzakura':
  actor(im,290,393);n=144 if quality=='standard' else 32
  for j in range(n):
   pet=j%5;a=pet*math.tau/5;u=(j//5)/max(1,(n//5));r=45+u*160
   x=590+math.cos(a)*(r)+math.cos(a+math.pi/2)*math.sin(u*math.pi)*50
   y=253+math.sin(a)*(r*.65)+math.sin(a+math.pi/2)*math.sin(u*math.pi)*30
   settle=phase(t,0,16);x=x*settle+(150+(j*173)%660)*(1-settle);y=y*settle+(160+j%10*19)*(1-settle)
   rush=phase(t,28+pet*2,34+pet*2);x=x*(1-rush)+680*rush;y=y*(1-rush)+365*rush
   if t<45:place(im,p+'blade_small',x,y,16,38,angle=-a*180/math.pi+90,alpha=1-phase(t,42,46))
   else:place(im,p+'falling_petal',x+math.sin(j+t*.08)*55,y+(t-45)*(1+j%3),20,angle=j*33+t,alpha=1-phase(t,48,64))
  place(im,p+'array_core',590,253,85,alpha=phase(t,18,24)*(1-phase(t,35,42)))
 elif soul=='raikiri':
  x=250+phase(t,16,20)*480;actor(im,int(x),365)
  place(im,p+'direction_arc',290,300,100,angle=t*12,alpha=phase(t,0,12)*(1-phase(t,16,20)))
  place(im,p+'landing_pulse',250,387,130,50,alpha=phase(t,5,12)*(1-phase(t,15,20)))
  if t>16:
   place(im,p+'step_ribbon',490,320,540,90,alpha=1-phase(t,23,36))
   for j in range(6):place(im,p+'direction_arc',300+j*75,320,70,120,angle=-10,alpha=phase(t,22+j,23+j)*(1-phase(t,25+j,29+j)))
  place(im,p+'piercing_light',500,315,760,55,alpha=phase(t,29,30)*(1-phase(t,32,38)))
 if kind!='awakening':place(im,p+'hud_corner',910,120,50,alpha=.85)
 d=ImageDraw.Draw(im);d.text((25,99),f'{t:04.1f} ticks  /  pose blocks = model placeholders',font=font(12),fill='#899ab0');return im

def write_movie(name,fn,ticks):
 out=O/(name+'.mp4');proc=subprocess.Popen(['ffmpeg','-v','error','-y','-f','rawvideo','-vcodec','rawvideo','-pix_fmt','rgb24','-s','960x540','-r','20','-i','-','-an','-c:v','libx264','-crf','21','-preset','fast','-pix_fmt','yuv420p','-movflags','+faststart',str(out)],stdin=subprocess.PIPE)
 for i in range(ticks):proc.stdin.write(fn(i).convert('RGB').tobytes())
 proc.stdin.close()
 if proc.wait():raise RuntimeError(name)
 print('Preview:',out.name)

def demo(kind,t):
 soul={'blade_transition':'senbonzakura','thunder_step':'raikiri','mirror_replay':'kyouka'}[kind];p=soul+'/';im=bg(soul,kind.upper()+' / DYNAMIC ASSEMBLY REFERENCE')
 if kind=='blade_transition':
  place(im,animated(soul,'blade_disperse',t if t<16 else 31-t),480,290,290);actor(im,250,370)
 elif kind=='thunder_step':
  x=200+phase(t,4,14)*520
  for j in range(3):actor(im,int(x-50-j*65),365,alpha=.12*(3-j))
  actor(im,int(x),365);place(im,p+'step_ribbon',(200+x)/2,316,max(40,x-200),65,alpha=1-phase(t,15,30));place(im,p+'landing_pulse',x,380,120,45,alpha=phase(t,12,14)*(1-phase(t,18,30)))
 else:
  actor(im,260,375);place(im,p+'water_mirror',470,290,150,230,alpha=phase(t,0,6)*(1-phase(t,25,36)))
  actor(im,470,335,alpha=.25);place(im,p+'replay_cut',470+phase(t,12,24)*240,290,160,angle=-20,alpha=phase(t,10,12)*(1-phase(t,25,34)))
 return im

def stills():
 for soul in C:
  aa=[a for a in M['assets'] if a['soul']==soul];height=160+math.ceil(len(aa)/4)*165
  im=Image.new('RGBA',(1000,height),'#10151f');d=ImageDraw.Draw(im);d.text((24,20),TITLE[soul],font=font(24,True),fill=C[soul]);d.text((24,58),'EXPORTED ATLAS REGIONS / dark + light readability / original SVG',font=font(14),fill='white')
  for i,a in enumerate(aa):
   x=(i%4)*245+12;y=110+(i//4)*165
   d.rectangle((x,y,x+232,y+125),fill='#0a0c14');d.rectangle((x+116,y,x+232,y+125),fill='#e6e5de')
   place(im,a['id'],x+58,y+63,112);place(im,a['id'],x+174,y+63,112)
   d.text((x,y+130),a['id'].split('/')[1],font=font(11),fill='white')
  im.convert('RGB').save(O/(soul+'_atlas.png'))
  if soul!='shared':scene(soul,'finisher',{'akatsuki':34,'kyouka':30,'senbonzakura':22,'raikiri':30}[soul]).convert('RGB').save(O/(soul+'_overview.png'))
 # Three arrays use exactly the same actual blade tile.
 im=bg('senbonzakura','THREE FORMATIONS / SAME blade_small REGION');d=ImageDraw.Draw(im)
 for kind in range(3):
  cx=170+kind*310;d.text((cx-95,115),['FAN / 24 blades','GUARD / 24 blades','SKY RAIN / 24 blades'][kind],font=font(15,True),fill=C['senbonzakura'])
  for j in range(24):
   if kind==0:a=-1.2+j*.1;x=cx+math.sin(a)*130;y=330-math.cos(a)*110;rot=-a*180/math.pi
   elif kind==1:a=j*math.tau/24;x=cx+math.cos(a)*110;y=290+math.sin(a)*85;rot=-a*180/math.pi
   else:x=cx-100+(j%8)*28;y=210+(j//8)*65;rot=-18
   place(im,'senbonzakura/blade_small',x,y,20,55,angle=rot)
  actor(im,cx,410,alpha=.6)
 im.convert('RGB').save(O/'sakura_formations.png')
 for soul in list(C)[:4]:
  frame={'akatsuki':34,'kyouka':30,'senbonzakura':22,'raikiri':30}[soul]
  pair=Image.new('RGB',(960,540));pair.paste(scene(soul,'finisher',frame,'low').resize((480,270)).convert('RGB'),(0,0));pair.paste(scene(soul,'finisher',frame).resize((480,270)).convert('RGB'),(480,0));pair.paste(scene(soul,'finisher',frame,'low',True).resize((480,270)).convert('RGB'),(0,270));pair.paste(scene(soul,'finisher',frame,'standard',True).resize((480,270)).convert('RGB'),(480,270));pair.save(O/(soul+'_quality.png'))
if __name__=='__main__':
 stills()
 for soul in list(C)[:4]:
  write_movie(soul+'_awakening',lambda t,s=soul:scene(s,'awakening',t),48)
  write_movie(soul+'_release',lambda t,s=soul:scene(s,'finisher',t),64 if soul=='senbonzakura' else 56 if soul!='raikiri' else 40)
 for kind,n in [('blade_transition',32),('thunder_step',32),('mirror_replay',40)]:write_movie(kind,lambda t,k=kind:demo(k,t),n)
