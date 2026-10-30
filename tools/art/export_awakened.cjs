#!/usr/bin/env node
// Dependency: sharp 0.35.5. Run from any cwd; input sources are never regenerated here.
const fs=require('fs'),path=require('path'),sharp=require('sharp');
const root=path.resolve(__dirname,'../..');const m=JSON.parse(fs.readFileSync(path.join(root,'docs/awakened-rework/assets.json')));
const target=id=>path.join(root,'src/main/resources/assets',id.replace(':','/'));
const check=process.argv.includes('--check');
async function publish(pipeline,out){
 const png=await pipeline.png().toBuffer();
 if(check){const a=await sharp(png).raw().toBuffer();const b=await sharp(out).raw().toBuffer();if(!a.equals(b))throw new Error('SVG/PNG pixel mismatch: '+out);}
 else await fs.promises.writeFile(out,png);
}
(async()=>{
 for(const soul of ['akatsuki','kyouka','senbonzakura','raikiri','shared']){
  const a=m.assets.filter(a=>a.soul===soul), layers=[];
  for(const e of a)layers.push({input:await sharp(path.join(root,e.source)).png().toBuffer(),left:e.rect[0],top:e.rect[1]});
  const out=target(a[0].texture);fs.mkdirSync(path.dirname(out),{recursive:true});
  await publish(sharp({create:{width:a[0].canvas_size[0],height:a[0].canvas_size[1],channels:4,background:'#00000000'}}).composite(layers),out);
 }
 for(const a of m.animations){const layers=[];for(let i=0;i<a.frames;i++)layers.push({input:await sharp(path.join(root,a.source_directory,`${i}`.padStart(2,'0')+'.svg')).png().toBuffer(),left:i%4*128,top:Math.floor(i/4)*128});await publish(sharp({create:{width:512,height:512,channels:4,background:'#00000000'}}).composite(layers),target(a.texture));}
 console.log((check?'Verified pixel-identical SVG export: ':'Exported: ')+'5 component atlases + 5 material animation sheets.');
})().catch(e=>{console.error(e);process.exit(1)});
