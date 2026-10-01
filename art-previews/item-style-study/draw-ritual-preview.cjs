const fs=require('fs'),path=require('path'),sharp=require('sharp');
const out=path.join(__dirname,'ritual-items-v2');
const src=path.resolve(__dirname,'../../src/main/resources/assets/blade_tetra/textures/item');
fs.mkdirSync(out,{recursive:true});
const rect=(x,y,w,h,c)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${c}"/>`;
const names=[['blood_crystal','血晶'],['sakura_soul_crystal','樱魂晶'],['sword_ghost_remnant','剑鬼残响'],['boundary_gate_charm','界门符'],['broken_oni_mask','破损鬼面']];
const oldPal={
 blood_crystal:{'59,7,20':'#3c2029','255,213,209':'#f5d0b4','255,107,120':'#da7a78','212,42,66':'#b43d4b','108,10,34':'#67273b','255,183,181':'#e9ad98','143,14,41':'#813045','247,192,122':'#d1a05e','156,16,40':'#993447'},
 sword_ghost_remnant:{'36,13,26':'#34222c','140,23,48':'#843346','240,106,121':'#c46970','255,225,215':'#ecd5b3'},
 boundary_gate_charm:{'33,16,14':'#33251d','75,33,23':'#60422c','217,152,67':'#b98c49','240,189,100':'#dec08a','182,108,46':'#a8793d','122,59,29':'#765432','199,151,85':'#b79a61','229,193,124':'#dcc38a','183,125,67':'#a7834e','242,217,150':'#e4ce9d','255,233,174':'#f0ddaf','255,241,173':'#f6e7c0','195,159,105':'#bca474','213,67,47':'#a84a3d','240,106,61':'#cf8060','74,20,21':'#53312a','142,29,28':'#83382f','201,166,116':'#c6ad7e','100,20,22':'#65342d','143,90,50':'#866c43','64,21,26':'#493039','157,38,48':'#8f454a','211,75,69':'#bd6d64'},
 broken_oni_mask:{'48,26,36':'#372b2d','215,201,184':'#dbc6a5','110,16,39':'#763741','59,38,48':'#65514b','142,23,51':'#984a49'}
};
function mapped(id,pixel,x,y,isEdge){
 const [red,green,blue]=pixel;
 if(id==='blood_crystal'){
  const light=(red+green+blue)/3;
  if(isEdge)return x<8&&y<8?'#c2767c':'#602c43';
  if(light>242)return '#f4c3ad';
  if(light>228)return '#dfa096';
  if(green<110)return '#8c354e';
  if(green<165)return '#ac4a61';
  if(blue-red>0)return '#a96777';
  return '#cc7b83';
 }
 if(id==='sakura_soul_crystal'){
  const light=(red+green+blue)/3;
  if(isEdge)return x<8&&y<8?'#d494a7':'#9d567f';
  if(light>242)return '#f5e6dc';
  if(light>228)return '#ead0d8';
  if(green<110)return '#b75b8d';
  if(green<165)return '#ce7ca1';
  if(blue-red>0)return '#be91bd';
  return '#e4b3cb';
 }
 let color=oldPal[id][pixel.slice(0,3).join(',')];
 if(id==='broken_oni_mask'&&color==='#dbc6a5'){
  if(y<6&&x<9)color='#ead7b7';
  else if(x>9||y>11)color='#bda88e';
 }
 if(id==='sword_ghost_remnant'&&color==='#843346'&&x>8&&y>8)color='#6b3040';
 return color||'#dbc6a5';
}
async function draw(id){
 if(id==='broken_oni_mask')return drawMask();
 const reference=id==='blood_crystal'?'sakura_soul_crystal':id;
 const {data,info}=await sharp(path.join(src,reference+'.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const scale=32/info.width;
 const opaque=(x,y)=>x>=0&&y>=0&&x<info.width&&y<info.height&&data[(y*info.width+x)*4+3]>200;
 let body='';
 for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++){
  const i=(y*info.width+x)*4,pixel=[...data.slice(i,i+4)];
  if(pixel[3]<200)continue;
  const isEdge=!opaque(x-1,y)||!opaque(x+1,y)||!opaque(x,y-1)||!opaque(x,y+1);
  const nx=x*scale,ny=y*scale;
  if(id==='boundary_gate_charm'&&ny>=27)continue;
  // The mask's lower-right chip cuts through the actual silhouette.
  if(id==='broken_oni_mask'&&((nx>=23&&ny>=24)||(nx>=21&&ny>=26)))continue;
  body+=rect(nx,ny,scale,scale,mapped(id,pixel,x/(info.width/16),y/(info.height/16),isEdge));
 }
 if(id==='blood_crystal'){
  body+=rect(14,5,2,1,'#f8d7be')+rect(12,10,1,3,'#f4c3ad')+rect(11,15,4,1,'#dfa096');
  body+=rect(13,18,1,3,'#dbc18b')+rect(14,21,1,1,'#dbc18b')+rect(15,22,3,1,'#dbc18b');
  body+=rect(18,20,1,2,'#cda16b')+rect(17,18,1,1,'#cda16b');
 }
 if(id==='sakura_soul_crystal'){
  body+=rect(14,5,2,1,'#fff0df')+rect(12,10,1,3,'#f8ebdf')+rect(11,15,4,1,'#f5e6dc');
  body+=rect(17,18,1,3,'#bf80a2')+rect(15,22,2,1,'#d79ebd')+rect(18,13,2,1,'#ebc6d4');
 }
 if(id==='sword_ghost_remnant'){
  body+=rect(16,8,1,3,'#db9c95')+rect(16,10,2,1,'#eed8ba')+rect(14,17,2,1,'#d8867b');
  body+=rect(15,18,2,4,'#e2a28a')+rect(16,19,1,2,'#f2d9b7')+rect(17,22,2,1,'#d38a7c');
  body+=rect(12,21,2,1,'#e7ad91')+rect(17,25,2,1,'#a6555d')+rect(22,27,1,2,'#b46566');
 }
 if(id==='boundary_gate_charm'){
  body+=rect(7,5,3,1,'#edcf94')+rect(19,5,2,1,'#876637')+rect(10,6,3,1,'#8f6538');
  body+=rect(10,10,1,1,'#d0b887')+rect(21,9,1,2,'#a08456')+rect(11,20,1,1,'#ab8151');
  body+=rect(9,25,3,1,'#e0bd80')+rect(20,24,2,1,'#98733e');
 }
 if(id==='broken_oni_mask'){
  body+=rect(9,8,3,1,'#f0dfbc')+rect(14,10,3,1,'#ead7b7')+rect(20,9,2,1,'#bda88e');
  body+=rect(20,17,1,3,'#776158')+rect(19,19,1,3,'#776158')+rect(20,22,2,1,'#776158');
  body+=rect(13,20,2,1,'#cbb493')+rect(17,20,2,1,'#cbb493')+rect(13,21,1,1,'#ead7b7');
  body+=rect(21,24,2,1,'#8e7663')+rect(19,25,2,1,'#dfc7a6');
 }
 return body;
}
function drawMask(){
 const poly=(points,color)=>`<polygon points="${points}" fill="${color}"/>`;
 // Fresh stepped silhouette: swept horns, sculpted cheeks and a chipped right jaw.
 let b=poly('6,3 8,3 8,6 10,6 10,9 13,9 13,8 19,8 19,9 22,9 22,6 24,6 24,3 26,3 26,11 27,11 27,18 25,18 25,22 23,22 23,25 20,25 20,28 12,28 12,26 9,26 9,23 7,23 7,19 5,19 5,11 6,11','#423039');
 b+=poly('7,5 8,5 8,8 10,8 10,11 13,11 13,10 19,10 19,11 23,11 23,8 25,6 25,12 26,12 26,17 24,17 24,21 22,21 22,24 19,24 19,27 13,27 13,25 10,25 10,22 8,22 8,18 6,18 6,12 7,12','#c5ac8a');
 b+=poly('7,5 8,8 10,8 10,11 14,11 14,10 18,10 18,12 14,12 14,17 12,17 12,19 9,19 9,17 7,17 7,12','#eddbb8');
 b+=poly('18,11 22,11 22,13 24,13 24,17 22,17 22,20 20,20 20,23 19,23 19,25 17,25 17,19 18,19','#a98e75');
 b+=rect(11,11,3,1,'#f5e7ca')+rect(14,10,4,1,'#e6cfa7');
 // Cinnabar brows sit over dark, inward-slanted eye slits.
 b+=poly('8,13 11,13 11,14 14,14 14,16 12,16 12,15 9,15 9,14 8,14','#9d4e50');
 b+=poly('18,14 21,14 21,13 24,13 24,14 23,14 23,15 20,15 20,16 18,16','#7b3845');
 b+=rect(10,16,3,1,'#443039')+rect(12,17,2,1,'#443039')+rect(19,16,3,1,'#443039')+rect(18,17,2,1,'#443039');
 b+=rect(15,15,2,4,'#e6cfa7')+rect(14,19,4,1,'#8a6b5f');
 b+=poly('11,21 13,21 13,20 19,20 19,21 21,21 21,23 19,23 19,24 13,24 13,23 11,23','#533841');
 b+=rect(12,21,2,2,'#f0debb')+rect(18,21,2,2,'#d7be97')+rect(15,22,2,1,'#bca180');
 b+=rect(13,25,5,1,'#e4cda6');
 // Crack joins the missing jaw corner rather than floating on the surface.
 b+=rect(23,17,1,2,'#5b4044')+rect(22,19,1,2,'#5b4044')+rect(21,21,1,2,'#5b4044')+rect(20,23,2,1,'#5b4044');
 b+=rect(21,24,2,1,'#e3cba4');
 return b;
}
async function main(){
 const width=1240,height=640;
 let sheet=rect(0,0,width,height,'#27231f');
 sheet+=`<text x="40" y="51" fill="#ead2a1" font-family="Microsoft YaHei" font-size="26" font-weight="bold">仪式物品 · 第二版预览</text><text x="40" y="82" fill="#bbaa8e" font-family="Microsoft YaHei" font-size="15">晶体轮廓统一 · 界门符底部收齐 · 鬼面重新绘制 · 尚未接入游戏</text>`;
 for(let i=0;i<names.length;i++){
  const [id,label]=names[i],body=await draw(id),x=40+i*240,y=115;
  const file=`<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">${body}</svg>`;
  fs.writeFileSync(path.join(out,id+'.svg'),file);
  await sharp(Buffer.from(file)).png().toFile(path.join(out,id+'.png'));
  sheet+=rect(x,y,220,390,'#332e27')+rect(x,y,220,2,'#68543d');
  sheet+=`<svg x="${x+46}" y="${y+21}" width="128" height="128" viewBox="0 0 32 32" shape-rendering="crispEdges">${body}</svg><text x="${x+110}" y="${y+186}" text-anchor="middle" fill="#e8d4b0" font-family="Microsoft YaHei" font-size="17">${label}</text>`;
  const old=fs.readFileSync(path.join(src,id+'.png')).toString('base64');
  sheet+=`<image x="${x+34}" y="${y+208}" width="64" height="64" image-rendering="pixelated" href="data:image/png;base64,${old}"/><svg x="${x+124}" y="${y+208}" width="64" height="64" viewBox="0 0 32 32" shape-rendering="crispEdges">${body}</svg>`;
  sheet+=`<text x="${x+66}" y="${y+296}" text-anchor="middle" fill="#aaa08e" font-family="Microsoft YaHei" font-size="13">现版</text><text x="${x+156}" y="${y+296}" text-anchor="middle" fill="#d8bd85" font-family="Microsoft YaHei" font-size="13">提案</text>`;
  sheet+=rect(x+86,y+324,48,48,'#88867f')+`<svg x="${x+94}" y="${y+332}" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">${body}</svg>`;
 }
 sheet+=`<text x="40" y="546" fill="#bbaa8e" font-family="Microsoft YaHei" font-size="15">血晶与樱魂晶采用同系晶体外形；界门符去除底部突出点；鬼面重绘角、眉眼、獠牙和破损边缘。</text><text x="40" y="581" fill="#bbaa8e" font-family="Microsoft YaHei" font-size="14">樱魂晶、剑鬼残响保持上一版。每卡下方灰底为小尺寸检查。</text>`;
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">${sheet}</svg>`;
 fs.writeFileSync(path.join(out,'ritual-items-preview.svg'),svg);
 await sharp(Buffer.from(svg)).png().toFile(path.join(out,'ritual-items-preview.png'));
 console.log('Rendered five SVG proposals with old/new comparisons.');
}
main().catch(e=>{console.error(e);process.exit(1)});
