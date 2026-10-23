# Validação da v0.4.1-dev r5 — 22/10/2026

Esta revisão altera comportamento após o teste manual da r4. Os 70/70 GameTests das duas rodadas da r4 são históricos: NÃO validam a r5.

## Verificações efetivamente realizadas

- 38/38 testes independentes do interpretador de comandos aprovados.
- 110/110 verificações independentes de prioridades aprovadas.
- 36 arquivos Java analisados pelo parser do compilador Java 17, sem erros de sintaxe. Isso não resolve tipos ou referências do Minecraft.
- Tentativa de `bash gradlew build compileTestmodJava --no-daemon`: interrompida antes da compilação, no download do Gradle 8.7, com `java.net.SocketException: Network is unreachable`.
- Compilação Fabric e 73 GameTests desta revisão: NÃO executados/concluídos. O workflow incluído executa essas etapas no GitHub.

## Mudanças

Pedra: coleta iniciada apenas sob cobertura natural carregada, a até 24 blocos acima. Folhas não contam; telhados de madeira, blocos protegidos e apoios próprios também não. Na superfície, a ordem termina com “Me leve até uma caverna pra eu pegar pedra, meu rei. Quando chegar lá, me peça de novo.” A autonomia não inicia expedições de pedra fora dessa condição. As galerias e escadas internas permanecem disponíveis na caverna, com as verificações de segurança existentes.

Madeira: subida usa terra/pedregulho, troncos ou tábuas reais, inclusive o último bloco disponível. A descida já verifica piso sólido abaixo antes de remover cada apoio. Não exige mais uma reserva lateral que impedia o início da subida. Centralização e salto cancelam comandos residuais de movimento. O salto ativo bloqueia a interferência da recuperação genérica de terreno; tentativas de subida atualizam o progresso da coleta.

## Testes e limitações

Foram adicionados três GameTests: subir com uma tábua e recuperá-la, concluir a coleta de uma árvore alta e recuperar todos os apoios, e recusar ordem de pedra ao ar livre sem usar a picareta. Os cenários anteriores de coleta de pedra receberam cobertura natural de teste para refletir a nova política. O teste de recusa de entrada a partir da superfície foi mantido e renomeado. Não foram relaxadas as verificações de quantidade, durabilidade ou conservação de materiais.

A identificação de caverna é uma regra local de cobertura, não um reconhecimento completo de biomas: uma saliência natural pode contar como caverna, e uma entrada aberta pode exigir entrar mais. Depois de levar o NPC até lá, repita a ordem. Ele ainda encerra coleta de madeira quando atinge a quantidade pedida; troncos excedentes ficam lembrados para outra ordem.

Validar no GitHub e no FCL: árvore de pinheiro alta, inventário só com tábuas, descida/recuperação em WAIT, pedra em caverna, recusa ao ar livre e ausência de escada de superfície. Não considerar esta revisão totalmente validada antes de executar os GameTests.
