# AI Player 0.6.0-dev: Baritone, diálogos e projetos físicos

## Arquitetura

A integração utiliza os fontes de referência do Baritone 1.10.5, fixados no
commit `8c55ad0721d42a01a359834edc16aaa9b5bc587d`, para uma biblioteca interna
Java 17 em `embedded-baritone/`. O pacote adaptado é
`dev.aiplayer.baritone`. O build inclui as classes da biblioteca diretamente
no JAR principal do AI Player. O módulo de fontes e seu binário separado
mantêm a versão `1.10.5-npc.1`, mas não são registrados como outro mod
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
com quebras físicas e fundações limitadas a quatro blocos. Construções marcadas
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

A busca usa terreno carregado e recursos expostos e visíveis, com rotas locais
segmentadas. O executor procura madeira da espécie escolhida, pedra, areia,
carvão e ferro; não realiza escavação ilimitada até minério oculto. Quando um
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

## Cobertura de regressão desta integração

Os 104 GameTests anteriores permanecem no projeto. A versão 0.6 acrescenta
cenários de terreno, porta, paleta, construção completa, materiais reais,
fornalha, balde reutilizado, agricultura existente, limite de 100 blocos e
prioridade de alimento. O diálogo tem testes locais de sinônimos, erros de
escrita, confirmação, recusa, expiração, proprietário, dimensão e origem.
Os resultados executados de cada revisão devem ser consultados no Actions;
este documento não declara testes concluídos antes do respectivo relatório.
