const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const out = path.join(__dirname, 'blade-backplate');
fs.mkdirSync(out, { recursive: true });
const rect = (x,y,w,h,c) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${c}"/>`;
const text = (x,y,s,size=18,c='#d8d1c1') => `<text x="${x}" y="${y}" font-family="Microsoft YaHei, sans-serif" font-size="${size}" fill="${c}">${s}</text>`;
const palettes = [
  ['iron','铁灰', ['#242a32','#39454e','#4b5d64','#596b70','#789397'], '#91cee1'],
  ['diamond','钻石青', ['#173034','#25454a','#355c5e','#42696a','#658b88'], '#9ae2e6'],
  ['violet','灰紫', ['#2e2638','#43364f','#594761','#66556c','#8b758f'], '#cab4f2']
];
function plate(colors, durability=1) {
  let s='';
  for(let y=2;y<30;y++) for(let x=2;x<30;x++) {
    const d=Math.abs(x-15.5)+Math.abs(y-15.5);
    if(d>14)continue;
    let c=d>12.5?'#181e27':d>11?colors[4]:d>9.5?colors[0]:x+y<30?colors[2]:colors[1];
    if(d<9.5 && x+y<23)c=colors[3];
    // Sparse, deliberate forged planes rather than random noise.
    if(d<8.5 && ((y===11&&x>=11&&x<=16)||(y===19&&x>=13&&x<=20)))c=colors[3];
    if(d<8.5 && y===20&&x>=15&&x<=20)c=colors[0];
    s+=rect(x,y,1,1,c);
  }
  s+=rect(14,5,3,1,'#b0b4ae')+rect(8,12,1,3,'#8f9c9b');
  s+=rect(14,25,3,1,'#262b34')+rect(24,15,1,2,'#242b32');
  // Separate durability rim: charcoal recess remains visible as it empties.
  const edge=[];
  for(let i=0;i<14;i++)edge.push([16+i,1+i]);
  for(let i=0;i<14;i++)edge.push([30-i,15+i]);
  for(let i=0;i<14;i++)edge.push([16-i,29-i]);
  for(let i=0;i<14;i++)edge.push([2+i,15-i]);
  const fill=durability>.5?'#76d9e6':durability>.25?'#e8c476':'#e18368';
  edge.forEach(([x,y],i)=>{
    s+=rect(x,y,1,1,i<Math.round(edge.length*durability)?fill:'#32333b');
  });
  if(durability>.5)s+=rect(16,1,1,1,'#d8fbef');
  return s;
}
function blade(tint) {
  return `<g transform="rotate(42 16 16)">
    <path d="M14 2h2v20h-2zM16 3h1v17h-1z" fill="#262333"/>
    <path d="M14 3h1v18h-1z" fill="#eceded"/><path d="M15 3h1v17h-1z" fill="${tint}"/>
    <path d="M12 21h6v2h-6z" fill="#b8aaa0"/><path d="M14 23h3v8h-3z" fill="#4e3940"/>
    <path d="M14 24h2v1h-2zM14 27h2v1h-2zM14 30h2v1h-2z" fill="#dfceb2"/>
  </g><g transform="rotate(-43 16 16)"><path d="M17 5h3v23h-3z" fill="#25232c"/>
  <path d="M17 6h2v19h-2z" fill="#655045"/><path d="M17 6h2v2h-2zM17 24h3v2h-3z" fill="#a2aaa7"/></g>`;
}
const wrap=b=>`<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">${b}</svg>`;
let sheet=rect(0,0,1000,640,'#201f24')+text(35,42,'模块化拔刀剑 · 动态铭板预览',26)+text(35,72,'同一张灰度底板随材料染色；耐久框独立显示，不参与背板染色。',16,'#aba598');
for(let i=0;i<3;i++){
  const [id,label,colors,tint]=palettes[i], x=35+i*320;
  sheet+=rect(x,100,290,480,'#2a292d')+text(x+20,132,label,20);
  sheet+=`<g transform="translate(${x+75} 154) scale(4.5)" shape-rendering="crispEdges">${plate(colors)}</g>`;
  sheet+=text(x+20,324,'背板放大 · 稀疏锻痕与金属内沿',15,'#b9b2a5');
  for(let j=0;j<2;j++){
    let bx=x+35+j*132;
    sheet+=rect(bx-4,351,104,104,'#777875')+`<g transform="translate(${bx} 355) scale(3)" shape-rendering="crispEdges">${plate(colors,j?0.2:1)+blade(tint)}</g>`;
    sheet+=text(bx,481,j?'20% 耐久':'满耐久',16);
  }
  sheet+=text(x+20,520,'32px 检查',14,'#b9b2a5');
  sheet+=rect(x+122,498,40,40,'#777875')+`<g transform="translate(${x+126} 502)" shape-rendering="crispEdges">${plate(colors)+blade(tint)}</g>`;
  fs.writeFileSync(path.join(out,id+'.svg'),wrap(plate(colors)));
}
sheet+=text(35,616,'刀与刀鞘为布局示意；本预览没有改动游戏资源。耐久框颜色仅用于展示层级。',16,'#a9a293');
const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="640" viewBox="0 0 1000 640">${sheet}</svg>`;
fs.writeFileSync(path.join(out,'preview.svg'),svg);
sharp(Buffer.from(svg)).png().toFile(path.join(out,'preview.png'));
