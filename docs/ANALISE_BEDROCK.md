# Migração do AI Player Bedrock v0.9.2

Fonte analisada: AI_Player_Bedrock_v0_9_2_mineracao_e_degraus_corrigidos.mcaddon.
O chat compartilhado não pôde ser recuperado; esta análise usa o arquivo anexado.

## Sistemas existentes

- `main.js` (~78 KB): controle por formulários e comandos, equipamento por pontuação, combate, durabilidade, alimentação, coleta de itens, madeira, diagnóstico e memória. Madeira usa origem fixa, raio 50, metas 4/8/16/64/128/264, verificação de solo/tronco/folhas, varredura parcelada e estados de perigo/retomada.
- `autonomy.js` (~35 KB): prioridades, produção de ferramentas/armaduras, receitas próprias, transações de inventário, colocação/recuperação de bancada, caça, tochas, rotas com alterações de terreno e recuperação de NPC preso. Há rotas manuais atualizadas por tick.
- `mining.js` (~33 KB): scanner de minério, seleção e planejamento de rotas, prospecção por escadaria, túneis, veios, ferramentas por tier e persistência da tarefa. Drops são uma tabela própria, não loot vanilla; isso precisa ser substituído na migração.
- Entidade: 30 pontos de vida, movimento 0,29, mochila 27, domesticação garantida com graveto, grupos seguir/proteger/recuar e propriedades de visual sincronizadas.
- Menus: ações e formulários Bedrock, entrega/devolução de itens por botões como alternativa à mochila nativa. O Java passa a usar slots de ScreenHandler.
- Aparência: humanoide Steve, animações Molang de olhar/andar/segurar/atacar/comer. Há geometrias próprias de equipamento e sprites e controladores de armadura; o cliente referencia texturas vanilla e texturas pequenas por material. Java não reutiliza os modelos improvisados: usa PlayerEntityModel, HeldItemFeatureRenderer e ArmorFeatureRenderer.
- Identificador preservado: `aiplayer:companion`. A identidade do dono em Java é UUID; não existe conversão automática de mundos ou inventários Bedrock.

## Limitações identificadas no código

O arquivo LEIA-ME declara revisão estática e testes simulados, não execução no jogo. Movimento mistura navegação nativa, controle manual, pulo e reposicionamento local. Há mapas de estado temporário reconstruídos a partir de propriedades persistidas e diversos catch silenciosos. O Java usa uma única prioridade de controle do movimento e grava NBT na entidade, com slots reais de equipamento, para reduzir conflitos e duplicações.

## Escopo da primeira versão Java

Entidade e proprietário, mochila e equipamento reais, interface, seguir/proteger/esperar, navegação vanilla, combate corpo a corpo, retirada em vida crítica e NBT. Sistemas avançados não são portados nesta etapa: madeira, mineração, produção, alimentação, colocação de blocos, skin personalizada e construção.

## Arquitetura para evolução

- `entity/CompanionEntity`: identidade, equipamentos vanilla, inventário, NBT e interação.
- `ai/CompanionGoal`: prioridade exclusiva de movimento; defesa/retirada acima de acompanhamento. Próximas tarefas devem entrar abaixo dessas prioridades, com descritor persistente próprio (tipo, origem, progresso e fase), sem substituir a ordem durante combate.
- `ai/EquipmentPolicy`: seleção sem duplicar itens.
- `screen/`: autoridade no servidor, transferência vanilla e visualização do equipamento real.
- `client/`: render e interface isolados do servidor.

Próximas etapas: GameTests e teste visual no FCL da base; madeira com identificação conservadora; receitas vanilla e transações; scanner parcelado com orçamento de ticks, planner incremental e loot vanilla. Não adicionar todas as tarefas antes de validar a base no aparelho.
