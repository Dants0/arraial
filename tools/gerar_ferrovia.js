// Resources of the railway (estação, ferroviário): the bilheteria block model, blockstate, item json,
// loot table, recipe and tags. Usage: node tools/gerar_ferrovia.js
'use strict';
const fs = require('fs'), path = require('path');
const R = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(R, 'assets', 'arraial'), D = path.join(R, 'data');

function json(arq, obj) { fs.mkdirSync(path.dirname(arq), { recursive: true }); fs.writeFileSync(arq, JSON.stringify(obj, null, 2) + '\n'); }
function caixa(de, ate, tex, faces = {}) {
  const dx = Math.min(16, ate[0] - de[0]), dy = Math.min(16, ate[1] - de[1]), dz = Math.min(16, ate[2] - de[2]);
  const uv = { north: [0, 0, dx, dy], south: [0, 0, dx, dy], east: [0, 0, dz, dy], west: [0, 0, dz, dy], up: [0, 0, dx, dz], down: [0, 0, dx, dz] };
  const f = {};
  for (const lado of ['north', 'south', 'east', 'west', 'up', 'down']) f[lado] = { texture: faces[lado] || tex, uv: uv[lado] };
  return { from: de, to: ate, faces: f };
}

// Bilheteria: the ticket window of the station; a counter, a grille, a little roof and a bell.
json(path.join(A, 'models', 'block', 'bilheteria.json'), { parent: 'minecraft:block/block',
  textures: { tabua: 'minecraft:block/dark_oak_planks', tampo: 'minecraft:block/stripped_dark_oak_log', grade: 'minecraft:block/iron_bars',
    sino: 'minecraft:block/gold_block', papel: 'minecraft:block/white_wool', particle: 'minecraft:block/dark_oak_planks' },
  elements: [
    caixa([0, 0, 3], [16, 10, 13], '#tabua'),
    caixa([-0.5, 10, 2], [16.5, 11, 14], '#tampo'),
    caixa([0, 11, 3], [2, 16, 5], '#tampo'),
    caixa([14, 11, 3], [16, 16, 5], '#tampo'),
    caixa([2, 11, 3.5], [14, 16, 4.5], '#grade'),
    caixa([-0.5, 15.5, 1], [16.5, 16.5, 6], '#tabua'),
    caixa([11, 11, 6], [13, 12.5, 8], '#sino'),
    caixa([4, 11, 7], [8, 11.2, 10], '#papel')] });
json(path.join(A, 'blockstates', 'bilheteria.json'), { variants: {
  'facing=north': { model: 'arraial:block/bilheteria' }, 'facing=east': { model: 'arraial:block/bilheteria', y: 90 },
  'facing=south': { model: 'arraial:block/bilheteria', y: 180 }, 'facing=west': { model: 'arraial:block/bilheteria', y: 270 } } });
json(path.join(A, 'items', 'bilheteria.json'), { model: { type: 'minecraft:model', model: 'arraial:block/bilheteria' } });
json(path.join(D, 'arraial', 'loot_table', 'blocks', 'bilheteria.json'), { type: 'minecraft:block', pools: [{ rolls: 1,
  condition: { type: 'minecraft:survives_explosion' }, entries: [{ type: 'minecraft:item', name: 'arraial:bilheteria' }] }],
  random_sequence: 'arraial:blocks/bilheteria' });
json(path.join(D, 'arraial', 'recipe', 'bilheteria.json'), { type: 'minecraft:crafting_shaped', category: 'misc',
  key: { I: 'minecraft:iron_bars', P: '#minecraft:planks', R: 'minecraft:rail' }, pattern: ['III', 'PRP', 'PPP'],
  result: { count: 1, id: 'arraial:bilheteria' } });

const arq = path.join(D, 'minecraft', 'tags', 'block', 'mineable', 'axe.json');
const tag = JSON.parse(fs.readFileSync(arq, 'utf8'));
if (!tag.values.includes('arraial:bilheteria')) tag.values.push('arraial:bilheteria');
json(arq, tag);
console.log('recursos da ferrovia gerados');
