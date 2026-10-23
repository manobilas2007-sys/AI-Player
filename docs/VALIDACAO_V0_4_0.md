# Validação executada — 21/10/2026

Base: ZIP `AI_Player_v0.4.0_fontes_corrigidos.zip` fornecido pelo usuário. Execução local real com Java 17, Gradle 8.7, Loom 1.6.12 e servidor Minecraft 1.20.1/Fabric.

- `build compileTestmodJava`: **BUILD SUCCESSFUL**, incluindo empacotamento/remapeamento do mod.
- `runGametest`: BUILD SUCCESSFUL; **64 casos, zero falhas**.
- Interpretador português: **38/38**.
- Arbitragem de prioridades: **110 verificações aprovadas**.
- Relatório da execução: `gametest-results-v0.4.0.xml`.

## Investigação

A suíte original registrada tinha 54 testes. A primeira execução reproduziu a falha de desmontagem de torre e falhou na sequência inventário vazio → madeira → picareta → pedra. A fabricação autônoma passou nessa primeira rodada, mas variou em outra execução.

O teste original da torre desativava a IA do mob, o que também suprimia sua física de deslocamento: o NPC permanecia sem contato com o solo. O teste foi corrigido para manter física e isolar a ordem com WAIT. Separadamente, havia um conflito real no coordenador: WAIT tinha prioridade sobre a limpeza pendente após finalizar uma ordem; isso foi corrigido no código principal.

Na mineração, uma pedra alcançável na diagonal podia ser escolhida apesar de a galeria exigir alinhamento lateral. A verificação foi aplicada na seleção e na execução, permitindo chegar à posição de trabalho antes de iniciar o túnel.

O pacote também tinha oito testes de escavação não registrados. Foram ativados e corrigidos para esperar contato real com o chão. As arenas dos testes antigos foram isoladas: construções e NPCs de cenários vizinhos interferiam nas buscas e nos drops. Cada rodada final usou um mundo de teste novo.

Adicionados dois testes integrados, sem reduzir as exigências anteriores:
- Torre de seis blocos, um único bloco de reserva, descida física e todos os sete blocos recuperados no inventário no modo WAIT.
- Galeria com pedra, faixa de diorito e mais pedra: remoção física dos três segmentos e preservação do piso.

A execução final aprovou também `autonomousThreeStoneTools`, `emptyInventoryWoodThenStone`, `tallWoodTowerDescendsAndPhysicallyRecoversEverySupport` e os cenários de segurança de escada, lava e bancada.

## Limites da evidência

Não foi executado cliente gráfico, Fold Craft Launcher ou workflow remoto do GitHub. O ambiente usou dependências já presentes no cache; a sondagem opcional de sockets do Loom tem uma adaptação local antiga, que não está no projeto entregue. A autenticação externa da Mojang ficou indisponível, mas o servidor de GameTest iniciou e concluiu os testes. Não houve push no repositório. Relatórios de outras versões na pasta docs são históricos.
