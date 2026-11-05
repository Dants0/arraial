// Turns the mod's 3D models (Java block/item JSON) into Blockbench projects (.bbmodel), with
// every texture embedded (ours from the resources, vanilla ones from the Minecraft client jar),
// so they open ready to edit in Blockbench. After editing, export with
// File > Export > Java Block/Item over the JSON in src/main/resources/assets/arraial/models.
//
// Usage: node tools/para_blockbench.js <client.jar>
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');

const raiz = path.resolve(__dirname, '..');
const assets = path.join(raiz, 'src/main/resources/assets');
const saida = path.join(raiz, 'tools/blockbench');
const jar = process.argv[2];
fs.mkdirSync(saida, { recursive: true });

const MODELOS = [
	'block/forno_farinha', 'block/banca_feira', 'block/cangalha', 'block/balcao_botica',
	'block/lampiao', 'block/lampiao_pendurado', 'item/chapeu_couro',
];

function png(ref) {
	const [ns, p] = ref.includes(':') ? ref.split(':') : ['minecraft', ref];
	const rel = `assets/${ns}/textures/${p}.png`;
	const local = path.join(assets, ns, 'textures', p + '.png');
	if (fs.existsSync(local)) {
		return { dados: fs.readFileSync(local), arquivo: local };
	}
	try {
		return { dados: execFileSync('unzip', ['-p', jar, rel], { maxBuffer: 1 << 24 }), arquivo: rel };
	} catch (e) {
		return null;
	}
}

for (const id of MODELOS) {
	const modelo = JSON.parse(fs.readFileSync(path.join(assets, 'arraial/models', id + '.json'), 'utf8'));
	const chaves = Object.keys(modelo.textures || {}).filter(k => k !== 'particle');
	const texturas = chaves.map((k, i) => {
		const ref = modelo.textures[k];
		const t = png(ref);
		const nome = ref.split('/').pop();
		return {
			path: t ? t.arquivo : '', name: nome + '.png', folder: ref.includes('/') ? ref.split(':').pop().split('/')[0] : '',
			namespace: ref.includes(':') ? ref.split(':')[0] : 'minecraft', id: String(i), group: '', width: 16, height: 16,
			uv_width: 16, uv_height: 16, particle: modelo.textures.particle === ref, use_as_default: false,
			layers_enabled: false, sync_to_project: '', render_mode: 'default', render_sides: 'auto',
			frame_time: 1, frame_order_type: 'loop', frame_order: '', frame_interpolate: false,
			visible: true, internal: true, saved: false, uuid: crypto.randomUUID(),
			source: t ? 'data:image/png;base64,' + t.dados.toString('base64') : '',
		};
	});
	const indice = Object.fromEntries(chaves.map((k, i) => ['#' + k, i]));
	const elementos = modelo.elements.map(e => {
		const faces = {};
		for (const [lado, f] of Object.entries(e.faces || {})) {
			faces[lado] = { uv: f.uv, texture: f.texture in indice ? indice[f.texture] : null };
			if (f.cullface) faces[lado].cullface = f.cullface;
		}
		const rot = [0, 0, 0];
		if (e.rotation) rot[{ x: 0, y: 1, z: 2 }[e.rotation.axis]] = e.rotation.angle;
		return {
			name: e.name || 'cubo', box_uv: false, rescale: !!(e.rotation && e.rotation.rescale), locked: false,
			light_emission: 0, render_order: 'default', allow_mirror_modeling: true,
			from: e.from, to: e.to, autouv: 0, color: 0, rotation: rot,
			origin: e.rotation ? e.rotation.origin : [8, 8, 8], faces, type: 'cube', uuid: crypto.randomUUID(),
		};
	});
	const projeto = {
		meta: { format_version: '4.10', model_format: 'java_block', box_uv: false },
		name: path.basename(id), parent: modelo.parent || '', ambientocclusion: true, front_gui_light: false,
		visible_box: [1, 1, 0], variable_placeholders: '', variable_placeholder_buttons: [],
		unhandled_root_fields: {}, resolution: { width: 16, height: 16 },
		elements: elementos, outliner: elementos.map(e => e.uuid), textures: texturas,
	};
	if (modelo.display) projeto.display = modelo.display;
	fs.writeFileSync(path.join(saida, path.basename(id) + '.bbmodel'), JSON.stringify(projeto, null, '\t'));
	console.log('bbmodel:', path.basename(id), elementos.length, 'cubos,', texturas.length, 'texturas');
}
