#!/usr/bin/env python3
"""Hand-authored vector VFX. SVGs are authoritative; export via sharp, no image model.
Run: python tools/art/build_awakened.py && node tools/art/export_awakened.cjs
"""
from pathlib import Path
import json, math
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'art/svg/awakened'
OUT=ROOT/'src/main/resources/assets/blade_tetra/textures/effect/awakened'
DOC=ROOT/'docs/awakened-rework'
for p in [SRC,OUT,DOC]:p.mkdir(parents=True,exist_ok=True)
P={'akatsuki':('#280c18','#ad2344','#ff5368','#fff0dd'), 'kyouka':('#163c58','#478eaa','#a2e7ef','#f0ffff'), 'senbonzakura':('#51243e','#b95983','#ffb7d5','#fff0f7'), 'raikiri':('#16354f','#447fce','#88dfff','#f6ffff'), 'shared':('#272434','#827c98','#d6c8f0','#ffffff')}
assets=[]; sheets=[]
LAYOUT={'akatsuki':(8,4),'kyouka':(4,4),'senbonzakura':(4,4),'raikiri':(4,4),'shared':(2,2)}
def path(d,fill='none',stroke=None,sw=1,extra=''):
 return f'<path d="{d}" fill="{fill}"'+(f' stroke="{stroke}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"' if stroke else '')+f' {extra}/>'
def poly(points,fill,extra=''):return f'<polygon points="{points}" fill="{fill}" {extra}/>'
def circle(x,y,r,c,sw=1):return f'<circle cx="{x}" cy="{y}" r="{r}" fill="none" stroke="{c}" stroke-width="{sw}"/>'
def group(s,transform='',alpha=1):return f'<g transform="{transform}" opacity="{alpha:.4f}">{s}</g>'
def svg(body):return '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">'+body+'</svg>\n'
def scar(angle=0,state=1,variant=0):
 d,c,h,w=P['akatsuki']; bend=[0,-3,2,-1,3,-2][variant]
 s=path(f'M 8 64 L 42 {61+bend} L 68 63 L 120 64 L 78 68 L 49 {66+bend} Z',d)
 s+=path(f'M 8 64 Q 58 {59+bend} 120 64 Q 66 {69+bend} 8 64',c)
 s+=path(f'M 13 64 Q 66 {62+bend} 114 64',stroke=h,sw=1.4)
 s+=path(f'M 21 64 Q 67 {63+bend} 106 64',stroke=w,sw=.6)
 s+=path('M 26 60 L 39 58 M 84 70 L 99 69',stroke=c,sw=.6)
 if state==0:s=group(s,'translate(64 0) scale(.6 1) translate(-64 0)',.65)
 if state==2:
  s=group(s,'',.33)+path('M 31 58 L 38 56 L 36 60 Z M 77 72 L 88 70 L 82 75 Z M 99 58 L 104 55 L 101 60 Z',h)
 return group(s,f'rotate({angle} 64 64)')
def node():
 d,c,h,w=P['akatsuki'];return poly('64,40 69,58 88,64 69,69 64,88 59,69 40,64 59,59',d)+poly('64,49 68,60 79,64 68,68 64,79 60,68 49,64 60,60',c)+poly('64,56 67,63 72,64 65,66 64,72 62,65 56,64 63,62',w)+path('M 51 48 L 48 51 M 77 48 L 80 51 M 48 77 L 51 80',stroke=h)
def crescent():
 d,c,h,w=P['akatsuki'];return path('M 94 14 C 34 -1 5 46 23 85 C 36 115 73 125 101 100 C 66 111 43 93 39 68 C 34 43 56 18 94 14 Z',d)+path('M 93 15 C 39 5 15 45 27 78 C 36 104 61 115 86 108 C 51 107 32 83 36 56 C 39 36 58 20 93 15',c)+path('M 78 14 C 31 19 15 59 30 87 M 36 97 L 43 104 M 53 110 L 62 112',stroke=h,sw=1.4)+path('M 64 22 L 56 29 M 25 59 L 26 71',stroke=w,sw=.7)
