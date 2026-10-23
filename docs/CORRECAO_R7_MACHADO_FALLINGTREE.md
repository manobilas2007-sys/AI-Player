# AI Player v0.4.1 r7: reposição autônoma do machado

## Defeito observado no FCL
O FallingTree corta árvores e gera drops normalmente. Após o desgaste/quebra do machado, a coleta podia continuar ou terminar sem fabricar um substituto, mesmo existindo toras, gravetos e bancada no inventário, em área com neve.

## Causas corrigidas
1. `CompanionIntelligence`: `FOLLOW` tinha prioridade 70 e `CRAFT` 65, impedindo a fabricação quando o NPC estava seguindo o dono. Agora manutenção urgente do machado tem prioridade 78 (85 em espera sem tarefa manual), mas mantém combate, fuga, alimentação, interface e desmontagem da torre acima.
2. `StarterCrafting`: a escolha explícita de novo machado dependia de `gathering.kind()==WOOD`, condição que deixa de existir ao concluir a quantidade pedida. A manutenção considera o equipamento real e os materiais disponíveis, mesmo em `NONE`.
3. `StarterCrafting.putBench`: um solo com uma camada rasa de neve impedia colocar a bancada física. Agora a bancada pode substituir somente uma camada de neve, obedecendo às verificações existentes de suporte sólido, entidades, chunk carregado e `mobGriefing`.
4. Bancada já colocada e em recuperação pode ser reutilizada para a receita urgente de machado se o bloco ainda existir, sem fabricar bancada imaginária.

## Regras preservadas
- Não usar picareta no tronco como substituto para o machado gasto.
- Machado gasto somente é retirado após os materiais terem sido consumidos e a nova ferramenta produzida.
- Coleta de madeira sem material suficiente para fabricar o primeiro machado pode acontecer com as mãos, pelas regras de mineração física.
- FallingTree 4.3.4 continua no modo instantâneo, com drops físicos e consumo real de durabilidade.
- Nenhum bloco/ingrediente é inserido magicamente no inventário.

## Novos testes do Minecraft
- `fallingtreetests.replacesbrokenaxeafterwoodorderwhileguarding`: reposição após a tarefa terminar com o dono distante, modo Proteger e prioridade de seguir ativa.
- `fallingtreetests.snowygroundallowscraftingnewaxe`: bancada real em floresta nevada e troca de machado desgastado.

**79 GameTests previstos (77 anteriores + 2 novos).** Testes do Minecraft e compilação Fabric estão PENDENTES: não há acesso ao download da distribuição Gradle neste ambiente. Não confundir os novos testes com resultados de execução.

## Validações efetivamente executadas
- 38 testes de comandos em português: PASSOU.
- 110 verificações de arbitragem: PASSOU.
- 38 arquivos Java analisados sintaticamente pelo parser Java 17: sem erros de sintaxe (não equivale a compilar contra a API Minecraft/Fabric).
- ZIP empacotado com estrutura completa e dependência FallingTree fornecida pelo usuário.
