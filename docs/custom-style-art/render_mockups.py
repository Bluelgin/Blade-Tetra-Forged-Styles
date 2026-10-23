#!/usr/bin/env python3
"""Concept-only layouts using production PNGs; never capture or simulate Minecraft.
Pillow 12.3.0. --font points to any locally licensed CJK font (not redistributed).
SVG layouts embed the generated pixel assets and keep all labels editable.
"""
import argparse, base64, html, json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
D=Path(__file__).resolve().parent;R=D.parents[1];M=json.loads((D/'export/asset-manifest.json').read_text())
AS={a['name']:R/a['texture'] for a in M['assets']};P=M['palette']
class Canvas:
 def __init__(self,w,h,font):
  self.w,self.h,self.font=w,h,font;self.im=Image.new('RGBA',(w,h));self.d=ImageDraw.Draw(self.im);self.parts=[]
 def rect(self,x,y,w,h,c):
  c=P.get(c,c);self.d.rectangle((x,y,x+w-1,y+h-1),fill=c);self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{c}"/>')
 def text(self,x,y,t,c='ink',sz=10):
  c=P.get(c,c);font=ImageFont.truetype(self.font,sz);self.d.text((x,y),t,font=font,fill=c,anchor='lt')
  self.parts.append(f'<text x="{x}" y="{y}" dominant-baseline="text-before-edge" font-family="Noto Sans CJK SC, sans-serif" font-size="{sz}" fill="{c}">{html.escape(t)}</text>')
 def asset(self,n,x,y,w=None,h=None):
  im=Image.open(AS[n]).convert('RGBA');w=w or im.width;h=h or im.height;self.im.alpha_composite(im.resize((w,h),Image.Resampling.NEAREST),(x,y))
  b=base64.b64encode(AS[n].read_bytes()).decode();self.parts.append(f'<image x="{x}" y="{y}" width="{w}" height="{h}" image-rendering="pixelated" href="data:image/png;base64,{b}"/>')
 def button(self,x,y,w,t,icon):
  # Three-slice horizontally: 3px caps, stretch the 18px centre only.
  im=Image.open(AS['button_normal']);self.im.alpha_composite(im.crop((0,0,3,20)),(x,y));self.im.alpha_composite(im.crop((3,0,21,20)).resize((w-6,20),Image.Resampling.NEAREST),(x+3,y));self.im.alpha_composite(im.crop((21,0,24,20)),(x+w-3,y))
  b=base64.b64encode(AS['button_normal'].read_bytes()).decode()
  for dx,sw,vx,vw in [(0,3,0,3),(3,w-6,3,18),(w-3,3,21,3)]:
   self.parts.append(f'<svg x="{x+dx}" y="{y}" width="{sw}" height="20" viewBox="{vx} 0 {vw} 20" preserveAspectRatio="none"><image width="24" height="20" image-rendering="pixelated" href="data:image/png;base64,{b}"/></svg>')
  self.asset(icon,x+4,y+4);self.text(x+19,y+5,t,'white',9)
 def save(self,n,scale=3):
  self.im.resize((self.w*scale,self.h*scale),Image.Resampling.NEAREST).save(D/f'mockups/{n}.png')
  (D/f'mockups/{n}.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}"><title>CONCEPT ONLY / NOT A GAME CAPTURE</title>'+''.join(self.parts)+'</svg>\n')
def scene(c):
 c.rect(0,0,320,240,'#343b3a');c.rect(0,120,320,120,'#424539');c.rect(0,180,320,60,'#33352f')
 for x,y,w,h in [(30,70,40,55),(240,58,30,70),(100,100,20,25)]:c.rect(x,y,w,h,'#2c3430')
 c.rect(154,116,12,1,'#c5c3b2');c.rect(160,110,1,12,'#c5c3b2')
 c.rect(65,215,190,22,'#24232a')
 for x in range(69,250,21):c.rect(x,218,18,16,'#514c47')
 c.text(8,224,'CONCEPT / NOT IN-GAME','shade',7)
