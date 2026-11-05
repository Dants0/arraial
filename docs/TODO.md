# TODO

Legenda: `[x]` feito e compilado · `[ ]` a fazer · (T) testado em jogo pelo usuário.

## Documentação
- [x] GDD versionado em `docs/GDD.md`
- [x] `ARCHITECTURE.md`, `DECISIONS.md`, `ROADMAP.md`, `TODO.md`

## 1. Mandioca e Casa de Farinha
- [ ] Bloco de cultivo mandioca (4 estágios) + item maniva/mandioca
- [ ] Itens farinha de mandioca e beiju (comida), texturas
- [ ] Ofício farinheiro com posto "tipiti/forno de farinha" e receitas
- [ ] Roceiro planta mandioca; mandioca pode ser achada no mato (grama)
- [ ] Planta "casa_de_farinha" no catálogo; prefeito pede quando há mandioca
- [ ] Skin do farinheiro

## 2. Itens Blockbench e diálogos
- [ ] Modelos `.bbmodel` em `tools/blockbench` + JSON exportado
- [ ] Cuia (beber, animação de uso), rapadura, paçoca, chapéu de couro, lampião
- [ ] Textura animada (lampião) via `.mcmeta`
- [ ] Conversa jogador → morador por contexto (ofício, fome, casa, crise, prefeito)
- [ ] Moradores conversam entre si quando se encontram (falas no chat próximo)

## 3. Feira e preços
- [ ] Moeda réis
- [ ] Preço local por oferta/procura, suavizado, por dia
- [ ] Bloco banca de feira + ofício feirante
- [ ] Jogador compra/vende na banca; excedente vira réis no tesouro

## 4. Vilas autônomas, tropeiro e reputação
- [ ] Registro de vilas em SavedData
- [ ] Vila NPC surge no mundo longe do jogador (com Cruzeiro, casas, moradores)
- [ ] Simulação distante (produção/consumo agregado por dia)
- [ ] Tropeiro leva excedente entre vilas (caravana); pode ser assaltada
- [ ] Reputação bilateral (-100..100), ajuda em crise aumenta reputação

## 5. Epidemia/botica e incêndio
- [ ] Crise epidemia: moradores adoecem, param de trabalhar; botica + boticário curam
- [ ] Crise incêndio: fogo numa construção de madeira; moradores apagam com balde

## 6. Estágios da vila
- [ ] Povoado/Arraial/Vila/Vila próspera/Cidade por população e prédios
- [ ] Desbloqueios por estágio; anúncio e Crônica

## 7. Satisfação e legitimidade
- [ ] Satisfação por casa, comida, segurança, crises, festas
- [ ] Legitimidade do prefeito e nova eleição quando baixa

## Correções em aberto
- [ ] Comida baixa em vilas grandes (mandioca deve resolver; conferir no teste)
