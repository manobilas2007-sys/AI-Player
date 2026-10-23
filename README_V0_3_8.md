# AI Player v0.3.8-dev — recuperação sob teto baixo

Minecraft Java 1.20.1, Fabric, Java 17. Base: v0.3.7.

## Correções
- Antes de saltar para colocar um apoio, verifica todo o volume do corpo durante a subida, incluindo a altura máxima do salto e obstáculos nas laterais.
- Antes de colocar o bloco, verifica novamente se há espaço para o corpo na posição de aterrissagem.
- Sob teto baixo, procura uma posição lateral alcançável, no mesmo nível e até três blocos de distância, com espaço para subir.
- Quando não há alternativa lateral, pode remover fisicamente terreno natural permitido acima da cabeça. Não remove telhados de madeira nem blocos protegidos pelo registro do mod.
- Considera a posição lateral alcançada apenas quando o NPC realmente se aproxima do centro de destino; anteriormente um bloco vizinho podia ser considerado alcançado antes de andar.
- Tentativas de deslocamento sem conclusão têm prazo e intervalo antes de repetir. O prazo não interrompe uma quebra com progresso ativo.

## Compilar no GitHub
Use o workflow `.github/workflows/build-fabric.yml` desta versão. Extraia o projeto na raiz do repositório ou envie o ZIP `AI_Player_Java_1.20.1_v0.3.8_dev_fontes.zip` junto com o workflow atualizado na pasta correta da raiz. Enviar apenas o ZIP não atualiza workflows antigos.

`bash gradlew build` gera o JAR; `bash gradlew runGametest` executa os testes. Esta entrega contém apenas o ZIP de fontes, conforme solicitado.

## Limites e teste no FCL
A saída depende de existir posição lateral alcançável ou terreno permitido para escavar. Um recinto fechado por blocos protegidos ainda pode impedir a saída. A proteção de construções antigas não é retroativa; telhados de madeira não pertencem à lista de materiais escaváveis da recuperação.

Repita o buraco junto à construção mostrado na imagem, com blocos no inventário. Observe se o NPC se desloca lateralmente antes de subir, se preserva o telhado e se o inventário conserva os materiais. Teste também teto baixo completamente fechado e retirada de um obstáculo durante a tentativa. Não houve validação visual no FCL nesta entrega.
