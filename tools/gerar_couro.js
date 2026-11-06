// The vaqueiro's leather gear, drawn from scratch (no vanilla textures): the worn layers
// (gibão on body and arms, alpercatas on the feet, perneira on the legs) and the three icons.
//
// Layers use the 64x32 humanoid armour layout (same UVs as the top half of a player skin).
// Usage: node tools/gerar_couro.js src/main/resources/assets/arraial/textures
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const CRC = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(b) { let c = -1; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function chunk(tipo, d) { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(tipo), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([l, td, c]); }
function png(w, h, px) {
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) px.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t);
const luz = (c, f) => (f >= 1 ? mix(c, [255, 244, 214], Math.min(1, (f - 1) * 1.6)) : mix(c.map(v => v * f), [52, 40, 72], (1 - f) * 0.35)).map(clamp);

class Img {
  constructor(w, h) { this.w = w; this.h = h; this.px = Buffer.alloc(w * h * 4); }
  set(x, y, c) { const i = (y * this.w + x) * 4; this.px[i] = c[0]; this.px[i + 1] = c[1]; this.px[i + 2] = c[2]; this.px[i + 3] = 255; }
  salvar(f) { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, png(this.w, this.h, this.px)); }
}

const COURO = hex('#8A5A34'), CLARO = hex('#B07A48'), ESCURO = hex('#5A3820'), PESPONTO = hex('#D9B47A'), LATAO = hex('#C9A646');

// ------------------------------------------------------------------ worn layers
const P = (u, v, w, h, d) => ({ u, v, w, h, d });
const LADOS = ['direita', 'frente', 'esquerda', 'tras'];
const LUZ = { frente: 1.0, direita: 0.86, esquerda: 0.86, tras: 0.78, topo: 1.1, baixo: 0.66 };
function face(p, f) {
  return {
    direita: [p.u, p.v + p.d, p.d, p.h], frente: [p.u + p.d, p.v + p.d, p.w, p.h], esquerda: [p.u + p.d + p.w, p.v + p.d, p.d, p.h],
    tras: [p.u + 2 * p.d + p.w, p.v + p.d, p.w, p.h], topo: [p.u + p.d, p.v, p.w, p.d], baixo: [p.u + p.d + p.w, p.v, p.w, p.d]
  }[f];
}
function pintar(img, p, faces, cor) {
  for (const f of faces) {
    const [fx, fy, fw, fh] = face(p, f);
    for (let x = 0; x < fw; x++) for (let r = 0; r < fh; r++) {
      const c = cor(f, x, r, fw, fh);
      if (c) img.set(fx + x, fy + r, luz(c, LUZ[f] * (f === 'topo' || f === 'baixo' ? 1 : 1.03 - 0.08 * r / (fh - 1))));
    }
  }
}
/** Tooled leather: a lattice of small diamonds pressed into it. */
const lavrado = (x, r) => ((x + r) % 4 === 0 || (x - r + 64) % 4 === 0 ? CLARO : COURO);

const CORPO = P(16, 16, 8, 12, 4), BRACO = P(40, 16, 4, 12, 4), PERNA = P(0, 16, 4, 12, 4);

// humanoid: the gibão (body, arms) and the alpercatas (feet).
const camada = new Img(64, 32);
pintar(camada, CORPO, [...LADOS, 'topo', 'baixo'], (f, x, r) => {
  if (f === 'frente' && (x === 3 || x === 4) && r < 11) return null;           // open at the front
  if (f === 'frente' && (x === 2 || x === 5)) return r % 2 ? PESPONTO : ESCURO;  // stitched edges
  if (r >= 10 && f !== 'topo' && f !== 'baixo') return r === 11 ? ESCURO : COURO; // hem
  if (f === 'topo' || f === 'baixo') return COURO;
  return lavrado(x, r);
});
for (const lado of [0, 1]) {                                                    // both arms share the UVs
  pintar(camada, BRACO, [...LADOS, 'topo'], (f, x, r) => {
    if (r >= 9) return null;
    if (r === 8) return ESCURO;                                                  // cuff
    if (r === 7) return f === 'frente' && x % 2 ? PESPONTO : COURO;
    return f === 'topo' ? COURO : lavrado(x, r);
  });
}
pintar(camada, PERNA, [...LADOS, 'baixo'], (f, x, r) => {                       // alpercatas: sole and straps
  if (f === 'baixo') return ESCURO;
  if (r === 11) return hex('#3E2716');
  if (r === 10) return f === 'frente' && (x === 1 || x === 2) ? CLARO : COURO;
  if (r === 9) return x % 2 ? COURO : null;
  return null;
});
camada.salvar(path.join(process.argv[2], 'entity/equipment/humanoid/vaqueiro.png'));

// humanoid_leggings: the perneira (chaps over the legs, belt at the waist).
const pernas = new Img(64, 32);
pintar(pernas, CORPO, LADOS, (f, x, r) => (r === 9 ? (f === 'frente' && (x === 3 || x === 4) ? LATAO : ESCURO) : r > 9 ? COURO : null));
pintar(pernas, PERNA, [...LADOS, 'topo'], (f, x, r) => {
  if (f === 'topo') return COURO;
  if (r >= 10) return null;
  if (f === 'direita' && x === 1 || f === 'esquerda' && x === 2) return r % 2 ? PESPONTO : ESCURO;  // lacing down the outside
  if (r === 9) return x % 2 ? ESCURO : CLARO;                                    // fringe
  return lavrado(x, r);
});
pernas.salvar(path.join(process.argv[2], 'entity/equipment/humanoid_leggings/vaqueiro.png'));

// ------------------------------------------------------------------ icons (left half, mirrored)
const PAL = { o: hex('#3A2414'), a: hex('#6A4224'), b: hex('#946238'), c: hex('#B9874F'), d: hex('#D9B47A'), s: hex('#E2D6BC'), y: hex('#C9A646'), f: hex('#7A5030') };
function icone(nome, metades) {
  const img = new Img(16, 16);
  metades.forEach((m, y) => {
    const linha = m.padEnd(8, '.') + [...m.padEnd(8, '.')].reverse().join('');
    [...linha].forEach((ch, x) => { if (ch !== '.') img.set(x, y, PAL[ch]); });
  });
  img.salvar(path.join(process.argv[2], 'item', nome + '.png'));
}
icone('gibao', [
  '........',
  '..oooo..',
  '.obccbo.',
  'obccbbos',
  'obcdbbbs',
  'obbbbdbs',
  'oaobdbbs',
  'oaobbbbs',
  '.o.obdbs',
  '...obbbs',
  '...obdbs',
  '...obbbs',
  '...oaaao',
  '...ooooo',
  '........',
  '........']);
icone('perneira', [
  '........',
  '..oooooo',
  '..oaaaay',
  '..obbbbo',
  '..obcbo.',
  '..obdbo.',
  '..obcbo.',
  '..obdbo.',
  '..obcbo.',
  '..obdbo.',
  '.ofbcbo.',
  '.ofbbbo.',
  '.ofofof.',
  '........',
  '........',
  '........']);
icone('alpercatas', [
  '........',
  '........',
  '........',
  '........',
  '........',
  '........',
  '........',
  '....oo..',
  '...ocdo.',
  '..ocoobo',
  '.obbcbbo',
  'oaaaaaao',
  '.oooooo.',
  '........',
  '........',
  '........']);
console.log('couro do vaqueiro gerado');
