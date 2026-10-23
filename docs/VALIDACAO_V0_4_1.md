# Validação r6 — 22/10/2026

## Executado

- Inspeção do JAR/ZIP fornecidos: ambos são idênticos (SHA-256 no aviso de terceiros), possuem classes compiladas e metadados Fabric/Forge. A integração usa somente a entrada Fabric.
- Inspeção das assinaturas e bytecode reais de FallingTree 4.3.4, incluindo TreeBuilder, IPlayer, wrappers Fabric e ToolDamageHandler.
- 38 testes independentes do parser: aprovados.
- 110 verificações independentes de DecisionPolicy: aprovadas. Não são testes de execução de todos os novos fluxos de fabricação.
- 38 fontes Java analisados pelo parser do compilador Java 17: sem erros de sintaxe. Não equivale à resolução de tipos/mapeamentos Minecraft.
- Tentativa de build/compileTestmodJava: falhou antes da compilação no download do Gradle 8.7 com java.net.SocketException: Network is unreachable.

## Não validado

Compilação Fabric com remapeamento do FallingTree, execução dos 77 GameTests, cliente FCL, multiplayer e mundos com outros mods. Nenhum resultado 70/70 de r4 deve ser atribuído à r6. Não há JAR compilado do AI Player nesta entrega.

## Novos GameTests incluídos (não executados)

1. Derrubada pelo adaptador produz exatamente sete drops físicos e não duplica na segunda chamada.
2. Tronco superior protegido impede o lote sem alterar o mundo ou gastar ferramenta.
3. Machado gasto não faz a picareta ser selecionada; o antigo não é apagado antes do substituto.
4. Fabricação do machado substituto durante uma ordem de madeira, seguida de retirada dos gastos.

Os 73 testes anteriores permanecem. O novo arquivo de testes está registrado no fabric.mod.json do testmod. A dependência está configurada tanto no Gradle como no fabric.mod.json principal. O workflow publica a dependência junto ao artefato do mod.

## Conferir no jogo

Com FallingTree em INSTANTANEOUS, cortar um pinheiro alto, observar drops e desgaste; pedir outra árvore com machado perto do fim; conferir fabricação, equipamento e descarte após produção; conferir construções protegidas e apoios próprios; repetir com inventário cheio e materiais insuficientes; conferir saída de buracos e subida quando o lote é recusado. Confirmar também pedra em caverna e recusa na superfície.

Limites do adaptador estão documentados no README: modos animados/SHIFT_DOWN e quebra forçada de folhas/raízes não são integrados ao NPC nesta revisão. O comportamento padrão desses modos para jogadores não foi alterado.
