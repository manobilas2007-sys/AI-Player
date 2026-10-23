# AI Player v0.4.0-dev: escavação planejada e recuperação de madeira

## Origem

Parte da v0.4.0 com correções de regressões no Fold Craft Launcher e 53 GameTests definidos. O usuário demonstrou em screenshots que, no mundo real, o NPC abria buracos no chão da caverna, caía, reconstruía um pilar com pedregulho e logo o quebrava, voltando a perder os drops. Árvores previamente cortadas também ficavam difíceis de identificar.

## Mudanças de código desta revisão

- `ai/SafeExcavation.java` (novo): executor limitado a 10 degraus ou 12 passos de galeria, com geometria física e segurança por passo. Escada desce avançando **um bloco horizontal para cada bloco vertical**, sem abrir poço vertical sob o NPC. Galeria abre **dois blocos de largura e dois de altura**, preservando o piso de ambos os corredores. O acesso permanece praticável na volta, desde que não haja mudanças externas no mundo.
- O executor quebra terra, grama, neve e pedra **fisicamente** por `PhysicalMining`, consumindo durabilidade e gerando apenas drops do Minecraft, sem conceder itens virtuais. Veta lava/água adjacente, teto com gravidade, blocos protegidos, entidades na área de escavação e piso sem apoio.
- Escadas passam a galerias horizontais após a primeira camada de pedra minerada. Galerias deixam de avançar após se esgotar a frente útil; alvos distantes na parede não são descartados antes de alcançar a primeira pedra.
- `SurfaceStoneGathering`: pedra do chão não é mais escolhida como bloco avulso a ser quebrado, mesmo quando o NPC consegue alcançá-la. O NPC busca parede e teto seguros; se só houver chão, tenta escada, não poço. Pedra do teto, ao alcance e com piso estável, continua podendo ser quebrada normalmente.
- `ResourceGathering`: árvores naturais já memorizadas podem ser revisitadas se o tronco inferior foi removido mas ainda existe copa com troncos flutuantes. A revisão não autoriza minerar construções desconhecidas. Árvores parcialmente terminadas podem ser registradas para outra tarefa.

## Limitações deliberadas

- Não escava minérios especiais, cascalho nem blocos com inventário no trajeto. Não mexe em estruturas protegidas. Para bloqueios a rota é abortada em vez de perfurar cegamente.
- Não é um algoritmo universal de procura de caminho subterrâneo. A galeria é retilínea e limitada; passagens inundadas, fissuras e blocos não autorizados exigem procurar outra direção.
- A segurança do retorno depende também de mudanças no mundo causadas por outros jogadores/mobs. As escadas e pisos não são destruídos pela tarefa.
- O controlador de combate/sobrevivência pode suspender e retomar o executor, mas a suíte de testes precisa verificar os detalhes temporais num servidor real.

## Testes definidos para o próximo Minecraft GameTest

A revisão inclui **59 GameTests anotados**: 53 anteriores + 6 novos em `src/testmod/java/dev/aiplayer/test/ExcavationTests.java`:

1. Não iniciar escada sobre vazio sem apoio;
2. Não cavar através de bancadas de trabalho;
3. Não abrir parede com lava atrás;
4. Escavar o primeiro trecho de galeria 2×2 sem remover seu piso;
5. Minerar uma pedra de teto mantendo o suporte;
6. Abrir um degrau sem remover o apoio atual ou futuro.

**Resultado real desta revisão:** 38 testes do parser e 110 checagens de prioridades passaram via `javac/java`. O Gradle 8.7 **não pôde baixar** por `UnknownHostException: services.gradle.org`; portanto nenhum dos 59 GameTests nem a compilação completa Fabric foram executados neste ambiente. O histórico de 48/48 pertence a um ZIP anterior, NÃO serve como validação deste.

### Como testar no Actions

Enviar este ZIP à raiz do repositório com o nome `AI_Player_v0.4.0_fontes_corrigidos.zip` e executar `Build AI Player Fabric 1.20.1`, ativando `run_gametests`. A verificação deve imprimir `59 GAME TESTS COMPLETE` e `All 59 required tests passed` caso todos passem. Uma falha exige correção: não reduzir o critério de testes.

### Cenários manuais no Fold Craft Launcher

- Ordem de oito pedras numa caverna; verificar que ele desce por degraus e recolhe itens sem o ciclo cair/empilhar/remover o próprio suporte.
- Ordem de oito pedras em planície com camada de terra sobre a rocha; procurar escada inclinada de retorno e galeria de seção 2×2.
- Pedra na parede e no teto; verificar que preserva o chão e recupera drops reais.
- Árvore natural parcialmente cortada e lembrada de uma ordem anterior; verificar que não confunde copa flutuante com estrutura do jogador.
- Combate durante a escavação, pausa, retomada e retorno ao jogador sem deslocamento artificial.
