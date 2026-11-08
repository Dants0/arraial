// Resources of the fazendas, the Baroque and the Spanish missions: textures (16x16 pixel art),
// block and item models, blockstates, loot tables, recipes and tags.
// Usage: node tools/gerar_fazendas_barroco.js
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const R = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(R, 'assets', 'arraial'), D = path.join(R, 'data');

// ------------------------------------------------------------------ PNG and colour
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
const mix = (a, b, t) => [0, 1, 2].map(i => Math.round(a[i] + (b[i] - a[i]) * t));
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
/** Pixel art from a grid of characters and a palette ('.' transparent). */
function grade(arq, linhas, pal) {
  textura(arq, (x, y) => { const ch = (linhas[y] || '')[x]; return ch && ch !== '.' ? hex(pal[ch]) : null; });
}
const TB = path.join(A, 'textures', 'block'), TI = path.join(A, 'textures', 'item');

// ------------------------------------------------------------------ block textures
// Adobe: sun-dried bricks of mud and straw, warm ochre, with straw flecks and mortar lines.
textura(path.join(TB, 'adobe.png'), (x, y) => {
  const linha = Math.floor(y / 4), dx = (x + (linha % 2 ? 4 : 0)) % 8;
  if (y % 4 === 3 || dx === 7) return hex('#8C5E3A');
  const n = hash(x, y, 3);
  if (n < 0.08) return hex('#D8B56A');                       // straw
  return mix(hex('#B9784A'), hex('#C98C58'), hash(x >> 1, y >> 1, 5));
});
// Pedra-sabão: grey-green soapstone with soft veins.
textura(path.join(TB, 'pedra_sabao.png'), (x, y) => {
  const veia = Math.abs(Math.sin((x + y * 0.6) * 0.7 + hash(0, y, 7) * 2)) < 0.12;
  const base = mix(hex('#6F7A6C'), hex('#8A9585'), hash(x >> 1, y >> 1, 9));
  return veia ? mix(base, hex('#C4CBBE'), 0.5) : (hash(x, y, 11) < 0.07 ? mix(base, hex('#4E574C'), 0.6) : base);
});
// Talha dourada: gilded carving, gold scrolls on a dark wood ground.
const TALHA = [
  'aabbbbbaabbbbbaa',
  'abbccbbaabbccbba',
  'bbcddcbbbbcddcbb',
  'bcdeedcbbcdeedcb',
  'bcdeedcbbcdeedcb',
  'bbcddcbaabcddcbb',
  'abbccbbaabbccbba',
  'aabbbbaffabbbbaa',
  'aabbbbaffabbbbaa',
  'abbccbbaabbccbba',
  'bbcddcbbbbcddcbb',
  'bcdeedcbbcdeedcb',
  'bcdeedcbbcdeedcb',
  'bbcddcbaabcddcbb',
  'abbccbbaabbccbba',
  'aabbbbbaabbbbbaa'];
grade(path.join(TB, 'talha_dourada.png'), TALHA, { a: '#5A3A1C', b: '#B8862A', c: '#D9A93A', d: '#F2CF5C', e: '#FFF0A8', f: '#7A2A2A' });
// Terreiro de café (top): brick floor with coffee spread out to dry (red cherries, brown beans).
textura(path.join(TB, 'terreiro_topo.png'), (x, y) => {
  const n = hash(x, y, 21);
  if (n < 0.22) return hex('#B03A2A');
  if (n < 0.45) return hex('#5A3420');
  if (n < 0.55) return hex('#7A4A2A');
  return (x % 8 === 0 || y % 4 === 0) ? hex('#8A5A40') : hex('#A86A48');
});
// Cafeeiro: four stages (seedling, young bush, flowering, ripe red cherries).
function cafeeiro(estagio) {
  return (x, y) => {
    const altura = [6, 11, 15, 15][estagio];
    if (y < 16 - altura) return null;
    const meio = Math.abs(x - 7.5);
    const largura = [2, 5, 7, 7][estagio] * (0.6 + 0.4 * (y - (16 - altura)) / altura);
    if (meio > largura + 0.5) return null;
    if (y >= 14 && meio < 1) return hex('#5A3A20');           // stem
    const n = hash(x, y, 31 + estagio);
    if (n < 0.25) return null;                                 // gaps between leaves
    if (estagio === 2 && n > 0.9) return hex('#F4F1E6');       // flowers
    if (estagio === 3 && n > 0.78) return n > 0.9 ? hex('#C8202A') : hex('#E0503A');   // cherries
    return n < 0.5 ? hex('#2E6A2E') : hex('#3F8A3A');
  };
}
for (let e = 0; e < 4; e++) textura(path.join(TB, `cafeeiro_estagio${e}.png`), cafeeiro(e));

