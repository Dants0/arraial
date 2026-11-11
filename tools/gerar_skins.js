// Generates the 64x64 morador skins (classic wide-arm player layout, both layers).
//
// Style: pixel art with soft shading. Every face of a part has its own light (front, sides,
// back, top, bottom) and clothes darken a little towards the bottom; shadows lean to cool purple
// and lights to warm yellow; fabric has a sparse weave; folds, collars, buttons, belts with a
// buckle, cuffs and soles are drawn in. Each trade is told apart by its silhouette and one or two
// signature pieces (straw hat, leather gibão, apron, cross belts, check shirt, bandoleer...).
//
// Files: <oficio>_<m|f>_<v>.png, oficio = "morador" (no trade) or the trade id, v = 0..5 is the
// person (skin tone, hair, eyes, face, everyday colours): changing trade keeps the face.
//
// Usage: node tools/gerar_skins.js <output dir> [preview.png]
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');

// ------------------------------------------------------------------ PNG
const CRC = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(b) { let c = -1; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function chunk(tipo, dados) {
  const len = Buffer.alloc(4); len.writeUInt32BE(dados.length);
  const td = Buffer.concat([Buffer.from(tipo), dados]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, rgba) {
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

// ------------------------------------------------------------------ colour
const hex = h => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t);
/** Light (f > 1, towards warm) or shade (f < 1, towards cool purple), like a painter would. */
function luz(c, f) {
  if (f >= 1) return mix(c, [255, 244, 214], Math.min(1, (f - 1) * 1.6)).map(clamp);
  const escuro = c.map(v => v * f);
  return mix(escuro, [52, 40, 72], (1 - f) * 0.35).map(clamp);
}
const C = h => (typeof h === 'string' ? hex(h) : h);
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 982451653) >>> 0; h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }

// ------------------------------------------------------------------ skin and UV layout
class Skin {
  constructor() { this.px = Buffer.alloc(64 * 64 * 4); }
  set(x, y, c, a = 255) { const i = (y * 64 + x) * 4; this.px[i] = c[0]; this.px[i + 1] = c[1]; this.px[i + 2] = c[2]; this.px[i + 3] = a; }
  get(x, y) { const i = (y * 64 + x) * 4; return [this.px[i], this.px[i + 1], this.px[i + 2], this.px[i + 3]]; }
}
const P = (u, v, w, h, d) => ({ u, v, w, h, d });
const CABECA = P(0, 0, 8, 8, 8), CHAPEU = P(32, 0, 8, 8, 8);
const CORPO = P(16, 16, 8, 12, 4), JAQUETA = P(16, 32, 8, 12, 4);
const BRACO_D = P(40, 16, 4, 12, 4), MANGA_D = P(40, 32, 4, 12, 4);
const BRACO_E = P(32, 48, 4, 12, 4), MANGA_E = P(48, 48, 4, 12, 4);
const PERNA_D = P(0, 16, 4, 12, 4), CALCA_D = P(0, 32, 4, 12, 4);
const PERNA_E = P(16, 48, 4, 12, 4), CALCA_E = P(0, 48, 4, 12, 4);
const LADOS = ['direita', 'frente', 'esquerda', 'tras'];
const LUZ = { frente: 1.0, direita: 0.86, esquerda: 0.86, tras: 0.78, topo: 1.1, baixo: 0.66 };

/** Rectangle of a face in the texture: x, y, width, height. */
function face(p, f) {
  switch (f) {
    case 'direita': return [p.u, p.v + p.d, p.d, p.h];
    case 'frente': return [p.u + p.d, p.v + p.d, p.w, p.h];
    case 'esquerda': return [p.u + p.d + p.w, p.v + p.d, p.d, p.h];
    case 'tras': return [p.u + p.d + p.w + p.d, p.v + p.d, p.w, p.h];
    case 'topo': return [p.u + p.d, p.v, p.w, p.d];
    case 'baixo': return [p.u + p.d + p.w, p.v, p.w, p.d];
  }
}
/**
 * Paints a part: cor(face, x, r) gives a colour (or null to skip) for column x and row r of that
 * face; light per face and a slight darkening downwards are applied unless plano is set.
 */
function pintar(s, p, faces, cor, opts = {}) {
  for (const f of faces) {
    const [fx, fy, fw, fh] = face(p, f);
    for (let x = 0; x < fw; x++) for (let r = 0; r < fh; r++) {
      let c = cor(f, x, r, fw, fh);
      if (!c) continue;
      c = C(c);
      let k = LUZ[f];
      if (opts.gradiente && (f !== 'topo' && f !== 'baixo')) k *= 1.04 - 0.1 * r / Math.max(1, fh - 1);
      s.set(fx + x, fy + r, luz(c, k));
    }
  }
}
/** Rows [r0, r1) of the side faces in one colour (with the fabric weave when tecido). */
function faixa(s, p, r0, r1, c, faces = LADOS, tecido = 0, semente = 0) {
  pintar(s, p, faces, (f, x, r) => {
    if (r < r0 || r >= r1) return null;
    if (tecido && hash(x + p.u * 3, r + p.v * 5, semente + LADOS.indexOf(f)) < tecido) return luz(C(c), 0.9);
    return c;
  }, { gradiente: true });
}
function tampa(s, p, c, faces = ['topo', 'baixo']) { pintar(s, p, faces, () => c); }
/** One pixel on a face, with that face's light. */
function px(s, p, f, x, r, c, k = 1) { const [fx, fy] = face(p, f); s.set(fx + x, fy + r, luz(C(c), LUZ[f] * k)); }

