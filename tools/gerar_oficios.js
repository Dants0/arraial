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

// ================================================================== 3B: carpinteiro e padeiro
// Bancada de carpinteiro: a heavy workbench with a plank in the vise and a saw on top.
posto('bancada_carpinteiro', { tampo: 'minecraft:block/stripped_spruce_log', perna: 'minecraft:block/spruce_log', tabua: 'minecraft:block/oak_planks',
  serra: 'minecraft:block/iron_block', cabo: 'minecraft:block/dark_oak_planks', particle: 'minecraft:block/stripped_spruce_log' }, [
  caixa([0, 10, 2], [16, 13, 14], '#tampo'),
  caixa([1, 0, 3], [3, 10, 5], '#perna'), caixa([13, 0, 3], [15, 10, 5], '#perna'),
  caixa([1, 0, 11], [3, 10, 13], '#perna'), caixa([13, 0, 11], [15, 10, 13], '#perna'),
  caixa([1, 3, 4], [15, 4, 12], '#tabua'),
  caixa([2, 13, 8], [12, 14, 13], '#tabua'),
  caixa([4, 13, 4], [13, 13.5, 6], '#serra'), caixa([13, 13, 3.5], [15, 15, 6.5], '#cabo')]);
json(path.join(RC, 'bancada_carpinteiro.json'), shaped('bancada_carpinteiro', ['III', 'SSS', 'L L'],
  { I: 'minecraft:iron_ingot', S: '#minecraft:wooden_slabs', L: '#minecraft:logs' }));
tag(path.join(MINEAVEL, 'axe.json'), ['arraial:bancada_carpinteiro']);

// Forno de pão: the clay dome oven of the padaria, embers glowing in its mouth.
posto('forno_pao', { base: 'minecraft:block/cobblestone', barro: 'minecraft:block/terracotta', brasa: 'minecraft:block/magma',
  boca: 'minecraft:block/black_concrete', particle: 'minecraft:block/terracotta' }, [
  caixa([0, 0, 0], [16, 6, 16], '#base'),
  caixa([1, 6, 2], [15, 11, 15], '#barro'),
  caixa([3, 11, 4], [13, 14, 13], '#barro'),
  caixa([5, 14, 6], [11, 15, 11], '#barro'),
  caixa([5, 6, 1], [11, 10, 2], '#boca'),
  caixa([6, 6, 1.5], [10, 7, 2.5], '#brasa')]);
json(path.join(RC, 'forno_pao.json'), shaped('forno_pao', [' T ', 'TCT', 'BBB'],
  { T: 'minecraft:terracotta', C: 'minecraft:charcoal', B: 'minecraft:cobblestone' }));
tag(path.join(MINEAVEL, 'pickaxe.json'), ['arraial:forno_pao']);

// Pão de queijo: a little basket of golden cheese breads.
grade(path.join(TI, 'pao_de_queijo.png'), [
  '................',
  '................',
  '.....aaa..aaa...',
  '....abbbaabbba..',
  '....abcbbabcba..',
  '....abbbaabbba..',
  '..aaaaaaaaaaaa..',
  '..abbbaabbbaa...',
  '..abcbbabcba....',
  '..abbbaabbba....',
  '.ddddddddddddd..',
  '.deedeedeedeed..',
  '..dddddddddddd..',
  '...ddddddddddd..',
  '................',
  '................'],
{ a: '#B8862A', b: '#E8B84A', c: '#F6DC8A', d: '#8A5A2A', e: '#B07A3A' });
itemSimples('pao_de_queijo');

