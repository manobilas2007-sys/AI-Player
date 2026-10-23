# AI Player 0.3.3-dev — ferramentas, animações e drops

Minecraft Java 1.20.1 / Fabric. Base: fontes da v0.3.2-dev enviada pelo usuário.

## Alterações
- Fabricação não chama mais a animação de quebrar ou balançar a ferramenta. A quebra real da bancada continua animada.
- Ataques têm um estado visual de oito ticks sincronizado do servidor para o cliente, separado da mineração.
- Com equipamento automático ligado, combate prioriza espadas disponíveis e usa machado como alternativa sem espada. Troca antes do golpe, mesmo com coleta em andamento.
- Mineração seleciona a ferramenta mais rápida disponível para o bloco, inclusive ao recuperar a bancada. Trocas preservam as pilhas reais da mão e mochila.
- Coleta busca a posição do item e valida o alcance final da rota, em vez de usar a distância de trabalho de mineração.
- Madeira caída também pode ser recolhida durante a busca por comida e no intervalo entre tarefas, mesmo sem necessidade de fabricar mais equipamentos.
- Antes de encerrar coleta de madeira, tenta recolher os drops próximos. Mantém timeout e exclusão temporária de drops inacessíveis.
- Workflow atualizado para produzir ai-player-0.3.3-dev.jar.

## Validação desta entrega
- 13 testes do interpretador de comandos: aprovados.
- 21 arquivos Java: análise sintática aprovada pelo parser do JDK 17.
- Compilação completa tentada: bloqueada no download do Gradle 8.7 (Network is unreachable).
- GameTests adicionados para combate durante coleta/troca de ferramentas e madeira caída durante busca por comida; teste de bancada ampliado para verificar ausência da animação de quebra durante fabricação.
- GameTests NÃO executados nesta versão. Parser não verifica tipos nem compatibilidade com APIs Minecraft.
- Sem validação gráfica ou teste no FCL. Sem JAR compilado nesta entrega.
- Nenhuma dependência nova; GeckoLib e SmartBrainLib não são necessárias para estas alterações.

## Compilar
Extraia os arquivos na raiz do repositório, incluindo .github. Execute o workflow existente.
O artefato deve conter ai-player-0.3.3-dev.jar. Opcionalmente habilite run_gametests para executar a suíte de regressão.

## Teste no mundo (faça uma cópia antes)
1. Deixe autoequipar/coletar/fabricar ligados; forneça troncos e observe fabricação sem movimentos de mineração.
2. Confira que a animação volta somente quando ele quebra a bancada.
3. Com espada e machado na mochila, provoque um alvo: confira espada e movimento do golpe.
4. Retome coleta de madeira e confira a volta ao machado, sem perder/duplicar a espada.
5. Jogue troncos a 3–6 blocos no chão e sobre neve, durante coleta de madeira, busca de comida e intervalo ocioso.
6. Repita com mochila cheia, comida em uso e modo Esperar: não deve duplicar itens nem iniciar deslocamento autônomo em Esperar.
