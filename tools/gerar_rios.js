// Resources of the rivers (trapiche and barqueiro): block and item models, blockstates,
// loot tables, recipes and tags. Usage: node tools/gerar_rios.js
'use strict';
const fs = require('fs'), path = require('path');
const R = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(R, 'assets', 'arraial'), D = path.join(R, 'data');

function json(arq, obj) { fs.mkdirSync(path.dirname(arq), { recursive: true }); fs.writeFileSync(arq, JSON.stringify(obj, null, 2) + '\n'); }
const BS = path.join(A, 'blockstates'), MB = path.join(A, 'models', 'block'), IT = path.join(A, 'items');
function caixa(de, ate, tex, faces = {}) {
  const f = {};
  for (const lado of ['north', 'south', 'east', 'west', 'up', 'down']) f[lado] = { texture: faces[lado] || tex };
  return { from: de, to: ate, faces: f };
}
const giratorio = nome => ({ variants: {
  'facing=north': { model: `arraial:block/${nome}` }, 'facing=east': { model: `arraial:block/${nome}`, y: 90 },
  'facing=south': { model: `arraial:block/${nome}`, y: 180 }, 'facing=west': { model: `arraial:block/${nome}`, y: 270 } } });

// Amarra: a mooring post with a coil of rope, and a paddle leaning on it.
json(path.join(MB, 'amarra.json'), { parent: 'minecraft:block/block',
  textures: { tora: 'minecraft:block/stripped_spruce_log', topo: 'minecraft:block/stripped_spruce_log_top', corda: 'minecraft:block/hay_block_side',
    remo: 'minecraft:block/oak_planks', particle: 'minecraft:block/stripped_spruce_log' },
  elements: [
    caixa([5, 0, 5], [11, 14, 11], '#tora', { up: '#topo', down: '#topo' }),
    caixa([4, 6, 4], [12, 9, 12], '#corda'),
    caixa([4, 11, 4], [12, 12, 12], '#corda'),
    caixa([12, 0, 7], [14, 3, 9], '#remo'),
    caixa([12, 3, 7.5], [13, 15, 8.5], '#remo')] });
json(path.join(BS, 'amarra.json'), giratorio('amarra'));
json(path.join(IT, 'amarra.json'), { model: { type: 'minecraft:model', model: 'arraial:block/amarra' } });

const LT = path.join(D, 'arraial', 'loot_table', 'blocks');
for (const b of ['amarra']) {
  json(path.join(LT, b + '.json'), { type: 'minecraft:block', pools: [{ rolls: 1, condition: { type: 'minecraft:survives_explosion' },
    entries: [{ type: 'minecraft:item', name: `arraial:${b}` }] }], random_sequence: `arraial:blocks/${b}` });
}

const RC = path.join(D, 'arraial', 'recipe');
const shaped = (res, n, pattern, key, cat = 'misc') => ({ type: 'minecraft:crafting_shaped', category: cat, key, pattern, result: { count: n, id: 'arraial:' + res } });
json(path.join(RC, 'amarra.json'), shaped('amarra', 1, ['R', 'L'], { R: 'minecraft:lead', L: '#minecraft:logs' }));

function tag(arq, novos) {
  const atual = fs.existsSync(arq) ? JSON.parse(fs.readFileSync(arq, 'utf8')) : { values: [] };
  for (const v of novos) if (!atual.values.includes(v)) atual.values.push(v);
  json(arq, atual);
}
tag(path.join(D, 'minecraft', 'tags', 'block', 'mineable', 'axe.json'), ['arraial:amarra']);
console.log('recursos dos rios gerados');
