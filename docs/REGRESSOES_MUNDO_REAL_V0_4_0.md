# AI Player v0.4.0: investigação de regressões no Fold Craft Launcher

Relato de partida real: NPC travou em dois blocos de apoio ao cortar árvore alta, ficou parado ao recolher madeira na água, não recuperou a bancada, não repôs uma bancada quando necessário e não minerou pedra em caverna. Foram enviadas seis capturas de tela. Capturas não mostram o tempo exato das ações nem logs do servidor, portanto não comprovam a causa isoladamente.

## Alterações realizadas (fontes)

* `TemporaryPillar`: abandonar o próprio piso passa por uma saída lateral verificada, removendo folhas bloqueadoras fisicamente antes; considera água próxima com oxigênio suficiente e não quebra o suporte sob os pés. Se não houver saída, adia a recuperação, preservando coordenadas e propriedade.
* `ResourceGathering`: recuperação adiada de pilar não monopoliza o executor e não impede completamente outro objetivo de autonomia. Uma subida que falhe retorna a árvore à lista de tentativas posteriores. A decisão de aguardar drops usa a MESMA condição de contato e linha de visão da coleta verdadeira.
* `WorkNavigation` e `SurvivalController`: curta aproximação física pela água para drops flutuantes próximos, sem teleportar ou duplicar itens. Coleta real continua exclusiva de `SurvivalController`.
* `StarterCrafting`: tentativas sem progresso para recuperar bancada têm limite; a bancada ainda existente/drops persistem no mundo. Bancadas temporariamente inalcançáveis ficam registradas em `DeferredOwnedBenches` e podem ser revisitadas. Isto impede a bancada remota de bloquear a fabricação permanentemente. Não cria bancada artificialmente.
* `SurfaceStoneGathering`: mantém a regra de não conduzir NPC da superfície até cavernas para minerar. Contudo, quando ele já está numa caverna coberta, admite blocos adjacentes com chão e rota seguros.
* `WorldRegressionTests`: cinco casos novos que complementam os 48 anteriores.

## Testes efetivamente executados localmente

* 38 testes de análise de comandos, aprovados.
* 110 checagens independentes da arbitragem, aprovadas.
* Conferência da estrutura e integridade do ZIP.

**Não** foram executados os 53 GameTests da nova versão neste ambiente. A suíte anterior de **48/48** refere-se ao ZIP _anterior_ e não valida estas alterações. O novo teste e o build compilado devem ser rodados pelo Actions. No jogo, verifique água gelada/superfície, árvore de 8+ troncos, terreno com teto de folhas, bancada bloqueada e caverna pequena. Se falhar, envie a execução e o `latest.log`.

## Regras mantidas

Proibido fabricar sem material, copiar drops diretamente à mochila, atravessar blocos, minerar blocos reivindicados pelo jogador, ou apagar suportes debaixo dos pés.