// ================================================================== 3C: tecelão e alfaiate
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
const TB = path.join(A, 'textures', 'block');
// Algodoeiro: four stages (seedling, bush, yellow flowers, open white bolls).
function algodoeiro(estagio) {
  return (x, y) => {
    const altura = [6, 11, 14, 15][estagio];
    if (y < 16 - altura) return null;
    const meio = Math.abs(x - 7.5);
    const largura = [2, 5, 6, 7][estagio] * (0.6 + 0.4 * (y - (16 - altura)) / altura);
    if (meio > largura + 0.5) return null;
    if (y >= 13 && meio < 1) return hex('#6A4A2A');
    const n = hash(x, y, 57 + estagio);
    if (n < 0.28) return null;
    if (estagio === 2 && n > 0.88) return hex('#F2D23A');
    if (estagio === 3 && n > 0.66) return n > 0.8 ? hex('#FBFBF6') : hex('#E6E4DA');
    return n < 0.5 ? hex('#3A6E34') : hex('#4E8A40');
  };
}
const varAlgodao = {};
for (let e = 0; e < 4; e++) {
  textura(path.join(TB, `algodoeiro_estagio${e}.png`), algodoeiro(e));
  json(path.join(A, 'models', 'block', `algodoeiro_estagio${e}.json`), { parent: 'minecraft:block/cross',
    textures: { cross: `arraial:block/algodoeiro_estagio${e}` }, render_type: 'cutout' });
  varAlgodao[`age=${e}`] = { model: `arraial:block/algodoeiro_estagio${e}` };
}
json(path.join(A, 'blockstates', 'algodoeiro.json'), { variants: varAlgodao });
json(path.join(D, 'arraial', 'loot_table', 'blocks', 'algodoeiro.json'), { type: 'minecraft:block', pools: [{ rolls: 1,
  entries: [{ type: 'minecraft:item', name: 'arraial:algodao' }] }], random_sequence: 'arraial:blocks/algodoeiro' });

// Algodão: an open cotton boll in its brown husk (it is also the seedling).
grade(path.join(TI, 'algodao.png'), [
  '................',
  '......wwww......',
  '....wwwwwwww....',
  '...wwwwgwwwww...',
  '..wwwgwwwwgwww..',
  '..wwwwwwwwwwww..',
  '..wwgwwwwwwgww..',
  '...wwwwwgwwww...',
  '...hwwwwwwwwh...',
  '....hhwwwwhh....',
  '.....hhhhhh.....',
  '......hhhh......',
  '.......ss.......',
  '.......ss.......',
  '................',
  '................'],
{ w: '#FBFBF4', g: '#DCDACE', h: '#7A5A34', s: '#4E8A40' });
itemSimples('algodao');
// Pano: a bolt of plain cotton cloth.
grade(path.join(TI, 'pano.png'), [
  '................',
  '................',
  '...aaaaaaaaaa...',
  '..abbbbbbbbbba..',
  '..abccccccccba..',
  '..abbbbbbbbbba..',
  '..aaaaaaaaaaaa..',
  '..dbbbbbbbbbbd..',
  '..dbccccccccbd..',
  '..dbbbbbbbbbbd..',
  '..dddddddddddd..',
  '..ebbbbbbbbbbe..',
  '..ebccccccccbe..',
  '..eeeeeeeeeeee..',
  '................',
  '................'],
{ a: '#BDB8A8', b: '#F3F0E6', c: '#E2DDCE', d: '#B2AD9C', e: '#A8A290' });
itemSimples('pano');
// Roupa de festa: a chita dress for the São João, red with yellow flowers and a white collar.
grade(path.join(TI, 'roupa_festa.png'), [
  '................',
  '.....ww..ww.....',
  '....rwwwwwwr....',
  '...rrrrrrrrrr...',
  '..rrryrrrryrrr..',
  '..rr.rrrrrr.rr..',
  '.....ryrrrr.....',
  '.....rrrryr.....',
  '....rrrrrrrr....',
  '....ryrrrryrr...',
  '...rrrrryrrrrr..',
  '...rryrrrrrrrr..',
  '..rrrrrrryrrrrr.',
  '..wwwwwwwwwwwww.',
  '................',
  '................'],
{ r: '#C8282E', y: '#F2C230', w: '#F4F0E4' });
itemSimples('roupa_festa');

