# AI Player 0.7.0-dev: Baritone, diálogos e projetos físicos

## Arquitetura

A integração utiliza os fontes de referência do Baritone 1.10.5, fixados no
commit `8c55ad0721d42a01a359834edc16aaa9b5bc587d`, para uma biblioteca interna
Java 17 em `embedded-baritone/`. O pacote adaptado é
`dev.aiplayer.baritone`. O build inclui as classes da biblioteca diretamente
no JAR principal do AI Player. O módulo de fontes e seu binário separado
mantêm a versão `1.10.5-npc.2`, mas não são registrados como outro mod
Fabric em tempo de execução. O usuário não precisa instalar esse módulo
nem o mod Baritone separadamente. Fabric API e FallingTree 4.3.4 continuam
sendo as dependências utilizadas pelo AI Player.

### Correção de empacotamento no FCL

O relato de execução no FCL registrou
`NoClassDefFoundError: dev/aiplayer/baritone/Goal`: o núcleo aparecia na
lista de mods, mas a classe não estava acessível ao código do AI Player.
A versão 0.5.1-dev passa a incluir as classes `dev/aiplayer/baritone/`
diretamente no JAR principal, sem JAR interno nem registro independente do
núcleo pelo carregador de mods. Os fontes e scripts da biblioteca continuam
separados para permitir sua substituição por recompilação.

O verificador de distribuição e os GameTests avaliam o artefato gerado no
ambiente do CI. Seus resultados não equivalem a uma execução no FCL; essa
validação específica ainda precisa ser conferida, e nenhum resultado de CI
é declarado aprovado antes de sua execução na revisão correspondente.

O JAR do cliente Baritone não é simplesmente colocado dentro do AI Player:
seus mixins e processos controlam um jogador cliente e não são um executor
de NPC no servidor. A biblioteca adaptada oferece planejamento ao NPC, que
continua responsável por andar, usar ferramentas, fabricar, quebrar e colocar
blocos de forma física e verificar os resultados no mundo.

Minecraft Java **1.20.1**, Fabric, Java 17 e Gradle Wrapper 8.7 permanecem como
base do projeto. A integração não utiliza Minecraft 1.21.3.

## Mapeamento de capacidades

| Componente | Papel na integração |
| --- | --- |
| Busca A*, nós e fila de prioridade | Núcleo Java adaptado do Baritone para calcular rotas com orçamento de busca. |
| Objetivos de navegação | `GoalBlock` e `GoalNear` são usados pelo adaptador; a biblioteca também disponibiliza objetivos X/Z, nível Y, combinação e afastamento. |
| Custos de ações | Preservam as fórmulas upstream de tempo e gravidade para comparar movimentos e rotas. |
| Estado e regras do mundo | Fornecidos por `NpcPathfinder`, no servidor, sem assumir um jogador cliente conectado. |
| Movimentação | `NpcNavigation` coordena caminhada, diagonais, saltos com arco validado, escadas existentes, natação, saídas da água, quedas limitadas, pontes e pilares pagos. `PreparedRoute` revalida ações, custos e progresso. |
| Mineração e colocação | O grafo compara caminhos comuns com passagens preparadas; o executor abre passagem e coloca apoios usando ferramentas e inventário reais. A coleta mantém a escada de retorno, trata gravidade/água e recolhe os drops fisicamente. |
| Fabricação e troca de ferramentas | Continuam nos executores do AI Player; o planejador não materializa recursos. |
| Abrigo, plantio e exploração | Implementados em `CompanionTasks`, que solicita rotas e executa ações físicas com recursos reais. São controladores do NPC, distintos dos processos do jogador cliente Baritone. |
| Mixins, comandos e renderização do cliente Baritone | Não são incorporados ao runtime do NPC. |
| Elytra e `nether-pathfinder` nativo | Não são incorporados a este núcleo Java de planejamento. |

Essa matriz distingue a funcionalidade adaptada de uma portabilidade integral
de todos os arquivos e processos do cliente Baritone. Copiar classes de
cliente sem um executor de NPC não tornaria essas capacidades funcionais.

