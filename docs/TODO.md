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

## 9. Calendário e festas
- [x] Calendário: 12 meses de 2 dias; tempo da seca (abr–out) e das águas; estação pesa nas crises
- [x] São João: fogueira perto do Cruzeiro, quadrilha em roda, fogos ao entardecer
- [x] Padroeiro (santo do nome do arraial, mês próprio): procissão e fogos
- [x] Feira Grande (1º de março e setembro, com banca): 15% de desconto, rende réis
- [x] Quentão, pé-de-moleque, bandeirinhas; festa farta/enfeitada rende mais satisfação
- [x] `/arraial festa <tipo|fim>`

## 10. Mundo vivo e economia regional
- [x] Simulação diária das vilas descarregadas (produção, consumo, fome, crises, renda)
- [x] Migração para o melhor vizinho; crescimento; decadência; abandono; repovoamento pelo jogador
- [x] Mercadores entre vilas; roubos; rota fechada por roubos ou guerra
- [x] Preço regional (vizinho com fartura barateia, rota fechada encarece)
- [x] Especialidade econômica; vilas vizinhas nascem perto da vila do jogador
- [x] História regional entra na Crônica quando a vila carrega; `/arraial mundo`, `/arraial avancar <dias>`

## 11. Política (fase C)
- [x] Grupos de interesse por ofício (Lavoura, Ofícios, Ordem, Comércio, Povo) e conselho com um representante de cada
- [x] Regime eleito (voto entre conselheiros) ou coronelismo hereditário (35% dos povoados autônomos)
- [x] Propostas: imposto ±, segurança, festa, obra, cidadania honorária, investigação, nova eleição; votação no dia seguinte
- [x] Jogador propõe na Urna (papel) e pode ser rejeitado; campanha com réis, com risco de escândalo
- [x] Imposto enche o tesouro e pesa na satisfação; corrupção do prefeito, boatos e investigação
- [x] Protestos de grupos insatisfeitos (param o trabalho no Cruzeiro) viram proposta
- [x] `/arraial conselho propor|votar`, `/arraial cronica`

## 12. Comércio avançado (fase D)
- [x] Mascate: NPC de uma vila vizinha com mercadoria real; compra e vende com o jogador; volta ao anoitecer
- [x] Escolta: espada na mão no tropeiro saindo; risco cai para 1/4; pagamento no Cruzeiro
- [x] Encomendas do arraial ao jogador (banca + papel), com prazo e prêmio
- [x] Contratos de fornecimento entre vilas (comida, pedra, madeira), preferindo especialistas; rota fechada rompe
- [x] Estrada batida (10+ viagens): frete 30% menor e mais segura; `/arraial mascate`

## Próximos (ver ROADMAP, fases E–H)
- [ ] Teste em jogo de tudo acima (T)
- [ ] E Território · F Cultura · G Vida pessoal · H Ruínas e ocupação
- [ ] Famílias e sucessão; blocos coloniais
- [ ] Eras/modernização
- [ ] Tratados e embargo (diplomacia completa); tela de diplomacia
