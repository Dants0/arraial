#!/usr/bin/env node
/*
 * Builds the Arraial wiki (arraial.dantas-dev.com) from the mod itself: guide pages, JEI texts,
 * trades, projects, recipes, config and textures come from src/main/resources, so the wiki never
 * drifts from the game. Output: build/wiki/ (index.html in Portuguese, en/index.html in English).
 *
 *   node tools/gerar_wiki.js <dir with assets/minecraft extracted from the client jar>
 *
 * The vanilla folder needs assets/minecraft/textures/{item,block} and lang/{en_us,pt_br}.json
 * (ingredient icons and names in recipes).
 */
const fs = require('fs');
const path = require('path');

const RAIZ = path.resolve(__dirname, '..');
const RES = path.join(RAIZ, 'src/main/resources');
const A = path.join(RES, 'assets/arraial');
const MC = path.resolve(process.argv[2] || '');
const SAIDA = path.join(RAIZ, 'build/wiki');
if (!fs.existsSync(path.join(MC, 'assets/minecraft/lang/en_us.json'))) {
	console.error('Uso: node tools/gerar_wiki.js <pasta com assets/minecraft do jar do cliente>');
	process.exit(1);
}

const ler = f => fs.readFileSync(f, 'utf8');
const json = f => JSON.parse(ler(f));
const LANG = { pt: json(path.join(A, 'lang/pt_br.json')), en: json(path.join(A, 'lang/en_us.json')) };
const VLANG = { pt: json(path.join(MC, 'assets/minecraft/lang/pt_br.json')), en: json(path.join(MC, 'assets/minecraft/lang/en_us.json')) };
const props = Object.fromEntries(ler(path.join(RAIZ, 'gradle.properties')).split(/\r?\n/).filter(l => l.includes('=') && !l.startsWith('#'))
	.map(l => [l.split('=')[0].trim(), l.slice(l.indexOf('=') + 1).trim()]));
const VERSAO = props.version;
const MCVER = props.minecraft_version;

fs.rmSync(SAIDA, { recursive: true, force: true });
fs.mkdirSync(path.join(SAIDA, 'img/icon'), { recursive: true });
fs.mkdirSync(path.join(SAIDA, 'en'), { recursive: true });

const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/** Minecraft formatting (§l, §o, colours) to HTML. */
function formatar(txt) {
	let out = '', abertos = [];
	const fecha = () => { while (abertos.length) out += '</' + abertos.pop() + '>'; };
	const s = String(txt);
	for (let i = 0; i < s.length; i++) {
		const c = s[i];
		if (c === '§' && i + 1 < s.length) {
			const k = s[++i].toLowerCase();
			if (k === 'l') { out += '<b>'; abertos.push('b'); }
			else if (k === 'o') { out += '<i>'; abertos.push('i'); }
			else if (k === 'n') { out += '<u>'; abertos.push('u'); }
			else if (k === '8' || k === '7') { fecha(); out += '<span class="dica">'; abertos.push('span'); }
			else if ('0123456789abcdefr'.includes(k)) { fecha(); if (k !== '0' && k !== 'r') { out += '<span class="cor">'; abertos.push('span'); } }
			continue;
		}
		out += c === '\n' ? '<br>' : esc(c);
	}
	fecha();
	return out.replace(/(<br>){3,}/g, '<br><br>');
}
const md = s => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/\*(.+?)\*/g, '<i>$1</i>').replace(/`(.+?)`/g, '<code>$1</code>')
	.replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, '<a href="$2">$1</a>');