// ------------------------------------------------------------------ the people (v = 0..5)
const PELES = ['#F1CBA6', '#DDA77D', '#C08458', '#9C6640', '#77492D', '#553220'];
const PESSOAS = [
  // cabelo, olhos, homem: barba/cabelo, mulher: penteado, roupa do dia a dia (camisa, calça / blusa, saia)
  { pele: 0, cabelo: '#5A3A22', olhos: '#4E7A9A', barba: 'bigode', corte: 'curto', pent: 'coque', camisa: '#4F6582', calca: '#5A4A38', blusa: '#E9DFC8', saia: '#4F6582' },
  { pele: 1, cabelo: '#2B1C13', olhos: '#5A3A22', barba: 'cheia', corte: 'curto', pent: 'longo', camisa: '#9A5A3C', calca: '#3F4652', blusa: '#D9B25A', saia: '#7A3B3B' },
  { pele: 2, cabelo: '#1A1310', olhos: '#3B2A1E', barba: 'nenhuma', corte: 'lados', pent: 'trancas', camisa: '#5E6B45', calca: '#6B5A44', blusa: '#F0E8D8', saia: '#5E6B45' },
  { pele: 3, cabelo: '#231812', olhos: '#2E2018', barba: 'cavanhaque', corte: 'crespo', pent: 'cacheado', camisa: '#B8923F', calca: '#4A3E33', blusa: '#C66A3C', saia: '#3F4F6A' },
  { pele: 4, cabelo: '#141010', olhos: '#2A1E16', barba: 'rala', corte: 'crespo', pent: 'lenco', camisa: '#7A3B3B', calca: '#3A3A40', blusa: '#E8D9B0', saia: '#8A3F2A' },
  { pele: 5, cabelo: '#B9B3AA', olhos: '#3B2A1E', barba: 'cheia', corte: 'careca', pent: 'coque', camisa: '#D8CDB4', calca: '#4A4A42', blusa: '#5A5A6E', saia: '#2F2F38', idoso: true }
];
const COURO = '#7A5236', COURO_ESC = '#523726', COURO_CLARO = '#9C6E48', SAPATO = '#3A2A1E', PALHA = '#D6B86E', CRU = '#E2D6BC', LATAO = '#C9A646';

// ------------------------------------------------------------------ head
function cabeca(s, o) {
  const pele = C(PELES[o.p.pele]), cab = C(o.p.cabelo), cabEsc = luz(cab, 0.7), cabClaro = luz(cab, 1.12);
  const fio = (x, r, sem) => (hash(x, r, sem) < 0.22 ? cabClaro : hash(x, r, sem + 9) < 0.15 ? cabEsc : cab);
  // Skin, with a cooler chin and a warm cheek.
  pintar(s, CABECA, [...LADOS, 'topo', 'baixo'], (f, x, r) => (r >= 7 && f !== 'topo' ? luz(pele, 0.92) : pele));
  // Hair on the head itself.
  const curto = !o.fem && o.p.corte !== 'careca';
  const altura = o.fem ? 8 : o.p.corte === 'careca' ? 0 : o.p.corte === 'crespo' ? 3 : 2;
  pintar(s, CABECA, ['topo'], (f, x, r) => (o.p.corte === 'careca' && !o.fem ? (x === 0 || x === 7 || r === 7 ? fio(x, r, 3) : null) : fio(x, r, 1)));
  pintar(s, CABECA, ['tras'], (f, x, r) => (o.fem || r < (o.p.corte === 'careca' ? 6 : 7) && (o.p.corte !== 'careca' || r >= 3) ? fio(x, r, 2) : null));
  pintar(s, CABECA, ['direita', 'esquerda'], (f, x, r) => {
    if (o.fem) return (f === 'direita' ? x < 6 : x > 1) || r < 3 ? fio(x, r, 4) : null;
    if (o.p.corte === 'careca') return r >= 3 && r < 6 && (f === 'direita' ? x < 3 : x > 4) ? fio(x, r, 4) : null;
    const nuca = f === 'direita' ? x < 3 : x > 4;
    return r < altura + (o.p.corte === 'lados' ? 0 : 1) || nuca && r < 6 || r < 4 && (f === 'direita' ? x < 2 : x > 5) ? fio(x, r, 4) : null;
  });
  // Front: fringe.
  pintar(s, CABECA, ['frente'], (f, x, r) => {
    if (o.fem) {
      if (r === 0 || r === 1 && (x < 3 || x > 4)) return fio(x, r, 5);
      return (x === 0 || x === 7) && r < 8 ? fio(x, r, 6) : null;
    }
    if (o.p.corte === 'careca') return null;
    if (r === 0) return fio(x, r, 5);
    if (r === 1 && (o.p.corte === 'crespo' || x < 2 || x > 5)) return fio(x, r, 5);
    if (r === 2 && (x === 0 || x === 7) && curto) return fio(x, r, 6);
    return null;
  });
  // Face.
  const olho = C(o.p.olhos), branco = [236, 230, 220], sobrancelha = o.p.idoso ? luz(cab, 0.8) : cabEsc;
  const fr = (x, r, c) => px(s, CABECA, 'frente', x, r, c);
  fr(1, 3, sobrancelha); fr(2, 3, sobrancelha); fr(5, 3, sobrancelha); fr(6, 3, sobrancelha);
  fr(1, 4, branco); fr(2, 4, olho); fr(5, 4, olho); fr(6, 4, branco);
  if (o.fem) { fr(1, 3, luz(sobrancelha, 1.05)); fr(6, 3, luz(sobrancelha, 1.05)); fr(2, 3, [40, 28, 24]); fr(5, 3, [40, 28, 24]); }
  fr(3, 5, luz(pele, 1.06)); fr(4, 5, luz(pele, 0.82));                // nose: lit side and shadow
  fr(1, 5, mix(pele, [210, 110, 100], o.fem ? 0.3 : 0.12)); fr(6, 5, mix(pele, [210, 110, 100], o.fem ? 0.3 : 0.12)); // cheeks
  const boca = o.fem ? mix(pele, [160, 60, 60], 0.55) : mix(pele, [110, 50, 40], 0.45);
  fr(3, 6, boca); fr(4, 6, boca);
  if (o.p.idoso) { fr(2, 2, luz(pele, 0.9)); fr(5, 2, luz(pele, 0.9)); fr(1, 6, luz(pele, 0.9)); fr(6, 6, luz(pele, 0.9)); }
  if (!o.fem) {
    const b = o.barba;
    const bc = o.p.idoso ? C('#D8D2C8') : cab;
    if (b === 'bigode' || b === 'cavanhaque' || b === 'cheia') { fr(2, 6, bc); fr(5, 6, bc); fr(3, 6, luz(bc, 0.85)); fr(4, 6, luz(bc, 0.85)); fr(3, 7, boca); fr(4, 7, boca); }
    if (b === 'cavanhaque') { fr(3, 7, bc); fr(4, 7, bc); }
    if (b === 'cheia') {
      for (let x = 0; x < 8; x++) fr(x, 7, x === 3 || x === 4 ? luz(bc, 0.9) : bc);
      fr(0, 5, bc); fr(7, 5, bc); fr(0, 6, bc); fr(7, 6, bc); fr(1, 6, bc); fr(6, 6, bc);
      for (const f of ['direita', 'esquerda']) for (let r = 5; r < 8; r++) for (let x = 0; x < 8; x++)
        if ((f === 'direita' ? x >= 5 : x <= 2) || r === 7) px(s, CABECA, f, x, r, bc);
    }
    if (b === 'rala') for (let x = 1; x < 7; x++) if (x !== 3 && x !== 4) fr(x, 7, mix(pele, cab, 0.45));
  }
}

