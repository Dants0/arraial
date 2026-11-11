// Resources of the water mill and the monjolo: the farinha de trigo item texture, the mó, roda
// d'água (four frames) and monjolo (two frames) block models, blockstates, item jsons, loot
// tables, recipes and tags. Usage: node tools/gerar_moinho.js
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const R = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(R, 'assets', 'arraial'), D = path.join(R, 'data');

function json(arq, obj) { fs.mkdirSync(path.dirname(arq), { recursive: true }); fs.writeFileSync(arq, JSON.stringify(obj, null, 2) + '\n'); }

// ------------------------------------------------------------------ PNG
const CRC = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(b) { let c = -1; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function chunk(t, d) { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([l, td, c]); }
function png(w, h, px) {
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
function grade(arq, linhas, pal) {
  const px = Buffer.alloc(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const ch = (linhas[y] || '')[x]; if (!ch || ch === '.') continue;
    const c = hex(pal[ch]), i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
  }
  fs.mkdirSync(path.dirname(arq), { recursive: true });
  fs.writeFileSync(arq, png(16, 16, px));
}

// Farinha de trigo: a cotton flour bag tied at the top, dusted white, with a wheat ear printed on it.
grade(path.join(A, 'textures', 'item', 'farinha_trigo.png'), [
  '................',
  '.......tt.......',
  '......tttt......',
  '.......kk.......',
  '.....wwwwww.....',
  '....wwwwwwww....',
  '...wwwwyywwww...',
  '...wwwyywyyww...',
  '...wwwwyyywww...',
  '...wwwyyywyww...',
  '...wwwwwyywww...',
  '...wwwwwywwww...',
  '...wwwwwywwww...',
  '...cwwwwwwwwc...',
  '....cccccccc....',
  '................'],
{ w: '#F2EEE2', c: '#CFC6B0', y: '#D9A83A', t: '#B88A4A', k: '#8A6A3A' });
json(path.join(A, 'models', 'item', 'farinha_trigo.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'arraial:item/farinha_trigo' } });
json(path.join(A, 'items', 'farinha_trigo.json'), { model: { type: 'minecraft:model', model: 'arraial:item/farinha_trigo' } });

// ------------------------------------------------------------------ block models
function caixa(de, ate, tex, faces = {}, rot = null) {
  const f = {};
  // Explicit UVs, clamped to the texture: parts that reach outside the block would otherwise get
  // out-of-range automatic UVs (fatal for animated textures such as water).
  const dx = Math.min(16, ate[0] - de[0]), dy = Math.min(16, ate[1] - de[1]), dz = Math.min(16, ate[2] - de[2]);
  const uv = { north: [0, 0, dx, dy], south: [0, 0, dx, dy], east: [0, 0, dz, dy], west: [0, 0, dz, dy], up: [0, 0, dx, dz], down: [0, 0, dx, dz] };
  for (const lado of ['north', 'south', 'east', 'west', 'up', 'down']) f[lado] = { texture: faces[lado] || tex, uv: uv[lado] };
  const e = { from: de, to: ate, faces: f };
  if (rot) e.rotation = rot;
  return e;
}
const MB = path.join(A, 'models', 'block'), BS = path.join(A, 'blockstates'), IT = path.join(A, 'items');
const Y = { north: 0, east: 90, south: 180, west: 270 };

// Mó: two millstones, a wooden hopper on top and a frame.
json(path.join(MB, 'mo.json'), { parent: 'minecraft:block/block',
  textures: { pedra: 'minecraft:block/smooth_stone', lado: 'minecraft:block/smooth_stone_slab_side', tabua: 'minecraft:block/spruce_planks',
    trigo: 'minecraft:block/hay_block_top', particle: 'minecraft:block/smooth_stone' },
  elements: [
    caixa([1, 0, 1], [15, 4, 15], '#tabua'),
    caixa([2, 4, 2], [14, 7, 14], '#lado', { up: '#pedra', down: '#pedra' }),
    caixa([2.5, 7, 2.5], [13.5, 10, 13.5], '#lado', { up: '#pedra', down: '#pedra' }),
    caixa([5, 10, 5], [11, 14, 11], '#tabua'),
    caixa([5.5, 13.5, 5.5], [10.5, 14, 10.5], '#trigo')] });
json(path.join(BS, 'mo.json'), { variants: Object.fromEntries(Object.entries(Y).map(([f, y]) => [`facing=${f}`, y ? { model: 'arraial:block/mo', y } : { model: 'arraial:block/mo' }])) });
json(path.join(IT, 'mo.json'), { model: { type: 'minecraft:model', model: 'arraial:block/mo' } });

// Roda d'água: a paddle wheel nearly three blocks across, in the x-y plane, turning about z: an
// octagonal rim, four spokes and four paddles reaching into the water. Block models only rotate
// elements by -45..45 degrees, so every part is first turned by whole quarter-turns by hand
// (about the centre) and then by the remaining angle; four frames, 22.5 degrees apart.
const RAIO = 18;
const quarto = ([x0, y0, z0], [x1, y1, z1], m) => {
  let a = [x0, y0], b = [x1, y1];
  for (let k = 0; k < m; k++) { a = [16 - a[1], a[0]]; b = [16 - b[1], b[0]]; }
  return [[Math.min(a[0], b[0]), Math.min(a[1], b[1]), z0], [Math.max(a[0], b[0]), Math.max(a[1], b[1]), z1]];
};
const norm = x => { while (x > 45) x -= 90; while (x <= -45) x += 90; return x; };
function rodaFrame(a) {
  const els = [caixa([5.5, 5.5, 3], [10.5, 10.5, 16], '#eixo')];
  const rot = r => (r === 0 ? null : { origin: [8, 8, 8], axis: 'z', angle: r });
  const por = (de, ate, tex, r, voltas) => { for (const m of voltas) { const [p, q] = quarto(de, ate, m); els.push(caixa(p, q, tex, {}, rot(r))); } };
  // Rim: eight straight pieces (two four-fold sets, 45 degrees apart).
  for (const r of [norm(a), norm(a + 45)]) por([8 - 7.5, 8 + RAIO - 2, 5], [8 + 7.5, 8 + RAIO, 11], '#aro', r, [0, 1, 2, 3]);
  // Spokes: two bars through the hub.
  por([8 - RAIO, 7, 6.5], [8 + RAIO, 9, 9.5], '#raio', norm(a), [0, 1]);
  // Paddles: four broad boards standing out of the rim.
  por([7, 8 + RAIO - 5, 2], [9, 8 + RAIO + 4, 14], '#pa', norm(a), [0, 1, 2, 3]);
  return els;
}
[0, 22.5, 45, 67.5].forEach((a, i) => {
  json(path.join(MB, 'roda_dagua_' + i + '.json'), { parent: 'minecraft:block/block',
    textures: { raio: 'minecraft:block/stripped_spruce_log', aro: 'minecraft:block/dark_oak_planks', pa: 'minecraft:block/spruce_planks',
      eixo: 'minecraft:block/spruce_log_top', particle: 'minecraft:block/spruce_planks' },
    elements: rodaFrame(a) });
});
const variantesRoda = {};
for (const [f, y] of Object.entries(Y)) for (let i = 0; i < 4; i++) {
  variantesRoda[`facing=${f},fase=${i}`] = y ? { model: `arraial:block/roda_dagua_${i}`, y } : { model: `arraial:block/roda_dagua_${i}` };
}
json(path.join(BS, 'roda_dagua.json'), { variants: variantesRoda });
json(path.join(IT, 'roda_dagua.json'), { model: { type: 'minecraft:model', model: 'arraial:block/roda_dagua_0' } });

// Monjolo: a beam on a post; water fills the trough at the back, the beam tips and the pestle at
// the front drops into the pilão. Two frames: raised and striking.
[['monjolo_alto', 22.5], ['monjolo_batendo', -22.5]].forEach(([nome, a]) => {
  const r = { origin: [8, 10, 8], axis: 'x', angle: a };
  json(path.join(MB, nome + '.json'), { parent: 'minecraft:block/block',
    textures: { tora: 'minecraft:block/stripped_oak_log', tabua: 'minecraft:block/oak_planks', pilao: 'minecraft:block/dark_oak_log',
      pilao_topo: 'minecraft:block/dark_oak_log_top', agua: 'minecraft:block/water_still', particle: 'minecraft:block/stripped_oak_log' },
    elements: [
      caixa([4, 0, -5], [12, 5, 3], '#pilao', { up: '#pilao_topo', down: '#pilao_topo' }),
      caixa([7, 0, 7], [9, 10, 9], '#tora'),
      caixa([5, 0, 6], [11, 2, 10], '#tabua'),
      caixa([7, 9, -3], [9, 11, 22], '#tora', {}, r),
      caixa([6, 4, -3], [10, 9, 1], '#tora', {}, r),
      caixa([5, 11, 17], [11, 15, 23], '#tabua', {}, r),
      caixa([5.5, 14, 17.5], [10.5, 14.5, 22.5], '#agua', {}, r)] });
});
const variantesMonjolo = {};
for (const [f, y] of Object.entries(Y)) for (const b of [false, true]) {
  const m = `arraial:block/${b ? 'monjolo_batendo' : 'monjolo_alto'}`;
  variantesMonjolo[`batendo=${b},facing=${f}`] = y ? { model: m, y } : { model: m };
}
json(path.join(BS, 'monjolo.json'), { variants: variantesMonjolo });
json(path.join(IT, 'monjolo.json'), { model: { type: 'minecraft:model', model: 'arraial:block/monjolo_alto' } });

// ------------------------------------------------------------------ loot, recipes, tags
const LT = path.join(D, 'arraial', 'loot_table', 'blocks');
for (const b of ['mo', 'roda_dagua', 'monjolo']) {
  json(path.join(LT, b + '.json'), { type: 'minecraft:block', pools: [{ rolls: 1, condition: { type: 'minecraft:survives_explosion' },
    entries: [{ type: 'minecraft:item', name: `arraial:${b}` }] }], random_sequence: `arraial:blocks/${b}` });
}
const RC = path.join(D, 'arraial', 'recipe');
const shaped = (res, pattern, key) => ({ type: 'minecraft:crafting_shaped', category: 'misc', key, pattern, result: { count: 1, id: 'arraial:' + res } });
json(path.join(RC, 'mo.json'), shaped('mo', [' W ', 'SSS', 'SSS'], { W: 'minecraft:wheat', S: 'minecraft:smooth_stone' }));
json(path.join(RC, 'roda_dagua.json'), shaped('roda_dagua', ['P P', ' L ', 'P P'], { P: '#minecraft:planks', L: '#minecraft:logs' }));
json(path.join(RC, 'monjolo.json'), shaped('monjolo', ['L  ', 'LLL', 'B  '], { L: '#minecraft:logs', B: 'minecraft:bowl' }));
// Farinha de trigo bakes into bread in the furnace (or the smoker, or on the campfire).
json(path.join(RC, 'pao_de_farinha.json'), { type: 'minecraft:smelting', category: 'food', ingredient: 'arraial:farinha_trigo',
  result: { id: 'minecraft:bread' }, experience: 0.2, cookingtime: 200 });
json(path.join(RC, 'pao_de_farinha_defumador.json'), { type: 'minecraft:smoking', category: 'food', ingredient: 'arraial:farinha_trigo',
  result: { id: 'minecraft:bread' }, experience: 0.2, cookingtime: 100 });

function tag(arq, novos) {
  const atual = fs.existsSync(arq) ? JSON.parse(fs.readFileSync(arq, 'utf8')) : { values: [] };
  for (const v of novos) if (!atual.values.includes(v)) atual.values.push(v);
  json(arq, atual);
}
tag(path.join(D, 'minecraft', 'tags', 'block', 'mineable', 'axe.json'), ['arraial:roda_dagua', 'arraial:monjolo']);
tag(path.join(D, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), ['arraial:mo']);
console.log('recursos do moinho gerados');
