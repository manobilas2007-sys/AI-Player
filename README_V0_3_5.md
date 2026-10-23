# AI Player 0.3.5-dev — coordenação e memória operacional

Minecraft Java 1.20.1 · Fabric Loader 0.15.11 · Fabric API 0.92.2+1.20.1 · Java 17.

## Base conferida
O ZIP v0.3.4 do repositório manobilas2007-sys/AI-Player, no commit
76bd929922191f5f11529e27226442418dd5d929, tem blob Git
f9751e95d2dc54e583fbc98e3a8e3922f6f88eda, idêntico ao ZIP usado nesta atualização.
A correção do TemporaryPillar que existia apenas no workflow foi incorporada ao fonte.
Não é mais necessário aplicar substituições de texto antes de compilar.

## O que mudou

### Um único coordenador
CompanionIntelligence é o ponto de execução de decisões no servidor.
Ele coleta ameaças, consulta o inventário, monta candidatos com prioridades e
despacha apenas uma atividade por tick. Os controladores de coleta e fabricação
executam ações; não competem mais por decisões independentes no tick da entidade.

Prioridades dependem de ameaças, saúde, distância do dono, ordem manual, espaço
na mochila, materiais e tarefas de recuperação. A sessão de inventário pausa
ações; perigo ambiental, retirada, combate e alimentação segura preemptam trabalho.
O comando Esperar bloqueia trabalho autônomo; uma ordem manual explícita de coleta
continua sendo executável nesse modo, como nas versões anteriores.

Uma atividade interrompida conserva quantidade, progresso e troncos restantes.
Os relógios de timeout de coleta são pausados. Seguir o dono não cancela a coleta.
Quando comida se torna crítica durante coleta de madeira, o objetivo anterior é
salvo como tarefa suspensa, e retomado após a tentativa de conseguir alimento.
A tentativa emergencial tem intervalo para não reiniciar incessantemente.

### Memória que influencia decisões
BrainMemory guarda recursos encontrados e falhas de navegação, com prazo,
contagem de falhas e escopo por dimensão. Árvores conhecidas são consultadas
antes da busca espacial. Destinos com falhas recentes são evitados temporariamente.
Há um limite de memória para impedir crescimento ilimitado por NPC.

Até oito árvores parcialmente trabalhadas podem ser adiadas e revisitadas,
com a lista real de troncos restantes e prazo de nova tentativa. Essa lista,
objetivos, tarefa suspensa, bancada e apoios persistem no NBT.

### Planejamento a partir dos itens reais
ResourcePlan identifica ferramentas ausentes ou desgastadas, materiais para
ferramentas básicas, gravetos, bancada, alimento e reserva de madeira.
A fabricação mantém sua sequência de receitas e usa a bancada enquanto houver
receitas executáveis. Materiais só são consumidos quando a receita termina e
o resultado cabe. Ao recarregar, a receita é reavaliada contra o inventário real.

Ferramentas perto do fim da durabilidade são substituíveis. Apoios preferem terra
ou pedregulho da mochila, conservando madeira mínima para emergências.
A coleta pausa quando falta espaço; não joga fora itens para abrir espaço.

### Movimento e sobrevivência
NavigationMonitor mede deslocamento e aproximação ao destino. Tenta refazer a rota,
um desvio local e, se não houver progresso, registra a falha e libera o alvo.
Rotas verificam chunks carregados, quedas entre nós e blocos perigosos.
O teleporte continua sendo recuperação de último recurso para seguir o dono,
com procura de espaço seguro, sem atravessar dimensões.

A avaliação de combate inclui quantidade de ameaças próximas e saúde.
A retirada não é encerrada antes da recuperação adequada; comida pode ser usada
quando o inimigo está distante. Alimentação tem intervalo entre refeições e
persiste a troca comida/arma ao salvar, evitando perder o equipamento.
Coleta próxima usa visibilidade para não atravessar paredes.

### Coleta física e construções temporárias
Mineração continua gradual, com ferramenta real, desgaste e drops no mundo.
Eficiência, pressa e fadiga de mineração entram no cálculo de velocidade.
Antes de quebrar, uma cópia do inventário verifica capacidade para os drops.
A cópia não altera nem cria itens no inventário real.

Drops gerados pelo trabalho recebem identificação do NPC e têm prioridade de busca.
Materiais recuperados de apoios não contam novamente como madeira coletada.

OwnedBlocks registra a autoria dos apoios e bancadas por dimensão.
Colocações de troncos e bancadas observadas de jogadores são protegidas.
Substituir um bloco temporário por um bloco do jogador revoga sua autoria.
A propriedade persiste com o mundo. A recuperação não descarta imediatamente a
lembrança da bancada quando o drop existe mas a mochila está cheia.

### Animações
Mineração, ataque, colocação, fabricação e troca de equipamento têm gestos
distintos, com tempo de término. Todos usam a mão dominante usada pelo renderizador
do item, incluindo NPCs canhotos; ambas as mangas acompanham o braço.
São animações procedurais no modelo existente, não uma nova coleção de animações
de corpo inteiro. Comida continua usando um estado próprio.

