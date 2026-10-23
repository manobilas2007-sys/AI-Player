# AI Player v0.4.1 r6 FallingTree: correção de compilação

## Falha verificada

- Repositório: manobilas2007-sys/AI-Player
- GitHub Actions: https://github.com/manobilas2007-sys/AI-Player/actions/runs/39780093870
- Etapa que falhou: `Build mod and compile all GameTests`, durante `:compileJava`.
- Diagnóstico: `FallingTreeBridge.java:113: cannot find symbol removeCommandTag(String)`.

## Correção efetuada

Na classe `dev.aiplayer.compat.FallingTreeBridge`, implementação do adaptador
`IPlayer.removeTag(String)` do FallingTree, a chamada
`npc.removeCommandTag(tag)` foi substituída por
`npc.removeScoreboardTag(tag)`.

`removeScoreboardTag(String)` é o nome mapeado para `Entity` no Yarn
Minecraft **1.20.1**. `removeCommandTag(String)` existe em versões
posteriores, mas não nesta versão do projeto.

Não foram alterados a lógica de corte, os drops físicos, os GameTests,
o jar FallingTree fornecido ou o workflow.

## Verificação nesta revisão

- 38 testes standalone do parser de comandos: **PASSOU**.
- 110 verificações standalone de prioridade: **PASSOU**.
- API `IPlayer` do FallingTree 4.3.4 inspecionada por `javap`: `removeTag(String)` existe.
- ZIP original: íntegro. ZIP corrigido: conferido após montagem.
- Gradle: **NÃO EXECUTADO**; tentativa de iniciar `gradlew --version` falhou
  com `java.net.UnknownHostException: services.gradle.org` antes de baixar Gradle 8.7.
- Minecraft GameTests (77): **NÃO EXECUTADOS** nesta revisão.

O workflow `.github/workflows/build-fabric.yml` foi preservado. Ele extrai
o ZIP v0.4.1 mais recente por ordenação de versão, compila no Java 17,
executa os testes standalone e depois os GameTests.

## Procedimento no GitHub

1. Faça upload de `AI_Player_v0.4.1_fontes_r6_FallingTree_corrigido.zip`
   para a raiz do repositório (`main`). Pode manter o ZIP original,
   pois o workflow seleciona a variante `..._corrigido.zip` ao ordenar.
2. Abra `Actions > Build AI Player Fabric 1.20.1`.
3. Verifique `Build mod and compile all GameTests`. Só considere
   os GameTests aprovados após a etapa `Run Minecraft regression suite` terminar.
4. Baixe o artifact com o jar do AI Player e o jar FallingTree para
   instalar no FCL com Fabric API.

Nenhum resultado de compilação ou de GameTests foi inventado.