def main():
 p=argparse.ArgumentParser();p.add_argument('--font',required=True);args=p.parse_args()
 for en in [False,True]:
  loc='en' if en else 'zh';c=Canvas(320,240,args.font);scene(c);c.asset('recording_hud',8,8);c.asset('state_recordable',17,14)
  c.text(34,13,'Ready: Rising Slash' if en else '可采录：上挑','white',9);c.text(34,32,'4 / 6','light',8);c.text(103,32,'[R] Record' if en else '[R] 采录','paper',8);c.save('hud_'+loc)
  c=Canvas(320,240,args.font);c.rect(0,0,320,240,'#252329');x,y=36,10;c.asset('editor_panel',x,y)
  c.text(x+40,y+10,'Qianmen Sword Manual' if en else '千门剑谱 · 编纂',sz=10);c.text(x+230,y+9,'×','edge',10)
  c.text(x+18,y+39,'Autumn Gate' if en else '秋门流',sz=10)
  actions=['Horizontal Slash','Follow-up B','Rising Slash','Spinning Slash'] if en else ['横斩','连击 B','上挑','回旋斩']
  for i,t in enumerate(actions):
   c.asset('action_row_selected' if i==3 else 'action_row',x+12,y+62+i*22);c.text(x+17,y+68+i*22,str(i+1),'white',9);c.text(x+39,y+68+i*22,t,sz=9)
  c.text(x+12,y+182,'Actions: 4 / 6' if en else '招式：4 / 6',sz=9)
  for xx,w,t,ic in [(10,52,'Undo' if en else '撤销','undo'),(66,52,'Clear' if en else '清空','clear'),(122,116,'Compile' if en else '完成剑谱','finish')]:c.button(x+xx,y+194,w,t,ic)
  c.text(8,232,'CONCEPT / NOT IN-GAME','shade',7);c.save('editor_'+loc)
  c=Canvas(320,240,args.font);c.rect(0,0,320,240,'#252329');c.rect(8,12,304,210,'#c7b697');c.rect(9,13,302,208,'#e6ddcf');c.text(18,22,'Blade / processing' if en else '刀身 · 加工',sz=11);c.rect(18,42,284,1,'gold')
  for i,t in enumerate(['Orthodox','Iaido','Rengeki','Dangaku'] if en else ['本传','居合','连舞','断岳']):c.text(24,55+i*22,t,'edge',9)
  c.rect(18,142,108,22,'edge');c.asset('style_custom',22,145);c.text(42,149,'Custom style' if en else '自创流派','white',9)
  c.rect(134,50,1,158,'gold');c.asset('compiled_schematic',142,54);c.text(181,56,'Autumn Gate' if en else '秋门流',sz=10);c.text(181,73,'4 actions' if en else '4 招 · 已识别','teal',9)
  c.text(143,93,'Author: Player' if en else '编纂者：玩家',sz=9);c.text(143,112,'Blade length' if en else '刀身规格',sz=9)
  for i,(n,t) in enumerate(zip(['short','standard','long'],['Short','Standard','Long'] if en else ['短','标准','长'])):
   xx=143+i*52;c.rect(xx,128,48,45,'shade' if i==1 else 'white');c.asset('length_'+n,xx+16,133);c.text(xx+4,155,t,sz=8)
  c.asset('state_recognized',143,183);c.text(159,185,'Manual recognized' if en else '工作台已识别剑谱','teal',8)
  c.text(8,232,'CONCEPT / EXISTING TETRA LAYOUT ADAPTER REQUIRED','shade',6);c.save('workbench_'+loc)
 # Contact sheet preserves exact pixels and exposes 16px downsample and normal 32px.
 c=Canvas(1000,680,args.font);c.rect(0,0,1000,680,'#252329');c.text(28,22,'千门剑谱 / QIANMEN • ART RESOURCE REVIEW','white',24);c.text(28,62,'CONCEPT DESIGN • STATIC MOCKUPS, NOT GAME CAPTURES','gold',13)
 for i,(n,zh,en) in enumerate([('shiroku_fuda','式录札','FIELD NOTES'),('shiroku_fuda_active','采录状态','RECORDING'),('blank_schematic','空白剑谱','BLANK MANUAL'),('compiled_schematic','千门剑谱','COMPILED MANUAL')]):
  x=28+i*244;c.rect(x,105,220,252,'#302a30');c.asset(n,x+46,117,128,128);c.text(x+15,264,zh,'paper',17);c.text(x+15,294,en,'gold',11);c.asset(n,x+148,303,32,32);c.asset(n,x+190,319,16,16)
 for i,n in enumerate(['style_custom','length_short','length_standard','length_long']):
  x=38+i*125;c.asset(n,x,390,48,48);c.text(x,450,n.replace('length_',''),'paper',11)
 c.text(575,390,'INK / VERMILION / AGED METAL','gold',14);c.text(575,420,'32px items · 16px length glyphs','paper',14);c.text(575,446,'9 state symbols · 3 event sheets','paper',14)
 for i,n in enumerate(['waiting','recordable','added','full','unavailable','compiled','locked','recognized','forged']):c.asset('state_'+n,38+i*105,502,36,36);c.text(30+i*105,550,n,'paper',10)
 c.text(28,615,'SVG pixel masters / additive resources / native Tetra scroll contract','gold',15);c.save('resource-board',1)
 c=Canvas(800,345,args.font);c.rect(0,0,800,345,'#252329');c.text(24,16,'EVENT FRAMES / CONCEPT ONLY','paper',18)
 for row,n in enumerate(['ink_write','seal_set','forge_glint']):
  c.text(24,65+row*90,n,'gold',13)
  sheet=Image.open(AS[n]).convert('RGBA')
  for f in range(6):
   im=sheet.crop((0,f*16,16,(f+1)*16)).resize((48,48),Image.Resampling.NEAREST);x=230+f*88;y=55+row*90;c.im.alpha_composite(im,(x,y));b=base64.b64encode((D/f'export/frames/{n}_{f}.png').read_bytes()).decode();c.parts.append(f'<image x="{x}" y="{y}" width="48" height="48" image-rendering="pixelated" href="data:image/png;base64,{b}"/>');c.text(x+18,y+54,str(f),'paper',10)
 c.save('animation-frames',1)
 print('Rendered 6 bilingual 320×240 concepts + resource board and editable SVG layouts')
if __name__=='__main__':main()
