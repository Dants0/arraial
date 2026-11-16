// Resources of the trilha 3 trades (ferreiro, carpinteiro, padeiro, tecelão, alfaiate): item
// textures, workstation block models, blockstates, item jsons, loot tables, recipes and tags.
// Usage: node tools/gerar_oficios.js
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
const TI = path.join(A, 'textures', 'item');
function itemSimples(nome) {
  json(path.join(A, 'models', 'item', nome + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'arraial:item/' + nome } });
  json(path.join(A, 'items', nome + '.json'), { model: { type: 'minecraft:model', model: 'arraial:item/' + nome } });
}

// ------------------------------------------------------------------ block helpers
function caixa(de, ate, tex, faces = {}) {
  const dx = Math.min(16, ate[0] - de[0]), dy = Math.min(16, ate[1] - de[1]), dz = Math.min(16, ate[2] - de[2]);
  const uv = { north: [0, 0, dx, dy], south: [0, 0, dx, dy], east: [0, 0, dz, dy], west: [0, 0, dz, dy], up: [0, 0, dx, dz], down: [0, 0, dx, dz] };
  const f = {};
  for (const lado of ['north', 'south', 'east', 'west', 'up', 'down']) f[lado] = { texture: faces[lado] || tex, uv: uv[lado] };
  return { from: de, to: ate, faces: f };
}
const giratorio = nome => ({ variants: {
  'facing=north': { model: `arraial:block/${nome}` }, 'facing=east': { model: `arraial:block/${nome}`, y: 90 },
  'facing=south': { model: `arraial:block/${nome}`, y: 180 }, 'facing=west': { model: `arraial:block/${nome}`, y: 270 } } });
function posto(nome, textures, elements) {
  json(path.join(A, 'models', 'block', nome + '.json'), { parent: 'minecraft:block/block', textures, elements });
  json(path.join(A, 'blockstates', nome + '.json'), giratorio(nome));
  json(path.join(A, 'items', nome + '.json'), { model: { type: 'minecraft:model', model: 'arraial:block/' + nome } });
  json(path.join(D, 'arraial', 'loot_table', 'blocks', nome + '.json'), { type: 'minecraft:block', pools: [{ rolls: 1,
    condition: { type: 'minecraft:survives_explosion' }, entries: [{ type: 'minecraft:item', name: 'arraial:' + nome }] }],
    random_sequence: 'arraial:blocks/' + nome });
}
const RC = path.join(D, 'arraial', 'recipe');
const shaped = (res, pattern, key, n = 1) => ({ type: 'minecraft:crafting_shaped', category: 'misc', key, pattern, result: { count: n, id: 'arraial:' + res } });
function tag(arq, novos) {
  const atual = fs.existsSync(arq) ? JSON.parse(fs.readFileSync(arq, 'utf8')) : { values: [] };
  for (const v of novos) if (!atual.values.includes(v)) atual.values.push(v);
  json(arq, atual);
}
const MINEAVEL = path.join(D, 'minecraft', 'tags', 'block', 'mineable');

// ================================================================== 3A: ferreiro
// Forja: a brick hearth with glowing coals, a hood and the bellows at the side.
posto('forja', { tijolo: 'minecraft:block/bricks', brasa: 'minecraft:block/magma', pedra: 'minecraft:block/stone_bricks',
  fole: 'minecraft:block/brown_wool', tabua: 'minecraft:block/dark_oak_planks', particle: 'minecraft:block/bricks' }, [
  caixa([0, 0, 2], [16, 9, 14], '#tijolo'),
  caixa([2, 9, 4], [14, 10, 12], '#brasa'),
  caixa([1, 9, 2], [15, 11, 4], '#tijolo'),
  caixa([1, 9, 12], [15, 11, 14], '#tijolo'),
  caixa([4, 13, 5], [12, 16, 11], '#pedra'),
  caixa([5, 10, 6], [6, 13, 7], '#pedra'),
  caixa([10, 10, 9], [11, 13, 10], '#pedra'),
  caixa([12, 2, 14], [16, 6, 16], '#fole', { north: '#tabua', south: '#tabua' })]);
json(path.join(RC, 'forja.json'), shaped('forja', ['B B', 'BCB', 'BBB'], { B: 'minecraft:bricks', C: 'minecraft:charcoal' }));
tag(path.join(MINEAVEL, 'pickaxe.json'), ['arraial:forja']);

// Ferradura: a horseshoe, with its nail holes.
grade(path.join(TI, 'ferradura.png'), [
  '................',
  '................',
  '....aaaaaaaa....',
  '...abbbbbbbba...',
  '..abbcbbbbcbba..',
  '..abb......bba..',
  '..acb......bca..',
  '..abb......bba..',
  '..abb......bba..',
  '..acb......bca..',
  '..abb......bba..',
  '..abba....abba..',
  '..aaaa....aaaa..',
  '................',
  '................',
  '................'],
{ a: '#4A4E52', b: '#A8ADB2', c: '#2A2C2E' });
itemSimples('ferradura');

console.log('recursos dos oficios gerados');
