# AI Player v0.4.1 — revisão das falhas de 21/10

Base: ZIP completo da v0.4.1 enviado pelo usuário. Nenhum subsistema foi recriado.

### Correções

- A ordem manual de pedra permanece ativa enquanto a picareta não estiver pronta. Se fabricação automática estiver ativada, o NPC aguarda e fabrica as peças físicas, em vez de cancelar a ordem por `NEED_TOOL`.
- A coleta autônoma dá prioridade ao pedregulho real que falta quando já existe picareta utilizável. A reserva opcional de troncos não interfere nessa dependência.
- Durante mineração autônoma, a inteligência pode pausar **entre blocos** para fabricar ferramenta de pedra que já possua materiais reais, mantendo o plano de escavação e sem reiniciar uma quebra física comprometida.
- Se há bancada própria prestes a ser recuperada e o inventário já permite fabricar uma ferramenta de pedra, a bancada é usada primeiro; só depois a rotina de recolhimento é retomada.
- A descida de torres recebeu ajuste na condução física lateral e na espera pela coleta do bloco reutilizável. O algoritmo continua quebrando os apoios fisicamente e requer drops reais: não invoca setPosition/teleporte para descer nem insere material virtual.

### Validação

- 38 testes do interpretador de comandos executados com `javac --release 17`: aprovados.
- 110 verificações da política de prioridades executadas com `javac --release 17`: aprovadas.
- Sintaxe de todos os fontes Java: verificada por compilador Java 17 em modo de análise sintática.
- `gradlew` e GameTests Fabric: **não executados nesta revisão**. O wrapper não baixou a distribuição porque `services.gradle.org` falha na resolução DNS no ambiente atual. Os GameTests precisam de nova execução no GitHub Actions; não se deve declarar 65/65 até isso ocorrer.

### Atenção

A suíte anterior (versão antes deste patch) apresentou 61/65 no Minecraft. Isso NÃO comprova que os reparos acima foram validados no jogo. Testar também em mundo comum: a altura da torre, recolhimento de todos os drops, crafting com bancada própria, mineração a partir de mochila vazia e recuperação do objetivo depois de pausas.


## Revisão r2, 21/10/2026

- A coleta de pedra mantém prioridade sobre recuperação opcional de bancada. Caso a bancada própria esteja no acesso imediato, a recuperação física pode desobstruir a passagem antes de iniciar a busca automática.
- Folhas naturais sobre o caminho não contam como teto sólido; estruturas artificiais e riscos de terreno continuam sendo rejeitados.
- Adicionados diagnósticos de rota nos testes anteriormente reprovados, sem alterar suas asserções.
- Testes locais: 38 comandos, 110 verificações de prioridade, 36 fontes com sintaxe Java 17 válida. Minecraft/Gradle r2 pendentes.


## Revisão r3, 21/10/2026

- Corrigida a transição entre mineração de um bloco da torre e descida: o NPC espera recuperar fisicamente o drop do suporte superior, em vez de gastar o último apoio e ficar preso.
- Corrigida a navegação curta na água quando um item flutua para a célula de ar diretamente acima da superfície; raycast do corpo complementa o dos olhos.
- Os testes de torre e água continuam ativos e com critérios inalterados; diagnósticos expandidos.
- Validado localmente: 38 testes de comandos e 110 verificações de prioridade passaram. Compilação Fabric e 65 GameTests r3 pendentes devido a falha de DNS do Gradle.