O planejador considera segmentos de terreno carregado. Tickets móveis do próprio NPC carregam a região seguinte e mantêm entidades e fornalhas ativas durante viagens. Não há raio total ligado à posição do dono para as ordens de busca. A
configuração do adaptador limita a busca a 4.096 nós por chamada e 48 blocos
de deslocamento por eixo, com orçamento agregado por NPC a cada tick. Um
resultado parcial não é tratado como chegada ao destino. Não há importação
de esquemas arbitrários, cache completo do mundo do cliente, comando
`#mine`/`#goto`, mineração genérica de todos os minérios ou voo de Elytra
nessa adaptação.

## Ordens por diálogo

As novas tarefas usam o chat normal com sinônimos e pequenos erros de escrita.
As conversas de confirmação pertencem ao dono e ao NPC, expiram e conservam a
dimensão e a posição da ordem original. Uma proposta não interrompe o trabalho
anterior até ser confirmada. “Não” descarta a proposta; “pare a tarefa” cancela
o trabalho atual.

| Frase | Resultado |
| --- | --- |
| “Construa uma casa” | Pergunta o modelo disponível e confirma a obra. |
| “Faça um abrigo de pinheiro” | Confirma o abrigo com paleta fixa de spruce. |
| “Crie uma plantação” | Confirma a construção de um canteiro irrigado. |
| “Cuide da plantação” | Confirma cuidado contínuo das plantações existentes até 100 blocos da origem. |
| “Plante trigo” | Pergunta se deve criar ou cuidar de uma plantação. |
| “Ache comida” | Prioriza animais adequados na busca de alimento. |
| “Me siga” | Cancela o trabalho e volta a seguir o dono. |
| “O que você está fazendo?” | Informa o status e os materiais pendentes. |

### Abrigo detalhado

O novo modelo tem piso de 5×7, paleta de madeira escolhida uma vez e persistida,
cinco janelas de vidro, porta real de duas metades, telhado inclinado com escadas
e lajes, bancada e fornalha internas. Prepara neve, vegetação e terreno natural
com levantamento das colunas, escolha da altura, quebras físicas de cima para baixo e fundações pagas até o apoio natural, sem o antigo limite fixo de quatro blocos. Construções marcadas
como protegidas, blocos de outros NPCs e inventários são preservados.

O executor mantém alcance, linha de visão, apoio e colisão reais. Blocos colocados
consomem itens. A ordem de telhado e paredes conserva as posições de trabalho;
a porta própria pode ser aberta durante navegação e fechada após passagem, sem
fechar sobre outra entidade. Portas de ferro e portas de outros proprietários
não recebem essa permissão.

### Materiais e fabricação

ProjectMaterials resolve receitas de componentes de madeira, ferramentas,
bancada, fornalha e balde usando o inventário real. Receitas que exigem bancada
usam uma bancada acessível. Ferro, vidro e carvão vegetal são processados por
uma fornalha vanilla colocada no mundo; combustível e tempo de cozimento são
reais. Mineração produz drops no bloco e exige aproximação para recolher.

A busca observa recursos e entradas de cavernas e pode abrir escadas e galerias para expor pedra, carvão e ferro ocultos. `MaterialExcavation` integra esse acesso às dependências dos projetos e às ordens diretas de pedra. `SafeExcavation` preserva pisos e altura de passagem, usa ferramentas reais e revalida líquidos, construções e atores a cada passo. As entradas abertas na superfície ficam registradas em `SurfaceRestoration`; depois de sair, o NPC coloca terra real apenas nessa camada, preservando o túnel. Cancelamento e NBT mantêm essa obrigação, e os chunks permanecem ativos durante o retorno.

`PreparedRoute` usa o A* incorporado para comparar caminhada, corte de terreno natural e colocação de apoios. O executor confirma alcance, material, durabilidade, colisão e estado atual antes de modificar cada bloco; busca terra próxima quando faltam apoios. A busca usa segmentos locais com orçamento de nós e continua conforme os chunks do NPC avançam. Isso não promete a rota global ótima em terreno desconhecido nem revela minérios através de blocos. Escavações sem recurso ou com perigo mudam a busca para outro trecho, sem perfurar um poço vertical.

Quando um
recurso ou rota não está acessível, mantém a necessidade no status. Não cria
sementes, materiais, água ou ferramentas para completar uma tarefa.