/** Women's hairstyles and the extra volume, on the hat layer (and the back of the jacket for long hair). */
function penteado(s, o) {
  const cab = C(o.p.cabelo), claro = luz(cab, 1.14), esc = luz(cab, 0.72);
  const fio = (x, r, sem) => (hash(x, r, sem) < 0.25 ? claro : hash(x, r, sem + 7) < 0.18 ? esc : cab);
  switch (o.p.pent) {
    case 'coque':                       // bun at the back of the head
      for (let x = 2; x < 6; x++) for (let r = 1; r < 5; r++) if (!((x === 2 || x === 5) && (r === 1 || r === 4))) px(s, CHAPEU, 'tras', x, r, fio(x, r, 11));
      pintar(s, CHAPEU, ['topo'], (f, x, r) => (r === 7 && x > 1 && x < 6 ? fio(x, r, 12) : null));
      break;
    case 'longo':                       // straight hair down the back
      pintar(s, CHAPEU, ['tras'], (f, x, r) => fio(x, r, 13));
      pintar(s, CHAPEU, ['direita', 'esquerda'], (f, x, r) => ((f === 'direita' ? x < 4 : x > 3) && r > 2 ? fio(x, r, 14) : null));
      pintar(s, JAQUETA, ['tras'], (f, x, r) => (r < 5 + (x % 3 === 0 ? 1 : 0) && x > 0 && x < 7 ? fio(x, r, 15) : null));
      break;
    case 'trancas':                     // two braids over the shoulders
      for (const [p, xs] of [[JAQUETA, [1, 6]]]) for (const x of xs) for (let r = 0; r < 6; r++) px(s, p, 'frente', x, r, r % 2 ? esc : fio(x, r, 16));
      for (const x of [1, 6]) px(s, JAQUETA, 'frente', x, 6, '#B03A3A');   // ribbons
      pintar(s, CHAPEU, ['tras'], (f, x, r) => (r > 4 && (x === 1 || x === 6) ? fio(x, r, 17) : null));
      break;
    case 'cacheado':                    // curly volume all round
      pintar(s, CHAPEU, ['topo'], (f, x, r) => (hash(x, r, 18) < 0.85 ? fio(x, r, 18) : null));
      pintar(s, CHAPEU, ['tras'], (f, x, r) => (r < 7 + (hash(x, 0, 19) < 0.5 ? 1 : 0) ? fio(x, r, 19) : null));
      pintar(s, CHAPEU, ['direita', 'esquerda'], (f, x, r) => (r < 6 && hash(x, r, 20) < 0.9 ? fio(x, r, 20) : null));
      pintar(s, CHAPEU, ['frente'], (f, x, r) => (r === 0 || r === 1 && (x < 2 || x > 5) || (x === 0 || x === 7) && r < 5 ? fio(x, r, 21) : null));
      break;
    case 'lenco':
      lenco(s, '#C0503A', '#E8D9B0');
      break;
  }
}

// ------------------------------------------------------------------ hats (hat layer)
function chapeuPalha(s, fita = '#7A3B2A') {
  const trama = (x, r) => (((x + r) % 2) ? luz(C(PALHA), 0.9) : C(PALHA));
  pintar(s, CHAPEU, ['topo'], (f, x, r) => trama(x, r));
  pintar(s, CHAPEU, LADOS, (f, x, r) => (r < 2 ? trama(x, r) : r === 2 ? fita : r === 3 ? luz(C(PALHA), 0.85) : null));
}
function chapeuCouro(s, barbicacho = true) {           // the sertanejo leather hat, half-moon front
  pintar(s, CHAPEU, ['topo'], (f, x, r) => (x === 0 || x === 7 || r === 0 || r === 7 ? COURO_ESC : r % 3 === 1 && x % 3 === 1 ? LATAO : COURO));
  pintar(s, CHAPEU, LADOS, (f, x, r) => (r < 2 ? (f === 'frente' && x > 1 && x < 6 && r === 0 ? COURO_CLARO : COURO) : r === 2 ? COURO_ESC
    : r === 3 && f === 'frente' ? COURO_ESC : null));
  if (barbicacho) for (const f of ['direita', 'esquerda']) for (let r = 3; r < 8; r++) px(s, CHAPEU, f, f === 'direita' ? 4 : 3, r, COURO_ESC);
}
function lenco(s, cor, pinta = null) {                 // head scarf tied at the back
  pintar(s, CHAPEU, ['topo'], (f, x, r) => (pinta && (x + r * 3) % 4 === 0 ? pinta : cor));
  pintar(s, CHAPEU, LADOS, (f, x, r) => (r < 2 || f !== 'frente' && r < 3 ? (pinta && (x * 3 + r) % 4 === 0 ? pinta : cor) : null));
  for (const [x, r] of [[3, 3], [4, 3], [4, 4], [3, 5]]) px(s, CHAPEU, 'tras', x, r, luz(C(cor), 0.85));
}
function gorro(s, cor) {                               // a wool cap with a folded rim
  pintar(s, CHAPEU, ['topo'], (f, x, r) => ((x + r) % 3 === 0 ? luz(C(cor), 0.9) : cor));
  pintar(s, CHAPEU, LADOS, (f, x, r) => (r < 2 ? cor : r === 2 ? luz(C(cor), 0.75) : null));
}
function chapeuArmado(s) {                             // militia cocked hat, black with a cockade
  pintar(s, CHAPEU, ['topo'], () => '#1E1D22');
  pintar(s, CHAPEU, LADOS, (f, x, r) => (r < 2 ? '#26252B' : r === 2 ? '#C9A646' : null));
  px(s, CHAPEU, 'frente', 1, 1, '#B23A3A'); px(s, CHAPEU, 'frente', 1, 0, '#E8E2D2');
}
function cartola(s) {                                  // a low gentleman's hat with a band
  pintar(s, CHAPEU, ['topo'], () => '#25242A');
  pintar(s, CHAPEU, LADOS, (f, x, r) => (r < 1 ? '#25242A' : r === 1 ? '#6A2E3A' : r === 2 ? '#1A191E' : null));
}

