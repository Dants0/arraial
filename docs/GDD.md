# ARRAIAL — Game Design Document e Especificação Técnica

> **Status:** Documento inicial de concepção (v0.1)  
> **Produto:** Mod de Minecraft com simulação de povoados históricos e culturais brasileiros  
> **Referências:** Millénaire (vilas autônomas) e MineColonies (gestão de assentamentos)  
> **Direção recomendada:** híbrido — jogador administra seu arraial e convive com comunidades autônomas  
> **Idioma:** Português brasileiro

## 1. Visão e pilares

**Pitch:** Fundar, desenvolver e governar um arraial inspirado nas diferentes regiões e períodos históricos do Brasil, em um mundo no qual outras comunidades também crescem, produzem, comerciam, formam alianças, disputam recursos e preservam suas identidades culturais.

**Pilares:**
1. **Mundo vivo:** vilas NPC continuam evoluindo sem depender do jogador.
2. **Brasil plural:** culturas regionais, povos indígenas, comunidades quilombolas, tradições e arquitetura representados com pesquisa e respeito.
3. **Economia sistêmica:** cadeias produtivas, preços, necessidades e logística com consequências observáveis.
4. **História emergente:** decisões, eventos, famílias e conflitos deixam marcas persistentes.
5. **Gestão acessível:** profundidade por camadas; o jogador pode delegar tarefas.
6. **Minecraft primeiro:** construções em blocos, integração com biomas, recursos, exploração e sobrevivência.

**Não objetivos iniciais:** reconstituição histórica exata, simulação individual de milhares de NPCs, estratégia militar em larga escala ou IA generativa obrigatória.

## 2. Loop principal de gameplay

1. Explorar e identificar local apropriado, recursos e comunidades vizinhas.
2. Fundar assentamento, nomeá-lo e escolher prioridades.
3. Recrutar famílias e atribuir moradia e profissões.
4. Produzir alimentos, materiais e ferramentas.
5. Construir e melhorar edifícios, rotas e serviços.
6. Participar de feiras, caravanas e acordos com outras vilas.
7. Reagir a crises, tensões internas e disputas externas.
8. Desbloquear marcos, especializações e novos núcleos.
9. Registrar a história do arraial e reavaliar decisões.

**Progressão sugerida (valores configuráveis, não regras históricas):**

| Estágio | População simulada | Requisitos principais | Desbloqueios |
|---|---:|---|---|
| Povoado | 5–15 | moradias, água, alimento | roça, depósito, carpintaria |
| Arraial | 15–40 | produção estável, ofícios | feira, botica, serviços |
| Vila | 40–100 | infraestrutura e reconhecimento | administração, tributos, diplomacia formal |
| Vila próspera | 100–200 | especialização e comércio | mercado, instituições e núcleos satélites |
| Cidade | 200+ | serviços, logística e estabilidade | influência regional |
| Centro regional | variável | rede de comunidades e acordos | conselho regional e políticas intermunicipais |

**Nota de desempenho:** população total não equivale a NPCs renderizados e com IA de pathfinding ativa.

## 3. Geração do mundo e identidades regionais

Cada comunidade tem `regionalStyle`, `historicalContext`, `foundingCulture`, `economicSpecialization`, `government`, `traditions`, `relations`, `territory` e `history`. Não associar uma região a uma única etnia ou tradição.

### 3.1 Arquétipos iniciais

| Arquétipo | Atividades | Edificações marcantes | Vulnerabilidades |
|---|---|---|---|
| Arraial mineiro | mineração, ourivesaria, ferraria, tropeirismo | oficina, mercado, capela, casario | exaustão mineral, contrabando |
| Vila do Recôncavo | mandioca, pesca, cana, navegação | casa de farinha, engenho, porto | enchentes, epidemias, conflitos fundiários |
| Povoado ribeirinho | pesca, extrativismo, transporte fluvial | trapiche, armazém, casas adaptadas | cheia, vazante, isolamento |
| Arraial sertanejo | pecuária, couro, roças, feiras | curral, açude, feira | seca, escassez hídrica |
| Colônia sulista | agricultura, carpintaria, moagem | moinho, serraria, casas regionais | frio, disputas de terra |

### 3.2 Comunidades e representação

