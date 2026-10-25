# Baritone: núcleo adaptado para AI Player

O módulo `embedded-baritone/`, pacote `dev.aiplayer.baritone`, contém código
derivado do Baritone e é distribuído sob **GNU LGPL versão 3 ou posterior**.
Os cabeçalhos e créditos dos arquivos upstream permanecem nos fontes.

Projeto original: Baritone, por leijurv, Brady e demais contribuidores.

- Projeto: https://github.com/cabaletta/baritone
- Versão de referência: `v1.10.5`
- Commit: `8c55ad0721d42a01a359834edc16aaa9b5bc587d`
- Fontes: https://github.com/cabaletta/baritone/tree/8c55ad0721d42a01a359834edc16aaa9b5bc587d
- Licença upstream: https://github.com/cabaletta/baritone/blob/8c55ad0721d42a01a359834edc16aaa9b5bc587d/LICENSE

As adaptações do AI Player separam o planejamento do jogador cliente e o
disponibilizam ao NPC no servidor. Elas não transformam todos os processos,
mixins e recursos do cliente Baritone em funcionalidades do NPC. A descrição
do que está integrado encontra-se em `docs/INTEGRACAO_BARITONE.md`.

Os textos completos estão em `LGPL-3.txt` e `GPL-3.txt`. A imagem
`LICENSE-Part-2.jpg` é preservada do mesmo commit upstream. Estes avisos e
licenças acompanham o JAR do AI Player em `META-INF/licenses/baritone/`.

## Fontes e substituição da biblioteca

Os fontes modificáveis da biblioteca estão em `embedded-baritone/`, junto
dos scripts Gradle de compilação. O repositório do AI Player disponibiliza
também os fontes do adaptador e os arquivos de configuração necessários para
recompilar a combinação. Os artefatos de distribuição incluem o JAR de
fontes da biblioteca e o JAR de fontes do AI Player.

É permitido modificar a biblioteca LGPL, depurar tais modificações e
recombinar/recompilar o AI Player com uma versão modificada da biblioteca.
As declarações de direitos reservados do restante do projeto não restringem
esses direitos. Veja `LICENSE.md` na raiz e as instruções de recompilação em
`docs/INTEGRACAO_BARITONE.md`.

O Baritone original não precisa ser instalado como mod separado. As classes
da biblioteca Java adaptada são incluídas diretamente no JAR principal
durante a construção do AI Player, sem registro de um mod interno separado.
O módulo de fontes e seu binário não combinado continuam disponíveis para
inspeção, modificação e recompilação da combinação. O JAR completo
do mod Baritone enviado pelo usuário não é carregado nem redistribuído como
se fosse uma implementação de inteligência do NPC.

Alterações para integração com AI Player: AI Player contributors, 2026.