// ------------------------------------------------------------------ item textures
grade(path.join(TI, 'cafe_cereja.png'), [
  '................', '................', '.......gg.......', '......gG........', '.....g..........', '....rr...rr.....',
  '...rRRr.rRRr....', '...rRwr.rRwr....', '...rrrr.rrrr....', '....rr...rr.....', '.......rr.......', '......rRRr......',
  '......rRwr......', '......rrrr......', '.......rr.......', '................'],
  { g: '#2E6A2E', G: '#3F8A3A', r: '#9A1A22', R: '#D0303A', w: '#F08A80' });
grade(path.join(TI, 'cafe_torrado.png'), [
  '................', '................', '....bb....bb....', '...bBBb..bBBb...', '...bBlb..bBlb...', '...bBlb..bBlb...',
  '...bbbb..bbbb...', '................', '.....bb....bb...', '....bBBb..bBBb..', '....bBlb..bBlb..', '....bBlb..bBlb..',
  '....bbbb..bbbb..', '................', '................', '................'],
  { b: '#2E1A10', B: '#5A3420', l: '#1A0E08' });
grade(path.join(TI, 'cafezinho.png'), [
  '................', '......s.s.......', '.......s.s......', '......s.s.......', '................', '...oooooooooo...',
  '..oCCCCCCCCCCo..', '..oCccccccccCo..', '..ocCCCCCCCCco..', '...ocCCCCCCco...', '...occCCCCcco...', '....occcccco....',
  '.....oooooo.....', '................', '................', '................'],
  { s: '#D8D2C8', o: '#4A2E16', C: '#8A5A30', c: '#6A4222' });
grade(path.join(TI, 'queijo_coalho.png'), [
  '................', '................', '.........ss.....', '........sYYs....', '.......sYyYs....', '......sYyYYs....',
  '.....sYYYyYs....', '....sYyYYYYs....', '...sYYYYyYs.....', '..sYYyYYYs......', '..soYYYYs.......', '...soYYs........',
  '....sos.........', '.....s..........', '................', '................'],
  { s: '#6A5A3A', Y: '#F2E2A8', y: '#E0C878', o: '#C08A4A' });
grade(path.join(TI, 'charque.png'), [
  '................', '................', '...ww...........', '..wRRw..........', '..wRrRw.........', '...wRrRw........',
  '....wRrRww......', '.....wRRrRw.....', '......wRrrRw....', '.......wRRrRw...', '........wRrRw...', '.........wRRw...',
  '..........ww....', '................', '................', '................'],
  { w: '#E8DED0', R: '#7A2A22', r: '#5A1A16' });