def rift():
 d,c,h,w=P['akatsuki'];return path('M 64 8 L 58 29 L 62 41 L 48 56 L 55 69 L 47 82 L 62 93 L 64 120 L 70 96 L 65 82 L 80 68 L 72 55 L 78 42 L 66 30 Z',c)+path('M 64 13 L 61 32 L 65 42 L 53 57 L 60 69 L 52 82 L 65 92 L 64 115 L 67 94 L 61 81 L 75 68 L 67 55 L 73 42 L 63 30 Z','#100d19')+path('M 59 31 L 63 41 L 50 56 M 74 56 L 80 68 L 67 82',stroke=h,sw=1.2)
def cross(soul):
 d,c,h,w=P[soul];return poly('64,6 69,57 122,64 69,70 64,122 58,70 6,64 58,58',d)+poly('64,10 66,61 118,64 66,67 64,118 61,67 10,64 61,61',h)+poly('64,18 65,63 110,64 65,65 64,110 63,65 18,64 63,63',w)
def mirror(stage=1,t=0):
 d,c,h,w=P['kyouka']; s=path('M 64 9 C 94 9 112 34 112 64 C 112 96 91 119 64 119 C 36 119 16 96 16 64 C 16 34 35 9 64 9 Z',d, c,1.2, 'fill-opacity=".35"')
 s+=path('M 63 13 C 90 13 107 34 107 64 C 107 94 89 114 65 114',stroke=w,sw=1.2)+path('M 58 15 C 34 19 21 39 21 64 C 21 90 37 109 56 113',stroke=h,sw=2)
 s+=path('M 30 47 Q 63 33 97 46 M 25 77 Q 65 64 101 78 M 37 98 Q 65 88 92 97',stroke=c,sw=.7,extra='opacity=".65"')
 s+=path('M 31 37 L 68 20 L 42 52 Z M 78 80 L 101 59 L 92 95 Z',h,extra='opacity=".13"')
 if stage>=2:
  s+=path('M 65 64 L 44 42 L 46 24 M 44 42 L 24 47 M 65 64 L 88 53 L 99 31 M 65 64 L 61 90 L 76 113 M 61 90 L 30 98 M 65 64 L 103 85',stroke=w,sw=1)
 if stage==3:s=group(s,'',.22)
 return s

def shard(i):
 d,c,h,w=P['kyouka']; shapes=['22,17 98,42 44,111','38,14 108,72 17,95','22,46 89,16 104,111','18,24 111,36 63,112'];pts=shapes[i%4]
 return poly(pts,d)+poly(pts,h,'fill-opacity=".25"')+path('M '+pts.replace(' ',' L ')+' Z',stroke=h,sw=1.3)+path('M 38 39 L 65 48 L 45 81',stroke=w,sw=.7)
def echo_cut():
 d,c,h,w=P['kyouka'];return path('M 10 101 Q 25 25 119 23 Q 50 34 10 101',d)+path('M 11 98 Q 33 29 116 24 Q 56 38 11 98',h)+path('M 16 84 Q 40 36 109 27',stroke=w,sw=1.4)+path('M 24 112 Q 48 51 111 40 M 38 115 Q 58 64 105 55',stroke=c,sw=1,extra='stroke-dasharray="9 5"')
def mask(soul,kind):
 d,c,h,w=P[soul]
 if kind=='gradient':return '<defs><linearGradient id="f"><stop stop-color="#fff" stop-opacity="0"/><stop offset=".7" stop-color="#fff" stop-opacity=".65"/><stop offset="1" stop-color="#fff"/></linearGradient></defs><rect x="8" y="8" width="112" height="112" fill="url(#f)"/>'
 # UV-neutral material, deliberately no body silhouette or fixed skin UV assumption.
 s=''
 for i in range(8):
  x=10+i*14;s+=path(f'M {x} 115 L {x+7} 13',stroke=h,sw=1+i%2,extra=f'opacity="{.12+i*.06}"')
 if kind=='edge':s+=path('M 9 102 L 23 18 M 105 110 L 119 26',stroke=w,sw=2)
 return s

