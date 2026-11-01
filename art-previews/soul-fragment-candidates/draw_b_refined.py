"""B refinement: retain approved blood/thunder sprites; redraw Sakura and Kyouka as open wisps."""
from PIL import Image, ImageDraw
from draw_candidates import Art, OUT, SOULS, NAMES, echo, label, paste_center


def sakura():
    a=Art('senbonzakura')
    # Separate petal/edge fragments converge onto one narrow vertical seam.
    # No filled flower or enclosing gem: the air gaps are part of the silhouette.
    a.poly([(7,1),(9,2),(10,4),(9,6),(10,9),(9,12),(7,15),(6,13),(7,10),(6,7),(7,4)],0)
    a.poly([(8,2),(9,4),(8,7),(9,9),(8,12),(7,14),(7,11),(7,8),(7,6)],2)
    a.poly([(8,4),(9,6),(8,8),(9,10),(8,12),(7,11),(7,8)],3)
    a.poly([(8,5),(8,8),(9,9),(8,11),(7,10),(7,8)],4)
    a.rect(8,6,1,4,5)
    # Left wake and two shard tips; the upper petal remains detached.
    a.poly([(1,8),(3,6),(5,7),(6,6),(7,8),(5,10),(3,9)],0)
    a.poly([(2,8),(3,7),(5,8),(6,7),(6,8),(5,9),(4,8)],2)
    a.rect(3,8,2,1,3)
    a.poly([(4,3),(6,3),(6,5),(5,6),(4,5)],1)
    a.poly([(4,3),(5,4),(5,5),(4,4)],3)
    # Two petals on the right echo the horizontal discharge of the retained pair.
    a.poly([(10,7),(12,5),(14,6),(15,7),(13,8),(12,9),(10,9)],0)
    a.poly([(10,8),(12,6),(14,7),(12,8),(11,9)],2)
    a.rect(11,7,2,1,3)
    a.poly([(11,11),(13,10),(13,12),(12,13),(11,12)],1)
    a.rect(12,11,1,1,3)
    a.rect(3,11,1,1,1);a.rect(11,2,1,1,2)
    a.shine(16,10);a.shine(24,14);a.shine(15,25,4)
    return a


def kyouka():
    a=Art('kyouka')
    # A split upright reflection, flanked by short, broken water echoes.
    # The secondary pale sliver suggests an afterimage without making a circular frame.
    a.poly([(8,1),(10,3),(9,5),(10,7),(9,10),(8,12),(7,15),(6,12),(7,9),(6,6),(7,3)],0)
    a.poly([(8,2),(9,3),(8,6),(9,8),(8,10),(7,13),(7,10),(7,8),(7,5)],2)
    a.poly([(8,4),(8,7),(9,8),(8,10),(7,11),(7,8),(7,6)],3)
    a.poly([(8,5),(8,7),(8.5,8),(8,10),(7,9),(7,7)],4)
    a.rect(7,6,1,4,5)
    a.poly([(10,2),(11,3),(10,5),(11,7),(10,9),(10,7),(9,5)],1)
    a.rect(10,4,1,1,3)
    # Loose water shards move horizontally across the bright slit, with gaps between them.
    a.poly([(1,8),(3,6),(5,6),(6,8),(4,10),(2,9)],0)
    a.poly([(2,8),(3,7),(5,7),(4,8),(3,9)],2)
    a.rect(3,7,1,1,3)
    a.poly([(10,8),(12,7),(14,6),(15,8),(13,9),(11,10)],0)
    a.poly([(10,8),(12,8),(14,7),(14,8),(12,9),(11,9)],2)
    a.rect(12,8,1,1,3)
    a.poly([(4,11),(6,10),(6,12),(5,13),(4,12)],1)
    a.rect(5,11,1,1,3)
    a.rect(12,4,1,1,1);a.rect(11,12,1,1,2)
    a.shine(15,7,4);a.shine(15,24,4);a.shine(26,16,4)
    return a


def preview():
    group='B2-echo'
    images=[]
    for soul in SOULS:
        art=sakura() if soul=='senbonzakura' else kyouka() if soul=='kyouka' else echo(soul)
        im=art.save(group,soul);images.append(im)
        if soul in ['akatsuki','raikiri']:
            assert im.tobytes()==Image.open(OUT/'B-echo'/f'{soul}.png').convert('RGBA').tobytes()
    canvas=Image.new('RGB',(1040,414),'#201e28');d=ImageDraw.Draw(canvas)
    label(d,(30,20),'B · 刀魂余响 / 统一画风修订',27,'#f0dfcb',True)
    label(d,(30,65),'沿用赤月与雷切的细长光核、横向碎片和留白，重画千本樱与镜花水月。',17)
    notes=['原 B 版 · 保留','新绘 · 樱瓣碎刃','原 B 版 · 保留','新绘 · 破镜水影']
    for i,(name,im,note) in enumerate(zip(NAMES,images,notes)):
        x=130+i*260
        d.rounded_rectangle((x-116,107,x+116,396),radius=8,fill='#292631',outline='#49404a')
        label(d,(x-43,119),name,21,'#e9d6c6',True)
        paste_center(canvas,im,x,153,128)
        label(d,(x-71,285),note,15,'#c7b3b9')
        for dx,size,bg in [(-41,32,'#8b8b8b'),(29,16,'#121019')]:
            d.rectangle((x+dx-19,320,x+dx+19,358),fill=bg,outline='#8c7f86')
            paste_center(canvas,im,x+dx,323+(32-size)//2,size)
        label(d,(x-66,368),'背包 32px / 16px',14,'#a89eaa')
    canvas.save(OUT/'B2-refined.png')
    # A second sheet makes the changed silhouettes easy to compare with the first B proposal.
    compare=Image.new('RGB',(700,530),'#201e28');c=ImageDraw.Draw(compare)
    label(c,(28,18),'千本樱 / 镜花水月 · 前后对照',23,'#f0dfcb',True)
    for j,(soul,name) in enumerate([('senbonzakura','千本樱'),('kyouka','镜花水月')]):
        x=250+j*280
        label(c,(x-42,66),name,21,'#e9d6c6',True)
        for row,folder in enumerate(['B-echo','B2-echo']):
            im=Image.open(OUT/folder/f'{soul}.png').convert('RGBA')
            paste_center(compare,im,x,115+row*198,128)
    label(c,(28,160),'原 B 版',18)
    label(c,(28,358),'新绘',18,'#f0dfcb',True)
    label(c,(28,488),'收窄主体，拆开环状外壳，让碎片与光核更接近赤月、雷切。',17)
    compare.save(OUT/'B2-before-after.png')
    (OUT/group/'README.md').write_text('''# B 版统一修订

赤月与雷切逐像素保留原 B-echo 版本；千本樱与镜花水月改为细长明亮光核、横向开放碎片。
使用 `python draw_b_refined.py` 重绘，仅更新候选目录；未替换游戏贴图。
千本樱以不对称樱瓣碎刃表现聚散；镜花以竖向重影和水平方向水镜碎片表现虚实。
四件均提供 32px RGBA PNG 与可编辑 SVG 母版。
''',encoding='utf-8')
    print('B2: two retained sprites verified pixel-for-pixel; Sakura and Kyouka redrawn; runtime assets untouched.')


if __name__=='__main__':
    preview()
