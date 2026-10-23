# AI Player v0.4.0-dev | patch após primeira execução real de GameTests (20/10/2026)

Origem: checkpoint v0.4.0, com melhorias anteriores preservadas. A execução do GitHub Actions [39482902443](https://github.com/manobilas2007-sys/AI-Player/actions/runs/39482902443) compilou os fontes e executou os **48 GameTests** do ZIP anterior: **45 aprovados, 3 falharam**. As falhas foram: `detectsStoneThirtyBlocksAway`, `foodTaskCollectsLooseWood` e `followingAcceptsOpenSlope`.

Este novo ZIP contém correções para os três cenários: teste de altura absoluta, memória de observação de pedra exposta mesmo antes de formar rota, coleta oportunista de troncos sem contabilizá-los como comida, e teste de encosta com solo construído de verdade.

Na revisão local do patch, os **38 testes de comandos** e **110 verificações da arbitragem** passaram novamente. **O patch ainda NÃO foi recompilado nem reexecutado no Minecraft**. O próximo GitHub Actions deve repetir a suíte completa; não presuma 48/48 aprovados.


## Revisão após relato no Fold Craft Launcher

As falhas de mundo aberto relatadas após 48/48 GameTests motivaram um reparo com cinco novos testes. Resultados de GameTests desta revisão ainda **não disponíveis**. Consulte docs/REGRESSOES_MUNDO_REAL_V0_4_0.md.

O ZIP precedente chegou a **48/48 GameTests aprovados** na execução GitHub Actions 39484759402. Esta revisão posterior ainda aguarda a execução dos **53** testes, e a aprovação anterior não é reutilizada como evidência.

## Revisão posterior: mineração com geometria segura

Relato de caverna real: NPC cavava o chão, caía no buraco e perdia os blocos usados para sair. Foram implementados executor de escada apoiada, galeria 2×2 com piso intacto e retomada de copas previamente identificadas. **59 GameTests definidos, sem execução Minecraft confirmada nesta revisão**. 38 parser + 110 prioridades passaram localmente. Consulte docs/ESCAVACAO_PLANEJADA_V0_4_0.md.

## Retificação: recolher a torre, não evitá-la

Revisada a política de `TemporaryPillar`: não bloquear alturas úteis arbitrariamente, mas garantir material de apoio para descer e recuperar todos os blocos colocados, com quebra e drops reais. Desmontagem em estágios + NBT e coleta final de drops. Teste de terceiro apoio revisto; novo GameTest de desmontagem com conservação de materiais. **62 GameTests no fonte, pendentes de execução completa desta revisão.** Testes independentes: parser 38/38; prioridades 110/110 aprovados.
