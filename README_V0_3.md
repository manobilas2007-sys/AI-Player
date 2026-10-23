# AI Player Java 1.20.1 Fabric | v0.3.0-dev

Esta é uma atualização de **código-fonte**, ainda **NÃO compilada/validada dentro do Minecraft**. A versão compilada precisa ser gerada por Gradle no Work ou GitHub Actions.

## Mudanças
- O NPC pode procurar árvores naturais, caminhar até troncos, quebrá-los usando machado, desgastar o equipamento e armazenar drops reais.
- Quando faltar comida, pode procurar vacas, porcos, ovelhas e coelhos adultos; combate e recolhe os drops. Não cozinha os alimentos nesta versão.
- Pode receber ordens via chat em português: `você pode pega 10 tronco de madeira pra mim`, `pege 16 madeira`, `traga 8 toras`, `pegue 6 carnes` e `cancelar tarefa`.
- O interpretador é por regras com correspondência aproximada, **não** um modelo de linguagem. Ordens de coleta são limitadas a 1 a 264 unidades; o NPC precisa estar a até 48 blocos do dono que envia a mensagem.
- Fabricação prioriza PICARETA -> MACHADO -> ESPADA e depois armaduras. Upgrade de madeira/pedra para ferro/diamante quando houver materiais.
- Receitas de ferramentas e armaduras exigem bancada no mundo: o NPC a coloca perto de si, trabalha brevemente, consome os ingredientes reais e depois a recolhe.
- Tarefa manual (tipo, progresso, quantidade e centro) e trabalho em bancada pendente são salvos no NBT.
- Proteção e sobrevivência permanecem prioritárias em relação à coleta.

## Limitações conscientes
- Não realiza pathfinding por mineração ainda: depende do navegador vanilla para chegar às árvores e aos animais próximos.
- A detecção de árvores por tronco+folhas é uma heurística; pode falhar em árvores incomuns e não oferece proteção absoluta a construções que imitem árvores.
- Busca automática de comida caça apenas animais terrestres adultos selecionados e sem dono; não pesca, planta nem assa comida.
- A escuta do chat pode interpretar somente pedidos dentro do vocabulário implementado, com tolerância pequena a erros de ortografia. Não é IA generativa.
- Não houve compilação em Minecraft neste ambiente devido à indisponibilidade de acesso aos downloads do Gradle/Fabric. Arquivo .jar NÃO incluído.

## Compilar com GitHub no Android
1. Crie um repositório no GitHub e envie **o conteúdo da pasta raiz deste projeto**, incluindo `.github/workflows/build-fabric.yml`, `gradlew`, `gradle/`, `src/` e `build.gradle`.
2. Acesse **Actions > Build AI Player Fabric 1.20.1 > Run workflow**.
3. Acesse o último workflow e baixe o artefato `AI-Player-Fabric-1.20.1-v0.3.0-dev`.
4. Extraia o `.jar` gerado e coloque em `mods` na instalação Minecraft 1.20.1/Fabric/Java 17 com Fabric API.
5. Se a compilação falhar, envie o log de erro. Não trate esta versão como pronta até compilar e testar no jogo.

## Teste recomendado
- Mundo cópia, perto de carvalhos: `você pode pegar 10 troncos de madeira para mim`.
- Confira se o NPC caminha, usa machado, guarda as toras, avisa quando concluir e volta ao dono.
- Sem comida na mochila, observe se procura animais; depois confira se pega a comida e se recupera.
- Entregue madeira e 64 ferros, e confira **a colocação da bancada no mundo** e a ordem picareta/machado/espada antes da armadura.
- Gere um zumbi perto dele durante a coleta e observe se se defende e continua depois.

## Compatibilidade
Projeto para Minecraft Java 1.20.1, Fabric API e Java 17. Não substitua seu `.jar` anterior por arquivos de código-fonte.