// ------------------------------------------------------------------ clothes
/** Shirt or blouse on the body and sleeves: sleeve length, collar, buttons, rolled cuffs. */
function camisa(s, o, cor, { manga = 6, gola = 'v', botoes = true, arregacada = false, punho = null, xadrez = null, listras = null } = {}) {
  const base = C(cor), sem = o.v * 7;
  const padrao = (x, r) => {
    if (xadrez) { const a = Math.floor(x / 2) % 2, b = Math.floor(r / 2) % 2; return a && b ? luz(C(xadrez), 0.85) : a || b ? mix(base, C(xadrez), 0.5) : base; }
    if (listras) return r % 2 === 0 ? C(listras) : base;
    return base;
  };
  faixa(s, CORPO, 0, 12, cor, LADOS, xadrez || listras ? 0 : 0.08, sem);
  if (xadrez || listras) pintar(s, CORPO, LADOS, (f, x, r) => padrao(x, r), { gradiente: true });
  tampa(s, CORPO, base);
  // Neck opening and collar.
  const pele = PELES[o.p.pele];
  if (gola === 'v') { px(s, CORPO, 'frente', 3, 0, pele); px(s, CORPO, 'frente', 4, 0, pele); px(s, CORPO, 'frente', 3, 1, luz(C(pele), 0.9)); px(s, CORPO, 'frente', 2, 0, luz(base, 1.12)); px(s, CORPO, 'frente', 5, 0, luz(base, 1.12)); }
  if (gola === 'redonda') { px(s, CORPO, 'frente', 3, 0, pele); px(s, CORPO, 'frente', 4, 0, pele); }
  if (gola === 'gravata') { px(s, CORPO, 'frente', 3, 0, '#F2EEE4'); px(s, CORPO, 'frente', 4, 0, '#F2EEE4'); px(s, CORPO, 'frente', 3, 1, '#E6E0D2'); px(s, CORPO, 'frente', 4, 1, '#E6E0D2'); }
  if (botoes) for (let r = 2; r < 8; r += 2) px(s, CORPO, 'frente', 4, r, luz(base, 0.7));
  // Fold shadows at the sides of the waist.
  px(s, CORPO, 'frente', 0, 7, luz(base, 0.85)); px(s, CORPO, 'frente', 7, 7, luz(base, 0.85));
  // Arms: sleeve then skin, a darker hand at the end.
  const peleC = C(pele);
  for (const b of [BRACO_D, BRACO_E]) {
    pintar(s, b, [...LADOS, 'topo', 'baixo'], (f, x, r) => {
      if (f === 'topo') return padrao(x, r);
      if (f === 'baixo') return luz(peleC, 0.9);
      if (r < manga) return (xadrez || listras) ? padrao(x, r) : (hash(x + b.u, r, sem + 3) < 0.08 ? luz(base, 0.9) : base);
      if (r === manga && arregacada) return luz(base, 1.1);
      return r >= 10 ? luz(peleC, 0.93) : peleC;
    }, { gradiente: false });
    if (punho && manga < 12) faixa(s, b, manga - 1, manga, punho);
    if (manga >= 4) for (const f of ['frente', 'tras']) px(s, b, f, 1, 5, luz(base, 0.82));   // elbow crease
  }
}
/** Trousers with a belt and buckle, knee fold, shoes with a sole; rolled up or barefoot. */
function calca(s, o, cor, { cinto = COURO_ESC, fivela = LATAO, sapato = SAPATO, arregacada = false, descalco = false, bota = false } = {}) {
  const base = C(cor), pele = C(PELES[o.p.pele]);
  faixa(s, CORPO, 9, 12, cor, LADOS, 0.06, o.v);
  faixa(s, CORPO, 8, 9, cinto);
  if (fivela) { px(s, CORPO, 'frente', 3, 8, fivela); px(s, CORPO, 'frente', 4, 8, luz(C(fivela), 0.8)); }
  for (const p of [PERNA_D, PERNA_E]) {
    pintar(s, p, [...LADOS, 'topo', 'baixo'], (f, x, r) => {
      if (f === 'topo') return base;
      if (f === 'baixo') return descalco ? luz(pele, 0.8) : luz(C(sapato), 0.8);
      const fimCalca = arregacada ? 8 : bota ? 7 : 10;
      if (r < fimCalca) return hash(x + p.u, r + p.v, o.v + 21) < 0.07 ? luz(base, 0.9) : r === 5 && f === 'frente' ? luz(base, 0.9) : base;
      if (r === fimCalca && arregacada) return luz(base, 1.12);
      if (descalco) return r === 11 ? luz(pele, 0.88) : pele;
      if (bota) return r === 7 ? luz(C(sapato), 1.15) : r === 11 ? luz(C(sapato), 0.75) : C(sapato);
      if (r < 10) return pele;
      return r === 11 ? luz(C(sapato), 0.7) : f === 'frente' ? luz(C(sapato), 1.12) : C(sapato);
    }, { gradiente: false });
  }
}
/** A blouse and a full skirt (the skirt also on the leg overlay, so it stands out from the legs). */
function vestido(s, o, blusa, saia, { manga = 6, avental = null, sapato = SAPATO, descalca = false, gola = 'redonda' } = {}) {
  camisa(s, o, blusa, { manga, gola, botoes: false });
  const base = C(saia), pele = C(PELES[o.p.pele]);
  pintar(s, CORPO, LADOS, (f, x, r) => (r >= 7 ? (r === 7 ? luz(base, 0.82) : hash(x, r, o.v + 31) < 0.08 ? luz(base, 0.9) : base) : null), { gradiente: true });
  pintar(s, CORPO, ['baixo'], () => base);
  for (const [p, cap] of [[PERNA_D, CALCA_D], [PERNA_E, CALCA_E]]) {
    pintar(s, p, [...LADOS, 'topo', 'baixo'], (f, x, r) => {
      if (f === 'topo') return base;
      if (f === 'baixo') return descalca ? luz(pele, 0.8) : luz(C(sapato), 0.8);
      if (r < 8) return base;
      if (r < 10) return pele;
      return descalca ? pele : r === 11 ? luz(C(sapato), 0.7) : C(sapato);
    });
    // Skirt overlay: folds every other column, a darker hem.
    pintar(s, cap, LADOS, (f, x, r) => (r < 8 ? (r === 7 ? luz(base, 0.75) : x % 2 ? luz(base, 0.9) : base) : null), { gradiente: true });
  }
  pintar(s, JAQUETA, LADOS, (f, x, r) => (r >= 8 ? (x % 2 ? luz(base, 0.9) : base) : null), { gradiente: true });
  if (avental) aventalFrente(s, avental, 9, false);
}
/** Apron on the jacket layer (chest down) and on the legs; pocket. */
function aventalFrente(s, cor, ate = 6, peito = true) {
  const base = C(cor);
  for (let x = 1; x < 7; x++) for (let r = peito ? 2 : 8; r < 12; r++) px(s, JAQUETA, 'frente', x, r, x === 1 || x === 6 ? luz(base, 0.9) : base);
  if (peito) for (const x of [1, 6]) { px(s, JAQUETA, 'frente', x, 0, base); px(s, JAQUETA, 'frente', x, 1, base); }
  px(s, JAQUETA, 'frente', 3, 9, luz(base, 0.8)); px(s, JAQUETA, 'frente', 4, 9, luz(base, 0.8));   // pocket
  for (const c of [CALCA_D, CALCA_E]) for (let x = 0; x < 4; x++) for (let r = 0; r < ate; r++) px(s, c, 'frente', x, r, r === ate - 1 ? luz(base, 0.85) : base);
}
function suspensorio(s, cor = COURO_ESC) {
  for (const f of ['frente', 'tras']) for (let r = 0; r < 8; r++) { px(s, CORPO, f, 1, r, cor); px(s, CORPO, f, 6, r, cor); }
  px(s, CORPO, 'frente', 1, 7, LATAO); px(s, CORPO, 'frente', 6, 7, LATAO);
}
function gibao(s) {                                    // the vaqueiro's tooled leather jacket
  pintar(s, JAQUETA, LADOS, (f, x, r) => {
    if (r >= 10) return null;
    if (f === 'frente' && (x === 3 || x === 4)) return null;                // open at the front
    if (r === 9) return COURO_ESC;
    return (x + r) % 4 === 0 ? COURO_CLARO : COURO;                         // tooling
  }, { gradiente: true });
  pintar(s, JAQUETA, ['topo'], () => COURO);
  for (const m of [MANGA_D, MANGA_E]) pintar(s, m, LADOS, (f, x, r) => (r < 9 ? (r === 8 ? COURO_ESC : (x + r) % 4 === 0 ? COURO_CLARO : COURO) : null), { gradiente: true });
}
function perneira(s) {                                 // leather chaps
  for (const c of [CALCA_D, CALCA_E]) pintar(s, c, LADOS, (f, x, r) => (r < 10 ? (r % 3 === 0 && x === 1 ? COURO_CLARO : COURO_ESC) : null), { gradiente: true });
}
function talabarte(s, cor = '#5A3A22', bolsas = true) {   // diagonal bandoleer with pouches
  for (const f of ['frente', 'tras']) for (let r = 0; r < 8; r++) px(s, JAQUETA, f, f === 'frente' ? 7 - r : r, r, cor);
  if (bolsas) { px(s, JAQUETA, 'frente', 5, 3, '#8A6B45'); px(s, JAQUETA, 'frente', 3, 5, '#8A6B45'); }
}
function farda(s, o) {                                 // militia coat: gold buttons, red facings, white cross belts
  for (let r = 1; r < 8; r += 2) { px(s, CORPO, 'frente', 2, r, LATAO); px(s, CORPO, 'frente', 5, r, LATAO); }
  for (let r = 0; r < 8; r++) { px(s, JAQUETA, 'frente', r, r, '#ECE6D6'); px(s, JAQUETA, 'frente', 7 - r, r, '#ECE6D6'); }
  px(s, JAQUETA, 'frente', 3, 3, LATAO); px(s, JAQUETA, 'frente', 4, 4, LATAO);
  for (const f of ['frente', 'tras']) for (let r = 0; r < 8; r++) { px(s, JAQUETA, f, r, r, '#ECE6D6'); px(s, JAQUETA, f, 7 - r, r, '#ECE6D6'); }
  // Collar and coat tails.
  px(s, CORPO, 'frente', 2, 0, '#9A2E2E'); px(s, CORPO, 'frente', 5, 0, '#9A2E2E');
  pintar(s, JAQUETA, ['tras'], (f, x, r) => (r >= 9 && (x < 3 || x > 4) ? '#2F4468' : null));
}
function colete(s, cor, botoes = LATAO) {              // a waistcoat over the shirt
  pintar(s, JAQUETA, LADOS, (f, x, r) => (r < 8 && !(f === 'frente' && (x === 3 || x === 4) && r < 3) ? (f === 'frente' && x === 3 && r >= 3 ? luz(C(cor), 0.8) : cor) : null), { gradiente: true });
  for (let r = 3; r < 8; r += 2) px(s, JAQUETA, 'frente', 4, r, botoes);
}
function casaca(s, cor, debrum) {                      // long coat with lapels and trims
  pintar(s, JAQUETA, LADOS, (f, x, r) => {
    if (f === 'frente' && (x === 3 || x === 4) && r < 8) return null;
    if (f === 'frente' && (x === 2 || x === 5) && r < 4) return debrum;      // lapels
    return cor;
  }, { gradiente: true });
  for (const m of [MANGA_D, MANGA_E]) pintar(s, m, LADOS, (f, x, r) => (r < 10 ? (r === 9 ? debrum : cor) : null), { gradiente: true });
}
function pala(s, cor, listra) {                        // the tropeiro's poncho over the shoulders
  pintar(s, JAQUETA, LADOS, (f, x, r) => (r < 6 + (x % 2) ? (r === 2 || r === 4 ? listra : (r === 5 + (x % 2) ? luz(C(cor), 0.8) : cor)) : null), { gradiente: true });
  pintar(s, JAQUETA, ['topo'], () => cor);
  for (const m of [MANGA_D, MANGA_E]) pintar(s, m, LADOS, (f, x, r) => (r < 4 ? (r === 2 ? listra : cor) : null), { gradiente: true });
}
/** Dirt, dust or flour: blended into what is already painted, thicker towards the bottom (base layer only). */
function sujeira(s, cor, partes, de, densidade = 0.5, semente = 41) {
  const c = C(cor);
  for (const p of partes) for (const f of LADOS) {
    const [fx, fy, fw, fh] = face(p, f);
    for (let x = 0; x < fw; x++) for (let r = de; r < fh; r++) {
      const atual = s.get(fx + x, fy + r);
      if (atual[3] === 0) continue;
      const t = densidade * (r - de + 1) / (fh - de);
      const n = hash(Math.floor((x + p.u) / 2), Math.floor((r + p.v) / 2), semente);
      if (n < t) s.set(fx + x, fy + r, mix(atual, c, 0.25 + 0.3 * (t - n)).map(clamp));
    }
  }
}
function bolsaCostas(s, cor) {                         // the peddler's pack on the back
  pintar(s, JAQUETA, ['tras'], (f, x, r) => (r > 1 && r < 10 && x > 0 && x < 7 ? (r === 2 || x === 1 || x === 6 ? luz(C(cor), 0.8) : cor) : null));
  for (let r = 0; r < 8; r++) px(s, JAQUETA, 'frente', 7 - r, r, '#5A3A22');
}
function oculos(s) {                                   // little round spectacles
  for (const x of [1, 2, 5, 6]) px(s, CHAPEU, 'frente', x, 4, x === 2 || x === 5 ? '#9AB6C8' : '#3A3A40');
  px(s, CHAPEU, 'frente', 3, 4, '#3A3A40'); px(s, CHAPEU, 'frente', 4, 4, '#3A3A40');
}
function lapis(s) { px(s, CHAPEU, 'direita', 5, 3, '#E0B23A'); px(s, CHAPEU, 'direita', 6, 3, '#3A3A40'); }

