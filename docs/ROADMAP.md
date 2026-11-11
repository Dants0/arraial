# Roadmap

Estado atual e próximas fases, alinhado ao [GDD](GDD.md) (seção 17). Detalhe em [TODO.md](TODO.md).

## Feito

| Marco | Conteúdo |
|---|---|
| Marco 1 | Pedreiro, Marco da Obra, Livro de Projetos, catálogo `.nbt`. |
| Marco 2 | Cruzeiro, moradores com casa e rotina, chegada por cama livre. |
| Marco 3 | Armazém, lenhador, roceiro, oleiro, comida diária, Crônica. |
| Marco 4 | Prefeito eleito, soldado, pastor, barreiro, cavouqueiro, monstros atacam moradores. |
| Ajustes | Receitas por madeira, estoque mínimo, prioridade de obras, skins low profile, curral e porteira. |
| Etapa 2 | Crises (seca, enchente, praga, salteadores), Prefeitura, paliçada, comando `/arraial crise`. |
| Vale muito | Mandioca/farinha, itens Blockbench, diálogos, feira e réis, tropeiro e vilas autônomas, epidemia/incêndio, estágios, satisfação. |

## Feito nesta fase — "vale muito" (aguardando teste em jogo)

1. **Mandioca e Casa de Farinha** — cultivo, farinheiro, farinha e beiju (resolve a falta de comida).
2. **Itens Blockbench + diálogos** — itens novos com modelo/textura/animação; conversa NPC↔jogador e NPC↔NPC.
3. **Feira e preços** — moeda (réis), preço por oferta/procura, feirante, compra e venda com o jogador.
4. **Vilas autônomas, tropeiro e reputação** — vilas NPC no mundo, caravanas entre vilas, simulação distante, reputação.
5. **Epidemia/botica e incêndio** — novas crises com resposta própria.
6. **Estágios da vila** — Povoado → Arraial → Vila → Vila próspera → Cidade, com desbloqueios.
7. **Satisfação e legitimidade** — humor dos moradores, legitimidade do prefeito, reeleição.

## Depois

- Festas e calendário (São João, feira grande, padroeiro).
- Famílias e sucessão; blocos coloniais (telha, taipa, cal).
- Eras/modernização das construções.
- Arquétipos regionais (Recôncavo, ribeirinho, sulista) e comunidades com pesquisa (D-011).
- Diplomacia completa (tratados, embargo), política interna e conflitos limitados.

## Pedidos de 2026-11-05 (noite): o grande plano "mundo vivo"

Ordem de implementação (cada fase compila, é testada no servidor e vira commit):

| Fase | Sistema | Estado |
|---|---|---|
| A | **Calendário e festas** — 12 meses de 2 dias, tempo da seca/águas, São João (fogueira + quadrilha), padroeiro (procissão), Feira Grande; quentão, pé-de-moleque, bandeirinhas | ✅ feito |
| B | **Mundo vivo + economia regional** — vilas descarregadas vivem por dia (produção por ofício e estação, consumo, fome, crises, migração, crescimento, decadência, abandono e repovoamento); mercadores entre vilas; rotas com roubo e bloqueio; guerra fecha rotas; preço regional (vizinho com fartura barateia, rota fechada encarece); especialidade econômica; vilas vizinhas nascem perto da vila do jogador; `/arraial mundo` e `/arraial avancar` | ✅ feito |
| C | **Política** — conselho com representantes dos ofícios, grupos de interesse, propostas (impostos, obras, segurança) votadas; jogador apoia/propõe e pode ser rejeitado; popularidade, corrupção e investigação, protestos, liderança hereditária ou eleita | ✅ feito |
| D | **Comércio avançado** — mascate visível (NPC mercador visita a vila), caravanas com inventário real e escolta pelo jogador, contratos de fornecimento assinados na banca, custo de transporte por estrada construída | ✅ feito |
| E | **Território** — fronteiras por chunk, reivindicações, disputas por recursos com causa econômica/histórica, tratados e tributos, postos avançados, escaramuças e paz | ✅ feito |
| F | **Cultura** — tradições por bioma e economia, bandeira e símbolo de cada vila, festivais próprios, cerimônias, intercâmbio cultural, reconhecimento cultural do jogador | ✅ feito |
| G | **Vida pessoal** — necessidades (comida, descanso, convívio), personalidade e preferências, lazer, amizades e rivalidades, objetivos individuais, pequenas missões sociais | ✅ feito |
| H | **Ruínas e ocupação** — vila abandonada vira ruína (deterioração), bandidos ocupam construções abandonadas (esconderijo), jogador pode restaurar; base abandonada do jogador também | ✅ feito |

## Pedido de 2026-11-11: expansão "rios, ofícios e instituições"

Ordem escolhida pelo usuário: 1 → 3 → 4. Cada etapa compila, passa por uma bateria curta no roteiro e vira commit.

### 1. Rios, portos e caminhos

| Etapa | Conteúdo | Estado |
|---|---|---|
| 1A | **Trapiche e barqueiro** — ofício barqueiro (posto: amarra), trapiche erguido na beira d'água (deque sobre o rio), viagens de canoa entre vilas com trapiche: mais carga, mais rápidas, sem salteador; enchente prende a canoa; frete fluvial barateia o preço regional | ✅ |
| 1B | **Porto e navio mercante** — vila no litoral ergue o porto; de tempos em tempos chega um navio que vende sal, ferro e tecido e compra açúcar, café, rapadura, couro e charque | ✅ |
| 1C | **Moinho d'água e monjolo** — o monjolo pila café e mandioca mais rápido; o moinho mói trigo em farinha de trigo (base para o padeiro da etapa 3) | ⏳ |
| 1D | **Ponte** — o engenheiro lança ponte onde a estrada cruza água | ⏳ |
| 1E | **Ferrovia (era Republicana)** — estação; vilas com estação trocam por trem: rápido, barato e seguro | ⏳ |

### 3. Novos ofícios e cadeias (depois de 1)
Ferreiro (ferramentas, ferradura, cravos), tecelão (algodão, pano, rede de dormir), padeiro (pão com farinha de trigo), carpinteiro, alfaiate.

### 4. Instituições (depois de 3)
Igreja com padre (missa, batizado, casamento), escola com professor, cadeia e delegado (julgamentos), Santa Casa.
