# AI Player 0.2.0-dev: alterações no código-fonte

**Esta pasta não contém um JAR compilado da v0.2.0.** O ambiente usado para esta revisão não conseguiu acessar o Gradle 8.7 nem os repositórios Fabric/Mojang. Portanto, o código abaixo ainda precisa ser compilado e validado no Minecraft 1.20.1.

## Implementações adicionadas (sem validação em jogo)

- `CompanionGoal`: prioridade a quem atacou o NPC, a quem atacou o dono e a quem foi atacado pelo dono; identificação de slimes e magma cubes, além de outros mobs hostis; retirada com vida crítica independente de manter um alvo válido; coleta de itens próximos apenas depois das prioridades de sobrevivência.
- `AIPlayer`: `AttackEntityCallback` registra o ataque do dono e ajusta o alvo dos companheiros próximos. `ENTITY_LOAD` também adiciona um objetivo para mobs da classe `SlimeEntity` escolherem um companheiro.
- `SurvivalController`: pega itens dropados reais e procura comida na mochila quando ferido. Faz troca temporária de alimento com a arma real, anima consumo e devolve a arma. A comida gasta uma unidade e cura vida; alimentação não simula fome de jogador.
- `CompanionModel`: braço na direção da boca enquanto segura a comida.
- `StarterCrafting`: protótipo de bancada, tábuas, gravetos, picareta, machado e espada de madeira/pedra/ferro, além de peças de armadura de ferro/diamante. Receitas consomem itens reais; bancada própria pode ser colocada e recolhida, sem gerar itens gratuitamente.
- `CompanionEntity`: novas configurações persistidas (`AutoSurvive`, `AutoLoot`, `AutoCraft`, `Retreating`), suporte à animação visual e pausa de alimentação na abertura da mochila.
- `CompanionTests`: três cenários novos de GameTest propostos (alimentação, prioridade ao slime, coleta sem duplicação).

## Limites e itens pendentes

- Não reimplementa ainda a extração de madeira a partir de árvores, procura de minérios, escavação, mineração por veios, planejamento de construções ou tarefas com retomada persistida. São módulos grandes e precisam de validação por etapas.
- Fabricação atual é apenas um subconjunto das receitas vanilla; não faz mineração nem fundição. A bancada temporária é colocada e recolhida na mesma atualização, sem uma animação dedicada.
- A seleção de alvo dos monstros pode variar, especialmente em mobs com IA própria (slime/creeper). Não garante prioridade sobre jogadores.
- Não execute essa fonte como mod; é preciso compilá-la antes, revisar eventuais erros de mapeamento e testar.

## Como validar num ambiente com Gradle e rede

1. Usar JDK 17 e executar `./gradlew clean build`.
2. Executar `./gradlew runGametest`; revisar GameTests antigos e novos. Até que passem, o build **não está aprovado**.
3. Abrir o cliente Minecraft 1.20.1 Fabric e testar alimentação, fuga, slime, prioridade do dono, crafting, equipamentos e conservação de inventário.
4. Com tudo aprovado, usar **somente** `build/libs/ai-player-0.2.0-dev.jar` (JAR remapeado, e não `-sources.jar`) no FCL.
