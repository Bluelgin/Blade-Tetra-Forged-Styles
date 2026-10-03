// Pixel-aligned SVG -> native 32 px runtime textures. Never antialias or smooth upscale.
const path = require('path');
const sharp = require(process.argv[2] || 'sharp');
const root = path.resolve(__dirname, '..');
const names = ['karmic_mirror_unmarked', 'karmic_mirror_echo', 'karmic_mirror_grudge',
  'karmic_mirror_hundred_ghosts', 'karmic_mirror_asura', 'karmic_mirror_avici',
  'dead_thought_soul_seal', 'sword_ghost_remnant'];
const labels = ['UNMARKED', 'ECHO', 'GRUDGE', 'HUNDRED GHOSTS', 'ASURA', 'AVICI', 'SOUL SEAL', 'SOUL REMNANT'];
async function main() {
  const images = [];
  let tiles = '';
  for (let i = 0; i < names.length; i++) {
    const x = 24 + (i % 4) * 220;
    const y = 72 + Math.floor(i / 4) * 262;
    const input = path.join(root, 'art/svg/divine_domain', `${names[i]}.svg`);
    const png = await sharp(input).resize(32, 32, { kernel: 'nearest' }).png().toBuffer();
    await sharp(png).toFile(path.join(root, 'src/main/resources/assets/blade_tetra/textures/item', `${names[i]}.png`));
    images.push({ input: await sharp(png).resize(160, 160, { kernel: 'nearest' }).png().toBuffer(), left: x + 25, top: y + 10 });
    images.push({ input: await sharp(png).resize(16, 16, { kernel: 'nearest' }).png().toBuffer(), left: x + 97, top: y + 206 });
    images.push({ input: png, left: x + 123, top: y + 198 });
    tiles += `<rect x="${x}" y="${y}" width="210" height="236" rx="12" fill="#251d27" stroke="#59434c"/>
      <text x="${x + 105}" y="${y + 192}" fill="#e2bd79" font-family="sans-serif" font-size="13" text-anchor="middle">${labels[i]}</text>`;
  }
  const board = `<svg xmlns="http://www.w3.org/2000/svg" width="918" height="626">
    <rect width="918" height="626" rx="18" fill="#18151c"/>
    <text x="24" y="40" font-family="sans-serif" font-size="22" fill="#fff0db">DIVINE DOMAIN / KARMIC MIRRORS AND SOUL RELICS</text>
    <path d="M24 54h870" stroke="#59434c"/>${tiles}
    <text x="24" y="604" font-family="sans-serif" font-size="13" fill="#ad8f98">Editable pixel SVG / 32 px runtime PNG / 16 and 32 px inventory samples</text>
  </svg>`;
  await sharp(Buffer.from(board)).composite(images).png().toFile(path.join(root, 'art/divine-domain-items-preview.png'));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