### Criar e cuidar de plantações

Criar usa um layout 7×7 com borda de troncos, 22 posições de solo e três fontes
no canal. Prepara o terreno, obtém os materiais, fabrica um balde se necessário,
busca água fisicamente e reutiliza o mesmo balde nas viagens. Depois prepara os
canteiros com enxada real e planta as sementes disponíveis. Ao terminar libera
o NPC para voltar a seguir o dono; falta de sementes não gera itens gratuitos.

Cuidar reconhece terra arada manualmente e plantações existentes de trigo,
cenoura, batata e beterraba em um raio horizontal inclusivo de 100 blocos da
posição da ordem. A confirmação autoriza apenas o trabalho agrícola nessa área,
preservando reivindicações de outros NPCs e os demais blocos protegidos. Colhe
apenas plantas maduras, recolhe os drops fisicamente e replanta com sementes ou
produtos reais do tipo correspondente. O cuidado continua até ser cancelado.

O scanner percorre colunas gradualmente, somente em chunks carregados. Rotas
maiores são divididas em trechos que continuam obedecendo às regras de segurança
do planejador; calcular um trecho não é contado como chegada ao destino final.

### Autonomia e compatibilidade

A observação de recursos e caminhadas curtas próximas do dono passam a integrar
a autonomia. Não existe uma nova ordem explícita de explorar. Combate, refeições,
inventário aberto e cancelamentos continuam interrompendo os projetos de forma
controlada. Progresso, origem e paleta são persistidos por dimensão.

As tarefas salvas da versão 0.5.x (abrigo de 80 tábuas, FARM e EXPLORE) continuam
legíveis para completar trabalhos antigos. Novos diálogos iniciam os executores
novos; os comandos antigos de construir, plantar e explorar foram removidos da
interface pública.

## Regras preservadas

O planejador propõe ações; o executor verifica novamente o estado real antes
de aplicá-las. A integração deve preservar os blocos protegidos ou
reivindicados, os apoios atuais do NPC, as limitações de ferramentas, as
regras de quebra e drops e os cancelamentos do dono. Coleta e fabricação
continuam usando blocos, drops e ingredientes reais. Não é permitido
considerar uma rota planejada como prova de que o NPC chegou ao destino.

Os testes de regressão existentes continuam necessários, incluindo
FallingTree, fabricação autônoma, troca de machado, mineração, movimentação
e recuperação de apoios. Novas rotas e tarefas precisam de testes dos seus
efeitos reais no mundo; a inclusão da biblioteca, por si só, não comprova
esses comportamentos.

## Compilar e modificar a biblioteca

O projeto-fonte inclui tanto a biblioteca modificável quanto o adaptador do
AI Player e os scripts Gradle usados para combiná-los. Com Java 17, a partir
da raiz:

```sh
bash gradlew :embedded-baritone:build :embedded-baritone:sourcesJar --no-daemon
bash gradlew build compileTestmodJava runGametest --no-daemon
python3 tools/check_embedded_baritone.py
```

Os binários e o JAR de fontes da biblioteca ficam em
`embedded-baritone/build/libs/`; os artefatos do mod ficam em `build/libs/`.
Os fontes da aplicação também são disponibilizados no seu JAR de fontes e
no repositório. O workflow publica `SOURCE_REVISION.txt` com a revisão e o
link do código correspondente; use os arquivos Gradle desse mesmo commit.
O JAR de fontes da biblioteca inclui os fontes adaptados e as referências
upstream em `upstream/`, que não são compiladas como classes do mod.

Para substituir a biblioteca, edite ou substitua os fontes de
`embedded-baritone/`, preservando a interface pública usada pelo adaptador
do NPC, e execute novamente o build da raiz. O Gradle inclui as classes da
nova biblioteca no JAR principal do AI Player. Não é necessário instalar o
JAR original do Baritone, e não se exige assinatura do autor para executar essa combinação
modificada. Consulte `LICENSE.md` para a permissão explícita de modificação,
depuração e recombinação das partes LGPL.

