# AI Player v0.4.0-dev | mineração de pedra e retomada segura

**Origem:** este projeto é uma continuação direta do `AI_Player_v0.4.0_checkpoint.zip` (base v0.3.9). Os arquivos de todas as versões anteriores foram preservados. Não se trata de reimplementação nem de um mod substituto.

**Alvo:** Minecraft Java 1.20.1, Fabric Loader 0.15.11, Fabric API 0.92.2+1.20.1, Yarn build.10, Java 17 e Gradle 8.7 / Loom 1.6.12.

## O que já estava no checkpoint

- `SurfaceStoneGathering`: busca de pedra natural exposta, acesso lateral, rejeição de pedra protegida, gravidade/líquidos e de local inacessível.
- `PhysicalMining`: trinca visível por ticks, tempo proporcional à dureza e ferramenta, durabilidade, quebra real no mundo, drops reais, guarda de espaço em inventário e sem inserção remota.
- `ResourcePlan` e `StarterCrafting`: necessidade autônoma de pedregulho para picareta/machado/espada de pedra, preparação de madeira, gravetos, bancada e ferramentas.
- `CompanionIntelligence`: prioridade única para fuga, sobrevivência, combate, fabricação, coleta, seguir, além de suspensão e recuperação das metas por NBT.
- `PortugueseOrderParser`: comando de madeira, comida, pedra e cancelamento, com sinônimos e tolerância limitada a erros de digitação.
- Interação com sistemas existentes: combate, inventário, dono, coleta de madeira, comida, navegação, bancada recuperável e falas baianas.

## Alterações desta revisão sobre o checkpoint

1. **Rota de trabalho com piso verificado** (`WorkNavigation`): um nó da rota precisa ter apoio, não pode passar por líquidos/perigos em trajeto de superfície, rejeita duas quedas laterais profundas e saltos descendentes acima de um bloco. Rotas aquáticas para recuperar drops mantêm um tratamento distinto.
2. **Busca de pedra menos trêmula** (`SurfaceStoneGathering`): o executor evita substituir uma rota que está progredindo a cada 20 ticks; pontos de mineração passam pela nova verificação do piso.
3. **Coleta fiel ao objetivo** (`ResourceGathering`): enquanto busca pedra, não persegue drops de madeira/comida; durante coleta de madeira prefere madeira, enquanto a busca de comida prioriza drops de alimento, mas pode salvar troncos próximos sem contabilizá-los como comida. Ocioso ainda pode recolher itens normalmente.
4. **Ação física preservada entre decisões voluntárias** (`CompanionIntelligence` e `ResourceGathering`): fabricação opcional e o ato de seguir o dono não preemptam uma pedra que já está sendo quebrada. Combate e emergências continuam mais prioritários.
5. **Retomada de ordens restauradas** (`ResourceGathering`): leitura NBT limpa rota, drop e alvo efêmeros da subtarefa anterior, sem perder o objetivo e as quantidades salvas.
6. **Pilares sem auto-sabotagem** (`TemporaryPillar`): a recuperação não minera o bloco que está segurando o NPC; tenta caminhar para um apoio adjacente, evita apropriação alheia e adia a limpeza enquanto não há saída segura.
7. **GameTests** (`SurfaceMiningTests`): cenário da busca a 30 blocos recebeu uma área de circulação apoiada maior, mantendo o alcance e uma asserção de não cair. Novo teste para evitar uma ponte com duas quedas profundas.

## Construir no GitHub Actions

**Recomendado:** descompacte o ZIP inteiro na raiz de seu repositório, preservando `.github/workflows/build-fabric.yml`, `gradle/wrapper/*`, `src/*`, `settings.gradle` e `build.gradle`. Adicionar somente o ZIP como arquivo no GitHub não atualiza o workflow. No GitHub, abra **Actions > Build AI Player Fabric 1.20.1 > Run workflow** e mantenha `run_gametests: true`.

O workflow usa Java 17, executa os testes Java independentes, `clean build compileTestmodJava` e `runGametest`, e publica logs/relatórios como artefato. Se deixar apenas um ZIP na raiz **e já tiver** um workflow compatível existente, o workflow incluído também suporta a extração de um ZIP v0.4.0.

Localmente em um PC com conexão às dependências:

```sh
bash gradlew clean build compileTestmodJava --no-daemon
bash gradlew runGametest --no-daemon
```

No Minecraft, usar `/aiplayer` conforme os comandos no `README.md` e testar uma ordem, por exemplo `minera 16 pedras`. Não requer um serviço de IA remoto ou API. Nenhuma biblioteca extra foi introduzida nesta revisão.

## Avisos de validação

