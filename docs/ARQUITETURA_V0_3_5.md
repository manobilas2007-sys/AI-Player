# Integração da v0.3.5

## Fluxo por tick
CompanionEntity -> CompanionIntelligence:
sensores -> atualização de sobrevivência passiva -> ResourcePlan ->
DecisionPolicy -> transição/pause -> um executor -> NavigationMonitor.

CompanionGoal reserva os controles MOVE/LOOK/JUMP para o NPC. SwimGoal permanece
para flutuação vanilla; ações de fuga e ar baixo também sinalizam salto.
A atualização do coordenador acontece uma vez por idade da entidade.

## Responsabilidades
- DecisionPolicy: seleção pura, determinística, testável sem Minecraft.
- CompanionIntelligence: objetivos, prioridades, preempção e retomada.
- BrainMemory: fatos temporários e persistentes, prazos e falhas por dimensão.
- ResourcePlan: necessidades e reservas calculadas de pilhas reais.
- NavigationMonitor: progresso, nova rota, desvio e registro de falha.
- ResourceGathering: tarefas de madeira/comida, drops e árvores adiadas.
- StarterCrafting: receitas, consumo atômico, bancada e recuperação.
- PhysicalMining: tempo, ferramenta, capacidade, alteração do mundo e drops.
- TemporaryPillar: salto, colocação, autoria e desmontagem.
- OwnedBlocks: proveniência por dimensão e proteção de colocações observadas.
- SurvivalController: transferência física de drops e sessão de alimentação.

## Como acrescentar mineração em v0.4
Implementar um executor com objetivo, resultado verificável, pause/resume e NBT.
Usar necessidades do ResourcePlan e destinos do BrainMemory.
Encaminhar deslocamentos pelo monitor e validar riscos com WorkNavigation.
Usar PhysicalMining para quebra, sem inserir drops diretamente no inventário.
Reservar tochas, comida e durabilidade antes de sair; persistir origem e retorno.
Adicionar candidatos ao coordenador sem introduzir um segundo tick concorrente
que mova a entidade ou consuma materiais.
Não usar estado visual como evidência de tarefa concluída.
