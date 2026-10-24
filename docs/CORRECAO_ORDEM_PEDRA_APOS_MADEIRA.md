# Ordem de pedra após obter madeira e fabricar a picareta

A execução do GitHub Actions 40034469329 compilou o mod e executou os 79 GameTests, mas `emptyInventoryWoodThenStone` falhou. O NPC terminou sobre a pedra solicitada, com picareta na mochila e uma bancada próxima. A proteção contra minerar o próprio apoio funcionou; faltou uma saída física dessa posição.

`leaveIsolatedPerch` não descia quando havia um bloco sólido adjacente à mesma altura, incluindo a bancada. A ordem manual de pedra, por sua vez, cede prioridade à coleta, não à recuperação da bancada. A busca acabava sem progresso.

## Correção

A coleta de pedra pode iniciar uma descida física de um bloco quando seu apoio é pedra natural não protegida nem reivindicada. O destino exige piso real, espaço livre, caminho completo seguro e cobertura de caverna em todos os nós. O apoio anterior é lembrado e reconsiderado como alvo antes de uma nova descida. O bloco só pode ser minerado depois de o NPC sair dele.

A fabricação conserva a verificação anterior de apoio isolado. A bancada não é quebrada pela descida. Não há teletransporte, inserção de recursos no inventário, alteração nas receitas, remoção da proteção de apoios ou mineração de pedra ao ar livre.

## Regressão

`stepsOffStoneBesideBenchBeforeMining` coloca o NPC sobre uma pedra, ao lado de uma bancada protegida, com uma picareta e uma ordem manual de coletar uma pedra. Exige a coleta do pedregulho real, o encerramento da ordem e a preservação da bancada. Os 79 testes originais permanecem intactos, incluindo a sequência completa desde o inventário vazio; a suíte agora tem 80 testes.

Minecraft 1.20.1, Java 17, Fabric e Gradle 8.7 foram mantidos. O ZIP r7 original permanece inalterado.

A reprodução sem a correção foi executada no commit `8b83b5aeba2245d18de7163c4791f884d9942d3d`, workflow 40048578358: compilação aprovada, 80 testes executados e somente a nova regressão falhou. Isso confirma a falha sem depender da posição aleatória ao terminar a coleta de madeira.
