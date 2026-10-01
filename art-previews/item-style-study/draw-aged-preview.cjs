const fs=require('fs'),path=require('path'),sharp=require('sharp');
const out=path.join(__dirname,'aged-paper');
fs.mkdirSync(out,{recursive:true});
const assets=path.resolve(__dirname,'../../src/main/resources/assets/blade_tetra/textures/item');
const R=(x,y,w,h,c)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${c}"/>`;
const P=(d,c)=>`<path d="${d}" fill="${c}"/>`;
const wrap=b=>`<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" shape-rendering="crispEdges">${b}</svg>`;
// Colors sampled from the existing clue parchment, rather than a flat beige fill.
const paper=['#ecd093','#e8ca8a','#e0c07b','#d7b874','#ccad6c','#bb9d5e'];
const edge=['#58370d','#715223','#8f7549','#b08b41'];
function scrap(seed=0){
 let s='';
 const rows=[[4,10],[3,12],[3,13],[2,13],[2,12],[2,13],[1,13],[2,14],[2,13],[3,13],[2,13],[3,12],[4,12],[5,10]];
 for(let y=1;y<=14;y++){
  const [left,right]=rows[y-1];
  for(let x=left;x<=right;x++){
   const border=x===left||x===right||y===1||y===14;
   const fold=(x===4&&y>5&&y<12)||(x+y===16&&y>9);
   let c=border?edge[(x+y+seed)%edge.length]:paper[2];
   if(!border&&x+y<14)c=paper[1];
   if(!border&&x>10)c=paper[4];
   if(!border&&y>11)c=paper[3];
   if(!border&&((x===6&&y===3)||(x===9&&y===4)||(x===3&&y===8)||(x===12&&y===9)||(x===8&&y===12)))c=paper[4];
   if(!border&&((x===8&&y===3)||(x===5&&y===7)||(x===11&&y===10)||(x===6&&y===12)))c=paper[0];
   if(!border&&x<6&&y<5)c=paper[(x+y)%2];
   if(fold)c=paper[4];
   s+=R(x,y,1,1,c);
  }
 }
 s+=R(4,3,3,1,'#f0dbac')+R(3,5,1,2,'#eacf96')+R(11,11,1,2,'#a8854b')+R(5,13,2,1,'#c6a365');
 return s;
}
function book(record){
 const blue=record?['#3b3349','#51425e','#6a526e']:['#292e55','#3b416d','#52577c'];
 let s=P('M3 1h9v1h2v12h-2v1H3v-1H2V2h1z','#30282d');
 s+=R(3,2,9,11,blue[1])+R(4,3,8,8,blue[0])+R(3,3,1,9,'#695542')+R(3,3,1,2,'#a48146')+R(3,7,1,1,'#a48146')+R(3,10,1,2,'#b89558');
 s+=P('M4 2h7v1H4zM12 3h1v8h-1zM4 11h7v1H4z','#8d703e')+R(4,2,3,1,'#cfab65')+R(11,3,1,2,'#b08b41')+R(5,11,3,1,'#bda063')+R(12,11,1,2,'#6e5432');
 s+=R(4,13,8,1,'#b39359')+R(5,12,7,1,'#e0c07b')+R(6,12,2,1,'#ecd093')+R(10,12,1,1,'#ccad6c')+R(4,4,1,2,blue[2])+R(11,7,1,2,blue[1]);
 if(record){
  s+=P('M9 4h2v1h-1v1H9v1H8v1H7V7h1V6h1z','#c9b989')+R(6,7,2,1,'#b3955b')+R(6,8,1,2,'#897048');
  s+=R(9,8,2,2,'#9b4d55')+R(9,8,1,1,'#d79081')+R(10,10,1,4,'#7a3645')+R(10,10,1,1,'#a14e5c');
 }else{
  s+=R(6,5,5,1,'#b99d61')+R(6,5,2,1,'#e0c07b')+R(7,7,3,1,'#94733d')+P('M7 8h3v1H9v1H8V9H7z','#c5a767')+R(5,9,1,1,'#736c69');
 }
 return s;
}
const patterns=[
 ['black_gold','黑金','#68562e',P('M5 5h2v1H6v2H5zM9 6h2v1h-1v2H9zM6 10h2v1H7v1H6z','#4e3c22')+R(7,7,1,1,'#c69649')+R(10,10,1,1,'#8f6b31')],
 ['vermilion_cloud','朱云','#a2623e',P('M4 7h1V6h2v1h2v1H4zM7 10h2V9h2v1h1v1H7z','#a25c39')+R(5,7,3,1,'#c98653')+R(8,10,2,1,'#c98653')],
 ['seigaiha','青海波','#58747a',P('M4 6h1V5h2v1h1v1H7V6H5v1H4zM8 6h1V5h2v1h1v1h-1V6H9v1H8zM6 10h1V9h2v1h1v1H9v-1H7v1H6z','#617575')+R(5,6,2,1,'#9ea393')+R(7,10,2,1,'#9ea393')],
 ['sakura','樱纹','#a36c68',P('M6 5h1v1h1v1H7v1H6V7H5V6h1zM9 9h1v1h1v1h-1v1H9v-1H8v-1h1z','#b57374')+R(6,6,1,1,'#e8beaa')+R(9,10,1,1,'#e8beaa')],
 ['purple_lightning','紫电','#766079',P('M8 4h2v2H8v2h2v1H8v3H6V9H5V8h2V6h1z','#796378')+R(8,5,1,1,'#b19991')+R(6,9,1,2,'#a48b87')],
 ['akatsuki','赤月','#a15c36',P('M8 4h3v1H8v2H7v3h1v1h3v1H7v-1H6V5h2z','#a35630')+R(7,5,1,2,'#c97c44')+R(7,10,1,1,'#c97c44')],
 ['kyouka','镜花','#5b7c85',P('M6 4h4v1h1v1h1v4h-1v1h-1v1H6v-1H5v-1H4V6h1V5h1z','#5b7881')+P('M6 5h4v1h1v4h-1v1H6v-1H5V6h1z','#c6b980')+P('M7 6h3v1H8v1H7v1h2v1H6V7h1z','#76958c')+R(9,9,1,1,'#9fb09a')]
];
const icons=[{id:'smithing_journal',name:'锻刀手记',body:book(false)},{id:'named_blade_record',name:'名刀映录',body:book(true)}];
patterns.forEach(([id,name,accent,motif],i)=>icons.push({id:'saya_pattern_'+id,name:name+'图样',body:scrap(i)+motif+R(10,12,2,1,accent)}));
async function main(){
 const width=1220,height=1050;
 let b=R(0,0,width,height,'#25231f');
 b+=`<text x="40" y="54" fill="#eacf96" font-family="Microsoft YaHei" font-size="27" font-weight="bold">Blade Tetra · 旧纸与旧墨</text><text x="40" y="84" fill="#b7aa91" font-family="Microsoft YaHei" font-size="15">第二版 · 以旧线索的材质为基准 · 取消平整色框，恢复磨损、折痕与褪色墨迹</text>`;
 b+=`<text x="40" y="128" fill="#d3b57c" font-family="Microsoft YaHei" font-size="17">风格基准：现版线索原图（保留）</text>`;
 const references=['shoshin','bairen','bansho','raikiri','senbonzakura','akatsuki','kyouka'];
 for(let i=0;i<7;i++){
  const x=58+i*162,old=fs.readFileSync(path.join(assets,'smithing_clue_'+references[i]+'.png')).toString('base64');
  b+=R(x-12,145,136,115,'#302d27')+`<image x="${x+24}" y="160" width="64" height="64" image-rendering="pixelated" href="data:image/png;base64,${old}"/>`;
 }
 b+=`<text x="40" y="300" fill="#d3b57c" font-family="Microsoft YaHei" font-size="17">新提案：书册与鞘绘图样</text>`;
 for(let i=0;i<9;i++){
  const row=Math.floor(i/5),col=i%5,x=40+col*234,y=320+row*292,icon=icons[i];
  b+=R(x,y,220,267,'#302d27')+R(x,y,220,2,'#655440');
  b+=`<svg x="${x+62}" y="${y+18}" width="96" height="96" viewBox="0 0 16 16" shape-rendering="crispEdges">${icon.body}</svg>`;
  b+=`<text x="${x+110}" y="${y+143}" text-anchor="middle" fill="#e3d1ac" font-family="Microsoft YaHei" font-size="16">${icon.name}</text>`;
  const old=fs.readFileSync(path.join(assets,icon.id+'.png')).toString('base64');
  b+=`<image x="${x+44}" y="${y+169}" width="32" height="32" image-rendering="pixelated" href="data:image/png;base64,${old}"/><svg x="${x+144}" y="${y+169}" width="32" height="32" viewBox="0 0 16 16" shape-rendering="crispEdges">${icon.body}</svg>`;
  b+=`<text x="${x+60}" y="${y+232}" text-anchor="middle" fill="#a99c86" font-family="Microsoft YaHei" font-size="13">现版</text><text x="${x+160}" y="${y+232}" text-anchor="middle" fill="#d3b57c" font-family="Microsoft YaHei" font-size="13">旧纸风格</text>`;
  fs.writeFileSync(path.join(out,icon.id+'.svg'),wrap(icon.body));
 }
 b+=`<text x="40" y="978" fill="#b7aa91" font-family="Microsoft YaHei" font-size="15">图样采用破损旧纸与褪色墨纹；书册采用旧金装订与泛黄纸页。晶体、鬼面等暂沿用现版。</text><text x="40" y="1008" fill="#b7aa91" font-family="Microsoft YaHei" font-size="14">仅为 SVG 美术预览，尚未接入游戏。</text>`;
 const sheet=`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">${b}</svg>`;
 fs.writeFileSync(path.join(out,'aged-paper-preview.svg'),sheet);
 await sharp(Buffer.from(sheet)).png().toFile(path.join(out,'aged-paper-preview.png'));
 console.log('Rendered nine revised icons with original clue references.');
}
main().catch(e=>{console.error(e);process.exit(1)});
