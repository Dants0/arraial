// The Brazilian fauna: one description per animal (parts, boxes, how each box is painted) gives
// both the Java model layers (src/client/.../client/fauna/ModelosFauna.java) and the textures
// (assets/arraial/textures/entity/fauna/*.png), plus the spawn egg icons.
//
// Model space as in Minecraft: y grows downwards, the ground is y = 24, the animal faces -z.
// Texture space is packed automatically (classic cube UV: top/bottom strip, then the four sides).
// Usage: node tools/gerar_fauna.js
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const RAIZ = path.join(__dirname, '..');
const TEX = path.join(RAIZ, 'src/main/resources/assets/arraial/textures/entity/fauna');
const JAVA = path.join(RAIZ, 'src/client/java/com/guilhermegoes/arraial/client/fauna/ModelosFauna.java');

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
const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => [0, 1, 2].map(i => a[i] + (b[i] - a[i]) * t);
const C = c => (typeof c === 'string' ? hex(c) : c);
function luz(c, f) {
  c = C(c);
  if (f >= 1) return mix(c, [255, 244, 214], Math.min(1, (f - 1) * 1.6)).map(clamp);
  return mix(c.map(v => v * f), [52, 40, 72], (1 - f) * 0.3).map(clamp);
}
function hash(x, y, s) { let h = (x * 374761393 + y * 668265263 + s * 982451653) >>> 0; h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0; return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
const LUZ = { frente: 1.0, direita: 0.88, esquerda: 0.88, tras: 0.82, topo: 1.08, baixo: 0.72 };

// ------------------------------------------------------------------ a model: parts and boxes
// parte: { nome, pai, pivo: [x,y,z], rot: [x,y,z], caixas: [{ de: [x,y,z], tam: [w,h,d], mat, espelho }] }
// mat(face, x, y, w, h, caixa) -> colour (face: frente/tras/direita/esquerda/topo/baixo; x,y in the face).
function empacotar(modelo) {
  // Shelf packing of the cube UV footprints: width 2(w+d), height d+h.
  const caixas = [];
  for (const p of modelo.partes) for (const c of p.caixas) caixas.push(c);
  caixas.sort((a, b) => (b.tam[2] + b.tam[1]) - (a.tam[2] + a.tam[1]));
  let x = 0, y = 0, linha = 0;
  for (const c of caixas) {
    const w = Math.ceil(2 * (c.tam[0] + c.tam[2])), h = Math.ceil(c.tam[2] + c.tam[1]);
    if (x + w > modelo.largura) { x = 0; y += linha; linha = 0; }
    c.uv = [x, y];
    x += w;
    linha = Math.max(linha, h);
  }
  if (y + linha > modelo.altura) throw new Error(modelo.nome + ': textura pequena demais (' + (y + linha) + ')');
}
function faces(c) {
  const [u, v] = c.uv, [w, h, d] = c.tam.map(Math.ceil);
  return {
    topo: [u + d, v, w, d], baixo: [u + d + w, v, w, d],
    direita: [u, v + d, d, h], frente: [u + d, v + d, w, h], esquerda: [u + d + w, v + d, d, h], tras: [u + d + w + d, v + d, w, h]
  };
}
function pintar(modelo) {
  const W = modelo.largura, H = modelo.altura, px = Buffer.alloc(W * H * 4);
  for (const p of modelo.partes) for (const c of p.caixas) {
    for (const [face, [fx, fy, fw, fh]] of Object.entries(faces(c))) {
      for (let x = 0; x < fw; x++) for (let y = 0; y < fh; y++) {
        const cor = c.mat(face, x, y, fw, fh, c);
        if (!cor) continue;
        const k = ((fy + y) * W + fx + x) * 4, f = luz(cor, LUZ[face]);
        px[k] = f[0]; px[k + 1] = f[1]; px[k + 2] = f[2]; px[k + 3] = 255;
      }
    }
  }
  fs.mkdirSync(TEX, { recursive: true });
  fs.writeFileSync(path.join(TEX, modelo.textura + '.png'), png(W, H, px));
}
const n = v => (Number.isInteger(v) ? v.toFixed(1) : String(v)) + 'F';
function java(modelo) {
  let s = `\tpublic static LayerDefinition ${modelo.nome}() {\n\t\tMeshDefinition mesh = new MeshDefinition();\n\t\tPartDefinition root = mesh.getRoot();\n`;
  for (const p of modelo.partes) {
    const pai = p.pai ? 'p_' + p.pai : 'root';
    let cubos = 'CubeListBuilder.create()';
    for (const c of p.caixas) {
      cubos += `.texOffs(${c.uv[0]}, ${c.uv[1]})${c.espelho ? '.mirror()' : ''}.addBox(${c.de.map(n).join(', ')}, ${c.tam.map(n).join(', ')})${c.espelho ? '.mirror(false)' : ''}`;
    }
    const r = p.rot || [0, 0, 0];
    s += `\t\tPartDefinition p_${p.nome} = ${pai}.addOrReplaceChild("${p.nome}", ${cubos},\n\t\t\t\tPartPose.offsetAndRotation(${p.pivo.map(n).join(', ')}, ${r.map(n).join(', ')}));\n`;
  }
  s += `\t\treturn LayerDefinition.create(mesh, ${modelo.largura}, ${modelo.altura});\n\t}\n`;
  return s;
}

// ------------------------------------------------------------------ materials
/** Fur: a base colour with grain, lighter on the belly side of the box. */
function pelo(base, claro, escuro, semente, extra) {
  return (face, x, y, w, h, c) => {
    if (extra) { const e = extra(face, x, y, w, h, c); if (e) return e; }
    const r = hash(x + c.uv[0] * 3, y + c.uv[1] * 7, semente);
    let cor = C(base);
    if (r < 0.18) cor = C(escuro); else if (r > 0.86) cor = C(claro);
    return cor;
  };
}
const liso = cor => () => C(cor);
/** Eyes on the side faces of a head box, near the front. */
function olhos(cor, linha, coluna = 1) {
  return (face, x, y, w) => {
    if (face === 'direita' && x === coluna && y === linha) return C(cor);
    if (face === 'esquerda' && x === w - 1 - coluna && y === linha) return C(cor);
    return null;
  };
}
const junta = (...fs) => (...a) => { for (const f of fs) { const r = f && f(...a); if (r) return r; } return null; };

// ------------------------------------------------------------------ the animals
const MODELOS = [];

// Capivara: a big, blunt, reddish-brown rodent, no tail to speak of.
{
  const corpo = pelo('#8C5A36', '#A8724A', '#6E4428', 1, (face, x, y, w, h) => (face === 'baixo' ? C('#A07A55') : null));
  const cabeca = pelo('#84542F', '#9E6A44', '#64401F', 2, olhos('#1A1210', 2, 4));
  const focinho = pelo('#5E3E26', '#6E4A2E', '#4A2E1C', 3, (face, x, y, w) => (face === 'frente' && y === 1 && (x === 1 || x === w - 2) ? C('#1A1210') : null));
  MODELOS.push({ nome: 'capivara', textura: 'capivara', largura: 64, altura: 64, partes: [
    { nome: 'body', pivo: [0, 13, 0], caixas: [{ de: [-5, 0, -9], tam: [10, 8, 18], mat: corpo }] },
    { nome: 'head', pivo: [0, 14, -9], caixas: [
      { de: [-3.5, -4, -7], tam: [7, 7, 7], mat: cabeca },
      { de: [-3, -1, -10], tam: [6, 5, 3], mat: focinho },
      { de: [-4, -5, -2], tam: [2, 1, 1], mat: liso('#4A2E1C') },
      { de: [2, -5, -2], tam: [2, 1, 1], mat: liso('#4A2E1C') }] },
    { nome: 'right_hind_leg', pivo: [-3.5, 20, 6], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#6E4428', '#7E5232', '#4A2E1C', 4) }] },
    { nome: 'left_hind_leg', pivo: [3.5, 20, 6], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#6E4428', '#7E5232', '#4A2E1C', 5) }] },
    { nome: 'right_front_leg', pivo: [-3.5, 20, -5], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#6E4428', '#7E5232', '#4A2E1C', 6) }] },
    { nome: 'left_front_leg', pivo: [3.5, 20, -5], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#6E4428', '#7E5232', '#4A2E1C', 7) }] }] });
}

// Tamanduá-bandeira: grey body, the black diagonal band edged in white, long snout, a huge flag tail.
{
  const faixa = (face, x, y, w, h) => {
    if (face !== 'direita' && face !== 'esquerda' && face !== 'topo') return null;
    const t = face === 'topo' ? y : x;           // across the band, from the shoulder backwards
    const d = Math.abs(t + (face === 'topo' ? 0 : y * 0.6) - (face === 'topo' ? 5 : 6));
    if (d < 1.2) return C('#151515');
    if (d < 2.2) return C('#E8E2D2');
    return null;
  };
  const corpo = pelo('#7A7468', '#948E80', '#5E5A50', 11, faixa);
  MODELOS.push({ nome: 'tamandua', textura: 'tamandua', largura: 64, altura: 64, partes: [
    { nome: 'body', pivo: [0, 12, 0], caixas: [{ de: [-4, 0, -8], tam: [8, 8, 16], mat: corpo }] },
    { nome: 'head', pivo: [0, 15, -8], caixas: [
      { de: [-2, -2, -5], tam: [4, 4, 5], mat: pelo('#8A8476', '#9E988A', '#6E6A60', 12, olhos('#101010', 1, 3)) },
      { de: [-1, -0.5, -13], tam: [2, 2, 8], mat: pelo('#6E6A60', '#7E7A70', '#4E4A42', 13, (f, x, y, w, h) => (f === 'frente' ? C('#202020') : null)) },
      { de: [-2.5, -3, -1], tam: [1, 1, 1], mat: liso('#4E4A42') },
      { de: [1.5, -3, -1], tam: [1, 1, 1], mat: liso('#4E4A42') }] },
    { nome: 'cauda', pai: 'body', pivo: [0, 1, 8], rot: [0.25, 0, 0], caixas: [{ de: [-1, -2, 0], tam: [2, 10, 11], mat: pelo('#4A4640', '#625E56', '#2E2C28', 14) }] },
    { nome: 'right_hind_leg', pivo: [-3, 20, 6], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#3A3834', '#4A4842', '#222220', 15) }] },
    { nome: 'left_hind_leg', pivo: [3, 20, 6], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#3A3834', '#4A4842', '#222220', 16) }] },
    { nome: 'right_front_leg', pivo: [-3, 20, -6], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#E2DCCC', '#F0EADA', '#BEB8A8', 17, (f, x, y) => (y >= 3 ? C('#202020') : null)) }] },
    { nome: 'left_front_leg', pivo: [3, 20, -6], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 4, 3], mat: pelo('#E2DCCC', '#F0EADA', '#BEB8A8', 18, (f, x, y) => (y >= 3 ? C('#202020') : null)) }] }] });
}

// Jacaré: low and long, olive scales with ridges along the back, pale belly, a long tail.
{
  const escamas = semente => (face, x, y, w, h, c) => {
    if (face === 'baixo') return mix(C('#C8C098'), C('#A8A078'), hash(x, y, semente) * 0.5);
    const base = mix(C('#3E4A2E'), C('#4E5A38'), hash(x >> 1, y >> 1, semente));
    if (face === 'topo' && (x % 3 === 1) && (y % 2 === 0)) return mix(base, C('#6A7A48'), 0.6);   // ridges
    return hash(x, y, semente + 1) < 0.12 ? mix(base, C('#222A18'), 0.5) : base;
  };
  MODELOS.push({ nome: 'jacare', textura: 'jacare', largura: 64, altura: 64, partes: [
    { nome: 'body', pivo: [0, 19, 0], caixas: [{ de: [-4, 0, -8], tam: [8, 4, 16], mat: escamas(21) }] },
    { nome: 'head', pivo: [0, 20, -8], caixas: [
      { de: [-3, -2, -10], tam: [6, 3, 10], mat: junta(olhos('#D9B020', 0, 7), escamas(22)) },
      { de: [-3, -3, -3], tam: [2, 1, 2], mat: liso('#3A4628') },
      { de: [1, -3, -3], tam: [2, 1, 2], mat: liso('#3A4628') }] },
    { nome: 'mandibula', pai: 'head', pivo: [0, 1, -0.5], caixas: [{ de: [-2.5, 0, -9], tam: [5, 1, 9], mat: (f, x, y, w, h) => (f === 'topo' ? C('#C88A80') : f === 'frente' || (f !== 'baixo' && y === 0 && x % 2 === 0) ? C('#F2EEDC') : C('#B8B088')) }] },
    { nome: 'cauda', pivo: [0, 20, 8], caixas: [{ de: [-3, -1, 0], tam: [6, 3, 9], mat: escamas(23) }] },
    { nome: 'cauda2', pai: 'cauda', pivo: [0, 0.5, 9], caixas: [{ de: [-2, -1, 0], tam: [4, 2, 9], mat: escamas(24) }] },
    { nome: 'cauda3', pai: 'cauda2', pivo: [0, 0, 9], caixas: [{ de: [-1, -1, 0], tam: [2, 2, 8], mat: escamas(25) }] },
    { nome: 'right_hind_leg', pivo: [-5, 21, 6], caixas: [{ de: [-1, 0, -1], tam: [2, 3, 2], mat: escamas(26) }] },
    { nome: 'left_hind_leg', pivo: [5, 21, 6], caixas: [{ de: [-1, 0, -1], tam: [2, 3, 2], mat: escamas(27) }] },
    { nome: 'right_front_leg', pivo: [-5, 21, -5], caixas: [{ de: [-1, 0, -1], tam: [2, 3, 2], mat: escamas(28) }] },
    { nome: 'left_front_leg', pivo: [5, 21, -5], caixas: [{ de: [-1, 0, -1], tam: [2, 3, 2], mat: escamas(29) }] }] });
}

// Onça-pintada: golden coat with black rosettes, pale belly, strong legs, a long tail.
{
  const roseta = semente => (face, x, y, w, h, c) => {
    if (face === 'baixo') return mix(C('#F0E6CC'), C('#E2D4B0'), hash(x, y, semente) * 0.5);
    const base = mix(C('#D9A23A'), C('#E8B85A'), hash(x >> 1, y >> 1, semente) * 0.6);
    // Rosettes: a ring of black around a darker gold centre, on a staggered grid.
    const cx = (x + (Math.floor(y / 5) % 2) * 3 + c.uv[0]) % 6, cy = (y + c.uv[1]) % 5;
    const anel = (cx === 1 || cx === 3) && cy >= 1 && cy <= 3 || (cy === 1 || cy === 3) && cx === 2;
    if (anel && hash(x, y, semente + 3) < 0.85) return C('#1E1610');
    if (cx === 2 && cy === 2) return C('#B07A20');
    return base;
  };
  MODELOS.push({ nome: 'onca', textura: 'onca', largura: 64, altura: 64, partes: [
    { nome: 'body', pivo: [0, 11, 0], caixas: [{ de: [-3.5, 0, -10], tam: [7, 7, 20], mat: roseta(31) }] },
    { nome: 'head', pivo: [0, 11, -10], caixas: [
      { de: [-3.5, -3, -6], tam: [7, 6, 6], mat: junta(olhos('#9AB830', 2, 4), roseta(32)) },
      { de: [-2, 0, -8], tam: [4, 3, 2], mat: (f, x, y, w) => (f === 'frente' && y === 0 && (x === 1 || x === 2) ? C('#3A2418') : C('#E8D4A8')) },
      { de: [-3, -4, -2], tam: [2, 1, 1], mat: liso('#1E1610') },
      { de: [1, -4, -2], tam: [2, 1, 1], mat: liso('#1E1610') }] },
    { nome: 'cauda', pivo: [0, 12, 10], rot: [0.7, 0, 0], caixas: [{ de: [-1, 0, -1], tam: [2, 12, 2], mat: (f, x, y) => (y >= 10 ? C('#1E1610') : roseta(33)(f, x, y, 2, 12, { uv: [0, 0] })) }] },
    { nome: 'right_hind_leg', pivo: [-2.5, 17, 7], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 7, 3], mat: roseta(34) }] },
    { nome: 'left_hind_leg', pivo: [2.5, 17, 7], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 7, 3], mat: roseta(35) }] },
    { nome: 'right_front_leg', pivo: [-2.5, 17, -7], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 7, 3], mat: roseta(36) }] },
    { nome: 'left_front_leg', pivo: [2.5, 17, -7], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 7, 3], mat: roseta(37) }] }] });
}

