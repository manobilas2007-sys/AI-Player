# Validação v0.3.9-dev

Compilação de fontes principais e de testes concluída. `runGametest` terminou com código Gradle 0. **36/36 GameTests aprovados** no servidor Minecraft 1.20.1 Fabric/Java 17.

Novo teste: primeira fala disponível, bloqueio global de repetição rápida, bloqueio do mesmo motivo, persistência dos intervalos após recarregar NBT, alternância das frases, preferência de silenciar persistente e inventário/tarefa inalterados pelo gerador de falas. Os 35 testes de regressão existentes também passaram.

Relatório real: `gametest-results-v0.3.9.xml`.

Não houve sessão visual no FCL, teste com cliente conectado para observar chat, nem teste interativo dos comandos. A camada de fala e o registro de comandos foram compilados; a limitação e persistência foram exercitadas no servidor. Os métodos de comunicação enviam apenas ao dono próximo. A personalidade é textual e contextual, sem geração de voz ou conversa livre.

Nenhum JAR de distribuição foi produzido. Workflow incluído; GitHub remoto não modificado. O ambiente usa o cache de ferramentas local das versões anteriores, com a sondagem opcional de sockets Unix do Loom desativada; essa adaptação não integra os fontes entregues.
