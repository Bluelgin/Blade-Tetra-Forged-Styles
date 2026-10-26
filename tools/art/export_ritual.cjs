#!/usr/bin/env node
// Uses the same sharp SVG rasterizer as export_awakened.cjs; never regenerates source art.
const fs=require('fs'), path=require('path'), sharp=require('sharp');
const root=path.resolve(__dirname,'../..');
(async()=>{for(const name of ['energy_veins','white_energy','blood_moon']) {
 const png=await sharp(path.join(root,'art/awakening-ritual',name+'.svg')).png().toBuffer();
 const target=path.join(root,'src/main/resources/assets/blade_tetra/textures/effect/awakened/ritual',name+'.png');
 if(process.argv.includes('--check')) {
  if(!(await sharp(png).raw().toBuffer()).equals(await sharp(target).raw().toBuffer()))
   throw new Error('SVG/PNG mismatch: '+name);
 } else {fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,png);}
} console.log('Ritual SVG/PNG exports verified');})().catch(e=>{console.error(e);process.exit(1)});