// ------------------------------------------------------------------ one skin
function skin(oficio, fem, v) {
  const s = new Skin();
  const p = PESSOAS[v];
  const o = { fem, v, p, barba: fem ? null : oficio === 'lenhador' ? 'cheia' : oficio === 'tropeiro' ? 'bigode' : p.barba };
  cabeca(s, o);
  if (fem) penteado(s, o);   // before the trade's hat, which sits on top of it
  const saia = fem;
  // Everyday clothes, then each trade's.
  switch (oficio) {
    case 'morador':
      if (fem) vestido(s, o, p.blusa, p.saia, { manga: 5 });
      else { camisa(s, o, p.camisa, { manga: 5 + (v % 3) }); calca(s, o, p.calca, { descalco: v === 4, arregacada: v === 4 }); }
      break;
    case 'pedreiro':
      camisa(s, o, CRU, { manga: 4, arregacada: true }); calca(s, o, '#8C877C', {});
      aventalFrente(s, COURO, 4); sujeira(s, '#C9C2B4', [PERNA_D, PERNA_E], 4, 0.7);
      px(s, JAQUETA, 'frente', 2, 9, '#6B6B70'); px(s, JAQUETA, 'frente', 2, 10, '#8A6B45');   // trowel in the pocket
      fem ? lenco(s, '#B8923F') : gorro(s, '#B8923F');
      break;
    case 'lenhador':
      camisa(s, o, '#9A3A2E', { manga: 9, xadrez: '#2A1E1E', gola: 'redonda', botoes: false }); calca(s, o, '#3F4652', { bota: true });
      suspensorio(s); gorro(s, '#5A2A2A');
      break;
    case 'roceiro':
      if (saia) vestido(s, o, CRU, '#6B5A44', { manga: 5, descalca: true });
      else { camisa(s, o, CRU, { manga: 5, arregacada: true }); calca(s, o, '#6B5A44', { arregacada: true, descalco: true }); }
      sujeira(s, '#6B4E34', [PERNA_D, PERNA_E], 8, 0.5);
      chapeuPalha(s);
      break;
    case 'oleiro':
      camisa(s, o, CRU, { manga: 3, gola: 'redonda', botoes: false }); calca(s, o, '#5A4A38', {});
      aventalFrente(s, '#A55A38', 6); sujeira(s, '#A8562F', [BRACO_D, BRACO_E], 7, 1.0);
      lenco(s, '#9A5A3C');
      break;
    case 'soldado':
      camisa(s, o, '#2F4468', { manga: 11, punho: '#9A2E2E', gola: 'gravata', botoes: false }); calca(s, o, '#E2DCCB', { bota: true, sapato: '#1E1A17', cinto: '#ECE6D6' });
      farda(s, o); chapeuArmado(s);
      break;
    case 'pastor':
      camisa(s, o, CRU, { manga: 9 }); calca(s, o, '#5A4A38', { bota: true, sapato: '#4A3222' });
      gibao(s); perneira(s); chapeuCouro(s);
      break;
    case 'barreiro':
      camisa(s, o, '#9A7B5A', { manga: 2, gola: 'redonda', botoes: false }); calca(s, o, '#5A4A38', { arregacada: true, descalco: true });
      sujeira(s, '#6B4E34', [PERNA_D, PERNA_E], 3, 1.0); sujeira(s, '#7A5A3E', [BRACO_D, BRACO_E], 7, 0.9, 44);
      lenco(s, '#D2C6AA');
      break;
    case 'cavouqueiro':
      camisa(s, o, '#7D7A72', { manga: 7 }); calca(s, o, '#45423D', { bota: true, sapato: '#2E2B28' });
      sujeira(s, '#C4C0B8', [CORPO, PERNA_D, PERNA_E, BRACO_D, BRACO_E], 0, 0.45, 51);
      for (const c of [CALCA_D, CALCA_E]) for (let x = 0; x < 4; x++) px(s, c, 'frente', x, 5, COURO_ESC);   // knee pads
      gorro(s, '#5B5A55');
      break;
    case 'farinheiro':
      if (saia) vestido(s, o, '#F0EADC', '#D8CDB4', { manga: 4, avental: '#FBF8F0' });
      else { camisa(s, o, '#F0EADC', { manga: 4 }); calca(s, o, '#7A6A55', {}); aventalFrente(s, '#FBF8F0', 6); }
      sujeira(s, '#FFFFFF', [CORPO, BRACO_D, BRACO_E], 2, 0.5, 61);
      lenco(s, '#F4EFE2');
      break;
    case 'feirante':
      if (saia) vestido(s, o, '#E8D9B0', '#B0563A', { manga: 5, avental: '#4F8A5A' });
      else { camisa(s, o, '#E8D9B0', { manga: 6, listras: '#B0563A' }); calca(s, o, '#4A4F3A', {}); }
      px(s, JAQUETA, 'frente', 6, 8, '#8A6B45'); px(s, JAQUETA, 'frente', 6, 9, LATAO);   // money pouch
      chapeuPalha(s, '#2F7A5A');
      break;
    case 'tropeiro':
      camisa(s, o, '#8A6B45', { manga: 9 }); calca(s, o, '#4A3B2C', { bota: true, sapato: '#3A2A1C' });
      pala(s, '#8A3A2A', '#E2C27A'); perneira(s); chapeuCouro(s);
      break;
    case 'boticario':
      camisa(s, o, '#ECE6D8', { manga: 10, punho: '#2F2F35', gola: 'gravata', botoes: false });
      if (saia) vestido(s, o, '#ECE6D8', '#2F2F35', { manga: 10, gola: 'gravata' }); else calca(s, o, '#2F2F35', {});
      colete(s, '#3B4A3A'); oculos(s);
      px(s, JAQUETA, 'frente', 1, 7, '#5A8A6A'); px(s, JAQUETA, 'frente', 1, 6, '#C9A646');   // a phial in the pocket
      break;
    case 'pescador':
      camisa(s, o, '#E8E4D8', { manga: 4, listras: '#3F5F8A', gola: 'redonda', botoes: false });
      if (saia) vestido(s, o, '#E8E4D8', '#3F5F8A', { manga: 4, descalca: true, gola: 'redonda' }); else calca(s, o, '#6B5A44', { arregacada: true, descalco: true });
      chapeuPalha(s, '#3F5F8A');
      break;
    case 'cacador':
      camisa(s, o, '#5A6B3A', { manga: 9 }); calca(s, o, '#4A3B2C', { bota: true });
      talabarte(s); perneira(s); chapeuCouro(s, false);
      break;
    case 'engenheiro':
      camisa(s, o, '#F0ECE2', { manga: 11, gola: 'gravata', botoes: false });
      if (saia) vestido(s, o, '#F0ECE2', '#2F3A55', { manga: 11, gola: 'gravata' }); else calca(s, o, '#3A3F4A', { sapato: '#1E1A17' });
      casaca(s, '#2F3A55', '#5A6A8A'); cartola(s); lapis(s);
      break;
    case 'relacoes':
      camisa(s, o, '#F2EEE4', { manga: 11, gola: 'gravata', botoes: false });
      if (saia) vestido(s, o, '#F2EEE4', '#6A2E3A', { manga: 11, gola: 'gravata' }); else calca(s, o, '#2A2A30', { sapato: '#1E1A17' });
      casaca(s, '#6A2E3A', LATAO);
      if (!fem) cartola(s);
      break;
    case 'mestre_engenho':
      camisa(s, o, CRU, { manga: 4, arregacada: true, gola: 'redonda', botoes: false });
      if (saia) vestido(s, o, CRU, '#7A5A3A', { manga: 4, avental: '#8A6B45' }); else { calca(s, o, '#6B5A44', { arregacada: true }); aventalFrente(s, '#8A6B45', 5); }
      sujeira(s, '#E8DCC0', [BRACO_D, BRACO_E], 8, 0.8, 71);
      chapeuPalha(s, '#8A3F2A');
      break;
    case 'cafeicultor':
      camisa(s, o, '#F0EADC', { manga: 7 });
      if (saia) vestido(s, o, '#F0EADC', '#5E6B45', { manga: 7 }); else calca(s, o, '#5A4A38', {});
      talabarte(s, '#7A5A3A', false);
      for (const [x, r] of [[2, 5], [3, 5], [2, 6], [3, 6]]) px(s, JAQUETA, 'frente', x, r, x === 2 && r === 5 ? '#B03A2A' : '#8A6B45');   // picking bag
      chapeuPalha(s, '#3F7A3A');
      break;
    case 'entalhador':
      camisa(s, o, '#E8E0D0', { manga: 6, arregacada: true });
      if (saia) vestido(s, o, '#E8E0D0', '#3A3040', { manga: 6, avental: COURO }); else { calca(s, o, '#3A3040', {}); aventalFrente(s, COURO, 6); }
      colete(s, '#2E2A36');
      sujeira(s, '#E0B040', [JAQUETA], 6, 0.35, 81);
      gorro(s, '#6A2E3A');
      break;
    case 'barqueiro':
      camisa(s, o, '#D9CFB8', { manga: 4, arregacada: true, gola: 'redonda', botoes: false });
      if (saia) vestido(s, o, '#D9CFB8', '#3F5A6A', { manga: 4 }); else calca(s, o, '#3F5A6A', { arregacada: true });
      talabarte(s, '#7A5A3A', false);
      chapeuPalha(s, '#2F5A7A');
      break;
    case 'estivador':
      camisa(s, o, '#E6E0CE', { manga: 2, arregacada: true, gola: 'redonda', botoes: false });
      if (saia) vestido(s, o, '#E6E0CE', '#6A5038', { manga: 2, descalca: true }); else calca(s, o, '#6A5038', { arregacada: true, descalco: true });
      suspensorio(s, '#4A3A2A');
      gorro(s, '#9A2E2A');
      break;
    case 'mascate':
      camisa(s, o, '#C9862A', { manga: 8, punho: '#7A2E5A' });
      if (saia) vestido(s, o, '#C9862A', '#2F6A5A', { manga: 8 }); else calca(s, o, '#3F5A4A', { bota: true });
      colete(s, '#7A2E5A', '#E2C27A'); bolsaCostas(s, '#8A6B45'); chapeuPalha(s, '#7A2E5A');
      break;
  }
  return s;
}

