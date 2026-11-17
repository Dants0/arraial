// Resources of the fauna's place in the village: couro de jacaré, pena de arara and the pele de onça
// (a rug), item textures, the rug block model, loot tables and recipes.
// Usage: node tools/gerar_fauna_vila.js
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const R = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(R, 'assets', 'arraial'), D = path.join(R, 'data');

function json(arq, obj) { fs.mkdirSync(path.dirname(arq), { recursive: true }); fs.writeFileSync(arq, JSON.stringify(obj, null, 2) + '\n'); }
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
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 982451653) >>> 0; h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
function textura(arq, fn) {
  const px = Buffer.alloc(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const c = fn(x, y); if (!c) continue;
    const i = (y * 16 + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
  }
  fs.mkdirSync(path.dirname(arq), { recursive: true });
  fs.writeFileSync(arq, png(16, 16, px));
}
function grade(arq, linhas, pal) {
  textura(arq, (x, y) => { const ch = (linhas[y] || '')[x]; return ch && ch !== '.' ? hex(pal[ch]) : null; });
}
const TI = path.join(A, 'textures', 'item'), TB = path.join(A, 'textures', 'block');
function itemSimples(nome) {
  json(path.join(A, 'models', 'item', nome + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'arraial:item/' + nome } });
  json(path.join(A, 'items', nome + '.json'), { model: { type: 'minecraft:model', model: 'arraial:item/' + nome } });
}

// Couro de jacaré: a tanned hide with the caiman's square scales.
grade(path.join(TI, 'couro_jacare.png'), [
  '................',
  '...aaaaaaaaaa...',
  '..abcbcbcbcbca..',
  '..accbccbccbca..',
  '..abbcbbcbbcba..',
  '..acbccbccbcca..',
  '..abcbcbcbcbba..',
  '..accbccbccbca..',
  '..abbcbbcbbcba..',
  '..acbccbccbcca..',
  '..abcbcbcbcbca..',
  '...accbccbcca...',
  '....abbcbbca....',
  '.....aaaaaa.....',
  '................',
  '................'],
{ a: '#3A3020', b: '#6E6A3A', c: '#545230' });
itemSimples('couro_jacare');

// Pena de arara: a long red feather tipped with blue and yellow.
grade(path.join(TI, 'pena_arara.png'), [
  '..............bb',
  '.............bbb',
  '............rbb.',
  '...........rrb..',
  '..........rrr...',
  '.........rrrr...',
  '........rrrr....',
  '.......rrrr.....',
  '......yrrr......',
  '.....yyrr.......',
  '....yyr.........',
  '...yy...........',
  '..w.............',
  '.w..............',
  'w...............',
  '................'],
{ r: '#D0282E', b: '#2E6AC8', y: '#F2C230', w: '#E8E0D0' });
itemSimples('pena_arara');

// Pele de onça: the jaguar's golden coat with black rosettes, as a rug.
textura(path.join(TB, 'tapete_onca.png'), (x, y) => {
  const borda = x === 0 || y === 0 || x === 15 || y === 15;
  if (borda) return hex('#8A5A1E');
  // Rosettes: rings of dark spots on a golden ground.
  const cx = ((x + (Math.floor(y / 5) % 2) * 3) % 6) - 2.5, cy = (y % 5) - 2;
  const r = Math.sqrt(cx * cx + cy * cy);
  if (r > 1.4 && r < 2.4 && hash(x, y, 5) > 0.25) return hex('#2A1E14');
  if (r <= 1.4) return hex('#C8862E');
  return hash(x, y, 9) > 0.85 ? hex('#E8B860') : hex('#D89A3A');
});
json(path.join(A, 'models', 'block', 'tapete_onca.json'), { parent: 'minecraft:block/carpet', textures: { wool: 'arraial:block/tapete_onca' } });
json(path.join(A, 'blockstates', 'tapete_onca.json'), { variants: {
  'facing=north': { model: 'arraial:block/tapete_onca' }, 'facing=east': { model: 'arraial:block/tapete_onca', y: 90 },
  'facing=south': { model: 'arraial:block/tapete_onca', y: 180 }, 'facing=west': { model: 'arraial:block/tapete_onca', y: 270 } } });
grade(path.join(TI, 'pele_onca.png'), [
  '................',
  '...aa......aa...',
  '...abaaaaaaba...',
  '....bbbbbbbb....',
  '...bbkbbbkbbb...',
  '..bbkbkbbkbkbb..',
  '..bbbkbbbbkbbb..',
  '..bkbbbbkbbbkb..',
  '..bbkbbkbkbbbb..',
  '..bbbbbbkbbkbb..',
  '...bkbbbbbbkb...',
  '...bbbkbbkbbb...',
  '..abb.bbbb.bba..',
  '..aa..bbbb..aa..',
  '.......bb.......',
  '.......aa.......'],
{ a: '#8A5A1E', b: '#D89A3A', k: '#2A1E14' });
json(path.join(A, 'models', 'item', 'pele_onca.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'arraial:item/pele_onca' } });
json(path.join(A, 'items', 'pele_onca.json'), { model: { type: 'minecraft:model', model: 'arraial:item/pele_onca' } });
json(path.join(D, 'arraial', 'loot_table', 'blocks', 'tapete_onca.json'), { type: 'minecraft:block', pools: [{ rolls: 1,
  condition: { type: 'minecraft:survives_explosion' }, entries: [{ type: 'minecraft:item', name: 'arraial:pele_onca' }] }],
  random_sequence: 'arraial:blocks/tapete_onca' });

// Loot: the jacaré gives its prized hide; the onça its coat.
const LE = path.join(D, 'arraial', 'loot_table', 'entities');
function acrescentar(arq, nome, min, max) {
  const t = JSON.parse(fs.readFileSync(arq, 'utf8'));
  if (JSON.stringify(t).includes(nome)) return;
  t.pools.push({ rolls: 1, entries: [{ type: 'minecraft:item', name: nome, functions: [
    { function: 'minecraft:set_count', count: { type: 'minecraft:uniform', min, max } },
    { function: 'minecraft:enchanted_count_increase', enchantment: 'minecraft:looting', count: { type: 'minecraft:uniform', min: 0, max: 1 } }] }] });
  json(arq, t);
}
acrescentar(path.join(LE, 'jacare.json'), 'arraial:couro_jacare', 1, 2);
acrescentar(path.join(LE, 'onca.json'), 'arraial:pele_onca', 1, 1);

// Feathers make festa bunting too.
json(path.join(D, 'arraial', 'recipe', 'bandeirinhas_penas.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: ['arraial:pena_arara', 'arraial:pena_arara', 'arraial:pena_arara', 'minecraft:string'], result: { count: 8, id: 'arraial:bandeirinhas' } });
console.log('recursos da fauna na vila gerados');