// ---------------------------------------------------------------- icons
const copiados = new Map();
function icone(texRef) {
	if (!texRef) return null;
	const [ns, p] = texRef.includes(':') ? texRef.split(':') : ['minecraft', texRef];
	const arq = ns === 'arraial' ? path.join(A, 'textures', p + '.png') : path.join(MC, 'assets/minecraft/textures', p + '.png');
	if (!fs.existsSync(arq)) return null;
	const nome = (ns + '_' + p.replace(/\//g, '_')) + '.png';
	if (!copiados.has(nome)) {
		fs.copyFileSync(arq, path.join(SAIDA, 'img/icon', nome));
		copiados.set(nome, true);
	}
	return 'img/icon/' + nome;
}
function texturaDoModelo(ref, prof = 0) {
	if (!ref || prof > 4) return null;
	const [ns, p] = ref.includes(':') ? ref.split(':') : ['minecraft', ref];
	if (ns !== 'arraial') return null;
	const f = path.join(A, 'models', p + '.json');
	if (!fs.existsSync(f)) return null;
	const m = json(f);
	const t = m.textures || {};
	const preferidas = ['layer0', 'front', 'all', 'side', 'top', 'cross', 'crop', 'texture', 'particle'];
	// Custom shapes: a texture of the mod says more than the particle (usually planks).
	const proprias = Object.values(t).filter(v => typeof v === 'string' && v.startsWith('arraial:'));
	for (const k of preferidas) if (typeof t[k] === 'string' && !t[k].startsWith('#') && (k !== 'particle' || !proprias.length)) return t[k];
	if (proprias.length) return proprias[0];
	return texturaDoModelo(m.parent, prof + 1);
}
function modeloDoItem(id) {
	const f = path.join(A, 'items', id + '.json');
	if (!fs.existsSync(f)) return null;
	let achado = null;
	(function busca(o) {
		if (achado || !o || typeof o !== 'object') return;
		if (typeof o.model === 'string') { achado = o.model; return; }
		for (const v of Object.values(o)) busca(v);
	})(json(f));
	return achado;
}
function iconeArraial(id) {
	return icone('arraial:item/' + id) || icone(texturaDoModelo(modeloDoItem(id))) || icone('arraial:block/' + id);
}
const TAGS = {
	'minecraft:planks': 'oak_planks', 'minecraft:logs': 'oak_log', 'minecraft:wooden_slabs': 'oak_planks', 'minecraft:wool': 'white_wool',
	'minecraft:swords': 'iron_sword', 'minecraft:shovels': 'iron_shovel', 'minecraft:pickaxes': 'iron_pickaxe', 'minecraft:axes': 'iron_axe',
	'minecraft:fences': 'oak_planks', 'minecraft:small_flowers': 'poppy', 'c:foods/raw_beef': 'beef', 'c:foods/safe_raw_fish': 'cod',
	'c:crops/rice': 'wheat_seeds', 'c:crops/onion': 'beetroot', 'c:crops/tomato': 'sweet_berries'
};
const NOMES_TAG = {
	pt: { 'minecraft:planks': 'Qualquer tábua', 'minecraft:logs': 'Qualquer tora', 'minecraft:wooden_slabs': 'Qualquer laje de madeira', 'minecraft:wool': 'Qualquer lã',
		'minecraft:swords': 'Qualquer espada', 'minecraft:shovels': 'Qualquer pá', 'minecraft:pickaxes': 'Qualquer picareta', 'minecraft:axes': 'Qualquer machado',
		'minecraft:fences': 'Qualquer cerca', 'minecraft:small_flowers': 'Qualquer flor', 'c:foods/raw_beef': 'Carne crua', 'c:foods/safe_raw_fish': 'Peixe cru',
		'c:crops/rice': 'Arroz', 'c:crops/onion': 'Cebola', 'c:crops/tomato': 'Tomate' },
	en: { 'minecraft:planks': 'Any planks', 'minecraft:logs': 'Any log', 'minecraft:wooden_slabs': 'Any wooden slab', 'minecraft:wool': 'Any wool',
		'minecraft:swords': 'Any sword', 'minecraft:shovels': 'Any shovel', 'minecraft:pickaxes': 'Any pickaxe', 'minecraft:axes': 'Any axe',
		'minecraft:fences': 'Any fence', 'minecraft:small_flowers': 'Any flower', 'c:foods/raw_beef': 'Raw beef', 'c:foods/safe_raw_fish': 'Raw fish',
		'c:crops/rice': 'Rice', 'c:crops/onion': 'Onion', 'c:crops/tomato': 'Tomato' }
};
function iconeVanilla(id) {
	for (const p of ['item/' + id, 'block/' + id, 'block/' + id + '_front', 'block/' + id + '_side', 'block/' + id + '_top', 'item/' + id + '_00']) {
		const r = icone('minecraft:' + p);
		if (r) return r;
	}
	return null;
}
/** Name and icon of an ingredient ("minecraft:x", "#tag", "arraial:x"). */
function ingrediente(ref, l) {
	if (ref && typeof ref === 'object') ref = ref.item || ref.tag && '#' + ref.tag || ref.id;
	if (Array.isArray(ref)) ref = ref[0];
	ref = String(ref);
	if (ref.startsWith('#')) {
		const tag = ref.slice(1);
		return { nome: NOMES_TAG[l][tag] || tag, icone: TAGS[tag] ? iconeVanilla(TAGS[tag]) : null };
	}
	const [ns, id] = ref.split(':');
	if (ns === 'arraial') return { nome: nomeArraial(id, l), icone: iconeArraial(id), link: '#item-' + id };
	return { nome: VLANG[l]['item.minecraft.' + id] || VLANG[l]['block.minecraft.' + id] || id, icone: iconeVanilla(id) };
}
const nomeArraial = (id, l) => LANG[l]['item.arraial.' + id] || LANG[l]['block.arraial.' + id] || id;

// ---------------------------------------------------------------- recipes
const RECEITAS = {};
for (const f of fs.readdirSync(path.join(RES, 'data/arraial/recipe'))) {
	const r = json(path.join(RES, 'data/arraial/recipe', f));
	const id = (r.result && (r.result.id || r.result.item) || '').split(':')[1];
	if (id) (RECEITAS[id] ||= []).push(r);
}
function slot(ref, l) {
	if (!ref) return '<span class="slot"></span>';
	const g = ingrediente(ref, l);
	const img = g.icone ? `<img src="${g.icone}" alt="">` : `<span class="sem">${esc(g.nome.slice(0, 2))}</span>`;
	return `<span class="slot" title="${esc(g.nome)}">${img}</span>`;
}
function receitaHtml(r, l) {
	const T = TXT[l];
	const qtd = r.result && r.result.count > 1 ? `<span class="qtd">×${r.result.count}</span>` : '';
	const resultado = `<span class="seta">→</span><span class="slot saida">${(r.result && iconeArraial(r.result.id.split(':')[1])) ? `<img src="${iconeArraial(r.result.id.split(':')[1])}" alt="">` : ''}${qtd}</span>`;
	const lista = refs => {
		const cont = new Map();
		for (const x of refs) { const k = JSON.stringify(x); cont.set(k, (cont.get(k) || 0) + 1); }
		return [...cont].map(([k, n]) => { const g = ingrediente(JSON.parse(k), l); return (n > 1 ? n + '× ' : '') + (g.link ? `<a href="${g.link}">${esc(g.nome)}</a>` : esc(g.nome)); }).join(', ');
	};
	if (r.type === 'minecraft:crafting_shaped') {
		const pat = r.pattern.map(p => p.padEnd(3, ' '));
		while (pat.length < 3) pat.push('   ');
		const cel = [];
		for (const linha of pat) for (const c of linha.slice(0, 3)) cel.push(slot(c === ' ' ? null : r.key[c], l));
		const usados = pat.join('').split('').filter(c => c !== ' ').map(c => r.key[c]);
		return `<div class="receita"><div class="tipo">${T.mesa}</div><div class="linha"><div class="grade">${cel.join('')}</div>${resultado}</div><div class="ingr">${lista(usados)}</div></div>`;
	}
	if (r.type === 'minecraft:crafting_shapeless') {
		return `<div class="receita"><div class="tipo">${T.semForma}</div><div class="linha"><div class="fila">${r.ingredients.map(x => slot(x, l)).join('')}</div>${resultado}</div><div class="ingr">${lista(r.ingredients)}</div></div>`;
	}
	const tipos = { 'minecraft:smelting': T.fornalha, 'minecraft:campfire_cooking': T.fogueira, 'minecraft:stonecutting': T.cortador, 'farmersdelight:cooking': T.panela };
	const ingr = r.ingredients || [r.ingredient];
	const extra = r.cookingtime ? ` · ${Math.round(r.cookingtime / 20)} s` : '';
	const cond = r['fabric:load_conditions'] ? ` <span class="etiqueta">Farmer's Delight</span>` : '';
	return `<div class="receita"><div class="tipo">${tipos[r.type] || r.type}${extra}${cond}</div><div class="linha"><div class="fila">${ingr.map(x => slot(x, l)).join('')}</div>${resultado}</div><div class="ingr">${lista(ingr)}</div></div>`;
}

// ---------------------------------------------------------------- images
const IMAGENS = path.join(RAIZ, 'docs/imagens');
fs.mkdirSync(path.join(SAIDA, 'img/fotos'), { recursive: true });
// Photos: resized JPEGs (Windows: System.Drawing); the icon stays PNG.
const DEST_FOTOS = path.join(SAIDA, 'img/fotos');
fs.copyFileSync(path.join(IMAGENS, 'icone.png'), path.join(DEST_FOTOS, 'icone.png'));
let jpg = false;
if (process.platform === 'win32') {
	try {
		require('child_process').execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
			path.join(__dirname, 'fotos_jpg.ps1'), '-Origem', IMAGENS, '-Destino', DEST_FOTOS], { stdio: 'inherit' });
		jpg = true;
	} catch (e) {
		console.warn('fotos em JPEG falharam; usando PNG');
	}
}
if (!jpg) for (const f of fs.readdirSync(IMAGENS)) fs.copyFileSync(path.join(IMAGENS, f), path.join(DEST_FOTOS, f));
const foto = n => !fs.existsSync(path.join(IMAGENS, n)) ? null : 'img/fotos/' + (jpg && n !== 'icone.png' ? n.replace(/\.png$/, '.jpg') : n);

