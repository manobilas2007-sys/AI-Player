# AI Player 0.3.6-dev — continuidade de coleta e recuperação do terreno

Minecraft Java 1.20.1, Fabric, Java 17. Base: fontes entregues da v0.3.5-dev.

## Correções desta versão
- A coleta mantém o bloco em andamento até concluir a quebra. Isso inclui folhas abertas para alcançar drops. Procurar itens no chão não reinicia mais a mineração; combate, ordens e situações de emergência ainda podem interromper trabalho.
- O tronco escolhido permanece estável durante a quebra. A escolha por distância deixa de alternar o alvo conforme o NPC se move.
- Rotas de trabalho e de aproximação a drops rejeitam descidas superiores a um bloco em relação ao ponto de partida e ao nível do destino. O deslocamento central também usa esse limite, reduzindo atalhos sob o terreno. Se uma árvore não tem rota aceita, a tarefa pode procurar outra.
- Recuperação local de buracos cercados: primeiro tenta saltar e colocar apoio do inventário; se isso não for possível, abre um degrau em terreno permitido com mineração física. Após subir, tenta alcançar a borda.
- Apoios da recuperação têm registro persistente separado. Sua retirada só ocorre de uma posição lateral segura e alcançável, sem conduzir o NPC de volta ao fundo ou remover seu apoio atual.
- Todos os blocos colocados pelo jogador e observados pelo mod passam a ser protegidos; não apenas troncos e bancadas.

Essas mudanças reforçam os executores e a navegação existentes. A coordenação central, prioridades, memória, inventário, combate, fabricação, sobrevivência e comandos da v0.3.5 continuam presentes. Não foram adicionadas bibliotecas ou mineração de minérios.

## Recuperação e limites
A recuperação construtiva inicia quando o NPC está no chão e cercado nos quatro lados por obstáculos de dois blocos. Não é um escavador universal de cavernas: caminhos longos, tetos complexos ou buracos com geometria irregular podem exigir intervenção. Cada tentativa tem duração máxima e intervalo para nova tentativa.

A escada aceita terra e variantes do grupo DIRT, pedra, andesito, diorito e granito. Não abre blocos protegidos, blocos com inventário, posições reclamadas por outro sistema, bolsões de líquidos ou suporte de areia/cascalho. `mobGriefing` continua sendo respeitado. A proteção de construções antigas ou alterações feitas por comandos/outros mods não é garantida: o registro depende de observar a colocação.

Apoios inacessíveis permanecem registrados; a versão não garante recuperação de todos os blocos abandonados a grandes distâncias. Rotas com desníveis maiores podem ser recusadas mesmo quando um jogador conseguiria atravessá-las. O limite é deliberado para tarefas de superfície; navegação subterrânea dedicada permanece fora desta atualização.

## Compilar pelo GitHub Actions
Extraia o ZIP na raiz do repositório, preservando `.github/workflows/build-fabric.yml`, os fontes e os arquivos Gradle. O workflow usa Java 17 e executa compilação, testes de comandos/prioridades e GameTests.

Se preferir manter o ZIP no repositório, coloque também o workflow desta versão na pasta `.github/workflows/` da raiz. O nome esperado é `AI_Player_Java_1.20.1_v0.3.6_dev_fontes.zip`. O workflow antigo de uma versão anterior não é atualizado automaticamente ao enviar apenas o ZIP.

Comandos equivalentes: `bash gradlew compileJava compileTestmodJava runGametest` para validação e `bash gradlew build` para o JAR. Nenhum JAR está incluído nesta entrega, conforme solicitado.

## Teste recomendado no FCL
1. Inicie sem ferramentas, diante de uma árvore de três ou mais troncos. Observe o segundo tronco enquanto o primeiro drop ainda está no chão.
2. Repita em floresta de pinheiros com folhas baixas, neve e entrada de caverna entre o NPC e a árvore.
3. Coloque o NPC em buraco de dois blocos: teste primeiro sem materiais e depois com três blocos de pedregulho. Confira a subida, os drops e o inventário.
4. Repita com blocos colocados pelo jogador e verifique que a escavação não os remove.
5. Ataque durante a coleta; confira defesa, troca para espada e retorno à atividade.
6. Salve e reabra o mundo após a colocação de um apoio. Confira a conservação dos itens e do registro.

Resultados automatizados e limitações da validação estão em `docs/VALIDACAO_V0_3_6.md`. Não houve validação visual no Android/FCL.