// ------------------------------------------------------------------ preview (front and back)
function previa(skins, cols, escala, arquivo) {
  const cw = 34 * escala, ch = 34 * escala, linhas = Math.ceil(skins.length / cols);
  const W = cols * cw, H = linhas * ch, out = Buffer.alloc(W * H * 4);
  for (let i = 0; i < W * H; i++) { out[i * 4] = 118; out[i * 4 + 1] = 146; out[i * 4 + 2] = 108; out[i * 4 + 3] = 255; }
  const peca = (s, sx, sy, w, h, dx, dy, ox, oy) => {
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const c = s.get(sx + x, sy + y); if (c[3] === 0) continue;
      for (let a = 0; a < escala; a++) for (let b = 0; b < escala; b++) {
        const X = (ox + dx + x) * escala + b, Y = (oy + dy + y) * escala + a, k = (Y * W + X) * 4;
        out[k] = c[0]; out[k + 1] = c[1]; out[k + 2] = c[2];
      }
    }
  };
  skins.forEach((s, i) => {
    const ox = (i % cols) * 34 + 1, oy = Math.floor(i / cols) * 34 + 1;
    for (const ov of [0, 1]) {   // front
      if (!ov) { peca(s, 8, 8, 8, 8, 4, 0, ox, oy); peca(s, 20, 20, 8, 12, 4, 8, ox, oy); peca(s, 44, 20, 4, 12, 0, 8, ox, oy); peca(s, 36, 52, 4, 12, 12, 8, ox, oy); peca(s, 4, 20, 4, 12, 4, 20, ox, oy); peca(s, 20, 52, 4, 12, 8, 20, ox, oy); }
      else { peca(s, 40, 8, 8, 8, 4, 0, ox, oy); peca(s, 20, 36, 8, 12, 4, 8, ox, oy); peca(s, 44, 36, 4, 12, 0, 8, ox, oy); peca(s, 52, 52, 4, 12, 12, 8, ox, oy); peca(s, 4, 36, 4, 12, 4, 20, ox, oy); peca(s, 4, 52, 4, 12, 8, 20, ox, oy); }
    }
    const bx = ox + 17;
    for (const ov of [0, 1]) {   // back
      if (!ov) { peca(s, 24, 8, 8, 8, 4, 0, bx, oy); peca(s, 32, 20, 8, 12, 4, 8, bx, oy); peca(s, 52, 20, 4, 12, 12, 8, bx, oy); peca(s, 44, 52, 4, 12, 0, 8, bx, oy); peca(s, 12, 20, 4, 12, 8, 20, bx, oy); peca(s, 28, 52, 4, 12, 4, 20, bx, oy); }
      else { peca(s, 56, 8, 8, 8, 4, 0, bx, oy); peca(s, 32, 36, 8, 12, 4, 8, bx, oy); peca(s, 52, 36, 4, 12, 12, 8, bx, oy); peca(s, 60, 52, 4, 12, 0, 8, bx, oy); peca(s, 12, 36, 4, 12, 8, 20, bx, oy); peca(s, 12, 52, 4, 12, 4, 20, bx, oy); }
    }
  });
  fs.writeFileSync(arquivo, png(W, H, out));
}

