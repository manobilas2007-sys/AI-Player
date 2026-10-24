# AI Player v0.4.1-dev r7 — FallingTree

Minecraft Java 1.20.1, Fabric, Java 17. Base: r5. Nova dependência obrigatória: FallingTree 4.3.4 (arquivo fornecido pelo usuário).

## Instalar e compilar

1. Clone o repositório e entre na raiz. Os fontes, recursos, configurações Fabric, Gradle Wrapper 8.7 e libs/ já estão versionados; use Java 17.
2. Execute o workflow incluído. Ele compila e tenta executar todos os GameTests, publicando os dois JARs necessários no artefato.
3. No FCL, coloque AI Player + FallingTree-1.20.1-4.3.4.jar + Fabric API na pasta mods da instância Fabric 1.20.1. Não instale cópias duplicadas do FallingTree.

O FallingTree está em libs/ para compilação local e instalação; NÃO fica embutido no JAR do AI Player. O Fabric exige exatamente a versão 4.3.4 nesta integração inicial. Nenhum VeinMine foi adicionado.

```sh
bash gradlew clean build compileTestmodJava runGametest --no-daemon
```

Os workflows compilam diretamente os arquivos da raiz. O ZIP r7 original permanece como referência da migração. Faça as próximas alterações diretamente em src/.

## Comportamento implementado

O NPC usa o detector TreeBuilder e o cálculo de desgaste ToolDamageHandler do FallingTree. Após concluir a mineração física do primeiro tronco ao alcance, derruba os troncos naturais reconhecidos, deixando drops reais nas posições dos blocos. Não há inserção remota no inventário. A tarefa pode recolher mais troncos que o pedido porque a árvore inteira cai.

A execução de quebra é própria para o NPC: exige machado utilizável, espaço para os drops, regras do mundo habilitadas, blocos carregados, árvore conhecida e ausência de blocos protegidos/reivindicados ou entidades apoiadas sobre os troncos. A identificação e os limites de árvore, a ordem dos troncos e o multiplicador/arredondamento de desgaste vêm do FallingTree. São aceitos até 160 troncos por lote.

Esta integração inicial suporta INSTANTANEOUS. Modos FALL_ITEM, FALL_BLOCK, FALL_ALL_BLOCK e SHIFT_DOWN continuam com mineração normal e subida física do NPC. Não há simulação de jogador conectado. Configurações exclusivas de jogador, como tecla de agachar/toggle, não controlam a tarefa autônoma. Folhas permanecem sujeitas ao decaimento natural; não foi integrado o corte forçado de folhas/raízes. A preservação da ferramenta é obrigatória no lote do NPC, mesmo se desativada para jogadores. Se faltar durabilidade ou outra verificação impedir o lote, mantém-se a quebra normal de bloco por bloco.

A construção de pilares, saída de buracos e recuperação de apoios continuam presentes. Apoios próprios nunca entram na derrubada em lote. A coleta de pedra permanece restrita à caverna, como na r5.

## Machado gasto

Madeira não seleciona picareta como alternativa a um machado gasto. Havendo materiais e fabricação automática, a troca de machado tem prioridade durante a coleta de madeira, usando o melhor material disponível. Somente após produzir o novo machado são descartados os machados gastos antigos, conforme solicitado; o novo é equipado quando o equipamento automático está ativo. O descarte inclui machados gastos encantados ou renomeados. Sem materiais/fabricação, pode coletar madeira com as mãos; não inventa um machado novo e não apaga o antigo antecipadamente.

## Validação

38 testes de comandos e 110 verificações de prioridades passaram. Sintaxe de 38 arquivos Java verificada. Compilação Fabric e 77 GameTests NÃO foram concluídos: o download do Gradle falhou com Network is unreachable. São necessários testes no GitHub e no FCL antes de considerar esta revisão validada. Os resultados de versões anteriores não certificam a r6.

Veja docs/VALIDACAO_V0_4_1.md e third-party/FallingTree/NOTICE.md.


## Correção r7 (machado + FallingTree)
A reposição do machado de madeira/pedra após desgaste/quebra é mantida inclusive quando termina a tarefa de madeira. O planejamento não deixa mais FOLLOW atrasar indefinidamente a fabricação, e bancada pode ser colocada sobre uma camada rasa de neve. Veja `docs/CORRECAO_R7_MACHADO_FALLINGTREE.md`. Dois novos GameTests elevam o conjunto de 77 para 79; ainda não executados neste ambiente.