- Aldeias indígenas e comunidades quilombolas devem ser comunidades completas, com autonomia, economia, diplomacia, história e objetivos próprios; não facções inimigas por padrão.
- Contextos históricos são pesquisáveis e explicitamente configurados. Evitar misturar períodos sem sinalizar história alternativa.
- Representar escravidão, expropriação e desigualdade, se incluídas, criticamente, com resistência e transformação social; nunca tratá-las como mero buff de produção.
- Consultar fontes históricas e culturais e, idealmente, pessoas das comunidades retratadas antes de produzir conteúdo específico.

## 4. Moradores, famílias e IA

### 4.1 Dados do morador

- Identificador persistente, nome, idade/faixa etária, família e residência.
- Profissão, nível de habilidade, experiência, posto de trabalho e jornada.
- Necessidades: alimentação, descanso, saúde, segurança e sociabilidade.
- Inventário e renda simplificados; vínculos e reputação local.
- Memória compacta de eventos significativos, não logs infinitos.
- Estados: trabalhar, transportar, comprar, descansar, socializar, fugir, patrulhar, viajar.

### 4.2 Comportamento

- Planejamento por tarefas e prioridades (utility AI ou GOAP simplificado).
- Rotinas condicionadas a horário, clima, segurança, estoques e eventos.
- Reatribuição de tarefas quando um prédio é destruído ou recursos acabam.
- Progressão de aprendiz para mestre, com transmissão de conhecimento.
- Migração voluntária por emprego, segurança, parentesco ou crise.
- Envelhecimento e sucessão como recurso opcional de configuração.

### 4.3 Profissões

| Cadeia | Profissões | Entradas | Saídas |
|---|---|---|---|
| Agricultura | lavrador, agricultor, meeiro | sementes, ferramentas, água | alimentos e fibras |
| Pecuária | vaqueiro, criador | pasto, água | animais, couro, alimentos |
| Alimentação | farinheiro, padeiro, cozinheiro | mandioca, trigo, lenha | farinha, pão, refeições |
| Extração | minerador, garimpeiro | ferramentas, jazidas | minérios e metais |
| Ofícios | carpinteiro, pedreiro, oleiro, ferreiro, ourives | madeira, barro, pedra, metais | componentes, equipamentos |
| Comércio | feirante, mascate, comerciante | mercadorias, capital | distribuição e renda |
| Logística | tropeiro, barqueiro, carreteiro | animais, embarcações, rotas | entregas e notícias |
| Serviços | boticário, professor, barbeiro | insumos e instalações | saúde, formação, bem-estar |
| Cultura | músico, artesão, organizador de festas | instrumentos, materiais | prestígio, eventos, bens |
| Administração | escrivão, tesoureiro, fiscal | registros, instituições | tributos, decisões |
| Segurança | guarda, capitão da guarda | equipamentos, suprimentos | proteção e patrulha |

**Profissões especiais:** tropeiro pode carregar notícias e acordos; mestre de ofício treina aprendizes; pregoeiro comunica acontecimentos; emissário realiza diplomacia.

## 5. Edifícios e construção

**Princípio:** edifícios têm custos, mão de obra, área, nível, capacidade, manutenção, produção e efeitos sociais.

| Edifício | Efeito sistêmico |
|---|---|
| Casa de taipa / alvenaria | moradia, conforto, resistência |
| Roça | produção agrícola |
| Casa de farinha | processa mandioca |
| Engenho | processa cana |
| Curral | criação animal |
| Ferraria | ferramentas e equipamento |
| Olaria | tijolos, telhas, cerâmica |
| Carpintaria | peças de madeira e mobiliário |
| Feira livre | comércio e encontro social |
| Armazém | armazenamento e logística |
| Botica | tratamento e mitigação de doenças |
| Escola | formação e transmissão de conhecimento |
| Capela e espaços comunitários | sociabilidade e tradições, conforme contexto |
| Casa de Câmara | administração e decisões |
| Cadeia / posto de guarda | justiça e segurança |
| Quartel / paliçada / torre | defesa e vigilância |
| Porto / trapiche | comércio fluvial |
| Estalagem | hospedagem de viajantes |
| Chafariz / poço / açude | abastecimento hídrico |

**Construção:** o jogador escolhe um blueprint e uma posição; construtores solicitam materiais, executam etapas e desbloqueiam funcionalidade ao concluir. Níveis de melhoria alteram planta, capacidade e manutenção. Suportar rotação, pré-visualização, validação de terreno, cancelamento e recuperação de materiais conforme regras.

## 6. Economia e produção