O verificador `tools/check_embedded_baritone.py` confere a presença das
classes do núcleo diretamente no JAR jogável, a ausência do registro
aninhado do núcleo e de componentes do cliente Baritone, a versão Minecraft
1.20.1 e a presença de fontes e licenças. Isso complementa os testes do núcleo
Java e os GameTests; não substitui a validação dos efeitos físicos no mundo
ou do carregador utilizado pelo FCL.

## Proveniência e distribuição

`third-party/Baritone/UPSTREAM.md` registra a origem, a versão e os hashes
verificados dos arquivos recebidos. O ZIP enviado pelo usuário é uma cópia
do JAR compilado, não um pacote de fontes. O código usado pela integração
vem dos fontes upstream identificados e das adaptações versionadas neste
repositório.

`third-party/Baritone/NOTICE.md`, `LGPL-3.txt`, `GPL-3.txt` e a imagem upstream
`LICENSE-Part-2.jpg` acompanham a distribuição. Os avisos e licenças são
incluídos no JAR em `META-INF/licenses/baritone/`. Os artefatos devem incluir
o binário separado da biblioteca, os JARs de fontes da biblioteca e do
AI Player e os scripts de compilação, além do JAR principal jogável. O
binário separado é uma forma inspecionável e recompilável da biblioteca,
não uma dependência que o usuário precise instalar. Ele é uma biblioteca
Java comum, sem `fabric.mod.json`, e não deve ser colocado na pasta `mods`.
A substituição do componente se faz editando ou recompilando o módulo e
reconstruindo o JAR principal, conforme as instruções acima.

O núcleo derivado de Baritone é LGPL-3.0-or-later. O restante do projeto
conserva seus próprios direitos. O estado específico de compilação e
GameTests de cada revisão deve ser consultado no workflow correspondente;
este documento não declara testes executados sem o respectivo resultado.

## Cobertura e critérios de entrega da 0.7.0

A suíte contém 378 nomes distintos: 185 preservados e 193 novos. A ampliação cobre 47 cenários de movimento, 30 de árvores/FallingTree, 33 de pedreiras e restauração, 30 de terreno/construção, 28 de lotes/receitas e 25 de memória/viagens. Os grupos críticos são repetidos nas sementes 17, 104729 e 8675309, em mundos isolados.

`tools/verify_gametest_suite.py` verifica os nomes exatos, ausência de omissões/falhas, todas as sementes e uma única revisão de fontes. A execução completa e os testes de produção Fabric 0.15.11 e 0.20.0 são necessários para gerar o pacote `VALIDADO`. Consulte o relatório da revisão; a existência dos testes no código não declara que uma execução passou.

## Adaptações adicionais do executor

Os fontes de referência de PathExecutor, PathingBehavior, MovementParkour, MovementPillar, MovementHelper, CalculationContext, WorldScanner e MineProcess estão preservados em `embedded-baritone/upstream`. A correspondência com os controladores do NPC está em `embedded-baritone/UPSTREAM.md`.

O índice de recursos varre seções residentes com orçamento compartilhado de 1.024 células por tick, mantém posições e regiões visitadas por dimensão e revalida blocos antes de interagir. O deslocamento em segmentos usa os tickets móveis já existentes; não há limite de viagem atrelado ao jogador. O índice não carrega remotamente todos os chunks do mundo.

Lotes distinguem mínimo para a próxima ação, quantidade planejada e entrega. Uma dependência antiga salva conserva sua quantidade. Um lote parcial é usado quando acaba a varredura local e não há outra fonte acessível conhecida; uma obra com muitos recursos disponíveis continua coletando em lote. NBT de materiais versão 3 lê as versões anteriores; caminhos em andamento são recalculados após carregar.

A interação aquática é autorizada para conter água com blocos reais e exige alcance, espaço livre, propriedade e ar suficiente. Gravidade não permite entrar sob blocos caindo. Elytra, portais/interdimensões e importação de esquemas arbitrários não fazem parte desta entrega. A validação cobre os cenários nomeados, sem prometer uma rota global ótima através de terreno desconhecido.

A navegação revalida água que avança sobre trechos antes secos e transfere o controle de natação para caminhada ao alcançar apoio na margem. Ao terminar ou cancelar uma rota, libera os comandos de movimento e freia o deslocamento horizontal. Drops apoiados na borda de uma coluna de areia aguardam o corte seguro dos blocos restantes antes da coleta física.