// ------------------------------------------------------------------ main
const OFICIOS = ['morador', 'pedreiro', 'lenhador', 'roceiro', 'oleiro', 'soldado', 'pastor', 'barreiro', 'cavouqueiro', 'farinheiro', 'feirante',
  'tropeiro', 'boticario', 'pescador', 'cacador', 'engenheiro', 'relacoes', 'mascate', 'mestre_engenho', 'cafeicultor', 'entalhador', 'barqueiro', 'estivador'];
const saida = process.argv[2];
fs.mkdirSync(saida, { recursive: true });
let n = 0;
const todas = {};
for (const of of OFICIOS) for (const fem of [false, true]) for (let v = 0; v < PESSOAS.length; v++) {
  const s = skin(of, fem, v);
  fs.writeFileSync(path.join(saida, `${of}_${fem ? 'f' : 'm'}_${v}.png`), png(64, 64, s.px));
  todas[`${of}_${fem ? 'f' : 'm'}_${v}`] = s;
  n++;
}
console.log('skins geradas:', n);
if (process.argv[3]) {
  const base = process.argv[3].replace(/\.png$/, '');
  previa(OFICIOS.map(o => todas[o + '_m_0']), 6, 5, base + '_oficios_m.png');
  previa(OFICIOS.map(o => todas[o + '_f_1']), 6, 5, base + '_oficios_f.png');
  previa([...Array(6).keys()].flatMap(v => [todas['morador_m_' + v], todas['morador_f_' + v]]), 6, 5, base + '_pessoas.png');
}
