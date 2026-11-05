# TODO

Legenda: `[x]` feito, compilado e com boot do servidor · `[ ]` a fazer · (T) testado em jogo pelo usuário.

## Documentação
- [x] GDD versionado em `docs/GDD.md`
- [x] `ARCHITECTURE.md`, `DECISIONS.md`, `ROADMAP.md`, `TODO.md`

## 1. Mandioca e Casa de Farinha
- [x] Bloco de cultivo mandioca (4 estágios) + item maniva/mandioca
- [x] Itens farinha de mandioca e beiju (comida), texturas
- [x] Ofício farinheiro com posto "forno de farinha" e receitas
- [x] Roceiro planta mandioca; maniva pode ser achada no mato (grama)
- [x] Planta "casa_de_farinha" no catálogo; prefeito pede com 5 moradores
- [x] Skin do farinheiro

## 2. Itens Blockbench e diálogos
- [x] Modelos `.bbmodel` em `tools/blockbench` (gerados por `tools/para_blockbench.js`) + JSON do jogo
- [x] Cuia de garapa (beber, efeito), rapadura, paçoca, chapéu de couro 3D, lampião, réis, garrafada
- [x] Texturas animadas (lampião, boca do forno) via `.mcmeta`
- [x] Conversa jogador → morador por contexto (ofício, fome, casa, crise, prefeito, doença, clima)
- [x] Dar comida a quem tem fome / garrafada a quem está doente
- [x] Moradores conversam entre si quando se encontram (balão na placa + chat próximo)

## 3. Feira e preços
- [x] Moeda réis e tesouro do arraial
- [x] Preço local por oferta/procura, suavizado, por dia
- [x] Banca de feira + ofício feirante (pregões)
- [x] Jogador compra/vende na banca; reputação dá desconto

## 4. Vilas autônomas, tropeiro e reputação
- [x] Registro de vilas no mundo (`arraial_vilas.json`)
- [x] Povoado autônomo surge longe do jogador (Cruzeiro, armazém, casas, roça, moradores)
- [x] Simulação distante (produção/consumo agregado dos dias perdidos)
- [x] Tropeiro leva excedente e traz o que falta; caravana pode ser assaltada
- [x] Reputação entre vilas e com o jogador; ajuda em crise rende muita reputação

## 5. Epidemia/botica e incêndio
- [x] Crise epidemia: doentes não trabalham; contágio; botica reduz; boticário cura com garrafada
- [x] Crise incêndio: fogo numa construção de madeira; moradores apagam com balde
- [x] Reparos: prefeito manda reconstruir blocos que sumiram das obras prontas

## 6. Estágios da vila
- [x] Povoado → Arraial → Vila → Vila próspera → Cidade (nome muda junto)
- [x] Desbloqueios por estágio (feira, botica, rancho, paliçada); anúncio, foguete e Crônica

## 7. Satisfação e legitimidade
- [x] Satisfação diária por comida, casa, saúde, segurança, serviços e crise
- [x] Muito insatisfeitos vão embora; muito satisfeitos atraem gente mais rápido
- [x] Legitimidade do prefeito; abaixo de 25 por 2 dias → nova eleição

## 8. Pedidos de 2026-11-05 (tarde)
- [x] Cavouqueiro e barreiro usam escada no próprio buraco (não ficam presos)
- [x] Morador com fome come assim que chega comida; rotinas toleram /time set
- [x] Pescador (Covo): pesca na água perto do posto
- [x] Caçador (Varal de Caça): caça bicho do mato; nunca curral, preso na corda, manso, cavalo, com nome ou filhote; deixa 3 de cada espécie
- [x] Engenheiro (Prancheta): ruas de terra batida até o Cruzeiro, pontes de tábua sobre água, postes de luz
- [x] Relações Públicas (Escrivaninha): missões aos vizinhos; aliados mandam comida nas crises, inimigos mandam salteadores
- [x] Guia do Arraial (livro de 18 páginas; o fundador ganha um)

## Próximos
- [ ] Teste em jogo de tudo acima (T)
- [ ] Festas e calendário (São João, feira grande, padroeiro)
- [ ] Famílias e sucessão; blocos coloniais
- [ ] Eras/modernização
- [ ] Tratados e embargo (diplomacia completa); tela de diplomacia