// ------------------------------------------------------------------ models, blockstates, items
function json(arq, obj) { fs.mkdirSync(path.dirname(arq), { recursive: true }); fs.writeFileSync(arq, JSON.stringify(obj, null, 2) + '\n'); }
const BS = path.join(A, 'blockstates'), MB = path.join(A, 'models', 'block'), MI = path.join(A, 'models', 'item'), IT = path.join(A, 'items');
for (const b of ['adobe', 'pedra_sabao', 'talha_dourada']) {
  json(path.join(BS, b + '.json'), { variants: { '': { model: `arraial:block/${b}` } } });
  json(path.join(MB, b + '.json'), { parent: 'minecraft:block/cube_all', textures: { all: `arraial:block/${b}` } });
  json(path.join(IT, b + '.json'), { model: { type: 'minecraft:model', model: `arraial:block/${b}` } });
}
// Cafeeiro (age 0..3), a cross plant; its item is the cherry.
const variantes = {};
for (let e = 0; e < 4; e++) {
  variantes[`age=${e}`] = { model: `arraial:block/cafeeiro_estagio${e}` };
  json(path.join(MB, `cafeeiro_estagio${e}.json`), { parent: 'minecraft:block/cross', textures: { cross: `arraial:block/cafeeiro_estagio${e}` }, render_type: 'cutout' });
}
json(path.join(BS, 'cafeeiro.json'), { variants: variantes });
// A box element with the same texture variable on every face.
function caixa(de, ate, tex, faces = {}) {
  const f = {};
  for (const lado of ['north', 'south', 'east', 'west', 'up', 'down']) f[lado] = { texture: faces[lado] || tex };
  return { from: de, to: ate, faces: f };
}
const giratorio = nome => ({ variants: {
  'facing=north': { model: `arraial:block/${nome}` }, 'facing=east': { model: `arraial:block/${nome}`, y: 90 },
  'facing=south': { model: `arraial:block/${nome}`, y: 180 }, 'facing=west': { model: `arraial:block/${nome}`, y: 270 } } });
// Moenda: three upright wooden rollers on a stone base, a beam on top.
json(path.join(MB, 'moenda.json'), { parent: 'minecraft:block/block',
  textures: { pedra: 'minecraft:block/smooth_stone', tora: 'minecraft:block/stripped_oak_log', topo: 'minecraft:block/stripped_oak_log_top', tabua: 'minecraft:block/oak_planks', particle: 'minecraft:block/oak_planks' },
  elements: [caixa([0, 0, 0], [16, 3, 16], '#pedra'),
    caixa([2, 3, 6], [6, 13, 10], '#tora', { up: '#topo', down: '#topo' }), caixa([6, 3, 6], [10, 13, 10], '#tora', { up: '#topo', down: '#topo' }),
    caixa([10, 3, 6], [14, 13, 10], '#tora', { up: '#topo', down: '#topo' }), caixa([0, 13, 5], [16, 16, 11], '#tabua')] });
json(path.join(BS, 'moenda.json'), giratorio('moenda'));
// Terreiro: a low brick platform with coffee drying on top.
json(path.join(MB, 'terreiro.json'), { parent: 'minecraft:block/block',
  textures: { topo: 'arraial:block/terreiro_topo', lado: 'minecraft:block/bricks', particle: 'minecraft:block/bricks' },
  elements: [caixa([0, 0, 0], [16, 4, 16], '#lado', { up: '#topo' })] });
json(path.join(BS, 'terreiro.json'), { variants: { '': { model: 'arraial:block/terreiro' } } });
// Bancada de entalhe: a workbench with a gilded carving on it.
json(path.join(MB, 'bancada_entalhe.json'), { parent: 'minecraft:block/block',
  textures: { tabua: 'minecraft:block/spruce_planks', talha: 'arraial:block/talha_dourada', particle: 'minecraft:block/spruce_planks' },
  elements: [caixa([1, 0, 1], [3, 12, 3], '#tabua'), caixa([13, 0, 1], [15, 12, 3], '#tabua'), caixa([1, 0, 13], [3, 12, 15], '#tabua'),
    caixa([13, 0, 13], [15, 12, 15], '#tabua'), caixa([0, 12, 0], [16, 14, 16], '#tabua'), caixa([4, 14, 4], [10, 20, 8], '#talha')] });
json(path.join(BS, 'bancada_entalhe.json'), giratorio('bancada_entalhe'));
for (const b of ['moenda', 'terreiro', 'bancada_entalhe']) json(path.join(IT, b + '.json'), { model: { type: 'minecraft:model', model: `arraial:block/${b}` } });
for (const i of ['cafe_cereja', 'cafe_torrado', 'cafezinho', 'queijo_coalho', 'charque']) {
  json(path.join(MI, i + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: `arraial:item/${i}` } });
  json(path.join(IT, i + '.json'), { model: { type: 'minecraft:model', model: `arraial:item/${i}` } });
}

