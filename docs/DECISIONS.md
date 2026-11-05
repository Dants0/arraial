# Decisões (ADR resumido)

| ID | Data | Decisão | Motivo |
|---|---|---|---|
| D-001 | 2026-11-04 | Fabric, Minecraft 26.3, Java 25, Gradle/Loom, mapeamentos Mojang. | Ambiente já testado no mod Citrino. |
| D-002 | 2026-11-04 | Código e assets originais; inspiração no Millénaire sem copiar nada. | Direito autoral e identidade própria. |
| D-003 | 2026-11-04 | Construções vêm de um catálogo autoral (`.nbt` gerados por `tools/GerarPlantas.java`), construídas bloco a bloco por pedreiros NPC. | Fantasia central: os NPCs constroem. |
| D-004 | 2026-11-04 | NPC próprio (`arraial:morador`) com skins por ofício, não aldeões vanilla. | Visual e IA sob controle. |
| D-005 | 2026-11-04 | Estado da vila dentro do bloco Cruzeiro (block entity). | `SavedDataType` do 26.3 exige DataFixTypes; o bloco já marca o centro. |
| D-006 | 2026-11-04 | Jogador funda e ajuda no começo; com 4 moradores elege-se um prefeito que passa a planejar. | Vila se governa sozinha (pilar "mundo vivo"). |
| D-007 | 2026-11-05 | Dados entre vilas (índice, reputação, simulação distante) em `SavedData` do Overworld. | Precisam existir com chunks descarregados. |
| D-008 | 2026-11-05 | Moeda: **réis** (item `arraial:reis`), preço local suavizado `clamp(base × escassez, min, max)` atualizado por dia. | Fórmula 6.3 do GDD, sem exploits de oscilação. |
| D-009 | 2026-11-05 | Diálogos são falas escritas (lang), escolhidas por contexto; sem IA generativa. | GDD 19.9. |
| D-010 | 2026-11-05 | Itens 3D modelados em Blockbench (`tools/blockbench/*.bbmodel`) e exportados para JSON vanilla; animação de item via textura animada (`.mcmeta`) e animação de uso (comer/beber). | Item vanilla não tem animação por keyframes. |
| D-011 | 2026-11-05 | Comunidades indígenas e quilombolas só como comunidades completas e pesquisadas, nunca inimigas por padrão; escravidão nunca como bônus. Ficam fora até haver pesquisa. | GDD 3.2. |
| D-012 | 2026-11-05 | Simulação em camadas: entidades só perto do jogador; vila distante avança por agregado diário. | GDD 15.3, servidor leve. |

## Pendentes

- Arquétipos regionais além do sertanejo/mineiro genérico atual.
- Profundidade de famílias, envelhecimento e mortalidade.
- Regras de PvP/guerra em multiplayer (conflitos armados configuráveis).
- Licença definitiva (hoje: todos os direitos reservados).
