const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const out = __dirname;
const source = path.resolve(out, '../../src/main/resources/assets/blade_tetra/textures/item');
const C = { ink:'#292331', dark:'#4b3440', gold:'#b98047', light:'#f0ca82', paper:'#ebd4a2', ivory:'#fff0cb', shade:'#b99a6b', red:'#b52e46', pink:'#e977a4', cyan:'#77c7c2', navy:'#343a66', violet:'#8964aa' };
const r=(x,y,w,h,c)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${c}"/>`;
const p=(d,c)=>`<path d="${d}" fill="${c}"/>`;
const svg=body=>`<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" shape-rendering="crispEdges">${body}</svg>`;
const icons=[];
function add(id,name,body){icons.push({id,name,body});}

function crystal(pink){
  let s=p('M7 1h2v2h2v2h2v6h-2v2H9v2H7v-2H5v-2H3V5h2V3h2z',C.ink);
  s+=p('M7 2h2v2h2v2h1v4h-2v2H9v2H7v-2H5v-2H4V6h2V4h1z',pink?'#8d4168':'#79243c');
  s+=p('M7 3h2v2h2v4H9v3H7v-2H5V6h2z',pink?C.pink:C.red);
  s+=p('M7 4h2v2H7zM5 6h2v3H5zM7 6h3v2H7z',pink?'#ffd2da':'#ed7e80');
  s+=r(7,4,1,2,C.ivory)+r(6,6,1,2,C.ivory)+r(9,10,1,2,pink?'#bd597f':'#9b2440');
  s+=r(8,8,2,1,pink?'#f9b9ca':'#e35b60')+r(10,6,1,3,pink?'#b65886':'#9b2440')+r(7,12,1,1,pink?'#e977a4':'#c8464e');
  return s;
}
add('blood_crystal','血晶',crystal(false));
add('sakura_soul_crystal','樱魂晶',
 p('M7 1h2v3h3v2h2v3h-3v3H9v3H6v-3H4V9H2V6h3V4h2z',C.ink)+
 p('M7 2h1v3h3v2h2v1h-3v3H8v3H7v-3H5V8H3V7h3V5h1z','#b25481')+
 p('M7 3h1v3h3v2H9v3H7V9H5V7h2z',C.pink)+r(7,5,1,2,C.ivory)+r(6,7,3,1,'#ffd2da')+r(7,8,1,2,'#ffd2da')+r(11,2,1,1,C.pink)+r(3,12,1,1,C.pink));
add('boundary_gate_charm','界门符',
  p('M2 1h12v3h-1v9h1v2H2v-2h1V4H2z',C.ink)+r(3,2,10,1,C.light)+r(4,4,8,8,C.gold)+r(5,4,6,7,C.paper)+r(5,4,6,1,C.ivory)+r(3,13,10,1,C.gold)+r(3,13,4,1,C.light)+r(6,6,4,1,C.red)+r(7,7,2,3,C.red)+r(6,10,4,1,C.red)+r(7,8,2,1,'#e57462')+r(3,4,1,8,C.gold)+r(12,4,1,8,C.gold)+r(4,4,1,8,C.light)+r(11,4,1,8,'#80543e')+r(5,12,6,1,C.light)+r(6,14,4,1,'#712b43'));
add('sword_ghost_remnant','剑鬼残响',
  p('M8 1h2v4h2v2h2v4h-2v2H9v2H5v-2H3V9h2V7h2V4h1z',C.ink)+
  p('M8 4h1v3h2v1h2v3h-2v1H8v2H6v-2H4v-2h2V8h2z','#712b43')+
  p('M8 7h2v2h2v2H9v2H7v-2H5v-1h2V9h1z',C.red)+r(8,8,1,3,'#e7777d')+r(6,11,2,1,'#e7777d')+r(11,4,1,1,C.pink));
add('broken_oni_mask','破损鬼面',
  p('M3 2h2v2h6V2h2v3h1v5h-2v2h-1v2H6v-1H4v-2H2V6h1z',C.ink)+
  p('M4 4h1v1h6V4h1v2h1v3h-2v3H7v1H6v-2H4V9H3V6h1z',C.shade)+
  p('M4 5h7v1h1v3h-2v3H7v-2H4V8H3V6h1z',C.paper)+r(4,6,3,1,C.ink)+r(9,6,3,1,C.ink)+r(5,7,2,1,C.red)+r(9,7,2,1,C.red)+r(7,8,2,2,C.gold)+r(6,11,4,1,C.ink)+r(7,11,1,1,C.ivory)+p('M10 9h1v1h1v2h-2z',C.ink));

