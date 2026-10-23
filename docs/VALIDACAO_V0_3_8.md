# Validação v0.3.8-dev

Execução `runGametest`: **BUILD SUCCESSFUL**, com compilação dos fontes principais e de teste. **35/35 GameTests aprovados**, em servidor Minecraft 1.20.1 Fabric, Java 17 e Gradle 8.7.

Novos testes: saída lateral de um buraco com teto de madeira, preservando o teto e sem colocar apoio na coluna bloqueada; recusa de salto cujo ponto mais alto atingiria o teto, sem consumir materiais. Os 33 cenários anteriores também passaram.

Relatório real: `gametest-results-v0.3.8.xml`.

Não houve sessão visual de cliente/FCL nem cobertura de todas as geometrias naturais ou construídas. Os testes usam cenários controlados. O GitHub remoto não foi modificado nem executado. Não foi gerado JAR de distribuição nesta entrega.

Foi utilizado o cache local de ferramentas das versões anteriores, com a sondagem opcional de sockets Unix do Loom desativada para este ambiente. Essa adaptação não integra o projeto entregue. Avisos de autenticação externa não impediram a execução local dos testes.