// Araras: the parrot's joints (so its flying and perching animations apply), a long tail and a big
// beak, drawn 1.5 times larger. Three species: vermelha, canindé and azul (hyacinth).
const ARARAS = {
  arara_vermelha: { corpo: '#C8202A', ventre: '#B01822', asa: '#E0B020', ponta: '#2A50B0', cauda: '#C8202A', caudaPonta: '#2A50B0', rosto: '#F2EEE6', bico: '#E8E0D0', bicoBaixo: '#2A2420' },
  arara_caninde: { corpo: '#2A70C0', ventre: '#F0C020', asa: '#2A70C0', ponta: '#1A4A90', cauda: '#2A70C0', caudaPonta: '#1A4A90', rosto: '#F2EEE6', bico: '#2A2420', bicoBaixo: '#2A2420' },
  arara_azul: { corpo: '#2A3E9A', ventre: '#2A3E9A', asa: '#24368A', ponta: '#1A2870', cauda: '#2A3E9A', caudaPonta: '#1A2870', rosto: '#F2C020', bico: '#2A2420', bicoBaixo: '#2A2420' }
};
for (const [nome, k] of Object.entries(ARARAS)) {
  const pena = (base, sem) => (f, x, y, w, h) => (hash(x, y, sem) < 0.15 ? luz(base, 0.85) : C(base));
  MODELOS.push({ nome: nome === 'arara_vermelha' ? 'arara' : null, textura: nome, largura: 32, altura: 32, partes: [
    { nome: 'body', pivo: [0, 16.5, -3], rot: [0.4937, 0, 0], caixas: [{ de: [-1.5, 0, -1.5], tam: [3, 6, 3], mat: (f, x, y, w, h) => (f === 'frente' ? C(k.ventre) : pena(k.corpo, 41)(f, x, y)) }] },
    { nome: 'tail', pivo: [0, 21.07, 1.16], rot: [1.015, 0, 0], caixas: [{ de: [-1.5, -1, -1], tam: [3, 10, 1], mat: (f, x, y, w, h) => (y >= 6 ? C(k.caudaPonta) : C(k.cauda)) }] },
    { nome: 'left_wing', pivo: [1.5, 16.94, -2.76], rot: [-0.6981, -3.1415927, 0], caixas: [{ de: [-0.5, 0, -1.5], tam: [1, 6, 3], mat: (f, x, y) => (y >= 4 ? C(k.ponta) : y >= 2 ? C(k.asa) : C(k.corpo)) }] },
    { nome: 'right_wing', pivo: [-1.5, 16.94, -2.76], rot: [-0.6981, -3.1415927, 0], caixas: [{ de: [-0.5, 0, -1.5], tam: [1, 6, 3], mat: (f, x, y) => (y >= 4 ? C(k.ponta) : y >= 2 ? C(k.asa) : C(k.corpo)) }] },
    { nome: 'head', pivo: [0, 15.69, -2.76], caixas: [{ de: [-1, -1.5, -1], tam: [2, 3, 2], mat: (f, x, y) => ((f === 'direita' || f === 'esquerda') && y === 1 ? (x === 0 || x === 1 ? C(k.rosto) : C('#101010')) : C(k.corpo)) }] },
    { nome: 'head2', pai: 'head', pivo: [0, -2, -1], caixas: [{ de: [-1, -0.5, -2], tam: [2, 1, 4], mat: liso(k.corpo) }] },
    { nome: 'beak1', pai: 'head', pivo: [0, -0.5, -1.5], caixas: [{ de: [-1, -1, -1], tam: [2, 2, 1], mat: liso(k.bico) }] },
    { nome: 'beak2', pai: 'head', pivo: [0, -1.75, -2.45], caixas: [{ de: [-0.5, 0, -0.5], tam: [1, 2, 1], mat: liso(k.bicoBaixo) }] },
    { nome: 'feather', pai: 'head', pivo: [0, -2.15, 0.15], caixas: [] },
    { nome: 'left_leg', pivo: [1, 22, -1.05], rot: [-0.0299, 0, 0], caixas: [{ de: [-0.5, 0, -0.5], tam: [1, 2, 1], mat: liso('#3A3A40') }] },
    { nome: 'right_leg', pivo: [-1, 22, -1.05], rot: [-0.0299, 0, 0], caixas: [{ de: [-0.5, 0, -0.5], tam: [1, 2, 1], mat: liso('#3A3A40') }] }] });
}

