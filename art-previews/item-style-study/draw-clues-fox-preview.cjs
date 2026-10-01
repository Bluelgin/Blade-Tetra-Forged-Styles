const fs=require('fs'),path=require('path'),sharp=require('sharp');
const out=path.join(__dirname,'clues-fox'),src=path.resolve(__dirname,'../../src/main/resources/assets/blade_tetra/textures/item');
const R=(x,y,w,h,c)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${c}"/>`;
const P=(d,c)=>`<path d="${d}" fill="${c}"/>`;
const ink='#80643c',red='#a65e56',green='#8c9a78',blue='#668695';
function paper(){
 const rows=[[4,11],[3,12],[2,12],[2,13],[2,13],[2,12],[1,13],[2,13],[2,13],[2,12],[2,13],[3,12],[3,12],[4,11]];
 let s='';
 for(let y=1;y<=14;y++){
  const [l,r]=rows[y-1];
  for(let x=l;x<=r;x++){
   const border=x===l||x===r||y===1||y===14;
   let c=border?(x+y<15?'#b8995d':'#947547'):(x<6?'#ead09a':x>10?'#cfb27a':'#dfc48b');
   if(!border&&y>11)c='#ceb17a';
   if(!border&&((x===4&&y===5)||(x===11&&y===9)||(x===6&&y===12)))c='#c9ab71';
   if(!border&&((x===5&&y===3)||(x===10&&y===4)))c='#f0dcac';
   s+=R(x,y,1,1,c);
  }
 }
 return s+R(4,2,5,1,'#efdaa5')+R(3,4,1,3,'#efdaa5')+R(10,12,2,1,'#b4935f');
}
const defs=[
 ['shoshin','初心线索',R(7,4,1,7,ink)+R(8,5,1,4,'#b28f54')+R(6,10,4,1,ink)+R(7,12,1,1,ink)],
 ['bairen','百炼线索',R(4,8,7,1,ink)+R(6,9,4,1,ink)+R(7,10,2,2,ink)+R(5,12,6,1,ink)+P('M9 4h1v1h1v2H9z',red)+R(8,6,1,1,'#c69463')],
 ['bansho','万象线索',P('M7 4h2v2H7zM4 7h2v2H4zM10 7h2v2h-2zM7 10h2v2H7z',green)+R(6,6,4,1,'#a29363')+R(6,9,4,1,'#a29363')+R(7,7,2,2,'#c2be89')],
 ['raikiri','雷切线索',P('M8 4h3v1H9v2h2v1H8v2H7v2H6V9H5V8h2V6h1z',ink)+R(8,5,1,1,'#c0a26a')],
 ['akatsuki','赤月线索',P('M7 4h4v1H8v1H7v4h1v1h3v1H7v-1H6v-1H5V6h1V5h1z',red)+R(6,6,1,3,'#c08361')+R(8,11,2,1,'#c08361')],
 ['kyouka','镜花线索',P('M6 4h4v1h1v1h1v4h-1v1h-1v1H6v-1H5v-1H4V6h1V5h1z',blue)+P('M6 5h4v1h1v4h-1v1H6v-1H5V6h1z','#b7c4b2')+P('M7 6h3v1H8v1H7v1H6V7h1z','#e4e2bc')+R(8,9,2,1,'#839e99')],
 ['senbonzakura','千本樱线索',P('M7 4h2v2h2v1h1v2h-2v2H9v1H7v-2H5V9H4V7h2V6h1z','#b77779')+R(7,7,2,2,'#ead4ae')+R(5,7,1,1,'#d49b8f')+R(8,5,1,1,'#d49b8f')+R(10,8,1,1,'#d49b8f')+R(7,10,1,1,'#d49b8f')]
];
function fox(){
 let s=P('M5 2h2v2h2v2h2v3h3V8h4v1h3V6h2V4h2V2h2v13h-1v4h-2v3h-2v3h-3v3h-2v2h-2v-2h-3v-3H9v-3H7v-3H5v-4H4V7h1z','#53404a');
 s+=P('M6 4h1v2h2v2h2v3h4v-1h2v1h5V8h2V6h2v8h-1v4h-2v3h-2v3h-3v3h-3v-2h-3v-3H9v-3H7v-4H6z','#c4ad8e');
 s+=P('M6 4h1v3h2v2h2v3h4v-1h1v8h-2v3h-2v-1H9v-3H7v-4H6z','#ecddbe');
 s+=P('M24 7h1v7h-2v-3h1zM7 7h1v4h2v3H7z','#ad6864');
 s+=R(13,12,6,1,'#f1e5cb')+R(15,13,2,7,'#e8d3af');
 s+=P('M8 14h3v1h3v2h-3v-1H8zM18 15h3v-1h3v2h-3v1h-3z','#a35758');
 s+=R(10,17,3,1,'#5c4046')+R(19,17,3,1,'#5c4046');
 s+=R(8,19,2,1,'#b76d61')+R(9,20,2,1,'#b76d61')+R(22,19,2,1,'#9d5958');
 s+=P('M14 21h4v1h-1v1h-2v-1h-1z','#62484b')+R(15,23,2,2,'#b79b80')+R(15,25,2,1,'#e4caaa');
 // A visible chipped right cheek, with a crack running toward it.
 // Erase the chip with the mask below so the missing corner is transparent.
 s+=R(22,18,1,2,'#745756')+R(21,20,1,2,'#745756')+R(20,22,1,2,'#745756');
 return `<defs><mask id="chip"><rect width="32" height="32" fill="white"/><path d="M25 18h7v14H21v-7h2v-2h2z" fill="black"/></mask></defs><g mask="url(#chip)">${s}</g>`;
}
async function main(){
 fs.mkdirSync(out,{recursive:true});
 const icons=defs.map(([id,name,motif])=>({id:'smithing_clue_'+id,name,body:`<g transform="scale(2)">${paper()+motif}</g>`}));
 icons.push({id:'broken_oni_mask',name:'破损鬼面 · 狐面提案',body:fox()});
 let sheet=R(0,0,1180,790,'#27231f')+`<text x="35" y="45" font-family="Microsoft YaHei" font-size="25" fill="#ead2a1">线索统一 · 狐面重绘</text><text x="35" y="76" font-family="Microsoft YaHei" font-size="15" fill="#bbaa8e">七件线索采用相同纸张尺寸、旧纸材质与像素密度；以中央图纹区分。仅预览，尚未接入。</text>`;
 for(let i=0;i<icons.length;i++){
  const a=icons[i],x=35+(i%4)*285,y=100+Math.floor(i/4)*320;
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" shape-rendering="crispEdges">${a.body}</svg>`;
  fs.writeFileSync(path.join(out,a.id+'.svg'),svg);await sharp(Buffer.from(svg)).png().toFile(path.join(out,a.id+'.png'));
  sheet+=R(x,y,255,300,'#332e27');
  // Unique IDs avoid collisions between the fox standalone and contact sheet.
  sheet+=`<svg x="${x+63}" y="${y+13}" width="128" height="128" viewBox="0 0 32 32" shape-rendering="crispEdges">${a.body}</svg><text x="${x+127}" y="${y+166}" text-anchor="middle" font-family="Microsoft YaHei" font-size="16" fill="#e8d4b0">${a.name}</text>`;
  const old=fs.readFileSync(path.join(src,a.id+'.png')).toString('base64');
  sheet+=`<image x="${x+39}" y="${y+185}" width="48" height="48" image-rendering="pixelated" href="data:image/png;base64,${old}"/><svg x="${x+103}" y="${y+185}" width="48" height="48" viewBox="0 0 32 32" shape-rendering="crispEdges">${a.body.replaceAll('chip','chipSmall')}</svg>`;
  sheet+=R(x+184,y+193,32,32,'#88867f')+`<svg x="${x+184}" y="${y+193}" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">${a.body.replaceAll('chip','chipTiny')}</svg>`;
  sheet+=`<text x="${x+63}" y="${y+261}" text-anchor="middle" font-family="Microsoft YaHei" font-size="12" fill="#bbaa8e">现版</text><text x="${x+127}" y="${y+261}" text-anchor="middle" font-family="Microsoft YaHei" font-size="12" fill="#d8bd85">新提案</text><text x="${x+200}" y="${y+261}" text-anchor="middle" font-family="Microsoft YaHei" font-size="12" fill="#bbaa8e">小尺寸</text>`;
 }
 sheet+=`<text x="35" y="759" font-family="Microsoft YaHei" font-size="14" fill="#bbaa8e">狐面采用骨白、褪色朱红与旧墨描边；保留“破损”特征，物品名称与用途暂不变。</text>`;
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1180" height="790">${sheet}</svg>`;
 fs.writeFileSync(path.join(out,'clues-fox-preview.svg'),svg);await sharp(Buffer.from(svg)).png().toFile(path.join(out,'clues-fox-preview.png'));
 console.log('Rendered eight preview assets. No game resources changed.');
}
main().catch(e=>{console.error(e);process.exit(1)});