### 6.1 Fluxo

Extração/agricultura → processamento → armazenamento → transporte → comércio/consumo → arrecadação/reinvestimento.

### 6.2 Regras

- Receitas de produção definidas em dados, não hardcoded.
- Estoques por prédio e reservas estratégicas por vila.
- Consumo de famílias e instituições por ciclo.
- Preço local derivado de preço-base, oferta, demanda, custo de transporte e eventos, com limites para evitar oscilações absurdas.
- Salários, tributos e orçamento público simplificados; políticas ajustáveis.
- Moedas, escambo e crédito comercial conforme contexto temporal.
- Importações e exportações com negociação de lotes.
- Falta de alimento gera piora de saúde, migração, insatisfação e, em casos graves, mortalidade opcional/configurável.

### 6.3 Exemplo de fórmula inicial

```text
preco = clamp(
  precoBase * fatorEscassez * fatorTransporte * fatorEvento,
  precoMinimo,
  precoMaximo
)
```

**Importante:** a fórmula é ponto de partida para balanceamento, não modelo econômico definitivo. Atualizar preços por ciclos e suavizar variações para impedir exploits.

### 6.4 Cadeias iniciais do MVP

1. Madeira → tábuas → construção.
2. Mandioca → farinha → consumo e venda.
3. Barro → tijolos → construção.
4. Minério → metal → ferramentas.
5. Alimento excedente → feira → renda e impostos.

## 7. Diplomacia e relações entre vilas

Cada par de comunidades mantém:

- Reputação (`-100..100`), confiança, medo e interesses comerciais.
- Tratados ativos, histórico de ajuda/prejuízo, reivindicações e conflitos.
- Acesso a rotas, direitos de passagem, tarifas e embargos.
- Líderes com perfis de decisão distintos (pragmático, expansionista, conciliador etc.).

**Ações:** enviar emissário, propor troca, negociar tratado, pedir ajuda, enviar doações, celebrar festividades conjuntas, mediar conflitos, criar alianças, reivindicar território, impor embargo e negociar paz.

**Memória diplomática:** ajuda durante uma epidemia pode aumentar a disposição de retribuir durante uma seca futura. Eventos relevantes persistem mesmo após troca de líder, com decaimento gradual de efeitos.

**Comunidades autônomas:** tomam decisões próprias baseadas em necessidades, estoques, segurança, objetivos e relações, sem depender da presença do jogador.

## 8. Conflitos e segurança

**Fontes:** fronteiras, minas, água, tarifas, rotas, dívidas, ataques a caravanas, rivalidades políticas e crises internas.

**Escalada sugerida:** tensão → incidente → exigência diplomática → embargo/mobilização → confronto limitado → negociação/cessar-fogo → acordo ou nova escalada.

**Mecânicas:** patrulhas, escoltas, postos avançados, paliçadas, torres, cercos simplificados, moral, suprimentos, rendição, indenização e cessão negociada de direitos.

**Restrições de design:** conflitos não devem ser automáticos por identidade cultural ou origem; não gerar ataques indiscriminados em áreas carregadas sem limites de performance; permitir desabilitar conflitos armados em configuração de mundo.

## 9. Política interna

Modelos de governança configuráveis por contexto histórico e evolução: administração nomeada, Câmara, conselho comunitário e instituições posteriores. Evitar anacronismos não intencionais.

**Indicadores:** legitimidade, satisfação, segurança, abastecimento, desigualdade, tesouro, influência de grupos e autonomia.

**Decisões:** orçamento, impostos, obras, reservas alimentares, concessões de terra, acordos externos, contratação de guardas e políticas de comércio.

**Crises:** protestos, boicotes, migração, disputas de liderança e revoltas. Resoluções incluem negociação, reformas, ajuda externa ou repressão com consequências sociais duradouras.

## 10. Cultura e patrimônio

Sistemas de culinária, artesanato, música, festas, arquitetura, religiosidade e memória coletiva. Tradições variam conforme região, contexto, migração e intercâmbio; não são atributos imutáveis.

**Eventos culturais:** São João, feiras, festas comunitárias, festas de padroeiro onde pertinentes, celebrações de colheita e manifestações regionais contextualizadas.

**Patrimônio:** construções e práticas podem ganhar relevância histórica; demolições, restaurações e mudanças urbanas afetam prestígio, memória e satisfação.

## 11. Eventos e crises

