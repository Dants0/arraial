// Resources of the port (porto, estivador, navio mercante): item textures (sal, chita), the
// guindaste block model, blockstate, item json, loot table, recipes and tags.
// Usage: node tools/gerar_porto.js
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

// Sal grosso: a burlap sack, open at the top, heaped with coarse white salt.
grade(path.join(TI, 'sal.png'), [
  '................',
  '......wwww......',
  '....wwbwwwww....',
  '...wwwwwbwwww...',
  '...awwwwwwwwa...',
  '...aassssssaa...',
  '..aasaaaaaasaa..',
  '..aaaacaaaaaaa..',
  '..aacaaaaacaaa..',
  '..aaaaaaaaaaaa..',
  '..aaacaaaacaaa..',
  '..aaaaaaaaaaaa..',
  '..daaaacaaaaad..',
  '...daaaaaaaad...',
  '....dddddddd....',
  '................'],
{ w: '#F4F4EE', b: '#D8DCE0', a: '#B8915A', c: '#9C7744', s: '#7A5A30', d: '#7E5F36' });
// Chita: a folded bolt of red cotton printed with big yellow and white flowers.
grade(path.join(TI, 'chita.png'), [
  '................',
  '................',
  '...rrrrrrrrrr...',
  '..rryrrrwrrryr..',
  '..ryoyrrrrryoy..',
  '..rryrrgrrrryr..',
  '..rrrrrrrwrrrr..',
  '..DDDDDDDDDDDD..',
  '..rrwrrrryrrrr..',
  '..rrrrrryoyrrr..',
  '..ryrrgrryrrwr..',
  '..yoyrrrrrrrrr..',
  '..ryrrrwrrgrrr..',
  '..DDDDDDDDDDDD..',
  '................',
  '................'],
{ r: '#C8282E', D: '#8E1C22', y: '#F2C230', o: '#E87A1E', w: '#F4F0E4', g: '#3E8A3A' });
for (const it of ['sal', 'chita']) {
  json(path.join(A, 'models', 'item', it + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'arraial:item/' + it } });
  json(path.join(A, 'items', it + '.json'), { model: { type: 'minecraft:model', model: 'arraial:item/' + it } });
}

// ------------------------------------------------------------------ guindaste
function caixa(de, ate, tex, faces = {}) {
  const f = {};
  for (const lado of ['north', 'south', 'east', 'west', 'up', 'down']) f[lado] = { texture: faces[lado] || tex };
  return { from: de, to: ate, faces: f };
}
const MB = path.join(A, 'models', 'block');
// A wooden crane: a post, an arm reaching out over the front with a brace, and a crate on the rope.
json(path.join(MB, 'guindaste.json'), { parent: 'minecraft:block/block',
  textures: { tora: 'minecraft:block/stripped_oak_log', topo: 'minecraft:block/stripped_oak_log_top', tabua: 'minecraft:block/spruce_planks',
    corda: 'minecraft:block/white_wool', caixa: 'minecraft:block/barrel_side', tampa: 'minecraft:block/barrel_top', particle: 'minecraft:block/stripped_oak_log' },
  elements: [
    caixa([5, 0, 9], [11, 2, 15], '#tabua'),
    caixa([6, 2, 10], [10, 16, 14], '#tora', { up: '#topo', down: '#topo' }),
    caixa([7, 13, 0], [9, 15, 10], '#tabua'),
    caixa([7, 9, 7], [9, 13, 9], '#tabua'),
    caixa([7.5, 7, 1.5], [8.5, 13, 2.5], '#corda'),
    caixa([5.5, 2, 0], [10.5, 7, 4], '#caixa', { up: '#tampa', down: '#tampa' })] });
json(path.join(A, 'blockstates', 'guindaste.json'), { variants: {
  'facing=north': { model: 'arraial:block/guindaste' }, 'facing=east': { model: 'arraial:block/guindaste', y: 90 },
  'facing=south': { model: 'arraial:block/guindaste', y: 180 }, 'facing=west': { model: 'arraial:block/guindaste', y: 270 } } });
json(path.join(A, 'items', 'guindaste.json'), { model: { type: 'minecraft:model', model: 'arraial:block/guindaste' } });
json(path.join(D, 'arraial', 'loot_table', 'blocks', 'guindaste.json'), { type: 'minecraft:block', pools: [{ rolls: 1,
  condition: { type: 'minecraft:survives_explosion' }, entries: [{ type: 'minecraft:item', name: 'arraial:guindaste' }] }],
  random_sequence: 'arraial:blocks/guindaste' });

// ------------------------------------------------------------------ recipes
const RC = path.join(D, 'arraial', 'recipe');
json(path.join(RC, 'guindaste.json'), { type: 'minecraft:crafting_shaped', category: 'misc',
  key: { L: '#minecraft:logs', R: 'minecraft:lead', B: 'minecraft:barrel' }, pattern: ['LLL', 'L R', 'L B'],
  result: { count: 1, id: 'arraial:guindaste' } });
// Salt cures meat: three raw beef and a sack of salt make three charque at the crafting table.
json(path.join(RC, 'charque_com_sal.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: ['minecraft:beef', 'minecraft:beef', 'minecraft:beef', 'arraial:sal'], result: { count: 3, id: 'arraial:charque' } });
// Chita makes the bandeirinhas of the festa junina.
json(path.join(RC, 'bandeirinhas_chita.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: ['arraial:chita', 'minecraft:string'], result: { count: 8, id: 'arraial:bandeirinhas' } });

function tag(arq, novos) {
  const atual = fs.existsSync(arq) ? JSON.parse(fs.readFileSync(arq, 'utf8')) : { values: [] };
  for (const v of novos) if (!atual.values.includes(v)) atual.values.push(v);
  json(arq, atual);
}
tag(path.join(D, 'minecraft', 'tags', 'block', 'mineable', 'axe.json'), ['arraial:guindaste']);
console.log('recursos do porto gerados');
