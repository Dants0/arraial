// The mod icon: a 32x32 pixel-art scene (sertão sky, bandeirinhas, a whitewashed house with a
// tiled roof and an azulejo band, the Cruzeiro) scaled up to 512x512.
// Usage: node tools/gerar_icone.js src/main/resources/assets/arraial/icon.png
'use strict';
const fs = require('fs'), zlib = require('zlib');
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
const mix = (a, b, t) => [0, 1, 2].map(i => Math.round(a[i] + (b[i] - a[i]) * t));
const N = 32, E = 16;
const g = Array.from({ length: N }, () => Array(N).fill(null));
const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < N && y < N) g[y][x] = typeof c === 'string' ? hex(c) : c; };
const rect = (x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, typeof c === 'function' ? c(x, y) : c); };

// Sky: warm sertão afternoon, a pale sun.
rect(0, 0, 31, 31, (x, y) => mix(hex('#E07A45'), hex('#F8DC98'), Math.min(1, y / 20)));
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { const d = Math.hypot(x - 24.5, y - 8.5); if (d < 4) set(x, y, d < 3 ? '#FFF4C8' : '#FCE6A8'); }
// Far hills.
for (let x = 0; x < N; x++) { const h = 19 - Math.round(2 * Math.sin(x / 4.5) + Math.sin(x / 2.1)); rect(x, h, x, 21, '#B98A5E'); }
// Ground and a dirt path.
rect(0, 22, 31, 31, (x, y) => ((x * 7 + y * 3) % 11 === 0 ? hex('#6E9A4A') : hex('#7FAE56')));
rect(0, 27, 31, 31, (x, y) => (y === 27 ? hex('#6E9A4A') : (x + y) % 5 === 0 ? hex('#B8915A') : hex('#C9A46A')));
// The house: whitewashed walls, azulejo band, door, shuttered windows.
rect(4, 15, 19, 26, (x, y) => (x === 19 ? hex('#D8D2C4') : hex('#F2EEE4')));
rect(4, 23, 19, 26, (x, y) => ((x + y) % 2 ? hex('#2C56A8') : hex('#E8ECF2')));
rect(10, 19, 12, 26, (x, y) => (x === 12 ? hex('#5A3820') : hex('#7A4E2A')));
set(11, 22, '#C9A646');
for (const wx of [6, 15]) { rect(wx, 18, wx + 1, 20, '#3F78B5'); set(wx, 18, '#2C5A8A'); set(wx + 1, 18, '#2C5A8A'); }
// Tiled roof (capa e canal), wider than the walls.
for (let r = 0; r < 5; r++) {
  const y = 10 + r, x0 = 6 - r, x1 = 17 + r;
  for (let x = x0; x <= x1; x++) set(x, y, (x + (r % 2) * 2) % 4 < 2 ? '#C9683C' : '#A9542E');
}
rect(1, 15, 22, 15, '#7C3A20');
// The Cruzeiro.
rect(26, 8, 27, 27, (x, y) => (x === 27 ? hex('#4A2E1A') : hex('#6E4426')));
rect(23, 11, 30, 12, (x, y) => (y === 12 ? hex('#4A2E1A') : hex('#6E4426')));
// Bandeirinhas on a sagging string.
const cores = ['#C0302A', '#D9A520', '#2E8A3A', '#2C56A8'];
for (let x = 0; x < N; x++) {
  const y = 3 + Math.round(2.2 * Math.sin(Math.PI * x / 31));
  set(x, y, '#5A3820');
  if (x % 4 === 1) { const c = cores[(x >> 2) % 4]; set(x, y + 1, c); set(x + 1, y + 1, c); set(x, y + 2, c); }
}
// A dark frame.
for (let i = 0; i < N; i++) { set(i, 0, '#3A2414'); set(i, 31, '#3A2414'); set(0, i, '#3A2414'); set(31, i, '#3A2414'); }

const W = N * E, px = Buffer.alloc(W * W * 4);
for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
  const c = g[Math.floor(y / E)][Math.floor(x / E)], i = (y * W + x) * 4;
  px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = 255;
}
fs.writeFileSync(process.argv[2], png(W, W, px));
console.log('icone', W + 'x' + W);
