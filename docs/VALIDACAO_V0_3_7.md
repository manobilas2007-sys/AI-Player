# Validação v0.3.7-dev

Compilação dos fontes principais e de testes concluída; execução final `runGametest`: BUILD SUCCESSFUL, **33/33 GameTests aprovados** no servidor Minecraft 1.20.1 Fabric/Java 17.

Novos cenários: saída de buraco 2x2 com neve durante ordem manual no modo Esperar; detecção e memória de árvore a 30 blocos em corredor acessível; aceitação de caminho ascendente de dois blocos (caminho controlado). Os 30 cenários anteriores também passaram.

A primeira versão do teste da árvore usava uma plataforma isolada sem acesso. A memória exclui temporariamente destinos bloqueados das consultas de seleção; isso misturava detecção com acessibilidade. O cenário final possui solo contínuo entre NPC e árvore. O relatório final real está em gametest-results-v0.3.7.xml.

Não foi testada sessão visual no FCL/Android, floresta natural prolongada, todas as formas de buracos, nem frequência de teleporte com jogador real. A redução dos teleportes foi implementada com atraso por falta de progresso e intervalo entre usos, mas ainda requer observação no jogo. O teste de subida verifica aceitação de caminho, não uma sessão completa seguindo jogador.

Sem JAR nesta entrega. O workflow está incluído; o GitHub remoto não foi modificado ou executado. Usado o cache de ferramentas local com a sondagem opcional de sockets Unix do Loom desativada para este ambiente, como nas entregas anteriores; essa adaptação não está nos fontes. Avisos de autenticação externa do servidor não impediram os testes locais.
