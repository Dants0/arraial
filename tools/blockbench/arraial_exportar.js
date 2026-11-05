// Blockbench plugin: "Exportar para o Arraial".
// Install: Blockbench > File > Plugins > (folder icon) Load Plugin from File > pick this file.
// Then, with a project from tools/blockbench open: File > Export > Exportar para o Arraial.
// It saves the project (.bbmodel), writes the Java item model to
// src/main/resources/assets/arraial/models/item/<name>.json and the texture to textures/item/<name>.png,
// using Blockbench's own Java exporter. Restart the game (F3+T reloads resources) to see it.
(function () {
	let acao;
	const RAIZ_MOD = 'D:/MinecraftMods/arraial';

	Plugin.register('arraial_exportar', {
		title: 'Arraial: exportar para o mod',
		author: 'Arraial',
		description: 'Exporta o modelo aberto direto para o mod Arraial (modelo do item e textura).',
		icon: 'agriculture',
		version: '1.0.0',
		variant: 'desktop',
		onload() {
			acao = new Action('arraial_exportar', {
				name: 'Exportar para o Arraial',
				description: 'Modelo e textura direto para src/main/resources/assets/arraial',
				icon: 'agriculture',
				click() {
					const fs = requireNativeModule('fs', { message: 'Gravar o modelo e a textura no projeto do mod Arraial' });
					if (!fs || !Project) {
						return;
					}
					const nome = Project.name || 'modelo';
					const assets = RAIZ_MOD + '/src/main/resources/assets/arraial';
					// The texture(s): the first is <name>.png, the rest <name>_<i>.png.
					Texture.all.forEach((t, i) => {
						const arquivo = i === 0 ? nome : nome + '_' + i;
						const dados = t.getBase64().replace(/^data:image\/png;base64,/, '');
						fs.writeFileSync(assets + '/textures/item/' + arquivo + '.png', Buffer.from(dados, 'base64'));
						t.namespace = 'arraial';
						t.folder = 'item';
						t.name = arquivo + '.png';
					});
					// The model, from Blockbench's own Java exporter.
					const json = Codecs.java_block.compile();
					fs.writeFileSync(assets + '/models/item/' + nome + '.json', typeof json === 'string' ? json : JSON.stringify(json, null, '\t'));
					// And the project itself, so tools/blockbench stays the source.
					fs.writeFileSync(RAIZ_MOD + '/tools/blockbench/' + nome + '.bbmodel', Codecs.project.compile());
					Blockbench.showQuickMessage('Arraial: ' + nome + ' exportado para o mod', 2500);
				},
			});
			MenuBar.addAction(acao, 'file.export');
		},
		onunload() {
			acao.delete();
		},
	});
})();
