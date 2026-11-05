// A quick front-view preview of item models (what you see in the inventory, before the GUI
// transform): renders models/item/<id>.json as a PPM, painter-sorted, each face filled with its
// texture region (nearest pixel). For checking shapes and rotations without starting the game.
//
// Usage: node tools/previa.js <saida_dir> id [id ...]
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const raiz = path.resolve(__dirname, '..');
const assets = path.join(raiz, 'src/main/resources/assets/arraial');
const [saida, ...ids] = process.argv.slice(2);
const ESC = 16;

function lerPng(arquivo) {
	const b = fs.readFileSync(arquivo);
	let i = 8, w = 0, h = 0, dados = [];
	while (i < b.length) {
		const len = b.readUInt32BE(i), tipo = b.toString('ascii', i + 4, i + 8);
		if (tipo === 'IHDR') { w = b.readUInt32BE(i + 8); h = b.readUInt32BE(i + 12); }
		if (tipo === 'IDAT') { dados.push(b.subarray(i + 8, i + 8 + len)); }
		i += 12 + len;
	}
	const raw = zlib.inflateSync(Buffer.concat(dados));
	const px = Buffer.alloc(w * h * 4), bpp = 4;
	let ant = Buffer.alloc(w * bpp);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (w * bpp + 1)], linha = raw.subarray(y * (w * bpp + 1) + 1, (y + 1) * (w * bpp + 1)), out = Buffer.alloc(w * bpp);
		for (let x = 0; x < w * bpp; x++) {
			const a = x >= bpp ? out[x - bpp] : 0, c = ant[x], d = x >= bpp ? ant[x - bpp] : 0;
			let v = linha[x];
			if (f === 1) v += a; else if (f === 2) v += c; else if (f === 3) v += (a + c) >> 1;
			else if (f === 4) { const p = a + c - d, pa = Math.abs(p - a), pb = Math.abs(p - c), pc = Math.abs(p - d); v += pa <= pb && pa <= pc ? a : pb <= pc ? c : d; }
			out[x] = v & 255;
		}
		out.copy(px, y * w * bpp);
		ant = out;
	}
	return { w, h, px };
}

for (const id of ids) {
	const m = JSON.parse(fs.readFileSync(path.join(assets, 'models/item', id + '.json'), 'utf8'));
	const tex = lerPng(path.join(assets, 'textures', m.textures['0'].split(':')[1] + '.png'));
	const W = 16 * ESC, img = Buffer.alloc(W * W * 3, 60);
	const zbuf = new Float32Array(W * W).fill(-1e9);
	for (const e of m.elements) {
		const ang = e.rotation ? e.rotation.angle * Math.PI / 180 : 0, ax = e.rotation ? e.rotation.axis : 'z';
		const o = e.rotation ? e.rotation.origin : [8, 8, 8];
		const rot = ([x, y, z]) => {
			const p = [x - o[0], y - o[1], z - o[2]], c = Math.cos(ang), s = Math.sin(ang);
			let q;
			if (ax === 'z') q = [p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]];
			else if (ax === 'y') q = [p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c];
			else q = [p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c];
			return [q[0] + o[0], q[1] + o[1], q[2] + o[2]];
		};
		// The south face (+z), which faces the viewer; sample the texture across it.
		const f = e.faces.south;
		if (!f) continue;
		const [x0, y0, z0] = e.from, [x1, y1, z1] = e.to;
		for (let v = 0; v < 1; v += 1 / 64) {
			for (let u = 0; u < 1; u += 1 / 64) {
				const p = rot([x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, z1]);
				const tu = Math.floor(f.uv[0] + (f.uv[2] - f.uv[0]) * u), tv = Math.floor(f.uv[3] - (f.uv[3] - f.uv[1]) * v - 0.001);
				const k = (Math.min(15, Math.max(0, tv)) * tex.w + Math.min(15, Math.max(0, tu))) * 4;
				if (tex.px[k + 3] === 0) continue;
				const sx = Math.floor(p[0] * ESC), sy = Math.floor((16 - p[1]) * ESC);
				for (let dy = 0; dy < 3; dy++) for (let dx = 0; dx < 3; dx++) {
					const X = sx + dx, Y = sy + dy;
					if (X < 0 || Y < 0 || X >= W || Y >= W || zbuf[Y * W + X] > p[2]) continue;
					zbuf[Y * W + X] = p[2];
					img[(Y * W + X) * 3] = tex.px[k]; img[(Y * W + X) * 3 + 1] = tex.px[k + 1]; img[(Y * W + X) * 3 + 2] = tex.px[k + 2];
				}
			}
		}
	}
	fs.writeFileSync(path.join(saida, id + '.ppm'), Buffer.concat([Buffer.from(`P6 ${W} ${W} 255\n`), img]));
	console.log('previa', id);
}
