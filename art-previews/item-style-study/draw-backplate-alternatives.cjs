const fs=require('fs'), path=require('path'), sharp=require('sharp');
const out=path.join(__dirname,'backplate-alternatives');
fs.mkdirSync(out,{recursive:true});
const themes=[['steel','铁灰','#6c8b9b','#c9dce1'],['jade','钻石青','#438f92','#b9eee1'],['violet','灰紫','#8e6cac','#ead9f6']];
const shapes=[['lacquer','夜漆 · 折光'],['wave','刀纹 · 流线'],['moon','静月 · 月轮']];
function body(kind,c,l,id){
 const defs=`<defs><linearGradient id="${id}base" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${c}"/><stop offset=".5" stop-color="#27303b"/><stop offset="1" stop-color="#10151e"/></linearGradient><linearGradient id="${id}light" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${l}" stop-opacity=".75"/><stop offset=".65" stop-color="${c}" stop-opacity=".2"/><stop offset="1" stop-color="${l}" stop-opacity=".5"/></linearGradient><clipPath id="${id}clip"><path d="M128 12 244 128 128 244 12 128Z"/></clipPath></defs>`;
 let s=defs+`<path d="M128 12 244 128 128 244 12 128Z" fill="url(#${id}base)"/>`;
 s+=`<g clip-path="url(#${id}clip)">`;
 if(kind==='lacquer'){
  s+=`<path d="M-20 126 126-20H175L-20 175Z" fill="${l}" opacity=".17"/><path d="M-20 190 190-20H207L-20 207Z" fill="${l}" opacity=".08"/><path d="m66 199 133-133 6 6L72 205Z" fill="${c}" opacity=".18"/>`;
  s+=`<path d="m72 94 56-56 56 56M72 162l56 56 56-56" fill="none" stroke="url(#${id}light)" stroke-width="2"/><path d="m83 91 45-45 45 45" fill="none" stroke="${l}" stroke-width="1" opacity=".35"/>`;
 } else if(kind==='wave'){
  s+=`<path d="M-15 178C58 48 145 224 263 67L275 115C151 276 65 73-15 215Z" fill="${c}" opacity=".18"/>`;
  for(let i=0;i<6;i++)s+=`<path d="M-18 ${146+i*13}C62 ${17+i*14} 149 ${231+i*5} 275 ${38+i*16}" fill="none" stroke="${l}" stroke-width="${i===2?3:1.5}" opacity="${i===2?.48:.14}"/>`;
  s+=`<path d="M81 57 128 10 175 57M81 199l47 47 47-47" fill="none" stroke="${l}" stroke-width="2" opacity=".3"/>`;
 } else {
  s+=`<circle cx="128" cy="128" r="82" fill="none" stroke="${c}" stroke-width="17" opacity=".12"/><circle cx="128" cy="128" r="77" fill="none" stroke="url(#${id}light)" stroke-width="2"/><circle cx="128" cy="128" r="67" fill="none" stroke="${l}" stroke-width="1" opacity=".18"/>`;
  s+=`<path d="M151 53a77 77 0 0 0 0 150A67 67 0 0 1 151 53Z" fill="${l}" opacity=".24"/><path d="M128 32v12m0 168v12M32 128h12m168 0h12" stroke="${l}" stroke-width="3" opacity=".4"/>`;
 }
 s+='</g>';
 // Decorative inner edge only; the native durability gauge stays a separate layer.
 s+=`<path d="M128 18 238 128 128 238 18 128Z" fill="none" stroke="url(#${id}light)" stroke-width="2"/><path d="M119 28h18M119 228h18" stroke="${l}" stroke-width="2" opacity=".45"/>`;
 return s;
}
const blade=`<g transform="rotate(43 128 128)"><path d="M116 24h11v157l-11 9Z" fill="#dce6e9" stroke="#222631" stroke-width="3"/><path d="M122 25h5v153l-5 5Z" fill="#809dab"/><path d="M103 183h37v10h-37Z" fill="#bdb5a1" stroke="#282831" stroke-width="3"/><path d="M114 194h15v49h-15Z" fill="#4a3440" stroke="#282831" stroke-width="3"/><path d="M116 201h11m-11 13h11m-11 13h11" stroke="#cebba3" stroke-width="4"/></g><g transform="rotate(-42 128 128)"><path d="M131 45h18v164h-18Z" fill="#433539" stroke="#20252d" stroke-width="3"/><path d="M132 46h16v12m-16 137h16v10" stroke="#adb9b9" stroke-width="4"/></g>`;
const t=(x,y,label,size=18,color='#d8d3c8')=>`<text x="${x}" y="${y}" font-family="Microsoft YaHei,sans-serif" font-size="${size}" fill="${color}">${label}</text>`;
let sheet='<rect width="1120" height="900" fill="#201f25"/>'+t(32,40,'普通刀背板 · 三套高清 SVG 候选',26)+t(32,70,'只展示背板纹理；外圈耐久沿用游戏逻辑，御影表情另留给赤月觉醒刀。',16,'#aaa597');
for(let row=0;row<3;row++){
 const [kind,label]=shapes[row],y=96+row*258;
 sheet+=t(32,y+28,label,20);
 for(let col=0;col<3;col++){
  const [theme,name,c,l]=themes[col],x=210+col*298,id=`${row}${col}`;
  const raw=body(kind,c,l,id);
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">${raw}</svg>`;
  fs.writeFileSync(path.join(out,`${kind}-${theme}.svg`),svg);
  sheet+=`<rect x="${x}" y="${y}" width="276" height="228" rx="6" fill="#2b2a30"/><g transform="translate(${x+12} ${y+28}) scale(.63)">${raw}</g>`;
  sheet+=t(x+22,y+212,name,16);
  sheet+=`<rect x="${x+188}" y="${y+57}" width="70" height="70" fill="#80817c"/><g transform="translate(${x+191} ${y+60}) scale(.25)">${body(kind,c,l,id+'b')}${blade}</g>`;
  sheet+=`<rect x="${x+203}" y="${y+156}" width="38" height="38" fill="#80817c"/><g transform="translate(${x+206} ${y+159}) scale(.125)">${body(kind,c,l,id+'c')}${blade}</g>`;
  sheet+=t(x+193,y+145,'叠刀示意',13,'#aaa597');
 }
}
sheet+=t(32,886,'右侧为 64px / 32px 检查。刀与刀鞘仅作布局示意；尚未接入游戏。',16,'#aaa597');
const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="900" viewBox="0 0 1120 900">${sheet}</svg>`;
fs.writeFileSync(path.join(out,'preview.svg'),svg);
sharp(Buffer.from(svg)).png().toFile(path.join(out,'preview.png'));
