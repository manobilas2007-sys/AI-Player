# Validação da v0.3.5-dev

## Resultado efetivamente obtido
- Build Gradle `build runGametest`: **BUILD SUCCESSFUL**.
- 26 GameTests executados no servidor Minecraft 1.20.1: **26 aprovados, zero falhas**.
- Interpretador de comandos: **13 testes aprovados**.
- Arbitragem de prioridades: **110 verificações aprovadas**, incluindo invariância à ordem dos candidatos.
- JAR remapeado para distribuição: `ai-player-0.3.5-dev.jar`; metadados conferidos e código de teste excluído do JAR.

Java 17, Gradle 8.7, Fabric Loom 1.6.12, Loader 0.15.11 e Fabric API 0.92.2+1.20.1.
O relatório XML real está em `gametest-results-v0.3.5.xml`. O arquivo `gametest-results.xml` e `TESTES.md` são históricos.

## Regressões encontradas e resolvidas nesta validação
Dois testes antigos desativavam a IA/física do mob: isso mantinha `isOnGround=false`, multiplicava o tempo de mineração e impedia movimentação para recolher a bancada. Os cenários de mineração e bancada agora executam com a física habilitada, preservando as verificações de tempo, existência dos drops, consumo e recuperação.

O teste integrado de busca de comida expôs uma falha real: uma tentativa de rota no primeiro tick, antes de aterrissar, fazia um drop alcançável ser ignorado por 300 ticks. Falhas de cálculo de rota agora permitem nova tentativa em 40 ticks. Itens perseguidos sem progresso por tempo excessivo continuam recebendo intervalo maior para evitar insistência permanente.

## Cobertura dos GameTests
- persistence
- equipmentconservesitems
- ownershipandrealslots
- ownerattacktargetisprioritized
- reloadpreservesbenchwithoutvalidrecipe
- plannerfindsmissingmaterialsatanytick
- combatinterruptsaxewithswordandrestoresminingtool
- emptyfoodsearchreleasesmovement
- autonomoustasksyieldbutmanualjobsstay
- blockedbenchyieldswithoutlosingownership
- memorypersistsandchangesrouting
- pausedgatheringsurviveslonginterruption
- mealreloadconservesfoodandweapon
- playerreplacementrevokestemporaryownership
- coordinatorpreemptsandresumesrealjob
- worntoolandsupportreserveareplanned
- realcombatanddurability
- pickupcountswoodafterinventorytransfer
- consumesrealfoodandrestoresweapon
- monsterrecognizescompanion
- picksupdroppeditemswithoutcopies
- miningspawnslootinsteadofremoteinsertion
- fullinventorypreventsdestructivemining
- navigationclimbsblock
- foodtaskcollectsloosewood
- craftingkeepsbenchacrossrecipes

## Limites da validação
Não houve sessão visual de cliente, teste no Fold Craft Launcher/Android nem teste prolongado de sobrevivência em mundo aberto. As animações foram compiladas e seus estados de servidor exercitados; a aparência de cada mão, mangas, armaduras e trocas ainda deve ser conferida no cliente.

O servidor de testes não conseguiu consultar a chave pública do serviço de autenticação externo neste ambiente. Isso não impediu os 26 GameTests locais. A compilação usou o cache local do Loom com a sondagem opcional de sockets Unix desativada, pois o ambiente não suporta essa sondagem. Essa adaptação é exclusiva da ferramenta local: não integra os fontes, o JAR ou o workflow. As dependências do projeto continuam as oficiais.

A configuração GitHub Actions foi incluída, mas o workflow remoto não foi executado nem o repositório alterado. O build local e o servidor GameTest foram executados de fato.

## Testes recomendados no jogo
Veja os cenários detalhados em `README_V0_3_5.md`: árvore alta e drops, combate interrompendo coleta, espada/machado na mão correta, bancada reutilizada e recolhida, mochila cheia, reload durante tarefas, pilares, obstáculos, água e afastamento do dono. Mineração subterrânea completa permanece para a v0.4.0.
