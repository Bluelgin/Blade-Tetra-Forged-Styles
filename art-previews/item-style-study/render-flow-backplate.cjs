const path = require('path');
const sharp = require('sharp');
const source = path.join(__dirname, 'backplate-alternatives/wave-steel.svg');
const output = path.resolve(__dirname, '../../src/main/resources/assets/blade_tetra/textures/item/flow_backplate.png');
sharp(source).grayscale().png().toFile(output);