| Evento | Gatilho | Consequências | Respostas |
|---|---|---|---|
| Seca | chuva baixa e reservas insuficientes | quebra de safra, inflação, migração | açudes, importação, racionamento |
| Enchente | nível do rio/chuvas | danos e interrupção de rotas | reconstrução, obras preventivas |
| Epidemia | saúde e densidade, gatilho contextual | redução de mão de obra | botica, isolamento contextualizado |
| Descoberta de ouro | exploração de jazida | riqueza, migração, disputa | regulamentação, acordos |
| Caravana assaltada | insegurança de rota | escassez e tensão | escolta, patrulha, negociação |
| Festa popular | calendário e recursos | prestígio, consumo e comércio | financiar, preparar, convidar |
| Revolta | baixa legitimidade e necessidades críticas | paralisação e crise política | negociação, reformas |
| Chegada de migrantes | oportunidade ou deslocamento | novos trabalhadores e necessidades | acolhimento e moradia |
| Incêndio | risco de construção e evento | perda de estoque e patrimônio | prevenção e reconstrução |

**Sistema de eventos encadeados:** seca → quebra de safra → alta de preços → protestos → proposta de ajuda de vila rival → decisão política → consequências futuras.

## 12. Marcos e crônicas

**Marcos:** fundação, primeira feira, primeira escola, caminho dos tropeiros, emancipação, pacto regional, grande celebração, transformação institucional e capital regional.

**Livro de Memórias:** registro persistente de datas do mundo, fundadores, lideranças, construções importantes, crises, tratados, festas, mortes e nomes de lugares. Permitir consulta em interface e, futuramente, livros físicos do Minecraft.

## 13. Expansão territorial

- Fronteiras por influência e reivindicação; nunca por simples raio fixo sem regras.
- Núcleos satélites: mina, fazenda, porto, entreposto e povoado de fronteira.
- Estradas e rios como corredores de comércio e influência.
- Assentamentos podem pedir autonomia e virar vilas independentes.
- Uma rede de vilas pode criar conselhos ou alianças regionais.
- Crescimento sem infraestrutura gera congestionamento, escassez e instabilidade.

## 14. UX e interfaces

**Telas principais:**
1. Painel do Arraial: população, alimento, tesouro, satisfação, segurança, construções.
2. Livro de Obras: blueprints, materiais faltantes, progresso e trabalhadores.
3. Livro de Profissões: postos, moradores, prioridades e aprendizes.
4. Mercado: estoques, preços, ordens de compra/venda e caravanas.
5. Mapa Regional: vilas, fronteiras, rotas, recursos, reputações e ameaças.
6. Diplomacia: líderes, tratados, pedidos e histórico.
7. Crônicas: linha do tempo, marcos e decisões históricas.
8. Alertas: falta de alimentos, obras paradas, ataques, epidemias e oportunidades.

**Princípios:** indicadores legíveis, explicação da causa de cada problema, alertas acionáveis, automação delegável e acessibilidade por teclado.

## 15. Arquitetura técnica proposta

> **Decisões pendentes:** versão exata de Minecraft, loader (NeoForge/Fabric), versão de Java, compatibilidade com servidores dedicados e dependências. Não criar código específico de loader antes de confirmar esses itens.

### 15.1 Módulos lógicos

```text
arraial/
  core/             # IDs, tempo, eventos de domínio, configuração
  worldgen/         # distribuição de assentamentos e identidade
  settlements/      # população, fronteiras, estágio, indicadores
  citizens/         # NPCs, necessidades, família, profissões
  buildings/        # blueprints, construção, upgrades
  production/       # receitas, ciclos produtivos e estoques
  economy/          # preços, salários, comércio, tesouro
  logistics/        # rotas, caravanas, entregas
  diplomacy/        # reputação, acordos, emissários
  politics/         # governo, decisões e legitimidade
  conflicts/        # tensão, segurança e combate
  culture/          # tradições, festas e patrimônio
  history/          # marcos, crônicas e memória
  events/           # crises e eventos encadeados
  networking/       # sincronização cliente-servidor
  ui/               # telas, HUD, livros e mapas
  integration/      # compatibilidade com outros mods
```

### 15.2 Autoridade e persistência