// ------------------------------------------------------------------ loot tables
const LT = path.join(D, 'arraial', 'loot_table', 'blocks');
for (const b of ['adobe', 'pedra_sabao', 'talha_dourada', 'moenda', 'terreiro', 'bancada_entalhe']) {
  json(path.join(LT, b + '.json'), { type: 'minecraft:block', pools: [{ rolls: 1, condition: { type: 'minecraft:survives_explosion' },
    entries: [{ type: 'minecraft:item', name: `arraial:${b}` }] }], random_sequence: `arraial:blocks/${b}` });
}
// The bush gives back a cherry; ripe, a few more.
json(path.join(LT, 'cafeeiro.json'), { type: 'minecraft:block', pools: [
  { rolls: 1, entries: [{ type: 'minecraft:item', name: 'arraial:cafe_cereja' }] },
  { rolls: 1, conditions: [{ condition: 'minecraft:block_state_property', block: 'arraial:cafeeiro', properties: { age: '3' } }],
    entries: [{ type: 'minecraft:item', name: 'arraial:cafe_cereja', functions: [{ function: 'minecraft:set_count', count: 2 }] }] }],
  random_sequence: 'arraial:blocks/cafeeiro' });

// ------------------------------------------------------------------ recipes
const RC = path.join(D, 'arraial', 'recipe');
const shaped = (res, n, pattern, key, cat = 'building') => ({ type: 'minecraft:crafting_shaped', category: cat, key, pattern, result: { count: n, id: 'arraial:' + res } });
const shapeless = (res, n, ingredients, cat = 'misc') => ({ type: 'minecraft:crafting_shapeless', category: cat, ingredients, result: { count: n, id: 'arraial:' + res } });
json(path.join(RC, 'adobe.json'), shapeless('adobe', 2, ['minecraft:mud', 'minecraft:mud', 'minecraft:wheat'], 'building'));
json(path.join(RC, 'pedra_sabao.json'), shapeless('pedra_sabao', 4, ['minecraft:stone', 'minecraft:stone', 'minecraft:stone', 'minecraft:stone', 'minecraft:clay_ball'], 'building'));
json(path.join(RC, 'talha_dourada.json'), shapeless('talha_dourada', 2, ['#minecraft:planks', '#minecraft:planks', 'minecraft:gold_nugget'], 'building'));
json(path.join(RC, 'moenda.json'), shaped('moenda', 1, ['LGL', 'SSS'], { L: '#minecraft:logs', G: 'minecraft:grindstone', S: 'minecraft:smooth_stone' }, 'misc'));
json(path.join(RC, 'terreiro.json'), shaped('terreiro', 1, ['BBB'], { B: 'minecraft:brick' }, 'misc'));
json(path.join(RC, 'bancada_entalhe.json'), shaped('bancada_entalhe', 1, ['GI', 'PP'], { G: 'minecraft:gold_nugget', I: 'minecraft:iron_ingot', P: '#minecraft:planks' }, 'misc'));
json(path.join(RC, 'cafezinho.json'), shapeless('cafezinho', 1, ['arraial:cuia', 'arraial:cafe_torrado', 'minecraft:sugar']));
json(path.join(RC, 'cafe_torrado.json'), { type: 'minecraft:smelting', category: 'food', ingredient: 'arraial:cafe_cereja', result: { id: 'arraial:cafe_torrado' }, experience: 0.2, cookingtime: 200 });
json(path.join(RC, 'charque.json'), { type: 'minecraft:campfire_cooking', category: 'food', ingredient: 'minecraft:beef', result: { id: 'arraial:charque' }, experience: 0.2, cookingtime: 1200 });

// ------------------------------------------------------------------ tags (tools that mine them)
function tag(arq, novos) {
  const atual = fs.existsSync(arq) ? JSON.parse(fs.readFileSync(arq, 'utf8')) : { values: [] };
  for (const v of novos) if (!atual.values.includes(v)) atual.values.push(v);
  json(arq, atual);
}
const MIN = path.join(D, 'minecraft', 'tags', 'block', 'mineable');
tag(path.join(MIN, 'pickaxe.json'), ['arraial:pedra_sabao', 'arraial:terreiro']);
tag(path.join(MIN, 'axe.json'), ['arraial:moenda', 'arraial:bancada_entalhe', 'arraial:talha_dourada']);
tag(path.join(MIN, 'shovel.json'), ['arraial:adobe']);
console.log('recursos gerados');