// ---------------------------------------------------------------- README bits
const README = ler(path.join(RAIZ, 'README.md')).split(/\r?\n/);
function blocoReadme(titulo, ate) {
	const i = README.findIndex(x => x.trim() === titulo);
	if (i < 0) return [];
	const out = [];
	for (let j = i + 1; j < README.length && !README[j].startsWith(ate); j++) out.push(README[j]);
	return out;
}
function listaMd(linhas) {
	let html = '', nivel = 0;
	for (const lin of linhas) {
		const m = lin.match(/^(\s*)[-*] (.*)$/) || lin.match(/^(\s*)\d+\. (.*)$/);
		if (!m) continue;
		const n = m[1].length >= 2 ? 2 : 1;
		while (nivel < n) { html += '<ul>'; nivel++; }
		while (nivel > n) { html += '</ul>'; nivel--; }
		html += '<li>' + md(m[2]) + '</li>';
	}
	while (nivel--) html += '</ul>';
	return html;
}

// ---------------------------------------------------------------- texts of the wiki itself
const LINKS = {
	curseforge: 'https://www.curseforge.com/minecraft/mc-mods/arraial', modrinth: 'https://modrinth.com/mod/arraial',
	github: 'https://github.com/Dants0/arraial', issues: 'https://github.com/Dants0/arraial/issues'
};
const TXT = {
	pt: {
		html: 'pt-BR', titulo: 'Arraial — Wiki oficial', outra: 'English', outraUrl: 'en/', raiz: '',
		desc: 'Wiki oficial do Arraial, mod de Minecraft (Fabric) de vilas coloniais brasileiras: ofícios, obras, economia, política, eras, fauna e mais.',
		lema: 'Funde um povoado no sertão e veja-o virar cidade. Moradores com ofício, obras bloco a bloco, feira, festas, política, guerras entre vilas, eras da história do Brasil e a fauna brasileira.',
		blog: 'https://dantas-dev.com/pt/blog', blogNome: 'Devblog',
		nav: { visao: 'Visão geral', comecar: 'Começando', guia: 'O Guia', oficios: 'Ofícios', obras: 'Obras e estágios', itens: 'Itens e blocos', fauna: 'Fauna', config: 'Integrações e config', comandos: 'Comandos', galeria: 'Galeria', faq: 'Perguntas' },
		requisitos: 'Requisitos', instalar: 'Instalação', primeiros: 'Primeiros passos',
		guiaIntro: 'O mesmo texto do <b>Guia do Arraial</b>, o livro que você ganha ao colocar o primeiro Cruzeiro (ou faz com livro + graveto).',
		oficioCol: ['Ofício', 'Posto de trabalho', 'O que faz'], semPosto: 'contratado', pedreiroTxt: 'Ergue as obras com o material do Armazém. Um morador vira pedreiro com o Contrato de Pedreiro ou quando o prefeito precisa.',
		mascateTxt: 'Comerciante ambulante que passa pelas vilas com mercadoria de fora.',
		oficiosIntro: 'Um morador sem ofício assume o posto livre mais próximo. Cada posto vem com uma obra que o prefeito manda construir quando a vila precisa.',
		obrasIntro: 'Escolhidas no Livro de Projetos e clicadas num Marco da Obra, ou pedidas pelo próprio prefeito. Os pedreiros erguem bloco a bloco com o material do Armazém; o estilo muda com a era e a cultura da vila.',
		estagiosTit: 'Estágios da vila', estagiosTxt: 'Mais gente e mais satisfação fazem o povoado subir de estágio:',
		itensIntro: 'Tudo o que o mod adiciona, com receita. Passe o mouse num ingrediente para ver o nome.', buscar: 'Buscar item ou bloco…',
		blocos: 'Blocos', itensTit: 'Itens', receita: 'Receita', semReceita: 'Sem receita: vem do trabalho dos moradores, do comércio ou do mundo.',
		mesa: 'Bancada', semForma: 'Bancada (sem forma)', fornalha: 'Fornalha', fogueira: 'Fogueira', cortador: 'Cortador de pedras', panela: 'Panela',
		faunaIntro: 'Cinco bichos brasileiros vivem nos biomas quentes. Todos têm ovo gerador na aba criativa do Arraial.',
		ondeVive: 'Onde vive', onde: { capivara: 'Rios, pântanos, savanas e selvas', tamandua: 'Savanas e planícies', jacare: 'Pântanos e rios, na beira d\'água', onca: 'Selvas (rara nas savanas)', arara: 'Selvas e savanas' },
		integracoes: 'Integrações (todas opcionais)', configTit: 'Configurações', configTxt: 'Pelo Mod Menu ou no arquivo <code>config/arraial.json</code>. Num servidor vale o arquivo do servidor.',
		comandosIntro: 'Comandos de administrador (nível 2, cheats ligados). Servem para testar, gravar vídeos e destravar situações. Use perto de um Cruzeiro.',
		comandos: {
			diagnostico: 'Resumo da vila mais próxima: gente, camas, fome, comida, obras, ofícios e o pedido atual.',
			cronica: 'Mostra a crônica (a história) da vila.', tesouro: 'Mostra ou altera o tesouro (réis) da vila.',
			colonos: 'Chama uma caravana de colonos para fundar um povoado na sua frente.', vila_nova: 'Cria uma vila autônoma vizinha.',
			crise: 'Inicia ou encerra uma crise (seca, enchente, praga, salteadores, epidemia, incêndio).', festa: 'Começa uma festa agora.',
			era: 'Muda a era da vila e reforma as casas no novo estilo.', guerra: 'Faz uma rixa entre duas vilas virar guerra.',
			avancar: 'Avança dias nas vilas distantes (que não estão carregadas).', tropa: 'Manda o tropeiro pegar a estrada.',
			mascate: 'Chama um mascate.', conselho: 'Mostra o conselho e as propostas em votação.'
		},
		faq: [
			['Funciona com que versão?', `Minecraft ${MCVER} com Fabric Loader 0.20.1+, Fabric API e Java 25.`],
			['Precisa instalar no servidor?', 'Sim, no servidor e em quem joga. Configurações valem as do servidor.'],
			['Funciona em mundo já criado?', 'Sim. Basta fazer um Cruzeiro. As vilas vizinhas e a fauna surgem nos chunks novos.'],
			['Meus moradores não constroem nada.', 'Confira o Armazém (precisa de material e comida), se há um pedreiro e se há lugar plano livre. Clique no Cruzeiro: o relatório mostra o que falta.'],
			['A vila não cresce.', 'Moradores novos só chegam com cama livre. Satisfação baixa (fome, sem casa, crise) também segura o estágio.'],
			['Posso usar em modpack ou vídeo?', 'Vídeos e lives: à vontade. Modpacks: pode, desde que baixem o mod pelo CurseForge ou Modrinth. Veja a licença no GitHub.'],
			['Achei um bug!', `Abra uma issue em <a href="${LINKS.issues}">GitHub</a> com a versão, os mods e o log.`]
		],
		rodape: 'Wiki gerada a partir dos arquivos do próprio mod. Arraial não é afiliado à Mojang nem à Microsoft.', versao: 'versão',
		galeriaIntro: 'Imagens do jogo.', baixar: 'Baixar'
	},
	en: {
		html: 'en', titulo: 'Arraial — Official Wiki', outra: 'Português', outraUrl: '../', raiz: '../',
		desc: 'Official wiki of Arraial, a Minecraft (Fabric) mod about Brazilian colonial villages: trades, building projects, economy, politics, eras, wildlife and more.',
		lema: 'Found a hamlet in the backlands and watch it become a city. Villagers with trades, buildings raised block by block, markets, festas, politics, wars between villages, eras of Brazilian history and Brazilian wildlife.',
		blog: 'https://dantas-dev.com/en/blog', blogNome: 'Devblog',
		nav: { visao: 'Overview', comecar: 'Getting started', guia: 'The Guide', oficios: 'Trades', obras: 'Buildings & stages', itens: 'Items & blocks', fauna: 'Wildlife', config: 'Integrations & config', comandos: 'Commands', galeria: 'Gallery', faq: 'FAQ' },
		requisitos: 'Requirements', instalar: 'Install', primeiros: 'First steps',
		guiaIntro: 'The same text as the <b>Arraial Guide</b>, the book you get when you place your first Cruzeiro (or craft with book + stick).',
		oficioCol: ['Trade', 'Workstation', 'What they do'], semPosto: 'hired', pedreiroTxt: 'Raises the buildings with material from the storehouse. A villager becomes a builder with the Builder\'s Contract or when the mayor needs one.',
		mascateTxt: 'A travelling peddler who passes through villages with goods from afar.',
		oficiosIntro: 'A villager without a trade takes the nearest free workstation. Each workstation comes with a building the mayor orders when the village needs it.',
		obrasIntro: 'Chosen in the Book of Projects and clicked on a Construction Marker, or ordered by the mayor. Builders raise them block by block from the storehouse; the style changes with the era and the village\'s culture.',
		estagiosTit: 'Village stages', estagiosTxt: 'More people and more satisfaction move the hamlet up a stage:',
		itensIntro: 'Everything the mod adds, with recipes. Hover an ingredient to see its name.', buscar: 'Search item or block…',
		blocos: 'Blocks', itensTit: 'Items', receita: 'Recipe', semReceita: 'No recipe: it comes from the villagers\' work, trade or the world.',
		mesa: 'Crafting table', semForma: 'Crafting (shapeless)', fornalha: 'Furnace', fogueira: 'Campfire', cortador: 'Stonecutter', panela: 'Cooking pot',
		faunaIntro: 'Five Brazilian animals live in the warm biomes. All have a spawn egg in the Arraial creative tab.',
		ondeVive: 'Where it lives', onde: { capivara: 'Rivers, swamps, savannas and jungles', tamandua: 'Savannas and plains', jacare: 'Swamps and rivers, at the water\'s edge', onca: 'Jungles (rare in savannas)', arara: 'Jungles and savannas' },
		integracoes: 'Integrations (all optional)', configTit: 'Settings', configTxt: 'Through Mod Menu or the file <code>config/arraial.json</code>. On a server, the server\'s file applies.',
		comandosIntro: 'Operator commands (level 2, cheats on). For testing, recording videos and getting unstuck. Use near a Cruzeiro.',
		comandos: {
			diagnostico: 'Summary of the nearest village: people, beds, hunger, food, works, trades and the current request.',
			cronica: 'Shows the village chronicle (its history).', tesouro: 'Shows or changes the village treasury (réis).',
			colonos: 'Calls a caravan of settlers to found a hamlet in front of you.', vila_nova: 'Creates a neighbouring autonomous village.',
			crise: 'Starts or ends a crisis (drought, flood, pests, highwaymen, epidemic, fire).', festa: 'Starts a festa now.',
			era: 'Changes the village era and reforms the houses in the new style.', guerra: 'Turns a feud between two villages into war.',
			avancar: 'Advances days in distant (unloaded) villages.', tropa: 'Sends the muleteer on the road.',
			mascate: 'Calls a peddler.', conselho: 'Shows the council and the proposals being voted.'
		},
		faq: [
			['Which version?', `Minecraft ${MCVER} with Fabric Loader 0.20.1+, Fabric API and Java 25.`],
			['Does the server need it?', 'Yes, both server and players. The server\'s settings apply.'],
			['Does it work in an existing world?', 'Yes. Just craft a Cruzeiro. Neighbouring villages and wildlife appear in new chunks.'],
			['My villagers build nothing.', 'Check the storehouse (it needs material and food), whether there is a builder and free flat ground. Click the Cruzeiro: the report says what is missing.'],
			['The village does not grow.', 'New villagers only arrive when there is a free bed. Low satisfaction (hunger, homelessness, crisis) also holds the stage back.'],
			['Can I use it in a modpack or video?', 'Videos and streams: go ahead. Modpacks: yes, as long as the mod is downloaded from CurseForge or Modrinth. See the licence on GitHub.'],
			['I found a bug!', `Open an issue on <a href="${LINKS.issues}">GitHub</a> with the version, your mods and the log.`]
		],
		rodape: 'Wiki generated from the mod\'s own files. Arraial is not affiliated with Mojang or Microsoft.', versao: 'version',
		galeriaIntro: 'In-game pictures.', baixar: 'Download'
	}
};