- Servidor é a fonte da verdade para recursos, cidadãos, diplomacia e eventos.
- Persistir estados em mecanismo adequado à versão/loader escolhidos (por exemplo, SavedData ou equivalente).
- IDs estáveis para vilas, cidadãos, prédios, rotas e eventos.
- Dados de conteúdo em JSON/data packs quando viável: profissões, receitas, culturas, eventos, edifícios e balanceamento.
- Versionar schemas de save e criar migrações para atualizações.
- Pacotes de rede apenas para ações válidas e diffs necessários à UI.
- Nunca confiar em valores econômicos enviados pelo cliente.

### 15.3 Simulação escalonada

**Nível A — próximo ao jogador:** NPCs com entidades, animação, navegação e tarefas físicas.

**Nível B — chunks próximos ou parcialmente ativos:** simulação reduzida de trabalho, logística e eventos.

**Nível C — vilas distantes:** ciclos agregados por tempo de jogo, sem entidades ativas nem pathfinding individual.

Reconciliar estado agregado com entidades quando a região for carregada. Aplicar limites por tick, processamento incremental e cache de rotas. Não carregar chunks permanentemente só para manter economia distante.

### 15.4 Sistemas orientados a eventos

Exemplos de eventos de domínio:

```text
SettlementFounded
BuildingCompleted
ProductionCycleFinished
StockBelowThreshold
CitizenMigrated
CaravanArrived
TreatySigned
BorderIncidentOccurred
CrisisStarted
MilestoneUnlocked
```

Separar regras de domínio da API Minecraft sempre que possível para facilitar testes unitários.

### 15.5 Compatibilidade e testes

- Single-player e multiplayer com servidor dedicado.
- Testes de serialização, determinismo de ciclos, economia, diplomacia e migração de save.
- Testes de carga: 5, 20 e 50 assentamentos simulados; medir tempo por tick e uso de memória.
- Testes de chunks descarregados, reinício de servidor e reconciliação de NPCs.
- Compatibilidade opcional com mods de mapa, biomas e receitas mediante adaptadores.

## 16. MVP jogável

**Escopo recomendado:** um arquétipo regional (por exemplo, arraial do Recôncavo), uma vila do jogador e duas vilas autônomas, com poucos NPCs ativos e simulação agregada à distância.

**Conteúdo:**
- Fundação e nomeação do arraial.
- 8 profissões: agricultor, farinheiro, lenhador, carpinteiro, pedreiro, comerciante, tropeiro e guarda.
- 10 edifícios: casa, roça, casa de farinha, depósito, carpintaria, olaria, feira, posto de guarda, poço e casa administrativa.
- 3 cadeias: madeira/construção, mandioca/farinha e barro/tijolo.
- Necessidades básicas: alimento, moradia e segurança.
- Mercado com oferta, demanda e preços suavizados.
- Caravana periódica entre duas vilas.
- Reputação bilateral e um tratado comercial.
- Dois eventos: seca e caravana assaltada.
- Painel do assentamento, Livro de Obras e tela simples de diplomacia.
- Persistência e funcionamento em servidor dedicado.

**Critérios de aceite do MVP:**
1. Jogador funda vila e atribui trabalhadores.
2. NPCs produzem e transportam recursos com feedback visual.
3. Construção consome materiais e conclui corretamente.
4. Duas vilas NPC evoluem economicamente sem jogador por perto.
5. Escassez altera preços e comportamento de compra.
6. Uma caravana transfere recursos e afeta estoques.
7. Reputação influencia ao menos uma negociação.
8. Seca afeta produção e pode ser mitigada por estoque/importação.
9. Save/load não duplica recursos, moradores ou construções.
10. Servidor dedicado não exige classes exclusivamente cliente.

## 17. Roadmap por fases

### Fase 0 — Fundamentos
- Confirmar versão de Minecraft, loader, Java e API.
- Criar projeto, CI, ambiente de teste, estrutura de dados e logging.
- Implementar registro de assentamentos e persistência mínima.

### Fase 1 — Fundação e produção
- Fundação, limites, NPCs, profissões, edifícios e estoques.
- Cadeias de produção e interface inicial.

### Fase 2 — Economia regional
- Mercado, caravanas, rotas, vilas autônomas e simulação distante.

### Fase 3 — Diplomacia e crises
- Reputação, tratados, seca, segurança, conflitos limitados e crônicas.

### Fase 4 — Cultura e expansão
- Novas regiões, tradições, festas, gerações, marcos e assentamentos satélites.