// Tear: a wooden hand loom, the warp stretched and a roll of cloth.
posto('tear', { madeira: 'minecraft:block/spruce_planks', tora: 'minecraft:block/stripped_spruce_log', fio: 'minecraft:block/white_wool',
  pano: 'minecraft:block/white_wool', particle: 'minecraft:block/spruce_planks' }, [
  caixa([0, 0, 2], [2, 16, 4], '#tora'), caixa([14, 0, 2], [16, 16, 4], '#tora'),
  caixa([0, 0, 12], [2, 10, 14], '#tora'), caixa([14, 0, 12], [16, 10, 14], '#tora'),
  caixa([0, 14, 2], [16, 16, 4], '#madeira'),
  caixa([0, 8, 4], [2, 10, 12], '#madeira'), caixa([14, 8, 4], [16, 10, 12], '#madeira'),
  caixa([2, 4, 2.6], [14, 14, 3.4], '#fio'),
  caixa([2, 8, 9], [14, 11, 13], '#pano'),
  caixa([0, 2, 4], [16, 3, 12], '#madeira')]);
json(path.join(RC, 'tear.json'), shaped('tear', ['PSP', 'PWP', 'P P'], { P: '#minecraft:planks', S: 'minecraft:string', W: '#minecraft:wool' }));
tag(path.join(MINEAVEL, 'axe.json'), ['arraial:tear']);

// Mesa de alfaiate: a cutting table with a bolt of red chita, white cloth, scissors and a tape.
posto('mesa_alfaiate', { tampo: 'minecraft:block/dark_oak_planks', perna: 'minecraft:block/dark_oak_log', chita: 'minecraft:block/red_wool',
  pano: 'minecraft:block/white_wool', tesoura: 'minecraft:block/iron_block', fita: 'minecraft:block/yellow_wool', particle: 'minecraft:block/dark_oak_planks' }, [
  caixa([0, 12, 1], [16, 14, 15], '#tampo'),
  caixa([1, 0, 2], [3, 12, 4], '#perna'), caixa([13, 0, 2], [15, 12, 4], '#perna'),
  caixa([1, 0, 12], [3, 12, 14], '#perna'), caixa([13, 0, 12], [15, 12, 14], '#perna'),
  caixa([2, 14, 3], [9, 16, 7], '#chita'),
  caixa([2, 14, 9], [11, 15, 13], '#pano'),
  caixa([11, 14, 4], [14, 14.5, 5], '#tesoura'), caixa([12, 14, 3], [13, 14.5, 7], '#tesoura'),
  caixa([10, 14, 8], [15, 14.3, 8.6], '#fita')]);
json(path.join(RC, 'mesa_alfaiate.json'), shaped('mesa_alfaiate', ['WT ', 'PPP', 'P P'], { W: '#minecraft:wool', T: 'minecraft:shears', P: '#minecraft:planks' }));
tag(path.join(MINEAVEL, 'axe.json'), ['arraial:mesa_alfaiate']);

// Rede de dormir: a striped hammock hanging from two ropes (decorative).
textura(path.join(TB, 'rede.png'), (x, y) => {
  const faixa = Math.floor(y / 2) % 4;
  return hex(['#E8D9B0', '#C8282E', '#E8D9B0', '#2E6A9E'][faixa]);
});
posto('rede', { pano: 'arraial:block/rede', corda: 'minecraft:block/hay_block_side', particle: 'arraial:block/rede' }, [
  caixa([0, 9, 4], [4, 10, 12], '#pano'), caixa([4, 7, 4], [12, 8, 12], '#pano'), caixa([12, 9, 4], [16, 10, 12], '#pano'),
  caixa([3, 8, 4], [5, 9, 12], '#pano'), caixa([11, 8, 4], [13, 9, 12], '#pano'),
  caixa([0, 10, 7.5], [1, 16, 8.5], '#corda'), caixa([15, 10, 7.5], [16, 16, 8.5], '#corda')]);
json(path.join(RC, 'rede.json'), shaped('rede', ['S S', 'PPP'], { S: 'minecraft:string', P: 'arraial:pano' }));
tag(path.join(MINEAVEL, 'axe.json'), ['arraial:rede']);
// Cotton spun by hand: two bolls make a string.
json(path.join(RC, 'linha_de_algodao.json'), { type: 'minecraft:crafting_shapeless', category: 'misc',
  ingredients: ['arraial:algodao', 'arraial:algodao'], result: { count: 1, id: 'minecraft:string' } });

console.log('recursos dos oficios gerados');
