# AI Player 0.5.0-dev: Baritone adaptado ao NPC

## Arquitetura

A integração utiliza os fontes de referência do Baritone 1.10.5, fixados no
commit `8c55ad0721d42a01a359834edc16aaa9b5bc587d`, para uma biblioteca interna
Java 17 em `embedded-baritone/`. O pacote adaptado é
`dev.aiplayer.baritone`. O build inclui seu JAR dentro do JAR do AI Player,
com o identificador Fabric `aiplayer_baritone_core` e a versão
`1.10.5-npc.1`. O usuário não precisa instalar esse módulo nem o mod
Baritone separadamente. Fabric API e FallingTree 4.3.4 continuam sendo as
dependências utilizadas pelo AI Player.

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
| Movimentação | O adaptador calcula rotas de caminhada, diagonais, subida, descida e água; os movimentos continuam físicos e são revalidados. |
| Mineração e colocação | Permanecem nos executores do AI Player, com verificações de inventário, ferramentas, propriedade e regras do mundo. A busca de rotas não quebra nem coloca blocos por conta própria. |
| Fabricação e troca de ferramentas | Continuam nos executores do AI Player; o planejador não materializa recursos. |
| Abrigo, plantio e exploração | Implementados em `CompanionTasks`, que solicita rotas e executa ações físicas com recursos reais. São controladores do NPC, distintos dos processos do jogador cliente Baritone. |
| Mixins, comandos e renderização do cliente Baritone | Não são incorporados ao runtime do NPC. |
| Elytra e `nether-pathfinder` nativo | Não são incorporados a este núcleo Java de planejamento. |

Essa matriz distingue a funcionalidade adaptada de uma portabilidade integral
de todos os arquivos e processos do cliente Baritone. Copiar classes de
cliente sem um executor de NPC não tornaria essas capacidades funcionais.

O planejador atual considera terreno carregado e rotas locais limitadas. A
configuração do adaptador limita a busca a 4.096 nós por chamada e 48 blocos
de deslocamento por eixo, com orçamento agregado por NPC a cada tick. Um
resultado parcial não é tratado como chegada ao destino. Não há importação
de esquemas arbitrários, cache completo do mundo do cliente, comando
`#mine`/`#goto`, mineração genérica de todos os minérios ou voo de Elytra
nessa adaptação.

## Ordens e uso no jogo

Os comandos atuam no NPC mais próximo pertencente ao jogador, a até 48
blocos. As frases de chat são interpretadas pelo parser local do AI Player;
não dependem de API de IA ou serviço remoto.

| Comando | Exemplo de chat | Comportamento |
| --- | --- | --- |
| `/aiplayer construir abrigo` | `construa um abrigo` | Inicia o plano fixo de abrigo de madeira 5×5. |
| `/aiplayer plantar` | `plante trigo` | Procura solos elegíveis próximos e mantém o ciclo de trigo. |
| `/aiplayer explorar` | `explore a área` | Visita até oito destinos locais seguros em terreno já carregado. |
| `/aiplayer cancelar` | `cancele o projeto` | Cancela o projeto e a coleta atual. |
| `/aiplayer status` | — | Mostra atividade, necessidade de recursos e progresso do projeto. |

### Abrigo

O plano possui 80 posições de tábuas: piso 5×5, perímetro com duas camadas
de parede, vão de entrada com duas alturas e teto 5×5. O vão é uma abertura,
não um bloco de porta. Cada bloco colocado consome uma tábua real; posições
já concluídas e reconhecidas como próprias são preservadas ao retomar.
A execução monta o piso, os quatro apoios de canto e o teto antes de
fechar as paredes; o NPC trabalha de dentro do abrigo para manter acesso.\nO teto avança da borda para o centro para preservar a linha de visão.

O local começa próximo ao NPC e exige terreno carregado, apoio para os
blocos, espaço livre e posições de trabalho alcançáveis. Blocos existentes
que não pertencem ao plano são preservados; o status informa o impedimento
em vez de limpar a área automaticamente. Com fabricação automática, o NPC
pode converter troncos em tábuas e solicitar coleta de madeira que falta.
Sem ela, entregue as tábuas necessárias.

### Plantio

