# AI Player 0.3.1-dev — Minecraft Java 1.20.1 Fabric

Continuação do ZIP v0.3.0-dev corrigido de sintaxe. Esta é uma versão de desenvolvimento em código-fonte, NÃO um JAR compilado ou uma versão validada no Minecraft.

## Alterações desta retomada

- PhysicalMining: quebra por ticks, dureza e velocidade da ferramenta; animação, rachaduras, alcance de 4,5 blocos a partir dos olhos e teste de obstrução. Penalidade no ar/água. Ferramenta inadequada não recebe drops de blocos que exigem ferramenta. Durabilidade pelo comportamento do item.
- Drops físicos: os itens surgem na posição do bloco; só entram na mochila pela coleta próxima, respeitando atraso de coleta. Não há inserção remota ao cortar troncos.
- Coleta: corrigida perda de identidade do ItemStack após transferência para a mochila; progresso de madeira/comida contabiliza itens recebidos. Busca de drops durante tarefas.
- Árvores: registra troncos conectados numa busca limitada (160 troncos, 30 blocos de altura, 6 de extensão lateral), incluindo galhos; folhas imediatamente acima podem ser quebradas para abrir espaço. Folhas restantes seguem decomposição vanilla.
- Colunas: usa troncos da mochila; salta fisicamente e só coloca quando o corpo passou acima do espaço. Registra posições; desmonta de cima para baixo usando mineração normal; salva os apoios em NBT. Drops dos apoios são marcados para não contar novamente como madeira nova.
- Bancada: deixa de desaparecer e reaparecer no inventário; quebra gradual, drop físico e tentativa de aproximação para recolher. A recuperação pendente é salva.
- Mantidos os controladores anteriores de combate, fuga, alimentação, mochila, equipamentos e comandos. Fabricação também pode avaliar equipamentos de metal sem exigir primeiro uma pilha de tábuas.
- Workflow incluído aponta para ai-player-0.3.1-dev.jar.

## Validação realizada nesta sessão

- 13 testes independentes do interpretador de comandos: PASSARAM.
- Parse Java 17 de todos os 20 fontes Java: PASSOU (somente sintaxe; não valida tipos/APIs).
- Compilação Gradle: BLOQUEADA antes de compilar, Network is unreachable ao obter Gradle 8.7. Nenhum JAR foi produzido.
- Dois GameTests novos: quebra gradual produz exatamente um drop físico sem inserir remotamente; coleta preserva identidade e contabiliza três troncos. NÃO EXECUTADOS, pois dependem do build Minecraft/Fabric.
- Relatórios antigos em docs pertencem a versões anteriores e não comprovam esta versão.

## Próxima validação

No repositório GitHub, colocar o conteúdo desta pasta na raiz e executar o workflow incluído. Alternativamente, em ambiente com Java 17, Gradle e dependências disponíveis:

    ./gradlew clean build
    ./gradlew runGametest

Testar em cópia de mundo: sem ferramenta versus machado; mochila cheia; bancada emprestada versus colocada pelo NPC; árvore alta; interrupção por combate; salvar/sair sobre coluna; cancelamento e recuperação de apoios. Testar também comandos, slimes, comida, fuga e inventário para detectar regressões.

## Limites ainda abertos

- Mineração realista aqui significa a quebra física usada nas tarefas existentes. Busca de minérios, túneis, mineração por veios e fornalha NÃO foram implementadas.
- Colunas e navegação precisam de teste real: obstáculos laterais, árvores largas, combate que derrube o NPC e apoios que fiquem inacessíveis podem exigir ajustes.
- Detecção de árvore ainda é heurística: troncos construídos junto de árvores podem ser confundidos com madeira natural.
- Velocidade não reproduz ainda todos os encantamentos e efeitos de poção do jogador.
- O registro de um apoio detecta mudanças no estado do bloco, mas não distingue um bloco idêntico recolocado por outro jogador.
- Não houve integração de SmartBrainLib/GeckoLib nesta rodada. Arquivos externos não foram incluídos. A avaliação anterior identificou Baritone enviado como NeoForge 1.21.1, fora do alvo Fabric 1.20.1.

## Referências de API consultadas

- https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/item/ItemStack.html
- https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/item/Item.html
- https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/entity/ItemEntity.html
