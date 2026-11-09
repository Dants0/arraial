// Farmer's Delight integration: three Brazilian dishes cooked in its cooking pot (baião de dois,
// moqueca, vaca atolada), the peixeira as a knife for its cutting board, and the textures/models.
// The recipes only load when Farmer's Delight is installed (fabric:load_conditions).
// Usage: node tools/gerar_farmers.js
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const R = path.join(__dirname, '..', 'src', 'main', 'resources');
const A = path.join(R, 'assets', 'arraial'), D = path.join(R, 'data');

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
function json(arq, obj) { fs.mkdirSync(path.dirname(arq), { recursive: true }); fs.writeFileSync(arq, JSON.stringify(obj, null, 2) + '\n'); }

// ------------------------------------------------------------------ textures: a wooden bowl with the food on top
const TIGELA = { o: '#3A2412', b: '#7A5230', B: '#9A6A3E', l: '#B88450' };
const pratos = {
  // rice (white) with beans-brown flecks, cheese cubes (yellow) and jerky (dark red)
  baiao_de_dois: [[
    '................', '................', '................', '.......g........', '....wwwgwww.....', '...wwcwwwRwww...',
    '..wwRwwywwwcww..', '..owwwcwwRwwyo..', '..oBBBBBBBBBBo..', '..obBlBBBBBBbo..', '...obBBBBBBbo...', '...oobBBBBboo...',
    '.....oooooo.....', '................', '................', '................'],
  { w: '#F2EEE2', c: '#8A5A3A', R: '#7A2A22', y: '#F0D070', g: '#3F8A3A' }],
  // orange-red stew of fish with coconut milk, white fish pieces, green coriander
  moqueca: [[
    '................', '................', '................', '......g..g......', '....rrrgrrgr....', '...rrwwrrOrrr...',
    '..rOrwwrrrrgrr..', '..orrrrOrwwrro..', '..oBBBBBBBBBBo..', '..obBlBBBBBBbo..', '...obBBBBBBbo...', '...oobBBBBboo...',
    '.....oooooo.....', '................', '................', '................'],
  { r: '#D0582A', O: '#E8902E', w: '#F2E6D0', g: '#3F8A3A' }],
  // brown beef stew with pale cassava chunks
  vaca_atolada: [[
    '................', '................', '................', '................', '....mmmmmmm.....', '...mmCmmMmCmm...',
    '..mMmmCCmmmMmm..', '..ommCmmMmmCmo..', '..oBBBBBBBBBBo..', '..obBlBBBBBBbo..', '...obBBBBBBbo...', '...oobBBBBboo...',
    '.....oooooo.....', '................', '................', '................'],
  { m: '#6A3A1E', M: '#8A4A28', C: '#EAD8A8' }],
};
const TI = path.join(A, 'textures', 'item'), MI = path.join(A, 'models', 'item'), IT = path.join(A, 'items');
for (const [id, [linhas, pal]] of Object.entries(pratos)) {
  grade(path.join(TI, id + '.png'), linhas, { ...TIGELA, ...pal });
  json(path.join(MI, id + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: `arraial:item/${id}` } });
  json(path.join(IT, id + '.json'), { model: { type: 'minecraft:model', model: `arraial:item/${id}` } });
}

// ------------------------------------------------------------------ cooking pot recipes (only with Farmer's Delight)
const SO_COM_FD = [{ condition: 'fabric:all_mods_loaded', values: ['farmersdelight'] }];
const panela = (id, ingredients) => ({ 'fabric:load_conditions': SO_COM_FD, type: 'farmersdelight:cooking', recipe_book_tab: 'meals',
  ingredients, result: { id: 'arraial:' + id }, container: { id: 'minecraft:bowl' }, experience: 1.0, cookingtime: 200 });
const RC = path.join(D, 'arraial', 'recipe');
json(path.join(RC, 'baiao_de_dois.json'), panela('baiao_de_dois', ['#c:crops/rice', 'arraial:queijo_coalho', 'arraial:charque', '#c:crops/onion']));
json(path.join(RC, 'moqueca.json'), panela('moqueca', ['#c:foods/safe_raw_fish', '#c:crops/tomato', '#c:crops/onion', 'arraial:farinha']));
json(path.join(RC, 'vaca_atolada.json'), panela('vaca_atolada', ['#c:foods/raw_beef', 'arraial:mandioca', '#c:crops/onion', '#c:crops/tomato']));

// ------------------------------------------------------------------ tags: the peixeira is a knife (cutting board, FD's knife enchantments)
function tag(arq, novos) {
  const atual = fs.existsSync(arq) ? JSON.parse(fs.readFileSync(arq, 'utf8')) : { replace: false, values: [] };
  for (const v of novos) if (!atual.values.includes(v)) atual.values.push(v);
  json(arq, atual);
}
tag(path.join(D, 'c', 'tags', 'item', 'tools', 'knife.json'), ['arraial:peixeira']);
tag(path.join(D, 'c', 'tags', 'item', 'foods', 'soup.json'), ['arraial:moqueca', 'arraial:vaca_atolada']);
console.log('recursos do Farmer\'s Delight gerados');
