# AI Player 0.3.7-dev
Minecraft Java 1.20.1 · Fabric · Java 17. Base: v0.3.6.

## Mudanças
- Busca de árvores em raio horizontal de 32 blocos e até 12 blocos acima/abaixo, sem saltar colunas ímpares. A varredura visita as colunas mais próximas primeiro e processa no máximo 96 por tick. Não carrega chunks ausentes nem encerra a busca enquanto a varredura está pendente.
- Memória de árvores consultada até 36 blocos. Mantidas validação de solo/tronco/copa e proteção de blocos colocados pelo jogador.
- Rotas acompanham desníveis entre origem e destino. Rejeitam perigos, quedas abruptas, desvios muito profundos e trechos cobertos que descem mais de um bloco abaixo do nível menor entre as pontas. Isso substitui a regra que rejeitava até subidas normais.
- Teleporte de seguimento exige mais de 240 ticks sem deslocamento significativo, distância excessiva ou falhas repetidas, além das condições de segurança existentes. Após uso, há intervalo mínimo de 200 ticks. Esses tempos são ticks de jogo, aproximadamente 12 e 10 segundos a 20 TPS.
- Recuperação reconhece cantos de buracos largos com pelo menos duas paredes após 60 ticks sem sair de uma área de dois blocos; preserva resposta imediata em buracos fechados nos quatro lados.
- Ordem manual de coleta no modo Esperar permite a recuperação do terreno. Neve pode ser removida fisicamente para liberar o degrau.

## Limites
Não é uma solução universal para qualquer caverna. Buracos muito grandes, obstáculos protegidos, líquidos ou formatos sem degrau seguro ainda podem impedir a saída. Árvores precisam estar em chunks carregados, dentro do alcance e atender à identificação de árvore; encontrar uma árvore não garante uma rota válida. A proteção de blocos depende do registro de colocação pelo mod.

O critério de superfície é aproximado: pode recusar um vale fundo coberto por folhas ou uma descida grande. O teleporte continua existindo como recuperação final, sem substituir todas as rotas recusadas imediatamente.

## Compilar
Use o workflow `.github/workflows/build-fabric.yml` incluído nesta versão. Extraia o projeto na raiz do repositório ou envie o ZIP `AI_Player_Java_1.20.1_v0.3.7_dev_fontes.zip` junto com esse workflow na raiz correta. Só enviar o ZIP não atualiza um workflow antigo. Compilação: `bash gradlew build`; testes: `bash gradlew runGametest`.

## Conferência no FCL
Teste uma ordem de madeira com árvore a 20 e a 30 blocos, buraco 2x2 com neve (com e sem blocos no inventário), ordem manual enquanto Esperar está selecionado, subida de colina seguindo o jogador e percurso longo sem teleporte imediato. O relatório automatizado está em `docs/VALIDACAO_V0_3_7.md`. Não houve teste visual no FCL nesta entrega.