### Diagnóstico
Use /aiplayer status perto do seu NPC (até 48 blocos).
Mostra atividade, motivo, objetivo/etapa e quantidades faltantes estimadas.
O comando só consulta o NPC do jogador que o executou.

## Compilação
O ZIP tem os arquivos diretamente na raiz, incluindo Gradle Wrapper e
.github/workflows/build-fabric.yml. Com Java 17:

    bash gradlew clean build compileTestmodJava
    bash gradlew runGametest

O JAR de distribuição esperado é build/libs/ai-player-0.3.5-dev.jar.
Não use o arquivo com sufixo -sources.jar no Minecraft.

O workflow incluído funciona tanto com fontes na raiz quanto com o ZIP
AI_Player_Java_1.20.1_v0.3.5_dev_fontes.zip na raiz.
O workflow antigo do repositório, que extrai a v0.3.4 e aplica um patch, precisa ser
substituído pelo workflow desta versão; enviar só um novo ZIP não altera workflows.
O relatório de validação desta entrega está em docs/VALIDACAO_V0_3_5.md.

## Limitações explícitas
- Não implementa mineração de pedra/minérios, túneis, tochas, fundição ou exploração
  subterrânea. Os novos coordenador, memória e monitor de navegação são a base
  para esses executores na v0.4.0.
- Fabricação usa o catálogo de receitas suportado pelo código existente
  (ferramentas básicas e progressão por materiais disponíveis, armaduras de ferro/
  diamante); não interpreta automaticamente todas as receitas de outros mods.
- Comida é buscada entre os animais já suportados. Não cozinha carne nem identifica
  currais como propriedade do jogador.
- A proteção de construções cobre colocações de jogadores observadas a partir
  desta versão. Estruturas antigas, comandos /setblock e alterações por outros mods
  não têm proveniência automática; a identificação de árvores naturais ainda usa
  tronco, solo e copa. Teste próximo a construções numa cópia do mundo.
- Blocos temporários são recuperados quando ainda acessíveis e reconhecíveis.
  Blocos destruídos por terceiros, drops recolhidos/destruídos por outros agentes
  ou descarregamento prolongado podem impedir a recuperação. Não são recriados.
- Troca de dimensão externa invalida tarefas posicionais para não quebrar blocos
  nas mesmas coordenadas do mundo errado. Não há retorno interdimensional para
  recuperar apoios ou bancada deixados para trás.
- Ao ficar sem espaço, o NPC pode esperar pelo dono liberar a mochila. Não existe
  ainda depósito automático em baús.
- Não há modelo de linguagem remoto, API paga ou necessidade de internet no jogo.
  A inteligência é planejamento e execução locais.
- Desempenho e aparência ainda precisam ser avaliados no Android/Fold Craft Launcher.

## Bibliotecas avaliadas
SmartBrainLib oferece uma estrutura de comportamentos/sensores e GeckoLib um motor
de animações. Nesta versão foram mantidos os goals, navegação e modelo Fabric já
existentes: os novos componentes se encaixam neles sem exigir outra biblioteca.
Isso é uma decisão de integração, não uma meta de tamanho do JAR. Uma migração
futura deve trazer benefício concreto e testes, especialmente no FCL.

Referências oficiais consultadas:
- https://github.com/Tslat/SmartBrainLib
- https://github.com/bernie-g/geckolib
- https://github.com/bernie-g/geckolib/wiki/Installation-(Geckolib4)

## Roteiro recomendado no jogo
1. Em cópia do mundo, substituir o JAR antigo (não manter duas versões).
2. Domesticar NPC novo e carregar um já existente da v0.3.4.
3. Com mochila vazia, observar madeira -> materiais -> bancada -> ferramentas;
   conferir quantidades e reserva.
4. Interromper ordem de madeira por combate, fuga, comida, menu e afastamento;
   verificar retomada com /aiplayer status.
5. Salvar/reabrir durante alimentação, fabricação e construção de pilar.
6. Testar ferramentas desgastadas, NPC canhoto, escudo na outra mão e armadura.
7. Bloquear uma rota, colocar drops atrás de parede e sobre folhas; observar
   novas tentativas limitadas sem teleporte de itens.
8. Encher a mochila, liberar um slot e confirmar retomada sem duplicação.
9. Substituir um apoio por tronco colocado pelo jogador; confirmar proteção.
10. Testar água, degraus, barrancos, lava próxima e afastamento grande do dono.
11. Dar ordem de Esperar e conferir ausência de passeio/coleta automáticos.
12. Testar sem GeckoLib/SmartBrainLib e com os demais mods usuais do seu perfil.

## Validação desta entrega
Build concluído e 26/26 GameTests aprovados. Também passaram 13 testes de comandos e 110 verificações de prioridades. Detalhes e limites em `docs/VALIDACAO_V0_3_5.md`.
