#!/usr/bin/env node
// SVG is the editable source; sharp exports the 64px textures used at configurable GUI size.
const fs = require('fs'), path = require('path'), sharp = require('sharp');
const root = path.resolve(__dirname, '../..');
const names = ['akatsuki', 'kyouka', 'senbonzakura', 'raikiri', 'pulse'];
const out = path.join(root, 'src/main/resources/assets/blade_tetra/textures/gui/soul_switch');
(async () => {
  fs.mkdirSync(out, {recursive: true});
  for (const name of names) {
    const png = await sharp(path.join(root, 'art/soul-switch', name + '.svg')).png().toBuffer();
    const target = path.join(out, name + '.png');
    if (process.argv.includes('--check')) {
      const a = await sharp(png).ensureAlpha().raw().toBuffer();
      const b = await sharp(target).ensureAlpha().raw().toBuffer();
      if (!a.equals(b)) throw new Error('SVG/PNG mismatch: ' + name);
    } else fs.writeFileSync(target, png);
  }
  const buff = path.join(root, 'src/main/resources/assets/blade_tetra/textures/mob_effect/thunder_armor.png');
  const buffPng = await sharp(path.join(root, 'art/soul-switch/raikiri.svg')).resize(32,32).png().toBuffer();
  if (process.argv.includes('--check')) {
    const a = await sharp(buffPng).ensureAlpha().raw().toBuffer();
    const b = await sharp(buff).ensureAlpha().raw().toBuffer();
    if (!a.equals(b)) throw new Error('Thunder armor icon mismatch');
  } else {
    fs.mkdirSync(path.dirname(buff), {recursive:true});
    fs.writeFileSync(buff,buffPng);
  }
  if (process.argv.includes('--preview')) {
    const previews = path.join(root, 'art-previews/soul-switch');
    fs.mkdirSync(previews, {recursive: true});
    const layers = [];
    for (let i = 0; i < 4; i++) {
      const file = path.join(out, names[i] + '.png');
      layers.push({input: await sharp(file).resize(80,80).toBuffer(), left: 40 + i * 140, top: 24});
      layers.push({input: await sharp(file).resize(22,22).toBuffer(), left: 69 + i * 140, top: 150});
    }
    for (let row = 0; row < 2; row++) {
      const a = path.join(out, row ? 'kyouka.png' : 'akatsuki.png');
      const b = path.join(out, row ? 'raikiri.png' : 'senbonzakura.png');
      layers.push({input: await sharp(a).resize(22,22).toBuffer(), left: 262, top: 218 + row * 38});
      const idle = await sharp(b).resize(18,18).ensureAlpha().raw().toBuffer({resolveWithObject: true});
      for (let p = 3; p < idle.data.length; p += 4) idle.data[p] = Math.round(idle.data[p] * .35);
      layers.push({input: await sharp(idle.data,{raw:idle.info}).png().toBuffer(), left: 291, top: 220 + row * 38});
    }
    await sharp({create:{width:600,height:302,channels:4,background:'#171b24'}})
      .composite(layers).png().toFile(path.join(previews,'soul-seals.png'));
  }
  console.log('Soul seal SVG/PNG exports verified');
})().catch(e => { console.error(e); process.exit(1); });
