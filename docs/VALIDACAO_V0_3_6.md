# Validação v0.3.6-dev

Compilação de fontes principais e dos GameTests concluída com Java 17 e Gradle 8.7. Execução final `compileJava runGametest`: BUILD SUCCESSFUL. **30/30 GameTests aprovados** no servidor Minecraft 1.20.1 Fabric.

## Novos cenários exercitados
- Três troncos consecutivos com drops físicos: todos quebrados e três itens recolhidos, sem duplicação.
- Buraco de dois blocos sem materiais: abertura física de degrau e chegada à borda superior.
- Buraco com pedregulho no inventário: colocação de apoio, chegada à borda e conservação da soma dos materiais no inventário, no chão e no mundo.
- Caminho sintético passando dois blocos abaixo da superfície: rejeitado pela política de rotas.

Os outros 26 testes de regressão da v0.3.5 também passaram. O relatório real está em `gametest-results-v0.3.6.xml`.

Não foi produzido JAR de distribuição, conforme solicitado. O ZIP inclui o workflow para compilá-lo no GitHub Actions; o workflow remoto não foi executado nesta tarefa. A configuração de compilação é a mesma da v0.3.5, com a versão atualizada.

## Limites
O cenário de rota valida a política com um caminho controlado; não demonstra cobertura de todas as cavernas naturais. Os cenários de buracos usam terreno simples controlado. Não houve teste visual em cliente/FCL, sessão longa em mundo aberto nem garantia de fuga de qualquer geometria. Recomenda-se repetir os cenários das capturas do usuário no jogo.

A execução usou o cache local de ferramentas da v0.3.5 com a sondagem opcional de sockets Unix do Loom desativada para este ambiente. Essa adaptação não faz parte do projeto entregue. O servidor exibiu aviso de indisponibilidade do serviço externo de autenticação; a execução local dos testes terminou normalmente.
