# Arquitetura do Arraial

Mod Fabric para Minecraft 26.3 (mapeamentos Mojang), Java 25, Gradle (Loom). Pacote raiz
`com.guilhermegoes.arraial`, modid `arraial`. Requisitos de design em [GDD.md](GDD.md);
decisões em [DECISIONS.md](DECISIONS.md); plano em [ROADMAP.md](ROADMAP.md) e [TODO.md](TODO.md).

## Visão geral

O servidor é a autoridade. Tudo que é simulação roda no servidor; o cliente só desenha
(entidade, skins, placa de nome). Não há pacotes de rede próprios: status e nomes viajam por
`SynchedEntityData` e mensagens de chat.

```text
Cruzeiro (ArraialBlockEntity)   ← coração da vila: nome, crônica, prefeito, crise, economia
 ├─ Prefeitura (lógica)         ← eleição, despacho diário, pedidos de obra, remanejamento
 ├─ Crise                       ← seca, enchente, praga, salteadores, epidemia, incêndio
 ├─ Feira / Preços              ← preço local por oferta e procura, vendas ao jogador
 └─ Registro de vilas (Vilas + SavedData) ← índice de vilas, reputação, simulação distante
Marco da Obra (CanteiroBlockEntity) ← um projeto do catálogo + progresso
 └─ PlanoDeObra                 ← etapas (posição, bloco) a partir de .nbt ou procedural (Muralha)
Armazém (ArmazemBlockEntity)    ← estoque compartilhado da vila
MoradorEntity                   ← NPC: nome, ofício, casa, posto, fome, status, falas
 └─ Goals (npc/*Goal)           ← um por ofício + dormir, fugir, conversar, prefeito
```

## Pacotes

| Pacote | Conteúdo |
|---|---|
| `registry` | Registro de blocos, itens, entidades, POIs, componentes e abas criativas. |
| `vila` | Cruzeiro, armazém, crônica, prefeitura, crises, feira, comandos, registro de vilas. |
| `obra` | Marco, catálogo, plano de obra, terreno, prioridade, muralha. |
| `npc` | Entidade morador, profissões e goals de IA, porteira (portões), ameaças, diálogos. |
| `trabalho` | Receitas, demanda (o que falta fabricar) e estoque mínimo do prefeito. |
| `client` | Renderizador do morador e registros só de cliente. |
| `tools/` (fora do jar) | Geradores de plantas `.nbt`, skins e texturas; modelos Blockbench. |

## Ciclos

- **Cruzeiro** (a cada 1 s): crise; a cada 5 s: chegada de moradores e prefeitura.
- **Prefeitura** (uma vez por manhã): ajusta pedreiros e despacha uma obra (casa, oficina,
  defesa, serviços) depois de checar viabilidade dos materiais (`Demanda.inviavel`) e terreno.
- **Morador** (por tick, IA vanilla `Goal`): goals por prioridade, cada ofício checa a cada ~1 s
  se tem trabalho (`OficioGoal.hora`). Comer uma vez por dia; fome por dias leva à partida.
- **Demanda**: necessidade bruta (obras, comida, metas) expandida por receitas, com urgência.
  Artesãos fazem o pedido mais urgente que conseguem; obras reservam material por prioridade.

## Persistência

- Estado da vila no `ArraialBlockEntity` (ValueInput/ValueOutput). O índice global de vilas e a
  memória entre vilas (reputação) ficam em `SavedData` de nível (ver DECISIONS D-007).
- Obras no `CanteiroBlockEntity` (projeto, progresso, prioridade, tamanho da muralha).
- Moradores salvam ofício, casa, posto, fome e vila na própria entidade.

## Desempenho

- Índices em memória de Cruzeiros e Marcos carregados (sem varrer o mundo).
- Varreduras limitadas por raio (roça 9, vila 48) e por intervalo (1–5 s).
- Simulação distante agregada: vilas sem jogador perto avançam produção/consumo por dia sem
  entidades (nível C do GDD).

## Convenções

- Nomes de domínio em português (vila, obra, morador); comentários em inglês.
- Novos valores de enums salvos por ordinal vão sempre no fim (`Profissao`).
- Toda novidade termina com build, boot do servidor dedicado e um roteiro de testes.