// ---------------------------------------------------------------- data
const PROFISSOES = [
	['pedreiro', null], ['lenhador', 'cepo'], ['roceiro', 'balaio'], ['oleiro', 'forno_olaria'], ['barreiro', 'gamela'], ['cavouqueiro', 'bancada_canteiro'],
	['pastor', 'cocho'], ['pescador', 'covo'], ['cacador', 'varal_caca'], ['farinheiro', 'forno_farinha'], ['feirante', 'banca_feira'], ['tropeiro', 'cangalha'],
	['boticario', 'balcao_botica'], ['soldado', 'posto_guarda'], ['engenheiro', 'prancheta'], ['relacoes', 'escrivaninha'], ['mestre_engenho', 'moenda'],
	['cafeicultor', 'terreiro'], ['entalhador', 'bancada_entalhe'], ['mascate', null]
];
const PROJETOS = Object.keys(LANG.pt).filter(k => k.startsWith('projeto.arraial.')).map(k => k.split('.')[2]);
const ESTAGIOS = ['povoado', 'arraial', 'vila', 'vila_prospera', 'cidade'];
const ITENS = Object.keys(LANG.pt).filter(k => /^(block|item)\.arraial\.[a-z_]+$/.test(k)).map(k => ({ id: k.split('.')[2], bloco: k.startsWith('block') }))
	.filter(x => !x.id.endsWith('_spawn_egg') && !['mandioca', 'cafeeiro'].includes(x.id) || !x.bloco && x.id === 'mandioca');
