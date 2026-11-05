// Exports Blockbench projects (.bbmodel) into the mod: the Java item model (models/item/<id>.json)
// and the texture embedded in the project (textures/item/<id>.png). The .bbmodel in tools/blockbench
// is the source of the model: edit it in Blockbench, save, and run this.
//
// Usage: node tools/de_blockbench.js [id ...]   (no ids: every item project below)
const fs = require('fs');
const path = require('path');

const raiz = path.resolve(__dirname, '..');
const assets = path.join(raiz, 'src/main/resources/assets/arraial');
const pasta = path.join(raiz, 'tools/blockbench');
const ITENS = ['facao', 'peixeira', 'foice', 'cantil', 'saco_saque', 'comenda'];
const ids = process.argv.length > 2 ? process.argv.slice(2) : ITENS;
const EIXOS = ['x', 'y', 'z'];
const r4 = v => Math.round(v * 10000) / 10000;

for (const id of ids) {
	const p = JSON.parse(fs.readFileSync(path.join(pasta, id + '.bbmodel'), 'utf8'));
	// Textures: the project's own PNGs become the item's textures.
	const nomes = p.textures.map((t, i) => {
		const nome = i === 0 ? id : id + '_' + i;
		if (t.source && t.source.startsWith('data:image/png;base64,')) {
			fs.writeFileSync(path.join(assets, 'textures/item', nome + '.png'), Buffer.from(t.source.split(',')[1], 'base64'));
		}
		return nome;
	});
	const texturas = { particle: 'arraial:item/' + nomes[0] };
	nomes.forEach((n, i) => { texturas[String(i)] = 'arraial:item/' + n; });
	const elementos = p.elements.filter(e => e.type === undefined || e.type === 'cube').map(e => {
		const out = { name: e.name, from: e.from.map(r4), to: e.to.map(r4) };
		const rot = e.rotation || [0, 0, 0];
		const eixo = rot.findIndex(v => v !== 0);
		if (eixo >= 0) {
			out.rotation = { angle: rot[eixo], axis: EIXOS[eixo], origin: (e.origin || [8, 8, 8]).map(r4) };
			if (e.rescale) {
				out.rotation.rescale = true;
			}
		}
		out.faces = {};
		for (const [lado, f] of Object.entries(e.faces || {})) {
			if (f.texture === null || f.texture === undefined) {
				continue;
			}
			out.faces[lado] = { uv: f.uv.map(r4), texture: '#' + f.texture };
			if (f.rotation) {
				out.faces[lado].rotation = f.rotation;
			}
		}
		return out;
	});
	const modelo = { credit: 'Feito no Blockbench (tools/blockbench/' + id + '.bbmodel)', textures: texturas, elements: elementos };
	if (p.display) {
		modelo.display = p.display;
	}
	fs.writeFileSync(path.join(assets, 'models/item', id + '.json'), JSON.stringify(modelo, null, '\t'));
	console.log('exportado do Blockbench:', id, elementos.length, 'cubos');
}
