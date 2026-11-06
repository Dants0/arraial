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

## 13. Território (fase E)
- [x] Território por chunks (raio cresce com estágio e população); Marco de Fronteira amplia (3x3 chunks); terra anexada é exclusiva
- [x] Aviso na tela ao cruzar fronteira (terras de X / terra disputada / terra de ninguém)
- [x] Tensão diária com causa: terra em comum, mesmos recursos, rixa (reputação), roubos na estrada; tratados acalmam
- [x] Disputa → piora relações → guerra (rotas fechadas), escaramuças por força (gente, soldados, marcos, estágio); milícia ataca a vila carregada
- [x] Paz com tributo diário e anexação da terra disputada; tratados de paz, comércio (frete menor), aliança, fronteira (divide a terra)
- [x] Escrivaninha: jogador vê vizinhos (reputação, tensão, força, tratados) e propõe tratados, que podem ser recusados; `/arraial guerra`

## 14. Cultura (fase F)
- [x] Cultura de cada vila pelo bioma e pela economia: cores, símbolo, bandeira hasteada no Cruzeiro (também para vilas distantes)
- [x] Costumes: hospitaleiros, desconfiados, festeiros, trabalhadores, devotos, comerciantes (efeitos reais)
- [x] 11 tradições (Folia de Reis, Navegantes, Colheita, Congada, Divino, Pinhão, Bumba-meu-boi, Cavalhada, Mutirão, Vaquejada, Santa Bárbara) viram festas no calendário, com estilo próprio (roda, procissão, feira, procissão até a água)
- [x] Intercâmbio cultural entre vizinhos amigos com estrada batida ou tratado
- [x] Reconhecimento do jogador (participar de festas, levar doces): conhecido → amigo da festa (ganha a bandeira) → filho da terra
- [x] Cerimônias: posse do prefeito com fogos; luto quando um morador morre

## 15. Vida pessoal (fase G)
- [x] Personalidade (2 traços entre 10), comida preferida, lazer preferido, sonho próprio
- [x] Necessidades: energia (cai de dia, volta dormindo; exausto para e descansa), convívio (cai sozinho, sobe com prosa e festa)
- [x] Rotina: fim de tarde é lazer (passear, rezar no Cruzeiro, beira d'água, perto do fogo, prosear)
- [x] Amizades e rivalidades entre moradores por compatibilidade de traços; rivais brigam em vez de conversar
- [x] Amizade com o jogador: conversa diária, comida preferida; compadre/comadre (30), presente (60); confidência do sonho
- [x] Missões sociais: trazer algo, levar bilhete, fazer as pazes com um rival; pagam réis, amizade e reputação
- [x] Humor pessoal entra na satisfação da vila

## 16. Ruínas e ocupação (fase H)
- [x] Vila abandonada se deteriora dia a dia (blocos caem, pedra com musgo e rachada, teias) até virar ruína
- [x] Vizinho com gente sobrando recoloniza as ruínas; ou bandidos fazem esconderijo (saqueiam vizinhos, estradas mais perigosas, saque fica no armazém)
- [x] Jogador limpa o esconderijo (réis, reputação com vizinhos) e repovoa; moradores reconstroem
- [x] Base do jogador (última cama) abandonada 8+ dias fora de território pode ser tomada por bandidos; retomar ao limpar
- [x] `/arraial abandonar`, `/arraial ocupar`, `/arraial base`

## 17. Itens, JEI e Blockbench
- [x] Crafts de tudo + JEI (páginas de informação); facão, peixeira, foice, couro do vaqueiro, cantil, saco de saque, comenda; 23 conquistas
- [x] Modelos 3D como projetos Blockbench (tools/blockbench) + exportador (tools/de_blockbench.js) + plugin do Blockbench

## 18. Famílias e sucessão
- [x] Sobrenome; casamento entre amigos (afinidade ≥ 30) com fogos; filhos (crianças menores que crescem em 3 dias)
- [x] Sucessão no coronelismo: filho mais velho ou cônjuge do último chefe; parentes votam em parentes
- [x] Comandos de teste: /arraial casamento, /arraial nascimento

## 19. Eras e modernização
- [x] Colonial → Imperial (Vila + olaria + 40 réis) → Republicana (Vila próspera + Capela + Cartório + 80 réis)
- [x] Reforma das construções (taipa→tijolo→cal; telhado de telha; pedra lavrada; lampião) e das ruas (terra→calçamento→paralelepípedo), com material do armazém
- [x] Engenheiro faz rua e poste no estilo da era; Capela (Imperial) e Chafariz (Republicana)
- [x] Comandos de teste: /arraial era (colonial, imperial, republicana), /arraial obra_pronta (projeto)

## 20. Teste automático no cliente
- [x] Roteiro (ARRAIAL_ROTEIRO=1 runClient) com fotos e verificações: itens, vaqueiro, foice, cantil, saque, festa, política, missão, família, território, chancelaria (tratado, embargo), mapa, eras com blocos coloniais

## 21. Blocos coloniais, diplomacia e mapa
- [x] Blocos: taipa, telha (bloco, escada, laje), parede caiada (cal), azulejo; texturas em tools/gerar_blocos_coloniais.ps1; receitas e corte
- [x] Eras usam os blocos: Colonial ergue de taipa; Imperial tijolo e telhado de telha; Republicana cal com barra de azulejo; pedreiros constroem já no estilo da era
- [x] Chancelaria (tela da Escrivaninha): mapa da região, vizinhos, propor paz/comércio/aliança/fronteira, exigir tributo, romper tratado, declarar e suspender embargo (1 papel, 1 proposta por dia)
- [x] Embargo: fecha a estrada (sem mercadores, tropeiros, contratos; preços sobem), rompe comércio e aliança; vilas autônomas em disputa também embargam
- [x] Mapa da Região (item): biomas, terras com a cor da bandeira, terra disputada, estradas por estado, ruínas, bandidos, jogador e base
- [x] Nomes de arraial sem repetição (lista maior)

## 22. Guia do Arraial (manual)
- [x] 49 páginas: capa, sumário clicável, Primeiros dias (early), Crescendo (mid), Grandeza (late), Ofícios, Comércio, Itens e blocos, Dicas
- [x] Roteiro mede todas as páginas em pt_br e en_us (cabem em 14 linhas) e fotografa algumas

## Ideias da comunidade
- [ ] **Agropecuária do Brasil** (viewer, 2026-11-06): fazendas e ciclos econômicos, por exemplo:
  - fazenda como obra própria com fazendeiro e colonos, e terra lavrada fora do núcleo da vila;
  - engenho de cana (moenda, açúcar, rapadura) na era Colonial; cafezal e terreiro de secagem na Imperial;
  - gado de corte e de leite com o vaqueiro: charque, couro, queijo coalho; milho (pamonha, cuscuz), algodão (tecelagem);
  - especialidade "agropecuária" na economia regional, com preços e contratos próprios.

## Próximos
- [ ] Teste em jogo pelo usuário (T)

## Depois
- [ ] Teste em jogo de tudo acima (T)

