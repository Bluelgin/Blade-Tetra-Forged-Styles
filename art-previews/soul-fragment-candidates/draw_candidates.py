"""Original SVG/pixel studies only. Never writes runtime assets or touches the running client."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
SOULS = ['akatsuki', 'senbonzakura', 'raikiri', 'kyouka']
NAMES = ['赤月', '千本樱', '雷切', '镜花水月']
# Blood / Sakura colors sampled from our own shipped crystals. The other two share their value steps.
PALETTES = [
    ['#602c43','#ac4a61','#c2767c','#dfa096','#f4c3ad','#f8d7be','#dbc18b'],
    ['#9d567f','#bf80a2','#ce7ca1','#e4b3cb','#ead0d8','#fff0df','#dbc18b'],
    ['#435668','#63828d','#8ca8ad','#bdc9bd','#ebe2c5','#fff0df','#dbc18b'],
    ['#435866','#6e9299','#91b7b5','#bfd0c6','#e6dfcf','#fff0df','#dbc18b'],
]

class Art:
    def __init__(self, soul):
        self.palette = PALETTES[SOULS.index(soul)]
        self.image = Image.new('RGBA', (32,32))
        self.draw = ImageDraw.Draw(self.image)
        self.holes = []
        self.svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">']
    def poly(self, points, color):
        color = self.palette[color] if isinstance(color,int) else color
        points = [(x*2,y*2) for x,y in points]
        self.draw.polygon(points,fill=color)
        self.svg.append('<polygon points="'+ ' '.join(f'{x},{y}' for x,y in points)+'" fill="'+color+'"/>')
    def rect(self,x,y,w,h,color):
        color = self.palette[color] if isinstance(color,int) else color
        self.draw.rectangle((x*2,y*2,(x+w)*2-1,(y+h)*2-1),fill=color)
        self.svg.append(f'<rect x="{x*2}" y="{y*2}" width="{w*2}" height="{h*2}" fill="{color}"/>')
    def shine(self,x,y,color=5):
        color = self.palette[color] if isinstance(color,int) else color
        self.draw.point((x,y),fill=color)
        self.svg.append(f'<rect x="{x}" y="{y}" width="1" height="1" fill="{color}"/>')
    def cut(self,points):
        points=[(x*2,y*2) for x,y in points]
        self.draw.polygon(points,fill=(0,0,0,0))
        self.holes.append('<polygon points="'+' '.join(f'{x},{y}' for x,y in points)+'" fill="black"/>')
    def save(self,group,soul):
        p=OUT/group;p.mkdir(exist_ok=True)
        self.image.save(p/f'{soul}.png')
        body=self.svg
        if self.holes:
            body=[self.svg[0],'<defs><mask id="openings"><rect width="32" height="32" fill="white"/>',*self.holes,
                  '</mask></defs><g mask="url(#openings)">',*self.svg[1:],'</g>']
        (p/f'{soul}.svg').write_text('\n'.join(body+['</svg>'])+'\n',encoding='utf-8')
        return self.image

def crystal(soul):
    a=Art(soul)
    if soul=='akatsuki':
        # An asymmetric blood shard with a pale crescent trapped inside.
        a.poly([(7,1),(9,3),(10,5),(12,7),(12,11),(10,13),(7,15),(6,12),(4,10),(4,7),(6,5)],0)
        a.poly([(7,2),(8,4),(9,6),(11,8),(11,11),(9,12),(7,14),(7,11),(5,9),(5,7),(7,5)],1)
        a.poly([(7,3),(8,5),(8,7),(10,8),(10,11),(8,12),(7,10),(6,8),(6,6)],3)
        a.poly([(7,4),(7,7),(6,8),(7,10),(8,11),(9,10),(9,12),(8,13),(6,11),(5,9),(5,7)],2)
        a.poly([(8,5),(9,7),(9,8),(8,9),(8,11),(9,12),(7,11),(6,9),(6,7)],4)
        a.rect(7,7,1,2,5);a.rect(8,9,1,1,5);a.rect(10,8,1,2,2)
        a.rect(4,4,1,1,2);a.rect(11,13,1,1,1);a.shine(14,5);a.shine(17,23,6)
    elif soul=='senbonzakura':
        # Five uneven crystalline petals, instead of recoloring the blood shard.
        a.poly([(7,1),(10,2),(10,5),(13,4),(15,7),(12,9),(13,12),(10,13),(8,15),(6,12),(3,12),(2,9),(4,7),(3,4),(6,4)],0)
        a.poly([(7,2),(9,3),(9,6),(12,5),(14,7),(10,9),(12,11),(9,12),(8,14),(7,10),(4,11),(3,9),(6,8),(4,5),(7,6)],2)
        a.poly([(7,3),(8,3),(8,7),(10,7),(12,6),(11,8),(9,9),(10,11),(8,10),(7,12),(7,9),(4,9),(6,8),(6,6),(7,7)],3)
        a.poly([(7,5),(8,6),(8,8),(10,8),(8,9),(8,11),(7,9),(5,8),(7,8)],4)
        a.rect(7,7,1,2,5);a.rect(8,8,1,1,5);a.rect(11,2,1,1,3)
        a.rect(2,13,1,1,1);a.shine(13,6);a.shine(23,14)
    elif soul=='raikiri':
        # Forked quartz; warm storm light runs through a blue-grey outer crystal.
        a.poly([(9,1),(11,2),(10,6),(13,5),(13,9),(10,12),(7,15),(6,12),(3,13),(4,8),(6,6),(6,3),(8,4)],0)
        a.poly([(9,2),(10,3),(8,7),(12,6),(12,9),(9,11),(7,14),(7,10),(4,12),(5,8),(7,6),(7,4),(8,5)],1)
        a.poly([(9,3),(9,5),(7,8),(10,7),(11,7),(10,9),(8,11),(7,13),(8,9),(5,10),(6,8),(8,6)],2)
        a.poly([(9,2),(9,5),(7,8),(10,7),(9,10),(7,13),(8,9),(6,9),(7,6)],3)
        a.poly([(8,5),(8,7),(7,8),(9,8),(8,10),(7,11),(8,8),(7,8)],4)
        a.rect(8,6,1,2,5);a.rect(4,5,1,1,2);a.rect(12,12,1,1,1)
        a.shine(17,5);a.shine(15,19)
    else:
        # Crescent mirror with a hollow edge and a water-drop core.
        a.poly([(8,1),(11,2),(13,5),(13,9),(11,12),(8,15),(5,13),(3,10),(3,6),(5,3)],0)
        a.poly([(8,2),(11,3),(12,5),(12,8),(10,11),(7,14),(5,12),(4,9),(4,6),(6,4)],1)
        a.poly([(7,3),(10,3),(11,5),(11,7),(9,10),(7,12),(5,11),(4,8),(5,6)],2)
        a.poly([(7,3),(9,3),(7,5),(6,7),(6,9),(7,11),(8,11),(7,13),(5,11),(4,8),(5,5)],4)
        a.poly([(9,5),(10,7),(9,9),(8,10),(7,9),(7,8)],3)
        a.rect(8,7,1,2,5);a.rect(6,4,1,1,5);a.rect(12,2,1,1,1)
        a.rect(3,13,1,1,2);a.shine(10,19);a.shine(17,27)
    return a

def echo(soul):
    a=Art(soul)
    if soul=='akatsuki':
        # Horizontal echo: a hanging crescent, pulled apart by blood filaments.
        a.poly([(1,8),(3,7),(5,7),(6,5),(8,2),(10,1),(9,4),(8,6),(8,10),(10,12),(9,15),(7,13),(6,10),(3,10),(3,9)],0)
        a.poly([(2,8),(5,8),(7,6),(8,4),(8,7),(7,9),(8,11),(9,12),(8,14),(7,12),(6,9),(3,9)],2)
        a.poly([(9,3),(11,5),(11,7),(14,6),(15,7),(13,9),(11,9),(10,12),(8,11),(9,8)],1)
        a.poly([(9,4),(10,6),(10,9),(9,11),(8,10),(8,7)],4)
        a.rect(8,6,1,4,5);a.rect(10,7,2,1,3);a.rect(13,7,1,1,2)
        a.rect(4,5,1,1,1);a.rect(11,13,1,1,2);a.shine(16,9);a.shine(27,15)
    elif soul=='senbonzakura':
        # Petal-shaped wake sweeps around a seed of light, open on both sides.
        a.poly([(1,8),(3,5),(6,4),(8,2),(11,2),(13,4),(14,7),(12,9),(14,11),(11,13),(7,14),(4,12),(2,10)],0)
        a.poly([(2,8),(4,6),(7,5),(9,3),(11,3),(13,5),(12,7),(10,6),(8,6),(6,8),(4,9),(3,10)],2)
        a.poly([(3,10),(6,11),(9,11),(11,9),(13,9),(12,11),(9,13),(7,13),(4,11)],1)
        a.poly([(6,7),(8,5),(10,6),(11,8),(9,10),(7,11),(5,9)],3)
        a.poly([(8,5),(9,7),(10,8),(8,10),(7,9),(7,7)],4)
        a.rect(8,7,1,2,5);a.rect(4,6,2,1,3);a.rect(10,11,1,1,3)
        a.rect(14,5,1,1,2);a.rect(3,13,1,1,2);a.shine(17,13);a.shine(24,9)
    elif soul=='raikiri':
        # An interrupted discharge line, with a white-gold needle through its center.
        a.poly([(1,8),(3,6),(5,7),(7,5),(8,2),(10,1),(10,5),(12,4),(11,7),(14,6),(15,8),(13,10),(11,9),(9,12),(7,15),(7,11),(5,12),(5,9),(3,10)],0)
        a.poly([(2,8),(3,7),(5,8),(8,5),(9,3),(9,7),(11,5),(10,8),(13,7),(14,8),(12,9),(10,8),(8,12),(8,10),(6,11),(6,8),(3,9)],1)
        a.poly([(8,3),(9,2),(9,6),(8,8),(10,7),(9,10),(8,12),(7,13),(8,9),(6,9),(7,7)],3)
        a.poly([(8,4),(8,7),(7,8),(9,8),(8,10),(8,8),(7,8)],4)
        a.rect(8,6,1,2,5);a.rect(3,8,2,1,2);a.rect(12,8,1,1,2)
        a.rect(4,4,1,1,1);a.rect(12,12,1,1,2);a.shine(17,6);a.shine(13,18)
    else:
        # Three broken ripples shelter a suspended water core; no metal frame.
        a.poly([(1,8),(3,5),(6,4),(8,2),(10,2),(12,4),(14,5),(15,8),(13,11),(11,12),(8,14),(5,13),(3,11)],0)
        a.poly([(2,8),(4,6),(6,5),(9,3),(11,4),(13,6),(12,7),(10,5),(8,5),(6,7),(4,8),(3,9)],2)
        a.poly([(4,10),(6,11),(9,11),(11,9),(13,8),(13,10),(10,12),(7,13),(5,12)],1)
        a.poly([(8,5),(10,7),(10,9),(8,11),(6,9),(6,8)],3)
        a.poly([(8,6),(9,8),(8,10),(7,9),(7,8)],4)
        a.rect(8,7,1,2,5);a.rect(4,6,1,1,4);a.rect(11,10,1,1,3)
        a.rect(2,12,1,1,1);a.rect(13,3,1,1,2);a.shine(17,6);a.shine(24,19)
    if soul=='senbonzakura':
        a.cut([(4,7),(6,6),(7,5),(7,6),(6,7),(5,8),(4,8)])
        a.cut([(10,10),(11,9),(11,10),(10,11),(9,12),(7,12),(8,11)])
    elif soul=='kyouka':
        a.cut([(4,7),(6,5),(8,4),(10,4),(10,5),(8,5),(6,6),(5,8),(4,8)])
        a.cut([(10,10),(11,9),(12,9),(11,10),(10,11),(8,12),(7,12),(8,11)])
    elif soul=='akatsuki':
        a.cut([(4,8),(6,7),(6,8),(5,9)])
    return a

def seal(soul):
    a=Art(soul)
    # An irregular jade/crystal tablet: ivory vein, aged gold clasp, open chipped bottom.
    a.poly([(5,2),(10,1),(12,3),(12,7),(13,10),(11,12),(9,12),(8,15),(5,14),(5,12),(3,11),(3,7),(4,5)],0)
    a.poly([(5,3),(10,2),(11,4),(11,8),(12,10),(10,11),(8,11),(7,14),(6,13),(6,11),(4,10),(4,7),(5,5)],1)
    a.poly([(6,3),(9,3),(10,4),(10,8),(11,10),(8,10),(7,12),(6,10),(5,10),(5,6)],2)
    a.poly([(6,3),(8,3),(7,5),(6,7),(6,10),(5,9),(5,6)],3)
    a.rect(6,2,4,1,'#a98468');a.rect(6,2,2,1,6);a.rect(6,3,1,1,'#edcfa1')
    if soul=='akatsuki':
        a.poly([(9,4),(8,5),(7,7),(7,9),(8,11),(9,10),(8,9),(8,7),(10,5)],4)
        a.rect(8,6,1,2,5);a.rect(8,10,1,1,3);a.rect(11,13,1,1,1)
    elif soul=='senbonzakura':
        a.poly([(8,4),(9,6),(11,6),(9,8),(10,10),(8,9),(6,10),(7,8),(5,7),(7,6)],3)
        a.rect(8,6,1,3,4);a.rect(7,7,3,1,4);a.rect(8,7,1,1,5)
        a.rect(12,12,1,1,2);a.rect(3,3,1,1,2)
    elif soul=='raikiri':
        a.poly([(9,4),(7,7),(9,7),(7,11),(8,8),(6,8),(8,4)],4)
        a.rect(8,5,1,2,5);a.rect(12,5,1,1,2);a.rect(3,13,1,1,1)
    else:
        a.poly([(8,4),(10,6),(10,8),(9,10),(7,10),(6,8),(6,6)],3)
        a.poly([(8,5),(9,6),(9,8),(8,9),(7,8),(7,6)],4)
        a.rect(7,6,1,2,5);a.rect(8,8,1,1,2);a.rect(12,13,1,1,1)
    a.shine(11,7);a.shine(19,21,6);a.shine(13,26,3)
    return a

GROUPS=[('A-crystal','A  晶体残片','贴近血晶与樱魂晶：温润切面、柔和亮边、每刀不同晶形。',crystal),
        ('B-echo','B  刀魂余响','更接近 Lore 的意象：悬浮光核、断续流线、散落的能量碎片。',echo),
        ('C-seal','C  封存魂铭','偏向旧物与传承：不规则晶片、浅金封扣、凝在内部的刀魂印记。',seal)]

def font(size,bold=False):return ImageFont.truetype('C:/Windows/Fonts/msyh'+('bd' if bold else '')+'.ttc',size)
def label(draw,xy,text,size=18,color='#d4c8bd',bold=False):draw.text(xy,text,font=font(size,bold),fill=color)
def paste_center(canvas,im,x,y,size):
    im=im.resize((size,size),Image.Resampling.NEAREST)
    canvas.paste(im,(x-size//2,y),im)

def build_candidates():
    all_images={}
    for group,title,desc,fn in GROUPS:
        all_images[group]=[fn(soul).save(group,soul) for soul in SOULS]

    canvas=Image.new('RGB',(1160,1110),'#201e28');d=ImageDraw.Draw(canvas)
    label(d,(36,24),'魂铭遗物 · 三组画风候选',30,'#f0dfcb',True)
    label(d,(36,70),'每列依次为赤月、千本樱、雷切、镜花水月；图案为原始像素放大，未替换游戏资源。',17)
    label(d,(36,120),'现有物品',18,'#cbb293',True)
    for x,name,title in [(282,'blood_crystal','血晶'),(545,'sakura_soul_crystal','樱魂晶')]:
        im=Image.open(ROOT/'src/main/resources/assets/blade_tetra/textures/item'/f'{name}.png').convert('RGBA')
        paste_center(canvas,im,x,99,96);label(d,(x+58,132),title,18)
    label(d,(810,119),'同一材质语言：',17,'#e1c3ad')
    label(d,(810,148),'暖亮心 · 彩色暗面 · 稀疏碎光',16)
    for row,(group,title,desc,fn) in enumerate(GROUPS):
        top=206+row*296
        d.rounded_rectangle((26,top,1134,top+280),radius=10,fill='#292631',outline='#49404a')
        label(d,(46,top+12),title,23,'#f0dfcb',True)
        label(d,(258,top+18),desc,16)
        for col,(name,im) in enumerate(zip(NAMES,all_images[group])):
            x=178+col*268
            label(d,(x-34,top+54),name,18,'#d9c9c1',True)
            paste_center(canvas,im,x,top+78,128)
            # Actual-sized item on grey inventory and dark tooltip surfaces.
            slotx=x-49;sloty=top+211
            d.rectangle((slotx-3,sloty-3,slotx+35,sloty+35),fill='#37323d')
            d.rectangle((slotx-1,sloty-1,slotx+33,sloty+33),fill='#8b8b8b',outline='#eeeeee')
            paste_center(canvas,im,slotx+16,sloty,32)
            d.rectangle((x+9,sloty-1,x+43,sloty+33),fill='#121019',outline='#65505b')
            paste_center(canvas,im,x+26,sloty+8,16)
            label(d,(x-52,top+250),'背包 32px  /  16px',13,'#a89eaa')
    canvas.save(OUT/'comparison.png')
    for group,title,desc,fn in GROUPS:
        row=GROUPS.index((group,title,desc,fn));top=206+row*296
        canvas.crop((26,top,1135,top+281)).save(OUT/(group+'.png'))
    (OUT/'README.md').write_text('''# 魂铭遗物候选（未接入游戏）

    A：晶体残片；B：刀魂余响；C：封存魂铭。每组各四件，SVG 与透明 PNG 同源导出。
    使用 `python draw_candidates.py` 重新生成。仅写此目录，不改运行资源。
    上方参照为当前仓库真实血晶与樱魂晶；候选主体以 2px 色块作画，少量 1px 高光。
    色盘沿用本模组晶体，参考灾厄 Lore 物品的光核、断续悬浮形体，不复制其贴图。
    参考：https://github.com/CalamityTeam/CalamityModPublic/tree/1.4.4/Items/LoreItems
    原参考图仅保存在 build/soul-fragment-style-reference，不属于候选资源。
    ''',encoding='utf-8')
    print('Wrote 12 original candidate SVG/PNG pairs, overview and three row previews. Runtime assets unchanged.')


if __name__ == "__main__":
    build_candidates()
