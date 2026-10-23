# Migração dos fontes r7 para a raiz

O projeto foi extraído integralmente de `AI_Player_v0.4.1_fontes_r7_FallingTree_machado_corrigido.zip`, preservado no repositório.

- SHA-256 do ZIP: `5b98660c3a62447c6cb490e6e36c44df89cb09089c63070256c5a97501fc0761`.
- Fontes Java, recursos, Gradle, configurações Fabric, dependência FallingTree e testes são idênticos aos do ZIP.
- Minecraft 1.20.1, Java 17, Gradle Wrapper 8.7, Fabric Loader 0.15.11, Fabric API 0.92.2+1.20.1 e FallingTree 4.3.4 foram mantidos.
- Ambos os workflows compilam a raiz; build-fabric também executa os GameTests em pull requests.
- Nenhum comportamento do NPC foi alterado.

## Validação local

38 testes do interpretador e 110 verificações de prioridades passaram. As 79 anotações `@GameTest` foram preservadas em cinco classes. A comparação byte a byte com o ZIP confirmou a integridade dos arquivos de implementação e testes.

A tentativa de `gradle clean build compileTestmodJava runGametest --no-daemon` com Gradle 8.7 e Java 17 foi bloqueada pelo sandbox ao abrir o socket local do daemon (`java.net.SocketException: Operation not permitted`), antes da compilação. Isso não constitui aprovação da compilação nem dos GameTests. A validação completa deve ser conferida no workflow do PR; os relatórios e logs são publicados como artefatos.
