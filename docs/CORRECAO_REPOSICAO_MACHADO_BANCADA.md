# Reposição do machado após recuperar a bancada

## Relato reproduzido

No FCL, uma ordem de obter 50 madeiras prosseguia com as mãos após o machado ficar gasto e não o substituía nem após terminar. Havia spruce comum, gravetos, bancada e picareta no inventário, além do machado gasto.

A recuperação da bancada pode permanecer pendente por um tick depois que o bloco virou drop ou o item já foi recolhido. O agendador cedia a recuperação à mineração sempre que faltava pedra para futuras melhorias. Essa condição era verdadeira também fora de cavernas, durante uma ordem de madeira e sem uma expedição de pedra realmente possível. O executor de fabricação deixava de receber ticks e não finalizava a sessão antiga nem produzia o novo machado.

## Correção

A recuperação da bancada só cede à coleta de pedra quando existe uma ordem de pedra pronta ou uma expedição automática que pode realmente começar: dono válido, modo apropriado, nenhuma ordem/limpeza ativa, capacidade, vida e acesso à caverna. Uma falta futura de pedra já não impede a reposição do machado durante WOOD ou depois da tarefa.

As prioridades existentes de fabricação e troca continuam em uso. O novo machado consome ingredientes reais e é produzido antes de remover o gasto. A coleta manual mantém sua quantidade e retoma após a manutenção. Combate, inventário aberto e recuperação segura dos apoios conservam prioridade. Na ausência de materiais suficientes não se inventam itens nem se remove antecipadamente a ferramenta antiga.

## Testes

Foram acrescentados dois GameTests com o inventário do relato: 55 SPRUCE_LOG, 11 STICK, bancada, picareta, machado gasto na mochila e espada na mão. Ambos iniciam com a sessão pendente de uma bancada que já não está no mundo.

- `replacesWornAxeDuringWoodOrderAfterBenchPickup`: ordem manual de 50 madeiras; exige pausa enquanto fabrica, manutenção do machado velho antes da receita, reposição, descarte posterior do gasto e retomada da coleta com a quantidade original.
- `replacesWornAxeAfterWoodOrderAndBenchPickup`: sem ordem ativa, em GUARD, fora de caverna; exige consumo real de spruce, reposição e remoção do gasto.

Os 80 testes anteriores, incluindo a regressão de descida para minerar pedra, permanecem intactos; a suíte agora contém 82 GameTests. Minecraft 1.20.1, Java 17, Gradle 8.7, Fabric, FallingTree e o ZIP r7 foram preservados.