def blade(kind=0):
 d,c,h,w=P['senbonzakura']
 shapes=['64,14 74,57 63,111 55,84 57,47','61,9 77,42 66,112 50,91 55,45','60,6 76,28 66,117 54,105 55,35','27,31 59,36 75,17 88,44 78,71 49,107 51,73 26,64','60,47 72,61 65,79 53,65']
 return poly(shapes[kind],d)+path('M '+shapes[kind].replace(' ',' L ')+' Z',c,h,1)+path('M 62 22 L 64 63 L 60 101 L 69 56 Z',w,extra='opacity=".9"')+path('M 58 47 L 56 78 M 68 34 L 71 45',stroke=h,sw=.8)
def petal():
 d,c,h,w=P['senbonzakura'];return path('M 65 30 C 93 16 108 40 92 67 C 79 88 51 102 30 101 C 33 78 36 50 51 33 L 59 41 Z',c,h,1)+path('M 37 94 Q 62 66 76 39',stroke=w,sw=1.3)+path('M 45 83 L 67 77 M 54 69 L 51 52',stroke=h,sw=.7)
def arc(kind=0):
 d,c,h,w=P['raikiri']; lines=['M 8 70 L 28 67 L 39 49 L 47 64 L 69 55 L 78 63 L 99 50 L 120 57','M 12 91 L 38 65 L 31 54 L 68 46 L 64 29 L 109 18','M 10 64 L 34 63 M 43 60 L 57 66 L 72 62 M 82 60 L 100 62 L 118 61']
 s=path(lines[kind],stroke=d,sw=7)+path(lines[kind],stroke=c,sw=4)+path(lines[kind],stroke=h,sw=1.8)+path(lines[kind],stroke=w,sw=.65)
 s+=path('M 40 51 L 52 37 L 47 31 M 79 62 L 90 79 L 103 82',stroke=c,sw=1)+path('M 18 78 L 42 73 M 86 43 L 111 41',stroke=h,sw=.6)
 return s

def pulse(soul):
 d,c,h,w=P[soul];s=''
 for i in range(6):s+=group(path('M 64 23 L 68 39 L 65 45 L 71 53',stroke=h,sw=1.2),f'rotate({i*60} 64 64)')
 return s+circle(64,64,23,c,1)+circle(64,64,25,w,.6)
def lightning_cut():
 d,c,h,w=P['raikiri'];return path('M 8 100 Q 31 19 119 19 Q 48 36 8 100',d)+path('M 11 93 Q 38 25 116 20 Q 57 39 11 93',w)+path('M 23 89 L 39 62 L 53 66 L 62 43 L 86 42 L 98 27',stroke=h,sw=1.8)+path('M 37 53 L 47 38 L 64 37 M 72 62 L 86 55 L 107 58',stroke=c,sw=1)
def seal(soul):
 if soul=='akatsuki':return group(crescent(),'translate(25 25) scale(.6)')+group(scar(135),'')
 if soul=='kyouka':return group(mirror(),'translate(20 15) scale(.7)')+group(shard(2),'translate(54 60) scale(.35)')
 if soul=='senbonzakura':return ''.join(group(petal(),f'rotate({i*72} 64 64) translate(28 10) scale(.55)') for i in range(5))
 return group(arc(1),'')+path('M 39 100 L 69 73 L 60 66 L 98 28',stroke=P[soul][3],sw=1)