function book(red){
 let s=p('M3 1h10v2h1v11h-1v1H3v-1H2V2h1z',C.ink)+r(3,2,10,11,C.gold)+r(4,3,8,9,red?'#553650':C.navy)+r(3,2,1,11,'#825641')+r(4,3,8,1,red?'#805069':'#555787')+r(4,4,1,7,red?'#6f435d':'#424875')+r(4,13,9,1,C.paper)+r(5,13,7,1,C.ivory)+r(11,3,1,1,C.light)+r(4,11,1,1,C.light)+r(3,4,1,1,C.light)+r(3,9,1,1,C.light)+r(12,5,1,7,'#775743');
 if(red) s+=p('M9 5h2v1h-1v1H9v1H8v1H7V8h1V7h1z',C.ivory)+r(6,8,2,1,C.gold)+r(6,9,1,2,C.gold)+r(9,10,2,2,C.pink)+r(10,11,1,3,C.red)+r(5,5,1,1,C.gold);
 else s+=r(6,5,5,1,C.light)+r(7,7,3,1,C.gold)+p('M7 8h3v1H9v1H8V9H7z',C.light)+r(6,11,5,1,C.gold)+r(6,6,1,3,'#6c6596')+r(10,7,1,2,'#6c6596');
 return s;
}
add('smithing_journal','锻刀手记',book(false));
add('named_blade_record','名刀映录',book(true));

const clueDefs=[
 ['shoshin','初心',C.gold,r(7,4,2,7,C.dark)+r(6,10,4,1,C.dark)+r(7,11,1,2,C.dark)],
 ['bairen','百炼',C.gold,r(4,8,8,2,C.dark)+r(6,10,4,1,C.dark)+r(7,11,2,2,C.dark)+r(9,4,2,3,C.red)+r(8,5,1,2,C.red)],
 ['bansho','万象',C.gold,r(7,4,2,2,C.dark)+r(4,7,2,2,C.dark)+r(10,7,2,2,C.dark)+r(7,10,2,2,C.dark)+r(6,6,4,1,C.gold)+r(6,9,4,1,C.gold)],
 ['raikiri','雷切',C.gold,p('M8 3h3v2H9v2h2v2H8v3H6V9H5V7h2V5h1z',C.dark)+r(8,5,1,2,C.gold)],
 ['senbonzakura','千本樱',C.pink,r(7,4,2,2,C.red)+r(4,7,2,2,C.red)+r(10,7,2,2,C.red)+r(7,10,2,2,C.red)+r(7,7,2,2,C.gold)],
 ['akatsuki','赤月',C.red,p('M7 4h4v1H8v2H7v3h1v1h3v1H6v-1H5V5h2z',C.red)],
 ['kyouka','镜花',C.cyan,r(5,4,6,1,C.dark)+r(4,5,1,6,C.dark)+r(11,5,1,6,C.dark)+r(5,11,6,1,C.dark)+r(5,5,6,6,'#87b8b7')+r(6,6,2,1,C.ivory)+r(6,7,1,2,C.ivory)+r(9,9,1,1,'#527f8c')]
];
function paper(symbol,accent){
 return p('M3 1h8v1h2v11h-1v2H3v-1H2V3h1z',C.ink)+r(3,2,9,11,C.shade)+r(3,2,8,10,C.paper)+r(3,2,7,1,C.ivory)+r(3,3,1,8,C.ivory)+r(10,2,1,2,C.shade)+r(11,3,1,1,C.gold)+r(4,13,7,1,C.shade)+r(3,12,2,1,C.gold)+symbol+r(10,12,2,1,accent);
}
for(const [id,name,accent,symbol] of clueDefs) add('smithing_clue_'+id,name+'线索',paper(symbol,accent));

