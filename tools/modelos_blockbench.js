// Creates the Blockbench projects (.bbmodel) of the mod's 3D items: the facão, peixeira, foice,
// cantil, saco de saque and comenda. Each cube is laid out here once; from then on the .bbmodel in
// tools/blockbench is the source: open it in Blockbench, change it, save, and run
// `node tools/de_blockbench.js` to export it into the mod (models/item/*.json and the textures).
// Texture atlases are 16x16 with four 8x8 swatches: A (top left), B, C, D (bottom right).
//
// Usage: node tools/modelos_blockbench.js   (overwrites the projects listed below)
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const raiz = path.resolve(__dirname, '..');
const texturas = path.join(raiz, 'src/main/resources/assets/arraial/textures/item');
const saida = path.join(raiz, 'tools/blockbench');
fs.mkdirSync(saida, { recursive: true });

const Q = { A: [0, 0], B: [8, 0], C: [0, 8], D: [8, 8] };

// Display (how the item sits in the hand, on the ground, in the GUI).
const NA_MAO = {
	thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
	thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
	firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
	firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
	ground: { translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
	head: { rotation: [0, 180, 0], translation: [0, 13, 7] },
	fixed: { rotation: [0, 180, 0] },
};
const OBJETO = {
	thirdperson_righthand: { rotation: [0, 30, 0], translation: [0, 2, 1.5], scale: [0.4, 0.4, 0.4] },
	thirdperson_lefthand: { rotation: [0, 30, 0], translation: [0, 2, 1.5], scale: [0.4, 0.4, 0.4] },
	firstperson_righthand: { rotation: [0, -30, 0], translation: [1.5, 3.5, 0], scale: [0.4, 0.4, 0.4] },
	firstperson_lefthand: { rotation: [0, -30, 0], translation: [1.5, 3.5, 0], scale: [0.4, 0.4, 0.4] },
	ground: { translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
	gui: { rotation: [20, -30, 0], scale: [0.9, 0.9, 0.9] },
	head: { rotation: [0, 180, 0], translation: [0, 13, 7] },
	fixed: { rotation: [0, 180, 0], scale: [0.8, 0.8, 0.8] },
};

// name, from, to, swatch [, rotation around z]
const MODELOS = {
	facao: {
		display: NA_MAO, rot: -45,
		cubos: [
			['pomo', [7, 0, 7], [9, 0.75, 9], 'D'],
			['cabo', [7.25, 0.75, 7.25], [8.75, 5, 8.75], 'C'],
			['guarda', [6, 5, 7], [10, 5.75, 9], 'D'],
			['lamina', [6.75, 5.75, 7.6], [9.6, 14.5, 8.4], 'A'],
			['fio', [9.6, 6.5, 7.7], [10.1, 14, 8.3], 'B'],
			['ponta', [7.6, 14.5, 7.6], [9.6, 15.75, 8.4], 'A'],
		],
	},
	peixeira: {
		display: NA_MAO, rot: -45,
		cubos: [
			['cabo', [7.4, 0.5, 7.4], [8.6, 5.5, 8.6], 'C'],
			['anel', [7.2, 5.5, 7.2], [8.8, 6.25, 8.8], 'D'],
			['lamina', [7.5, 6.25, 7.75], [8.9, 14, 8.25], 'A'],
			['fio', [8.9, 7, 7.8], [9.15, 13.5, 8.2], 'B'],
			['ponta', [7.5, 14, 7.75], [8.4, 15.5, 8.25], 'A'],
		],
	},
	foice: {
		display: NA_MAO, rot: -22.5,
		cubos: [
			['haste', [7.4, 0, 7.4], [8.6, 10, 8.6], 'C'],
			['anel', [7.2, 9.25, 7.2], [8.8, 10.5, 8.8], 'D'],
			['lamina_base', [8.6, 9.75, 7.75], [12.25, 11.25, 8.25], 'A'],
			['lamina_costas', [11.5, 11.25, 7.75], [13, 13.75, 8.25], 'A'],
			['lamina_curva', [10.25, 13.5, 7.75], [12.5, 15, 8.25], 'A'],
			['lamina_topo', [7.75, 14.25, 7.75], [10.5, 15.5, 8.25], 'A'],
			['fio', [9.5, 11.25, 7.8], [11.5, 12, 8.2], 'B'],
			['ponta', [6.5, 13, 7.75], [8, 14.5, 8.25], 'B'],
		],
	},
	cantil: {
		display: OBJETO, rot: 0,
		cubos: [
			['corpo', [4, 2, 6], [12, 11, 10], 'A'],
			['costura', [3.75, 6, 5.75], [12.25, 7, 10.25], 'B'],
			['gargalo', [6.5, 11, 7], [9.5, 12.5, 9], 'B'],
			['tampa', [6, 12.5, 6.5], [10, 14, 9.5], 'C'],
			['alca_esq', [3, 3, 7.5], [4, 14, 8.5], 'D'],
			['alca_dir', [12, 3, 7.5], [13, 14, 8.5], 'D'],
			['alca_topo', [3, 14, 7.5], [13, 15, 8.5], 'D'],
		],
	},
	saco_saque: {
		display: OBJETO, rot: 0,
		cubos: [
			['corpo', [3.5, 1, 4.5], [12.5, 9, 11.5], 'A'],
			['ombro', [4.5, 9, 5.5], [11.5, 10.5, 10.5], 'B'],
			['gargalo', [6.5, 10.5, 7], [9.5, 12, 9], 'B'],
			['corda', [6, 11, 6.5], [10, 11.75, 9.5], 'C'],
			['topete', [5.5, 12, 6.5], [10.5, 13.5, 9.5], 'A'],
			['moeda', [11.5, 1, 3.5], [13.5, 1.5, 5.5], 'D'],
		],
	},
	comenda: {
		display: OBJETO, rot: 0,
		cubos: [
			['barra', [4.5, 14.5, 7.6], [11.5, 16, 8.4], 'B'],
			['fita_verde', [5, 8, 7.75], [7.5, 14.5, 8.25], 'C'],
			['fita_amarela', [8.5, 8, 7.75], [11, 14.5, 8.25], 'D'],
			['disco_largo', [4.5, 2.5, 7.5], [11.5, 7.5, 8.5], 'A'],
			['disco_alto', [5.5, 1.5, 7.5], [10.5, 8.5, 8.5], 'A'],
			['argola', [7.25, 8.5, 7.75], [8.75, 9.5, 8.25], 'B'],
			['centro', [6.5, 3.5, 8.5], [9.5, 6.5, 8.75], 'B'],
		],
	},
};

function faces(de, ate, q) {
	const [qx, qy] = Q[q];
	const w = ate[0] - de[0], h = ate[1] - de[1], d = ate[2] - de[2];
	const uv = (a, b) => [qx, qy, qx + Math.min(a, 8), qy + Math.min(b, 8)];
	return {
		north: { uv: uv(w, h), texture: 0 }, south: { uv: uv(w, h), texture: 0 },
		east: { uv: uv(d, h), texture: 0 }, west: { uv: uv(d, h), texture: 0 },
		up: { uv: uv(w, d), texture: 0 }, down: { uv: uv(w, d), texture: 0 },
	};
}

for (const [id, m] of Object.entries(MODELOS)) {
	const png = fs.readFileSync(path.join(texturas, id + '.png'));
	const elementos = m.cubos.map(([nome, de, ate, q]) => ({
		name: nome, box_uv: false, rescale: false, locked: false, light_emission: 0, render_order: 'default',
		allow_mirror_modeling: true, from: de, to: ate, autouv: 0, color: 0,
		rotation: [0, 0, m.rot], origin: [8, 8, 8], faces: faces(de, ate, q), type: 'cube', uuid: crypto.randomUUID(),
	}));
	const projeto = {
		meta: { format_version: '4.10', model_format: 'java_block', box_uv: false },
		name: id, parent: '', ambientocclusion: false, front_gui_light: false,
		visible_box: [1, 1, 0], variable_placeholders: '', variable_placeholder_buttons: [], unhandled_root_fields: {},
		resolution: { width: 16, height: 16 }, elements: elementos, outliner: elementos.map(e => e.uuid),
		textures: [{
			path: path.join(texturas, id + '.png'), name: id + '.png', folder: 'item', namespace: 'arraial', id: '0',
			group: '', width: 16, height: 16, uv_width: 16, uv_height: 16, particle: true, use_as_default: false,
			layers_enabled: false, sync_to_project: '', render_mode: 'default', render_sides: 'auto', frame_time: 1,
			frame_order_type: 'loop', frame_order: '', frame_interpolate: false, visible: true, internal: true,
			saved: false, uuid: crypto.randomUUID(), source: 'data:image/png;base64,' + png.toString('base64'),
		}],
		display: m.display,
	};
	fs.writeFileSync(path.join(saida, id + '.bbmodel'), JSON.stringify(projeto, null, '\t'));
	console.log('projeto Blockbench:', id + '.bbmodel', elementos.length, 'cubos');
}
