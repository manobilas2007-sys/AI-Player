# AI Player v0.4.1-dev, revisão r3: validação

## Última execução real antes deste patch

GitHub Actions: https://github.com/manobilas2007-sys/AI-Player/actions/runs/39617227391

- Fabric e Java 17: compilação aprovada no GitHub da revisão r2.
- 65 GameTests executados: **63 aprovados e 2 reprovados** na r2.
- Reprovados: `worldregressiontests.waitmoderecoverstalltowerandcollectsalldrops` e `worldregressiontests.submergeddropacceptsshortswimroute`.
- Falha da torre: `Tower support remains at level 0`, inventário zerado e 4 apoios ainda registrados.
- Falha da água: `Nearby water drop route refused while swimming`.

## Correções da revisão r3

1. `TemporaryPillar`: fase `WAIT_TOP_PICKUP` entre quebrar o suporte superior e caminhar para a coluna inferior. Espera a coleta física, respeitando o atraso real de pickup, para usar a madeira recuperada como próximo apoio lateral. Nenhum item é inserido remotamente; a quebra continua com `PhysicalMining`.
2. `WorkNavigation`: permite coleta de itens flutuantes em ar imediatamente acima de um bloco de água. Verifica visibilidade também do tronco do NPC, que pode ter linha de natação livre mesmo com raycast ocular bloqueado por margem.
3. `WorldRegressionTests` e diagnóstico da torre: mensagens de falha mais completas, sem relaxar as asserções.

## Validação no ambiente desta revisão

- 38 testes unitários do interpretador: **aprovados** (javac + java).
- 110 verificações de prioridades: **aprovadas** (javac + java).
- A sintaxe dos fontes Java foi revisada, mas compilação integral Fabric **não foi executada**.
- `gradlew --version` falhou por `java.net.UnknownHostException: services.gradle.org` antes de baixar o Gradle.
- **65 GameTests da r3 ainda não executados**. Não afirmar 65/65 sem a execução real.

## GitHub

Enviar o ZIP completo r3 à raiz do repositório para o workflow existente `Build AI Player Fabric 1.20.1`, que detecta automaticamente arquivos `*v0.4.1*.zip` e seleciona o último em `sort -V`. O workflow já está incluído no ZIP como referência, mas não requer substituição nesta revisão.
