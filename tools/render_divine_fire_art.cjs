// Converts editable SVG sources into Minecraft textures and a human-readable preview.
const fs = require('fs');
const path = require('path');
const sharp = require(process.argv[2] || 'sharp');
const root = path.resolve(__dirname, '..');
const names = ['divine_fire_ready', 'divine_fire_active', 'divine_fire_spent'];
async function main() {
  const composites = [];
  for (let i = 0; i < names.length; i++) {
    const input = path.join(root, 'art/svg/divine_domain', `${names[i]}.svg`);
    for (const size of [32, 64, 128, 256]) {
      await sharp(input, { density: 192 }).resize(size, size).png().toFile(
        path.join(root, 'src/main/resources/assets/blade_tetra/textures/divine', `${names[i]}_${size}.png`));
    }
    composites.push({ input: await sharp(input).resize(200, 200).png().toBuffer(), left: 62 + i * 276, top: 79 });
    composites.push({ input: await sharp(input).resize(32, 32).png().toBuffer(), left: 146 + i * 276, top: 326 });
  }
  const backdrop = `<svg xmlns="http://www.w3.org/2000/svg" width="876" height="422">
    <rect width="876" height="422" rx="20" fill="#18151c"/>
    <text x="34" y="42" fill="#fff0db" font-family="sans-serif" font-size="21">MIKAGE / DIVINE FIRE RESCUE</text>
    <path d="M34 58h808" stroke="#58404a"/>
    <g fill="#241c26" stroke="#57404a"><rect x="34" y="72" width="256" height="226" rx="14"/>
      <rect x="310" y="72" width="256" height="226" rx="14"/><rect x="586" y="72" width="256" height="226" rx="14"/></g>
    <g fill="#e2bd79" font-family="sans-serif" font-size="16" text-anchor="middle">
      <text x="162" y="320">READY</text><text x="438" y="320">RESCUING</text><text x="714" y="320">SPENT</text></g>
    <text x="34" y="393" fill="#ad8f98" font-family="sans-serif" font-size="14">SVG sources / transparent PNG textures / native 32 px samples below</text>
  </svg>`;
  await sharp(Buffer.from(backdrop)).composite(composites).png().toFile(path.join(root, 'art/divine-fire-rescue-preview.png'));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
