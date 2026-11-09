// Checks the mod's crafting recipes for clashes with vanilla recipes and with each other
// (same shape and overlapping ingredients = the game picks one and the other becomes uncraftable).
// Usage: node tools/verificar_receitas.js <folder with vanilla data/minecraft (recipe/ and tags/item/)>
//   e.g. unzip the Minecraft jar's data/minecraft/recipe and data/minecraft/tags/item into that folder.
'use strict';
const fs = require('fs'), path = require('path');
const V = path.join(process.argv[2], 'data', 'minecraft');
const MEU = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'arraial', 'recipe');

function tag(t) {
  const f = path.join(V, 'tags', 'item', t.replace('minecraft:', '') + '.json');
  if (!fs.existsSync(f)) return [t];
  return JSON.parse(fs.readFileSync(f)).values.flatMap(x => (x.startsWith('#') ? tag(x.slice(1)) : [x]));
}
function itens(i) {
  if (Array.isArray(i)) return i.flatMap(itens);
  if (i && typeof i === 'object') return itens(i.item || (i.tag && '#' + i.tag) || i.items);
  if (typeof i !== 'string') return [];
  return i.startsWith('#') ? tag(i.slice(1)) : [i];
}
function normalizar(r) {
  if (r.type === 'minecraft:crafting_shaped') {
    let linhas = r.pattern.map(l => [...l].map(c => (c === ' ' ? null : itens(r.key[c]))));
    // trim empty rows and columns, as the game does
    while (linhas.length && linhas[0].every(c => !c)) linhas.shift();
    while (linhas.length && linhas[linhas.length - 1].every(c => !c)) linhas.pop();
    const larg = Math.max(...linhas.map(l => l.length));
    linhas = linhas.map(l => [...l, ...Array(larg - l.length).fill(null)]);
    while (linhas.length && linhas.every(l => !l[0])) linhas = linhas.map(l => l.slice(1));
    while (linhas.length && linhas.every(l => !l[l.length - 1])) linhas = linhas.map(l => l.slice(0, -1));
    return { tipo: 'forma', grade: linhas };
  }
  if (r.type === 'minecraft:crafting_shapeless') return { tipo: 'livre', grade: r.ingredients.map(itens) };
  return null;
}
const cruza = (a, b) => a.some(x => b.includes(x));
function conflita(a, b) {
  if (a.tipo !== b.tipo) return false;
  if (a.tipo === 'livre') {
    if (a.grade.length !== b.grade.length) return false;
    const usados = new Set();
    return a.grade.every(x => {
      for (let j = 0; j < b.grade.length; j++) if (!usados.has(j) && cruza(x, b.grade[j])) { usados.add(j); return true; }
      return false;
    });
  }
  const igual = (g1, g2) => g1.length === g2.length && g1.every((l, i) => l.length === g2[i].length
    && l.every((c, j) => (c === null) === (g2[i][j] === null) && (c === null || cruza(c, g2[i][j]))));
  return igual(a.grade, b.grade) || igual(a.grade.map(l => [...l].reverse()), b.grade);
}
const ler = (pasta, f) => [f, normalizar(JSON.parse(fs.readFileSync(path.join(pasta, f))))];
const vanilla = fs.readdirSync(path.join(V, 'recipe')).map(f => ler(path.join(V, 'recipe'), f)).filter(x => x[1]);
const meus = fs.readdirSync(MEU).map(f => ler(MEU, f)).filter(x => x[1]);
let n = 0;
for (const [f, r] of meus) {
  for (const [vf, vr] of vanilla) if (conflita(r, vr)) { console.log('CONFLITO com o jogo:', f, 'x', vf); n++; }
}
for (let i = 0; i < meus.length; i++) for (let j = i + 1; j < meus.length; j++) {
  if (conflita(meus[i][1], meus[j][1])) { console.log('CONFLITO entre receitas do mod:', meus[i][0], 'x', meus[j][0]); n++; }
}
console.log(n === 0 ? 'nenhum conflito em ' + meus.length + ' receitas' : n + ' conflito(s)');
process.exit(n === 0 ? 0 : 1);