Entregue **sementes reais de trigo** e forneça uma enxada utilizável ou
materiais para fabricá-la. Com fabricação automática, o mesmo executor de
receitas do AI Player pode produzir uma enxada com bancada, gravetos e
materiais reais; pode buscar madeira quando necessário. As sementes não
são fabricadas nem geradas pela tarefa.

O controlador escolhe até nove solos próximos de água, prepara terra ou
grama naturais, planta consumindo uma semente e colhe apenas trigo maduro
sob sua responsabilidade. A água precisa existir a até quatro blocos nos
eixos horizontais, na altura do solo ou logo acima. O projeto não coloca
água, acelera crescimento ou toma uma plantação de outro proprietário.

Depois de colher, os drops de trigo e sementes permanecem no mundo e são
recolhidos fisicamente antes do replantio. A coleta automática precisa estar
ativa e a mochila precisa ter espaço. O ciclo continua até ser cancelado;
sem sementes, solo irrigado ou ferramenta, o status descreve o que falta.

### Exploração

O percurso usa oito deslocamentos locais em torno do ponto inicial:
cardeais de oito blocos e diagonais de seis por seis. O destino pode
procurar piso até três blocos acima ou abaixo. Isso é uma exploração local,
não uma varredura ilimitada do mapa nem um raio euclidiano estrito de oito
blocos.

Destinos descarregados, inseguros ou sem rota válida são pulados. O contador
informa quantos locais foram realmente visitados, que pode ser menor que
oito. Em cada visita, o NPC registra recursos próximos observáveis, como
árvores, pedra e trigo. O controlador não força carregamento de chunks nem
trata uma rota apenas calculada como uma visita concluída.

### Interrupções e persistência

Inventário aberto, alimentação e combate podem pausar o projeto. O estado
do projeto e as posições já trabalhadas são salvos; rotas transitórias são
recalculadas. Uma mudança de dimensão cancela o projeto, e um projeto
restaurado muito longe do NPC não é retomado automaticamente. As ordens
anteriores de madeira, pedra e comida continuam disponíveis; uma nova ordem
substitui o projeto em andamento.

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
do NPC, e execute novamente o build da raiz. O Gradle combina a nova
biblioteca com o AI Player. Não é necessário instalar o JAR original do
Baritone, e não se exige assinatura do autor para executar essa combinação
modificada. Consulte `LICENSE.md` para a permissão explícita de modificação,
depuração e recombinação das partes LGPL.

O verificador `tools/check_embedded_baritone.py` confere que o JAR jogável
tem a biblioteca interna, que ela não registra mixins nem entrypoints do
cliente, que o Minecraft continua em 1.20.1 e que fontes e licenças estão
presentes. Isso complementa os testes do núcleo Java e os GameTests; não
substitui a validação dos efeitos físicos no mundo.

## Proveniência e distribuição

`third-party/Baritone/UPSTREAM.md` registra a origem, a versão e os hashes
verificados dos arquivos recebidos. O ZIP enviado pelo usuário é uma cópia
do JAR compilado, não um pacote de fontes. O código usado pela integração
vem dos fontes upstream identificados e das adaptações versionadas neste
repositório.

`third-party/Baritone/NOTICE.md`, `LGPL-3.txt`, `GPL-3.txt` e a imagem upstream
`LICENSE-Part-2.jpg` acompanham a distribuição. Os avisos e licenças são
incluídos no JAR em `META-INF/licenses/baritone/`. Os artefatos devem incluir
os JARs de fontes da biblioteca e do AI Player, além dos binários.

O núcleo derivado de Baritone é LGPL-3.0-or-later. O restante do projeto
conserva seus próprios direitos. O estado específico de compilação e
GameTests de cada revisão deve ser consultado no workflow correspondente;
este documento não declara testes executados sem o respectivo resultado.

## Cobertura de regressão desta integração

A suíte contém 103 GameTests: os 79 originais, quatro regressões de
ferramentas e mineração e 20 cenários de navegação e projetos. O teste de
borda do piso verifica uma rota física sem teletransporte. O cenário de
item submerso zera apenas o impulso lateral aleatório da montagem do teste,
preservando gravidade, flutuação, tempo limite e verificações originais.
O plantio com fabricação automática também verifica que uma única enxada\nreal é produzida e utilizada, sem repetir a receita após sua conclusão.\nOs resultados de execução pertencem à revisão identificada no Actions.