Consulte `docs/VALIDACAO_V0_4_0.md`. Os **38 testes de comandos** e **110 verificações da arbitragem** foram realmente executados nesta revisão e passaram. Os **48 GameTests existentes no código não foram executados neste ambiente** porque o wrapper não conseguiu baixar o Gradle da internet; não há confirmação atual de compilação Minecraft ou comportamento no FCL. O checkpoint relatava 46/47 GameTests, mas seu arquivo não continha o relatório XML da v0.4.0 para confirmar essa execução.

## Patch de regressão após os primeiros 48 GameTests (20/10/2026)

Execução real [39482902443](https://github.com/manobilas2007-sys/AI-Player/actions/runs/39482902443): compilação Fabric aprovada, 45/48 GameTests aprovados. O arquivo foi corrigido para abordar as 3 falhas:

- `detectsStoneThirtyBlocksAway`: comparação de Y absoluto com altura relativa inválida no teste. O scanner registra observações reais de pedra exposta independentemente do êxito imediato do pathfinding; o teste continua exigindo 30 blocos e apoio no chão.
- `foodTaskCollectsLooseWood`: resgata drops legítimos de madeira durante procura de alimento, sem contar madeira como comida, priorizando alimento quando presente.
- `followingAcceptsOpenSlope`: constrói o terreno de uma subida ampla antes de validar a rota; continua rejeitando pontes perigosas.

**Resultado deste patch:** 38/38 testes do parser e 110 verificações de decisões passaram localmente. A recompilação e os 48 GameTests da versão corrigida estão **pendentes** de nova execução no GitHub; os 45/48 referem-se à versão anterior.

## Revisão após testes reais em Fold Craft Launcher (regressões de mundo aberto)

A execução [39484759402](https://github.com/manobilas2007-sys/AI-Player/actions/runs/39484759402) **aprovou 48/48 GameTests do pacote anterior**, mas os screenshots do jogo real revelaram problemas fora daquela cobertura. Esta revisão posterior corrige acesso a água, recuperação dos apoios, bancada e mineração em área coberta; adiciona **cinco GameTests** para esses cenários, totalizando **53**. Eles ainda não foram executados no Minecraft nem recompilados neste ambiente. As notas anteriores neste README descrevem estados históricos, não a validação deste ZIP. Leia `docs/REGRESSOES_MUNDO_REAL_V0_4_0.md`.

## Revisão: escadas e galerias seguras

Após o teste real no Fold Craft Launcher foi adicionada uma escavação **em degraus** para pedra do piso e **em galeria 2×2** para pedra na parede; pedra elevada segura continua sendo minerada normalmente. Árvores naturais já reconhecidas podem ser revisitadas após corte parcial. Veja [docs/ESCAVACAO_PLANEJADA_V0_4_0.md](docs/ESCAVACAO_PLANEJADA_V0_4_0.md).

Esta revisão define 59 GameTests, **ainda não rodados neste ZIP**. Testes locais: 38 comandos e 110 prioridades aprovados. O Gradle não conseguiu baixar a distribuição no ambiente local.

## Ajuste solicitado: torres são permitidas, mas devem ser recuperadas

O comportamento desejado **não** é proibir uma torre de madeira alta. A revisão da altura foi substituída por uma descida controlada (`TemporaryPillar`): o NPC reserva ao menos um bloco físico para apoiar a descida, coloca um apoio lateral um nível abaixo, caminha até ele, minera o apoio superior, retorna ao eixo da torre e minera o apoio lateral. Repete até alcançar solo seguro. Os blocos são consumidos do inventário ao colocar e os materiais da remoção só reaparecem por drops reais do mundo. Nunca quebra o piso em que está pisando. O trabalho de recuperar os drops da torre permanece prioritário por um intervalo após desmontá-la.

O estado da descida (`PillarDescent*`) é persistido em NBT, evitando perder a sequência em uma interrupção. O limite técnico de 20 blocos rastreados foi mantido, mas a proibição de uma terceira camada isolada foi retirada. Foram revisados os testes de altura e adicionado um GameTest de desmontagem física de torre alta.

**Validação desta alteração:** 38 testes de comandos e 110 verificações de prioridades executados com sucesso. O servidor Minecraft e a suíte de **62 GameTests definidos** não puderam ser executados localmente por falha de resolução de `services.gradle.org`. Portanto, o teste de desmontagem da torre ainda **não foi aprovado** e pode apontar outros ajustes quando executado no GitHub Actions. Nenhuma modificação do repositório remoto foi feita nesta etapa.


---

# Evidências e limites da validação v0.4.0-dev

Data da revisão: 20/10/2026. Fonte: `AI_Player_v0.4.0_checkpoint.zip` enviado pelo usuário.

| Verificação | Realmente executada agora? | Resultado |
|---|---|---|
| Integridade e inspeção dos 79 itens originais | Sim | ZIP lido e extraído; Gradle, classes, resources e testes identificados |
| Testes do parser de ordens (`PortugueseOrderParserTest`) | Sim, com `javac --release 17` sobre JDK 21 | 38/38 aprovados |
| Testes da arbitragem (`DecisionPolicyTest`) | Sim, com `javac --release 17` sobre JDK 21 | 110 verificações aprovadas |
| Compilação Loom e Minecraft/Yarn do ZIP anterior | Sim, no GitHub Actions 39482902443 | Aprovada |
| GameTests do ZIP anterior | Sim, no GitHub Actions 39482902443 | 45 aprovados de 48; 3 falhas identificadas |
| Compilação e GameTests deste patch | Não | Pendente no GitHub Actions; **não reportar 48/48** |
| Teste interativo no FCL, quedas e variação do relevo real | Não | Pendente no dispositivo |

A revisão não confunde os 46/47 GameTests **alegados pelo checkpoint** com execução recente. `docs/gametest-results-v0.3.9.xml` é histórico da versão anterior e não comprova nenhum teste de v0.4.0.

## Cenários prioritários para a próxima execução real

- `detectsStoneThirtyBlocksAway`: distância original preservada; arena alargada para evitar borda artificial e verificação extra para detectar queda.
- `refusesThinExposedBridgeForMining`: reprovar escolha de piso estreito com duas bordas altas.
- `physicalOrderStopsAfterThreePickups`: quebrar fisicamente três blocos, gastar três pontos de durabilidade e guardar exatamente três pedregulhos.
- `worldDropBeforeInventory`: verificar que a pedra se transforma primeiro em entidade de drop, sem crédito remoto ou duplicação.
- `makesPickAndResumesManualOrder`, `emptyInventoryWoodThenStone`: preparar a picareta de verdade, retornar ao trabalho anterior e manter a contagem.
- `autonomousThreeStoneTools`: consumir pedregulho real e fabricar as ferramentas de pedra.
- `savesAndCancelsMaterialSubgoal`: preservar/cancelar corretamente objetivo maior via NBT.
- Testes históricos de navegação, recuperação de degrau, pilares, combate, inventário, alimentação, bancada e falas (em `CompanionTests`).

## Pendências conhecidas

1. O primeiro `runGametest` real passou em 45/48; a execução do ZIP com estas correções ainda precisa ocorrer. **Não afirmar aprovação até conferir o novo relatório do GitHub Actions.**
2. A rejeição de caminhos estreitos é deliberadamente prudente. Avaliar in-game se estradas muito estreitas ou áreas rochosas naturais estão sendo recusadas em excesso. Se houver recusa, investigar o percurso e não afrouxar a proteção de forma cega.
3. Pilares que não tenham local seguro para descida/recuperação são preservados e a limpeza fica adiada. Isso evita quedas, mas pode exigir melhoria posterior de planejamento de saída.
4. Cenários com mobs, chunk descarregado, queda em água, encostas, outros mods, persistência após recarga do mundo, desempenho no Android/FCL e interferência do jogador ainda exigem teste manual.

## Reprodução das verificações locais

```sh
mkdir -p build/standalone-tests
javac --release 17 -encoding UTF-8 -d build/standalone-tests \
  src/main/java/dev/aiplayer/commands/PortugueseOrderParser.java \
  src/main/java/dev/aiplayer/ai/brain/DecisionPolicy.java \
  src/devtests/PortugueseOrderParserTest.java src/devtests/DecisionPolicyTest.java
java -cp build/standalone-tests PortugueseOrderParserTest
java -cp build/standalone-tests DecisionPolicyTest
```

Um `javac` sem as bibliotecas Minecraft não equivale a compilar o mod; mensagens de imports não resolvidos são esperadas. O GitHub Actions é a referência para a compilação e os GameTests completos.

## Primeiro relatório real (GitHub Actions)

- [Execução #39482902443](https://github.com/manobilas2007-sys/AI-Player/actions/runs/39482902443) de 20/10/2026: `clean build compileTestmodJava` aprovado; 45/48 GameTests aprovados.
- `surfaceminingtests.detectsstonethirtyblocksaway`: teste comparava `a.getY()` absoluto com `1.9` relativo, provocando falso positivo de queda em mundo de testes situado em Y negativo. Patch compara Y absolutos e separa detecção de pedra da obtenção de rota.
- `companiontests.foodtaskcollectsloosewood`: o filtro anterior recusava troncos durante forrageamento, apesar de o teste exigir aproveitamento oportunista. Patch permite troncos e prioriza comida; somente comida atualiza progresso de FOOD.
- `companiontests.followingacceptsopenslope`: o teste construía três nós de subida no vazio, violando a exigência de apoio real. Patch cria uma rampa larga suportada, sem mudar as regras de segurança.
- O patch corrigido não teve Java/Minecraft reexecutados neste ambiente; 38/38 testes do parser e 110 verificações do escalonador executados localmente.
