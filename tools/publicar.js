// Publishes the built jar as a new version on Modrinth and/or CurseForge.
//
// Usage: node tools/publicar.js <modrinth|curseforge|todos> [--teste]
//   - version and jar come from gradle.properties (build first: ./gradlew build);
//   - changelog from docs/publicacao/changelog-<version>.md;
//   - release type from the version (alpha/beta in the name, else release);
//   - tokens are read from files outside the repository, never printed:
//       %USERPROFILE%\.modrinth_token.txt, %USERPROFILE%\.curseforge_token.txt
//   --teste shows what would be sent without sending anything.
'use strict';
const fs = require('fs'), path = require('path'), os = require('os');

const MODRINTH_PROJETO = 'Deu15Ni5';
const CURSEFORGE_PROJETO = process.env.CURSEFORGE_PROJETO || '';   // filled in once the project exists
const MC = '26.3';
// CurseForge game version ids (minecraft.curseforge.com/api/game/versions).
const CF_VERSOES = { minecraft: 17045, fabric: 7499, java25: 14454, client: 9638, server: 9639 };

const raiz = path.join(__dirname, '..');
const props = fs.readFileSync(path.join(raiz, 'gradle.properties'), 'utf8');
const versao = props.match(/^version=(.+)$/m)[1].trim();
const jar = path.join(raiz, 'build', 'libs', `arraial-${versao}.jar`);
const changelogArq = path.join(raiz, 'docs', 'publicacao', `changelog-${versao}.md`);
const tipo = /alpha/.test(versao) ? 'alpha' : /beta|rc/.test(versao) ? 'beta' : 'release';
const alvo = process.argv[2] || '';
const teste = process.argv.includes('--teste');
const AGENTE = 'Dants0/arraial-publicacao';

function token(nome) {
  const f = path.join(os.homedir(), nome);
  if (!fs.existsSync(f)) throw new Error(`token não encontrado: ${f}`);
  return fs.readFileSync(f, 'utf8').replace(/^﻿/, '').trim();
}
function exigir() {
  if (!['modrinth', 'curseforge', 'todos'].includes(alvo)) throw new Error('uso: node tools/publicar.js <modrinth|curseforge|todos> [--teste]');
  if (!fs.existsSync(jar)) throw new Error(`jar não encontrado (rode ./gradlew build): ${jar}`);
  if (!fs.existsSync(changelogArq)) throw new Error(`changelog não encontrado: ${changelogArq}`);
}

async function modrinth(changelog) {
  const data = {
    project_id: MODRINTH_PROJETO, name: `Arraial ${versao}`, version_number: versao, changelog,
    dependencies: [{ project_id: 'P7dR8mSH', dependency_type: 'required' }, { project_id: 'u6dRKJwZ', dependency_type: 'optional' }],
    game_versions: [MC], version_type: tipo, loaders: ['fabric'], featured: true, status: 'listed', file_parts: ['jar'], primary_file: 'jar'
  };
  if (teste) return console.log('[modrinth] enviaria:', { ...data, changelog: changelog.length + ' caracteres' });
  const t = token('.modrinth_token.txt');
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  form.append('jar', new Blob([fs.readFileSync(jar)], { type: 'application/java-archive' }), path.basename(jar));
  const r = await fetch('https://api.modrinth.com/v2/version', { method: 'POST', headers: { Authorization: t, 'User-Agent': AGENTE }, body: form });
  const j = await r.json();
  if (!r.ok) throw new Error('[modrinth] ' + r.status + ' ' + JSON.stringify(j));
  // The environment now lives on each version (API v3).
  const e = await fetch(`https://api.modrinth.com/v3/version/${j.id}`, {
    method: 'PATCH', headers: { Authorization: t, 'User-Agent': AGENTE, 'Content-Type': 'application/json' },
    body: JSON.stringify({ environment: 'client_and_server' })
  });
  console.log(`[modrinth] versão ${j.version_number} publicada (id ${j.id}); ambiente: ${e.status === 204 ? 'ok' : 'falhou ' + e.status}`);
}

async function curseforge(changelog) {
  if (!CURSEFORGE_PROJETO) throw new Error('[curseforge] defina o Project ID (CURSEFORGE_PROJETO)');
  const meta = {
    changelog, changelogType: 'markdown', displayName: `Arraial ${versao}`, releaseType: tipo,
    gameVersions: [CF_VERSOES.minecraft, CF_VERSOES.fabric, CF_VERSOES.java25, CF_VERSOES.client, CF_VERSOES.server],
    relations: { projects: [{ slug: 'fabric-api', type: 'requiredDependency' }, { slug: 'jei', type: 'optionalDependency' }] }
  };
  if (teste) return console.log('[curseforge] enviaria para o projeto', CURSEFORGE_PROJETO, { ...meta, changelog: changelog.length + ' caracteres' });
  const form = new FormData();
  form.append('metadata', JSON.stringify(meta));
  form.append('file', new Blob([fs.readFileSync(jar)], { type: 'application/java-archive' }), path.basename(jar));
  const r = await fetch(`https://minecraft.curseforge.com/api/projects/${CURSEFORGE_PROJETO}/upload-file`, {
    method: 'POST', headers: { 'X-Api-Token': token('.curseforge_token.txt'), 'User-Agent': AGENTE }, body: form
  });
  const txt = await r.text();
  if (!r.ok) throw new Error('[curseforge] ' + r.status + ' ' + txt);
  console.log(`[curseforge] arquivo ${versao} enviado (id ${JSON.parse(txt).id})`);
}

(async () => {
  exigir();
  const changelog = fs.readFileSync(changelogArq, 'utf8');
  console.log(`Arraial ${versao} (${tipo}) · ${path.basename(jar)} · ${(fs.statSync(jar).size / 1024).toFixed(0)} KB`);
  if (alvo === 'modrinth' || alvo === 'todos') await modrinth(changelog);
  if (alvo === 'curseforge' || alvo === 'todos') await curseforge(changelog);
})().catch(e => { console.error(e.message); process.exit(1); });