// ------------------------------------------------------------------ write
let corpoJava = '';
for (const m of MODELOS) {
  empacotar(m);
  pintar(m);
  if (m.nome) corpoJava += '\n' + java(m);
}
fs.mkdirSync(path.dirname(JAVA), { recursive: true });
fs.writeFileSync(JAVA, `package com.guilhermegoes.arraial.client.fauna;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Model layers of the Brazilian fauna. Generated by tools/gerar_fauna.js: do not edit by hand. */
public final class ModelosFauna {${corpoJava}
	private ModelosFauna() {
	}
}
`);

// Spawn eggs: an egg in the animal's two colours.
const OVOS = { capivara: ['#8C5A36', '#5E3E26'], tamandua: ['#7A7468', '#151515'], jacare: ['#3E4A2E', '#C8C098'], onca: ['#D9A23A', '#1E1610'], arara: ['#C8202A', '#2A50B0'] };
const TI = path.join(RAIZ, 'src/main/resources/assets/arraial/textures/item');
for (const [nome, [a, b]] of Object.entries(OVOS)) {
  const px = Buffer.alloc(16 * 16 * 4);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const dx = (x - 7.5) / 5.2, dy = (y - 8.5) / 6.8;
    if (dx * dx + dy * dy > 1) continue;
    let cor = C(a);
    if (hash(x >> 1, y >> 1, nome.length) < 0.3) cor = C(b);
    if (dx * dx + dy * dy > 0.8) cor = luz(cor, 0.7);
    if (dx < -0.3 && dy < -0.3) cor = luz(cor, 1.2);
    const k = (y * 16 + x) * 4; px[k] = cor[0]; px[k + 1] = cor[1]; px[k + 2] = cor[2]; px[k + 3] = 255;
  }
  fs.writeFileSync(path.join(TI, nome + '_spawn_egg.png'), png(16, 16, px));
}
console.log('fauna gerada:', MODELOS.map(m => m.textura).join(', '));