const patterns=[
 ['black_gold','黑金', '#373039',r(5,5,1,1,C.light)+r(8,6,1,1,C.gold)+r(6,8,2,1,C.light)+r(9,9,1,2,C.gold)+r(5,11,2,1,C.gold)],
 ['vermilion_cloud','朱云','#a24443',p('M4 7h1V6h2v1h2v1H4zM8 10h1V9h2v1h1v1H8z',C.light)],
 ['seigaiha','青海波','#384b69',p('M4 6h1V5h2v1h1v1H7V6H5v1H4zM8 6h1V5h2v1h1v1h-1V6H9v1H8zM6 10h1V9h2v1h1v1H9v-1H7v1H6z','#8fa9c6')],
 ['sakura','樱纹','#84475b',r(6,5,1,2,'#f3b4ba')+r(5,6,3,1,'#f3b4ba')+r(9,9,1,2,'#f3b4ba')+r(8,10,3,1,'#f3b4ba')+r(6,6,1,1,C.light)+r(9,10,1,1,C.light)],
 ['purple_lightning','紫电','#443451',p('M8 4h2v2H8v2h2v1H8v3H6V9H5V8h2V6h1z','#b69bc9')],
 ['akatsuki','赤月','#643442',p('M8 5h2v1H8v2H7v2h1v1h2v1H7v-1H6V6h2z','#e48b76')],
 ['kyouka','镜花','#304858',r(6,5,4,1,'#9dbac4')+r(5,6,1,4,'#9dbac4')+r(10,6,1,4,'#9dbac4')+r(6,10,4,1,'#9dbac4')+r(7,7,2,1,'#c6dedb')+r(8,8,1,1,'#7facbd')]
];
for(const [id,name,bg,motif] of patterns){
 add('saya_pattern_'+id,name+'图样',p('M2 1h9v1h2v2h1v10H2z',C.ink)+r(3,2,8,11,C.paper)+r(11,4,2,9,C.shade)+r(3,2,7,1,C.ivory)+r(3,3,1,9,C.ivory)+r(4,4,8,8,bg)+r(4,4,8,1,'#ffffff18')+motif+r(11,2,1,1,C.ivory)+r(11,3,2,1,C.gold));
}

async function main(){
 for(const icon of icons){fs.writeFileSync(path.join(out,icon.id+'.svg'),svg(icon.body));}
 const width=1260,height=1040;
 let body=r(0,0,width,height,'#191b25');
 body+=`<text x="40" y="52" fill="#f5dfb7" font-family="Microsoft YaHei" font-size="28" font-weight="bold">Blade Tetra · 物品美术统一提案</text>`;
 body+=`<text x="40" y="82" fill="#a9acbb" font-family="Microsoft YaHei" font-size="15">16 × 16 像素 SVG · 暖纸 / 旧金 / 墨色轮廓 · 仅预览，尚未接入游戏</text>`;
 const sections=['仪式材料与书册','锻造线索 · 用符号区分，减少纸面噪点','鞘绘图样 · 折角图样纸与对应纹样'];
 for(let row=0;row<3;row++){
  let y=124+row*282;
  body+=`<text x="40" y="${y}" fill="#e4c68e" font-family="Microsoft YaHei" font-size="17">${sections[row]}</text>`;
  for(let col=0;col<7;col++){
   const icon=icons[row*7+col],x=40+col*172,cy=y+18;
   body+=r(x,cy,160,226,'#232632')+r(x,cy,160,2,'#46404a');
   body+=`<svg x="${x+32}" y="${cy+14}" width="96" height="96" viewBox="0 0 16 16" shape-rendering="crispEdges">${icon.body}</svg>`;
   body+=`<text x="${x+80}" y="${cy+133}" text-anchor="middle" fill="#f0e3ca" font-family="Microsoft YaHei" font-size="15">${icon.name}</text>`;
   const oldPath=path.join(source,icon.id+'.png');
   if(fs.existsSync(oldPath))body+=`<image x="${x+30}" y="${cy+155}" width="32" height="32" image-rendering="pixelated" href="data:image/png;base64,${fs.readFileSync(oldPath).toString('base64')}"/>`;
   body+=`<svg x="${x+98}" y="${cy+155}" width="32" height="32" viewBox="0 0 16 16" shape-rendering="crispEdges">${icon.body}</svg>`;
   body+=`<text x="${x+46}" y="${cy+211}" text-anchor="middle" fill="#979aaa" font-family="Microsoft YaHei" font-size="12">现版</text><text x="${x+114}" y="${cy+211}" text-anchor="middle" fill="#c9b78d" font-family="Microsoft YaHei" font-size="12">提案</text>`;
  }
 }
 body+=`<text x="40" y="995" fill="#a9acbb" font-family="Microsoft YaHei" font-size="14">主图 6 倍放大；下方对照为 2 倍物品栏大小。刀、原生耀魂宝珠及 Tetra 卷轴沿用现有资源。</text>`;
 const sheet=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">${body}</svg>`;
 fs.writeFileSync(path.join(out,'item-style-preview.svg'),sheet);
 await sharp(Buffer.from(sheet)).png().toFile(path.join(out,'item-style-preview.png'));
 let qa=r(0,0,336,48,'#8b8b8b');
 icons.forEach((icon,i)=>{qa+=`<svg x="${i*16}" y="16" width="16" height="16" viewBox="0 0 16 16" shape-rendering="crispEdges">${icon.body}</svg>`;});
 await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="336" height="48">${qa}</svg>`)).png().toFile(path.join(out,'inventory-size-check.png'));
 console.log(`Rendered ${icons.length} SVG icons and the comparison sheet.`);
}
main().catch(e=>{console.error(e);process.exit(1);});