const FAUNA = ['capivara', 'tamandua', 'jacare', 'onca', 'arara'];
const CAPITULOS = [['inicio', 6], ['meio', 11], ['fim', 13], ['comercio', 5], ['dicas', 2]];
const CONFIGS = Object.keys(LANG.pt).filter(k => /^arraial\.config\.[a-z_]+$/.test(k) && !['titulo', 'servidor'].includes(k.split('.')[2])).map(k => k.split('.')[2]);

// ---------------------------------------------------------------- page
function pagina(l) {
	const T = TXT[l], L = LANG[l], R = T.raiz;
	const img = s => s ? R + s : null;
	const ic = (src, alt = '') => src ? `<img class="px" src="${R + src}" alt="${esc(alt)}" loading="lazy">` : '';
	const sec = (id, titulo, corpo) => `<section id="${id}"><h2><a href="#${id}">${titulo}</a></h2>${corpo}</section>`;
	const NAV = Object.entries(T.nav).map(([k, v]) => `<a href="#${k}">${v}</a>`).join('');
	const [feitos] = [blocoReadme(l === 'pt' ? '### O que tem' : '### Features', '###')];
	const integ = feitos.slice(feitos.findIndex(x => /Integra/.test(x)) + 1);
	const recursos = feitos.slice(0, feitos.findIndex(x => /Integra/.test(x)));

	// Overview
	const visao = listaMd(recursos);

	// Getting started
	const passos = blocoReadme(l === 'pt' ? '### Como começar' : '### Getting started', '###');
	const inst = blocoReadme(l === 'pt' ? '### Instalação' : '### Install', '##');
	const comecar = `<div class="duas"><div><h3>${T.primeiros}</h3>${passos.length ? listaMd(passos) : formatar(L['arraial.guia.inicio.1'])}</div>
		<div><h3>${T.instalar}</h3>${listaMd(inst)}<p><a class="botao" href="${LINKS.curseforge}">CurseForge</a> <a class="botao" href="${LINKS.modrinth}">Modrinth</a></p></div></div>`;

	// Guide
	const guia = `<p>${T.guiaIntro}</p>` + CAPITULOS.map(([c, n]) => {
		const pags = [];
		for (let i = 1; i <= n; i++) if (L[`arraial.guia.${c}.${i}`]) pags.push(`<article class="pagina">${formatar(L[`arraial.guia.${c}.${i}`])}</article>`);
		return `<details ${c === 'inicio' ? 'open' : ''}><summary>${esc(L['arraial.guia.cap.' + c])} <small>(${pags.length})</small></summary><div class="paginas">${pags.join('')}</div></details>`;
	}).join('');

	// Trades
	const linhas = PROFISSOES.map(([p, posto]) => {
		const nome = L[`arraial.profissao.${p}.m`] || p;
		const nomeF = L[`arraial.profissao.${p}.f`];
		const postoHtml = posto ? `<a href="#item-${posto}">${ic(iconeArraial(posto))} ${esc(nomeArraial(posto, l))}</a>` : `<span class="dica">${T.semPosto}</span>`;
		const txt = posto ? L['arraial.jei.' + posto] : p === 'pedreiro' ? T.pedreiroTxt : T.mascateTxt;
		return `<tr><td><b>${esc(nome)}</b>${nomeF && nomeF !== nome ? `<br><small>${esc(nomeF)}</small>` : ''}</td><td>${postoHtml}</td><td>${esc(txt || '')}</td></tr>`;
	}).join('');
	const oficios = `<p>${T.oficiosIntro}</p><div class="tabela"><table><thead><tr>${T.oficioCol.map(c => `<th>${c}</th>`).join('')}</tr></thead><tbody>${linhas}</tbody></table></div>`;

	// Buildings and stages
	const obras = `<p>${T.obrasIntro}</p><ul class="chips">${PROJETOS.map(p => `<li>${esc(L['projeto.arraial.' + p])}</li>`).join('')}</ul>
		<h3>${T.estagiosTit}</h3><p>${T.estagiosTxt}</p><ol class="estagios">${ESTAGIOS.map(e => `<li>${esc(L['arraial.estagio.' + e])}</li>`).join('')}</ol>`;

	// Items
	const cartao = it => {
		const nome = nomeArraial(it.id, l);
		const desc = L['arraial.jei.' + it.id];
		const rs = (RECEITAS[it.id] || []).map(r => receitaHtml(r, l).replace(/src="img\//g, `src="${R}img/`)).join('');
		return `<article class="item" id="item-${it.id}" data-busca="${esc((nome + ' ' + it.id).toLowerCase())}">
			<header>${ic(iconeArraial(it.id), nome)}<h4>${esc(nome)}</h4></header>
			${desc ? `<p>${esc(desc)}</p>` : ''}${rs || `<p class="dica">${T.semReceita}</p>`}</article>`;
	};
	const blocos = ITENS.filter(i => i.bloco), soltos = ITENS.filter(i => !i.bloco);
	const itens = `<p>${T.itensIntro}</p><input type="search" id="busca" placeholder="${T.buscar}" aria-label="${T.buscar}">
		<h3>${T.blocos}</h3><div class="itens">${blocos.map(cartao).join('')}</div>
		<h3>${T.itensTit}</h3><div class="itens">${soltos.map(cartao).join('')}</div>`;

	// Wildlife
	const fauna = `<p>${T.faunaIntro}</p>${foto('70_fauna_todos.png') ? `<figure class="larga"><img src="${img(foto('70_fauna_todos.png'))}" alt="" loading="lazy"></figure>` : ''}
		<div class="fauna">${FAUNA.map(b => {
			const f = foto('71_fauna_' + b + '.png') || (b === 'arara' && foto('72_fauna_araras.png'));
			return `<article class="bicho">${f ? `<img src="${img(f)}" alt="${esc(L['entity.arraial.' + b])}" loading="lazy">` : ''}
			<div><h3>${ic(iconeArraial(b + '_spawn_egg'))} ${esc(L['entity.arraial.' + b])}</h3><p><b>${T.ondeVive}:</b> ${esc(T.onde[b])}</p><p>${esc(L['arraial.jei.' + b + '_spawn_egg'] || '')}</p></div></article>`;
		}).join('')}</div>`;

	// Integrations and settings
	const config = `<h3>${T.integracoes}</h3>${listaMd(integ)}<h3>${T.configTit}</h3><p>${T.configTxt}</p>
		<dl class="config">${CONFIGS.map(c => `<dt>${esc(L['arraial.config.' + c])}</dt><dd>${esc(L['arraial.config.' + c + '.dica'] || '')}</dd>`).join('')}</dl>`;

	// Commands
	const comandos = `<p>${T.comandosIntro}</p><dl class="config">${Object.entries(T.comandos).map(([c, d]) => `<dt><code>/arraial ${c}</code></dt><dd>${esc(d)}</dd>`).join('')}</dl>`;

	// Gallery
	const fotos = fs.readdirSync(IMAGENS).filter(f => /^\d/.test(f) && !f.startsWith('71_') && !f.startsWith('70_'));
	const galeria = `<div class="galeria">${fotos.map(f => `<a href="${img(foto(f))}"><img src="${img(foto(f))}" alt="" loading="lazy"></a>`).join('')}</div>`;

	const faq = T.faq.map(([q, a]) => `<details><summary>${esc(q)}</summary><p>${a}</p></details>`).join('');

	return `<!doctype html>
<html lang="${T.html}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(T.titulo)}</title>
<meta name="description" content="${esc(T.desc)}">
<meta property="og:title" content="${esc(T.titulo)}">
<meta property="og:description" content="${esc(T.desc)}">
<meta property="og:image" content="https://arraial.dantas-dev.com/img/fotos/35_vila_natural.jpg">
<link rel="icon" href="${R}img/fotos/icone.png">
<link rel="alternate" hreflang="pt-BR" href="https://arraial.dantas-dev.com/">
<link rel="alternate" hreflang="en" href="https://arraial.dantas-dev.com/en/">
<link rel="stylesheet" href="${R}estilo.css">
</head>
<body>
<header class="topo">
	<a class="marca" href="#visao"><img src="${R}img/fotos/icone.png" alt="" class="px"> Arraial <small>wiki</small></a>
	<button class="menu" aria-label="Menu" aria-expanded="false">☰</button>
	<a class="lingua" href="${T.outraUrl}">${T.outra}</a>
</header>
<div class="corpo">
<nav class="lado">${NAV}</nav>
<main>
<div class="capa" style="background-image:url('${img(foto('35_vila_natural.png'))}')">
	<div class="capa-texto">
		<h1>Arraial</h1>
		<p>${esc(T.lema)}</p>
		<p class="selos"><span>Minecraft ${MCVER}</span><span>Fabric</span><span>Java 25</span><span>${T.versao} ${esc(VERSAO)}</span></p>
		<p><a class="botao forte" href="${LINKS.curseforge}">${T.baixar} · CurseForge</a> <a class="botao" href="${LINKS.modrinth}">Modrinth</a> <a class="botao" href="${LINKS.github}">GitHub</a> <a class="botao" href="${T.blog}">${T.blogNome}</a></p>
	</div>
</div>
${sec('visao', T.nav.visao, visao)}
${sec('comecar', T.nav.comecar, comecar)}
${sec('guia', T.nav.guia, guia)}
${sec('oficios', T.nav.oficios, oficios)}
${sec('obras', T.nav.obras, obras)}
${sec('itens', T.nav.itens, itens)}
${sec('fauna', T.nav.fauna, fauna)}
${sec('config', T.nav.config, config)}
${sec('comandos', T.nav.comandos, comandos)}
${sec('galeria', T.nav.galeria, galeria)}
${sec('faq', T.nav.faq, faq)}
<footer><p>${T.rodape}</p><p><a href="${LINKS.github}">GitHub</a> · <a href="${LINKS.curseforge}">CurseForge</a> · <a href="${LINKS.modrinth}">Modrinth</a> · <a href="${T.blog}">${T.blogNome}</a> · Arraial ${esc(VERSAO)}</p></footer>
</main>
</div>
<script>
(function(){
	var b=document.getElementById('busca');
	if(b)b.addEventListener('input',function(){var q=b.value.trim().toLowerCase();document.querySelectorAll('.item').forEach(function(e){e.hidden=q&&e.getAttribute('data-busca').indexOf(q)<0;});});
	var m=document.querySelector('.menu'),n=document.querySelector('.lado');
	m.addEventListener('click',function(){var a=n.classList.toggle('aberto');m.setAttribute('aria-expanded',a);});
	n.addEventListener('click',function(e){if(e.target.tagName==='A'){n.classList.remove('aberto');m.setAttribute('aria-expanded',false);}});
	var links=[].slice.call(n.querySelectorAll('a')),secs=links.map(function(a){return document.querySelector(a.getAttribute('href'));});
	var obs=new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting){links.forEach(function(a){a.classList.toggle('atual',a.getAttribute('href')==='#'+e.target.id);});}});},{rootMargin:'-20% 0px -70% 0px'});
	secs.forEach(function(s){if(s)obs.observe(s);});
	if(location.hash&&location.hash.indexOf('#item-')===0){var it=document.querySelector(location.hash);if(it)it.classList.add('destaque');}
})();
</script>
</body>
</html>
`;
}

const CSS = `:root{--fundo:#f6efe2;--papel:#fffaf0;--tinta:#2b1d10;--suave:#6b5844;--borda:#e2d3b8;--terra:#b5562c;--terra2:#8f3f1c;--ocre:#d79b2b;--verde:#3f7a3a;--slot:#c6c6c6;--slotb:#8b8b8b;--sombra:0 1px 2px rgba(60,40,20,.08),0 4px 16px rgba(60,40,20,.06)}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--fundo:#1b1611;--papel:#251e17;--tinta:#efe4d2;--suave:#b7a58d;--borda:#3d3227;--terra:#e07a4a;--terra2:#f09566;--ocre:#e8b04a;--verde:#7cbf6f;--slot:#3a3a3a;--slotb:#555;--sombra:none}}
:root[data-theme="dark"]{--fundo:#1b1611;--papel:#251e17;--tinta:#efe4d2;--suave:#b7a58d;--borda:#3d3227;--terra:#e07a4a;--terra2:#f09566;--ocre:#e8b04a;--verde:#7cbf6f;--slot:#3a3a3a;--slotb:#555;--sombra:none}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:70px}
body{margin:0;background:var(--fundo);color:var(--tinta);font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
a{color:var(--terra)}a:hover{color:var(--terra2)}
code{background:var(--borda);padding:.1em .35em;border-radius:4px;font-size:.9em}
img.px{image-rendering:pixelated;width:20px;height:20px;vertical-align:-4px}
.topo{position:sticky;top:0;z-index:10;display:flex;align-items:center;gap:12px;padding:10px 20px;background:var(--papel);border-bottom:1px solid var(--borda)}
.marca{font-weight:800;font-size:1.2rem;text-decoration:none;color:var(--tinta);display:flex;align-items:center;gap:8px}.marca img{width:28px;height:28px}.marca small{color:var(--suave);font-weight:500}
.lingua{margin-left:auto;text-decoration:none;border:1px solid var(--borda);padding:4px 12px;border-radius:999px}
.menu{display:none;background:none;border:1px solid var(--borda);color:var(--tinta);border-radius:8px;font-size:1.1rem;padding:2px 10px;cursor:pointer}
.corpo{display:flex;max-width:1280px;margin:0 auto}
.lado{position:sticky;top:57px;align-self:flex-start;width:220px;flex:none;padding:24px 12px;height:calc(100vh - 57px);overflow:auto}
.lado a{display:block;padding:6px 12px;border-radius:8px;text-decoration:none;color:var(--suave)}.lado a:hover{background:var(--papel);color:var(--tinta)}
.lado a.atual{background:var(--papel);color:var(--terra);font-weight:600}
main{flex:1;min-width:0;padding:24px 28px 60px}
.capa{border-radius:16px;overflow:hidden;background:#3a2a1a center/cover;min-height:320px;display:flex;align-items:flex-end}
.capa-texto{width:100%;padding:28px;background:linear-gradient(transparent,rgba(20,12,4,.85) 35%);color:#fff7ea}
.capa h1{margin:0;font-size:3rem;letter-spacing:-.02em}.capa p{max-width:720px}
.selos span{display:inline-block;margin:0 6px 6px 0;padding:2px 10px;border-radius:999px;background:rgba(255,255,255,.15);font-size:.85rem}
.botao{display:inline-block;margin:4px 4px 0 0;padding:8px 16px;border-radius:10px;border:1px solid var(--borda);background:var(--papel);color:var(--tinta);text-decoration:none;font-weight:600}
.capa .botao{background:rgba(255,255,255,.12);border-color:rgba(255,255,255,.3);color:#fff}
.botao.forte,.capa .botao.forte{background:var(--terra);border-color:var(--terra);color:#fff}
section{margin-top:48px}h2{font-size:1.7rem;margin:0 0 12px;border-bottom:3px solid var(--ocre);display:inline-block}h2 a{color:inherit;text-decoration:none}
h3{margin:28px 0 10px}.lead{font-size:1.15rem;color:var(--suave)}
.duas{display:grid;grid-template-columns:1fr 1fr;gap:24px}
details{background:var(--papel);border:1px solid var(--borda);border-radius:12px;margin:10px 0;box-shadow:var(--sombra)}
summary{cursor:pointer;padding:12px 16px;font-weight:700}summary small{color:var(--suave);font-weight:400}
details>p{padding:0 16px 12px;margin:0}
.paginas{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:14px;padding:0 16px 16px}
.pagina{background:#fbf3e1;color:#2b1d10;border:1px solid #e6d6b6;border-radius:6px;padding:14px 16px;font-family:Georgia,"Times New Roman",serif;font-size:.95rem;line-height:1.5}
.pagina .cor{color:#7a3b12}.pagina .dica,.dica{color:var(--suave)}.pagina .dica{color:#7d6a52}
.tabela{overflow-x:auto;border:1px solid var(--borda);border-radius:12px;background:var(--papel)}
table{border-collapse:collapse;width:100%;min-width:560px}th,td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--borda);vertical-align:top}
th{background:var(--fundo);font-size:.85rem;text-transform:uppercase;letter-spacing:.04em;color:var(--suave)}td a{text-decoration:none;white-space:nowrap}
.chips{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:8px}.chips li{background:var(--papel);border:1px solid var(--borda);padding:4px 12px;border-radius:999px}
.estagios{display:flex;flex-wrap:wrap;gap:8px;padding:0;list-style:none;counter-reset:e}.estagios li{counter-increment:e;background:var(--papel);border:1px solid var(--borda);border-radius:10px;padding:8px 14px}
.estagios li::before{content:counter(e) " · ";color:var(--terra);font-weight:700}
#busca{width:100%;max-width:420px;padding:10px 14px;border-radius:10px;border:1px solid var(--borda);background:var(--papel);color:var(--tinta);font-size:1rem}
.itens{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px}
.item{background:var(--papel);border:1px solid var(--borda);border-radius:12px;padding:14px;box-shadow:var(--sombra)}.item.destaque{outline:3px solid var(--ocre)}
.item header{display:flex;align-items:center;gap:10px}.item header img.px{width:32px;height:32px}.item h4{margin:0;font-size:1.05rem}.item p{margin:8px 0;font-size:.93rem}
.receita{margin-top:10px;border-top:1px dashed var(--borda);padding-top:8px}.tipo{font-size:.8rem;color:var(--suave);text-transform:uppercase;letter-spacing:.04em}
.linha{display:flex;align-items:center;gap:10px;margin:6px 0}.grade{display:grid;grid-template-columns:repeat(3,34px);gap:2px}.fila{display:flex;flex-wrap:wrap;gap:2px}
.slot{width:34px;height:34px;background:var(--slot);border:2px solid;border-color:var(--slotb) #fff #fff var(--slotb);display:inline-flex;align-items:center;justify-content:center;position:relative}
.slot img{width:26px;height:26px;image-rendering:pixelated}.slot .sem{font-size:.7rem;color:#333}.slot.saida{width:42px;height:42px}.slot.saida img{width:32px;height:32px}
.qtd{position:absolute;right:1px;bottom:-2px;font-weight:800;color:#fff;text-shadow:1px 1px 0 #3f3f3f;font-size:.8rem}
.seta{font-size:1.4rem;color:var(--suave)}.ingr{font-size:.85rem;color:var(--suave)}.ingr a{text-decoration:none}
.etiqueta{background:var(--verde);color:#fff;border-radius:6px;padding:0 6px;text-transform:none;letter-spacing:0}
.larga{margin:16px 0}.larga img{width:100%;border-radius:12px}
.fauna{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:16px}
.bicho{background:var(--papel);border:1px solid var(--borda);border-radius:12px;overflow:hidden;box-shadow:var(--sombra)}.bicho>img{width:100%;aspect-ratio:16/9;object-fit:cover;display:block}
.bicho>div{padding:12px 16px}.bicho h3{margin:0 0 6px}.bicho p{margin:6px 0;font-size:.93rem}
.config{display:grid;grid-template-columns:max-content 1fr;gap:8px 18px;background:var(--papel);border:1px solid var(--borda);border-radius:12px;padding:16px}
.config dt{font-weight:700}.config dd{margin:0;color:var(--suave)}
.galeria{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:10px}.galeria img{width:100%;aspect-ratio:16/10;object-fit:cover;border-radius:10px;display:block}
footer{margin-top:60px;padding-top:16px;border-top:1px solid var(--borda);color:var(--suave);font-size:.9rem}
@media (max-width:860px){.menu{display:block}.lado{position:fixed;top:57px;left:0;bottom:0;height:auto;background:var(--fundo);z-index:9;transform:translateX(-100%);transition:transform .2s;border-right:1px solid var(--borda)}
.lado.aberto{transform:none}main{padding:16px}.duas{grid-template-columns:1fr}.capa h1{font-size:2.2rem}.capa{min-height:260px}.config{grid-template-columns:1fr}.config dd{margin-bottom:8px}}
`;

fs.writeFileSync(path.join(SAIDA, 'estilo.css'), CSS);
fs.writeFileSync(path.join(SAIDA, 'index.html'), pagina('pt'));
fs.writeFileSync(path.join(SAIDA, 'en/index.html'), pagina('en'));
const semIcone = ITENS.filter(i => !iconeArraial(i.id)).map(i => i.id);
console.log(`wiki em ${SAIDA}: ${ITENS.length} itens/blocos, ${Object.keys(RECEITAS).length} receitas, ${copiados.size} ícones` + (semIcone.length ? `; sem ícone: ${semIcone.join(', ')}` : ''));
