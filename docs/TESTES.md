> **RESULTADOS HISTÓRICOS:** os relatórios incluídos nesta pasta pertencem ao projeto original v0.1.0. Os três novos GameTests de v0.2.0-dev ainda NÃO foram executados, pois as dependências do Gradle não puderam ser baixadas neste ambiente.

# Validação — AI Player 0.1.0

Resultado: **BUILD SUCCESSFUL**, **6 testes obrigatórios aprovados**, nenhum teste de comportamento reprovado na execução final.
Execução final em 18/10/2026, relatório UTC em `gametest-results.xml`.

Ambiente: servidor Minecraft 1.20.1 real, Fabric Loader 0.15.11, Fabric API 0.92.2+1.20.1, JDK 17, Gradle 8.7 e Fabric Loom 1.6.12. Testes executados por Fabric GameTest, com entidades, mundo, inventários e navegação reais do jogo. Jogadores usados no teste de permissões são jogadores de teste fornecidos pelo Minecraft, não conexões de clientes.

| Teste | O que foi verificado | Resultado |
|---|---|---|
| persistence | Gravação/leitura NBT de UUID, modo, opção automática, último slot da mochila, armadura, durabilidade e encantamentos | Aprovado |
| equipmentConservesItems | Troca automática com mochila cheia, conservação de quantidades e ausência de cópia de arma/armadura | Aprovado |
| ownershipAndRealSlots | Graveto consumido ao domesticar, transferência de itens pelos slots, rejeição de outro jogador, ordem do dono e liberação do menu | Aprovado |
| realCombatAndDurability | Dano real de espada de diamante contra zumbi, desgaste da espada e desgaste da armadura ao receber dano | Aprovado |
| monsterRecognizesCompanion | Zumbi escolhe companheiro como alvo no mundo do servidor | Aprovado |
| navigationClimbsBlock | Navegação da entidade vence um degrau de um bloco e chega à plataforma elevada | Aprovado |

## Limites da evidência

- Os testes acima executaram dentro do servidor Minecraft. Não são simulações independentes em Python/JavaScript.
- Persistência foi exercitada pelo ciclo NBT usado pela entidade; não houve sessão manual de sair/reabrir pelo FCL.
- Renderizador, skin, animações, interface gráfica e arraste por toque não foram inspecionados num cliente gráfico.
- Não foi executado no Android/Fold Craft Launcher; não há medição de FPS, bateria ou memória no celular.
- Não há cobertura de todos os monstros, encantamentos, obstáculos, rios, cavernas, dimensões ou combinações com mods externos. Teleporte de recuperação, defesa do dono e retirada crítica têm implementação, mas não têm testes dedicados nesta suíte.
- Os GameTests usam as classes do mod no ambiente de desenvolvimento; o JAR de distribuição é produzido pela tarefa `remapJar`. A estrutura do JAR e o refmap do accessor foram inspecionados.

## Ocorrências corrigidas na preparação

- Ajuste da chamada de desgaste da arma para aceitar LivingEntity/NPC.
- `damageArmor` declarado público para corresponder à API com access wideners do Fabric.
- Relatório GameTest alterado para `reports/gametest-results.xml`; o caminho sem diretório causava erro ao salvar o relatório, mesmo depois de todos os testes passarem. A execução final concluiu normalmente.
- O host de build não oferece sockets UNIX. A detecção desse recurso no Loom recebeu uma adaptação local para retornar indisponível em vez de abortar. Isso afeta apenas a ferramenta de build do host, não foi incluído no JAR nem altera permissões do sistema. O projeto usa Loom padrão em ambientes comuns.
- O servidor de teste registrou aviso de consulta de chave pública de autenticação sem rede direta; isso não impediu os testes locais. O log completo está em `validation.log`.

Reprodução em ambiente comum: JDK 17, `./gradlew build runGametest`.
O módulo `aiplayer_test` é separado e não é empacotado no JAR instalável.
