# AI Player 0.3.4-dev — mão correta e revisão das pausas

Base: v0.3.3-dev; Minecraft 1.20.1 Fabric.

## Revisão implementada
- Animação consulta getMainArm(), a mesma mão dominante usada para renderizar o item. Mineração, ataque e alimentação agora escolhem o braço correspondente; espelhamento lateral e ambas as mangas são atualizados.
- Busca por comida sem alvos libera o controle de movimento durante a espera pela próxima busca.
- Seguir o dono a mais de 15 blocos tem prioridade sobre coleta automática sem colunas pendentes. Ordens manuais não são canceladas por essa regra.
- Passeio próximo usa intervalo de 2–4 segundos entre tentativas em vez de uma chance aleatória que podia adiar indefinidamente a movimentação. Continua respeitando Esperar.
- Árvores sem caminho completo são temporariamente ignoradas; pontos de exploração têm limite de tentativa.
- Caça exige linha de visão para golpear, equipa arma durante a aproximação e desiste temporariamente de animais inacessíveis.
- Aproximação de bancada e recuperação de colunas inacessíveis cedem movimento após tentativas limitadas e voltam a tentar depois. A propriedade continua registrada para não esquecer a limpeza.
- Sem novas dependências e sem novos sistemas de mineração de pedra/minérios nesta versão.

## Validação
13 testes de comandos aprovados; 21 arquivos Java analisados sintaticamente no JDK 17.
Adicionados GameTests para busca de comida vazia liberar movimento, prioridade de tarefas automáticas/manuais e recuperação de bancada bloqueada sem perder propriedade.
GameTests não executados. Build tentado, bloqueado no download do Gradle 8.7 por Network is unreachable.
A análise sintática não substitui compilação com as APIs Minecraft.
Não testado em cliente gráfico nem no Fold Craft Launcher; ZIP contém fontes, não um JAR validado.

## Conferência no jogo
- Testar NPC destro e canhoto: item, golpe, mineração, alimentação e mangas no mesmo braço, também com armadura.
- Sem animais por perto, verificar que continua acompanhando/passeando enquanto busca comida.
- Afastar-se mais de 15 blocos durante coleta automática; repetir com ordem manual para conferir a diferença.
- Testar árvore/animal atrás de obstáculo e bancada inacessível; confirmar que não insiste parado indefinidamente.
- Confirmar recuperação das colunas e da bancada após reabrir o mundo.
- Modo Esperar, mochila cheia e autoequipar desligado continuam necessários na checagem de regressão.

Atualização: substitua a versão anterior do mod depois de compilar; evite manter dois JARs AI Player juntos.