### Fase 5 — Profundidade e polimento
- Política, múltiplas facções, guerras mais elaboradas, acessibilidade, balanceamento, desempenho e compatibilidade.

## 18. Backlog priorizado

| ID | Prioridade | Entrega | Dependência |
|---|---|---|---|
| ARR-001 | P0 | Bootstrap do mod e CI | decisão de loader/versão |
| ARR-002 | P0 | Modelo e persistência de assentamento | ARR-001 |
| ARR-003 | P0 | Fundação e painel inicial | ARR-002 |
| ARR-004 | P0 | Registro de profissões e tarefas | ARR-002 |
| ARR-005 | P0 | Estoques e receitas de produção | ARR-002 |
| ARR-006 | P0 | Blueprint e construção básica | ARR-003, ARR-005 |
| ARR-007 | P0 | IA de trabalho e transporte | ARR-004, ARR-005 |
| ARR-008 | P1 | Mercado e preços | ARR-005 |
| ARR-009 | P1 | Vila NPC autônoma | ARR-002, ARR-005 |
| ARR-010 | P1 | Simulação agregada distante | ARR-009 |
| ARR-011 | P1 | Caravanas e rotas | ARR-008, ARR-009 |
| ARR-012 | P1 | Reputação e tratado comercial | ARR-009 |
| ARR-013 | P1 | Evento de seca | ARR-005, ARR-008 |
| ARR-014 | P1 | Crônicas e marcos | ARR-002 |
| ARR-015 | P2 | Política e governança | ARR-012, ARR-014 |
| ARR-016 | P2 | Disputas territoriais e conflitos | ARR-012 |
| ARR-017 | P2 | Novas identidades regionais | ARR-009 |
| ARR-018 | P2 | Famílias e sucessão | ARR-004 |

## 19. Orientações para Claude Code

### Papel

Atue como arquiteto e desenvolvedor sênior de mods Minecraft, com foco em código testável, desempenho de servidor, compatibilidade multiplayer e design orientado a dados. Este GDD é a fonte inicial de requisitos, não uma autorização para implementar tudo simultaneamente.

### Fluxo de trabalho obrigatório

1. Inspecione o repositório existente antes de propor mudanças.
2. Identifique versão de Minecraft, loader, Java, mappings e build system. Se não existirem, pergunte ao usuário antes de escolher.
3. Produza `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` e `docs/DECISIONS.md` a partir deste GDD.
4. Faça um plano incremental para a **Fase 0**, com arquivos afetados, testes e critérios de aceite.
5. Implemente apenas a primeira fatia vertical aprovada.
6. Ao terminar cada entrega, execute build/testes disponíveis, informe limitações e atualize documentação.
7. Nunca invente APIs do loader ou assuma versões sem verificar dependências locais.
8. Mantenha lógica de simulação independente de classes cliente sempre que possível.
9. Não introduza IA generativa como dependência obrigatória dos NPCs.
10. Prefira conteúdo extensível por dados e regras determinísticas com sementes controladas.

### Primeira tarefa sugerida

> Analise este GDD e o repositório. Identifique decisões técnicas ainda abertas e proponha a arquitetura mínima para fundação de um arraial, persistência do assentamento e uma profissão funcional. Não implemente os demais módulos. Entregue um plano com interfaces, estruturas de dados, riscos de performance e testes, e aguarde aprovação.

## 20. Decisões abertas

- [ ] Versão do Minecraft.
- [ ] NeoForge ou Fabric.
- [ ] Java e ferramenta de build.
- [ ] Primeiro arquétipo regional.
- [ ] Escala de tempo e calendário histórico.
- [ ] Profundidade de famílias, envelhecimento e mortalidade.
- [ ] Modo de construção: construtores NPC, colocação direta ou ambos.
- [ ] Limite de NPCs ativos por vila e orçamento de ticks.
- [ ] Nível de realismo econômico e moeda.
- [ ] Como o jogador assume ou perde liderança.
- [ ] Regras de PvP e guerras em multiplayer.
- [ ] Configuração de temas históricos sensíveis.
- [ ] Estratégia de assets, blueprints e localização pt-BR.

---

**Princípio norteador:** o Arraial deve produzir histórias por meio de sistemas interconectados. Uma estrada pode criar comércio; o comércio pode criar riqueza; a riqueza pode gerar disputa; a disputa pode criar uma aliança, uma revolta ou uma nova vila. O jogador influencia esse mundo, mas não é a única força que o transforma.