def add(soul,name,body,role,size=(1,1),blend='alpha',interface='billboard',stage='attack',tier='daily'):
 index=sum(a['soul']==soul and a['sheet']==soul for a in assets)
 f=SRC/soul/(name+'.svg');f.parent.mkdir(exist_ok=True);f.write_text(svg(body))
 cols,rows=LAYOUT[soul];width=cols*128;height=rows*128
 x=(index%cols)*128;y=(index//cols)*128
 assets.append(dict(id=soul+'/'+name,soul=soul,status='produced',sheet=soul,source=str(f.relative_to(ROOT)),texture=f'blade_tetra:textures/effect/awakened/{soul}/atlas.png',rect=[x,y,128,128],canvas_size=[width,height],uv=[(x+.5)/width,(y+.5)/height,(x+127.5)/width,(y+127.5)/height],pivot=[.5,.5],size_blocks=list(size),blend=blend,interface=interface,stage=stage,tier=tier,role=role))
for i,a in enumerate([0,30,60,90,120,150]):
 for st,sn in enumerate(['appear','stable','break']):add('akatsuki',f'scar_{i}_{sn}',scar(a,st,i),f'断命线 {a}° / {sn}',(2.8,.7),'alpha','oriented_plane','mark/'+sn)
for name,body,role,sz in [('causal_node',node(),'因果交错的独立交点',(.3,.3)),('crescent',crescent(),'觉醒背后残月',(2.5,2.5)),('rift',rift(),'命断黑红裂隙',(1.2,4)),('execution_cross',cross('akatsuki'),'命断十字终斩',(5,5)),('blade_trace',scar(),'刀身红纹的独立条带',(1.2,.06))]:add('akatsuki',name,body,role,sz,stage='awakening' if name in ['crescent','blade_trace'] else 'finisher',tier='ultimate')
for name,body,role,sz in [('water_mirror',mirror(),'稳定水镜',(1.4,2.1)),('mirror_cracked',mirror(2),'裂纹镜面',(1.4,2.1)),('mirror_ghost',group(mirror(),'translate(12 12) scale(.8)',.45),'远层水镜',(1,1.5)),('replay_cut',echo_cut(),'重演的双轨虚斩',(2.6,2.6)),('ripple',circle(64,64,42,'#a2e7ef',1)+circle(64,64,33,'#478eaa',.7),'独立水波圈',(2,2))]:add('kyouka',name,body,role,sz,stage='mirror/replay',tier='medium')
for i in range(4):add('kyouka',f'shard_{i}',shard(i),'镜面可复用碎片',(.2,.35),'alpha','atlas_billboard','shatter','medium')
for soul in ['kyouka','raikiri']:
 for k in ['wash','edge','gradient']:add(soul,'echo_'+k,mask(soul,k),'模型残影材质；不是人物图',(1,1),'alpha','model_pass','afterimage')
for i,n in enumerate(['blade_small','blade_medium','phantom_blade','petal_blade','dissolve_chip']):add('senbonzakura',n,blade(i),'散华独立碎刃 / '+n,(.12,.4+i*.1),'alpha','atlas_billboard','disperse')
add('senbonzakura','falling_petal',petal(),'终景后飘落樱瓣',(.15,.2))
add('senbonzakura','array_core',seal('senbonzakura'),'五瓣阵心；外围由独立剑刃组成',(1.2,1.2),stage='finisher',tier='ultimate')
for i,n in enumerate(['step_ribbon','direction_arc','dash_strobe']):add('raikiri',n,arc(i),'雷步定向条带',(3,.3),'additive','ribbon','dash','medium')
for name,b,sz in [('landing_pulse',pulse('raikiri'),(1.3,1.3)),('thunder_cut',lightning_cut(),(2.7,2.7)),('piercing_light',group(scar(),'rotate(-10 64 64)'),(7,.18))]:
 if name=='piercing_light':b=b.replace('#280c18','#16354f').replace('#ad2344','#447fce').replace('#ff5368','#88dfff').replace('#fff0dd','#f6ffff')
 add('raikiri',name,b,'雷刃 / 神鸣终结层',(sz),'additive','oriented_plane','finisher','ultimate')
for soul in list(P)[:4]:
 add(soul,'true_name_seal',seal(soul),'专属真名符纹，不含文字',(1.2,1.2),stage='release',tier='medium')
 add(soul,'hud_corner',group(seal(soul),'translate(12 12) scale(.8)'),'HUD 真名角标',(0,0),'alpha','hud','release')
add('shared','success_burst',cross('shared'),'终结成功短光爆',(.8,.8),'additive','billboard','success','medium')
add('shared','alpha_ramp',mask('shared','gradient'),'透明度渐变辅助纹理',(1,1),'alpha','model_pass','fade')
add('shared','switch_slash',path('M 12 100 Q 33 28 116 23 Q 55 39 12 100','#ffffff'),'普通到解放刀光过渡遮罩',(2,2),'alpha','oriented_plane','switch')
# Independently editable material-frame sheets; chronological left-to-right, then top-to-bottom.
def anim(soul,name,frames,ticks,fn,states):
 folder=SRC/soul/name;folder.mkdir(exist_ok=True)
 for i in range(frames):(folder/f'{i:02}.svg').write_text(svg(fn(i/(frames-1),i)))
 sheets.append(dict(id=soul+'/'+name,soul=soul,status='produced',source_directory=str(folder.relative_to(ROOT)),texture=f'blade_tetra:textures/effect/awakened/{soul}/{name}.png',layout=[4,4],cell=[128,128],frames=frames,ticks_per_frame=ticks,loop=False,states=states,pivot=[.5,.5],blend='alpha',interface='animated_plane'))
def scar_frame(t,i):
 s=scar(0,1)
 if t<.25:return group(s,f'translate(64 0) scale({.15+t*3.4} 1) translate(-64 0)',t*4)
 if t<.65:return s
 return group(scar(0,2),'',max(0,(1-t)/.35))
def mirror_frame(t,i):
 if t<.3:return group(mirror(),f'translate(64 64) scale({.15+t*2.8}) translate(-64 -64)',min(1,t*5))
 if t<.55:return mirror()+circle(64,64,20+(t-.3)*70,'#a2e7ef',.7)
 if t<.8:return mirror(2)
 return ''.join(group(shard(j),f'translate({math.cos(j*1.57)*(t-.8)*150} {math.sin(j*1.57)*(t-.8)*150}) translate(64 64) scale(.5) translate(-64 -64)',max(0,(1-t)*5)) for j in range(4))
def transition(t,i):
 return group(blade(2),'',max(0,1-t*2))+''.join(group(blade(j%4),f'translate({math.cos(j*2.4)*t*35} {math.sin(j*2.4)*t*35}) translate(64 64) rotate({j*27*t}) scale(.45) translate(-64 -64)',min(1,t*3)) for j in range(7))
anim('akatsuki','scar_lifecycle',16,1,scar_frame,{'appear':[0,3],'stable':[4,9],'break':[10,15]})
anim('kyouka','mirror_lifecycle',16,2,mirror_frame,{'generate':[0,3],'ripple':[4,7],'stable':[8,8],'crack':[9,11],'shatter':[12,15]})
anim('senbonzakura','blade_disperse',16,1,transition,{'blade':[0,0],'split':[1,7],'scatter':[8,15],'gather':'reverse frames 15..0'})
anim('raikiri','step_flicker',8,1,lambda t,i:group(arc(i%3),'',1-t*.9),{'flash':[0,2],'residue':[3,7]})
anim('shared','release_fold',12,1,lambda t,i:group(path('M 64 18 L 79 52 L 110 64 L 79 76 L 64 110 L 49 76 L 18 64 L 49 52 Z',stroke='#ffffff',sw=1),f'translate(64 64) scale({math.sin(t*math.pi):.4f} 1) translate(-64 -64)',math.sin(t*math.pi)),{'unfold':[0,5],'fold':[6,11]})
manifest=dict(schema_version=1,baseline='72331ebbf5f0f62f2aded0cf28a713ca3a146fb7',namespace='blade_tetra',license='MIT; original hand-authored vector artwork',tile_size=128,atlas_layout=LAYOUT,padding_px=2,alpha='straight RGBA; RGB retained at edges, no premultiplied output',sampling='linear; clamp-to-edge; no mipmaps; sample rect inset by 0.5 px',assets=assets,animations=sheets,quality={'low':{'mirrors':3,'blades':32,'echoes':1,'ribbon_planes':1},'standard':{'mirrors':8,'blades':144,'echoes':3,'ribbon_planes':2}},dynamic_effects=['server-confirmed events','attack replay model poses','ribbon endpoints','sakura formation transforms','blade-surface overlay UV','localized true-name text'],concept_only=['formation layouts in offline previews are composition references, not gameplay implementation'])
(DOC/'assets.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(f'Authored {len(assets)} components and {sum(s["frames"] for s in sheets)} material frames')
