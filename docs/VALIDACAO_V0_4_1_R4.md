# Validação da v0.4.1-dev r4

## Resultado confirmado em 22/10/2026

| Verificação | Resultado observado |
| --- | --- |
| Java 17 / Fabric: build e compileTestmodJava | BUILD SUCCESSFUL; 7 tarefas executadas |
| PortugueseOrderParserTest (execução separada) | 38/38 aprovados |
| DecisionPolicyTest (execução separada) | 110/110 verificações aprovadas |
| runGametest — rodada 1, mundo novo | 70/70 testes obrigatórios aprovados; saída 0 |
| runGametest — rodada 2, mundo novo | 70/70 testes obrigatórios aprovados; BUILD SUCCESSFUL; saída 0 |
| Terceira rodada | Iniciada, resultado não confirmado |

As duas rodadas foram executadas depois da reconstrução final dos fontes r4. Incluem os 65 testes anteriores e cinco regressões novas. A segunda rodada foi uma repetição completa em mundo novo. O código Java, recursos e configuração deste ZIP permanecem idênticos ao checkpoint usado nessas execuções; a finalização atualiza somente a documentação.

Este registro resume os resultados observados na sessão. A limpeza automática posterior apagou os relatórios XML e logs brutos das duas rodadas. Não foram recriados XMLs para simular relatórios originais. Os XMLs antigos em docs são históricos e não comprovam a r4. O workflow incluído gera novos relatórios ao executar no GitHub.

## Escopo das correções

- Descida de torres removendo fisicamente apoios próprios, com centralização, piso seguro e recuperação dos drops; cobertura sem material de reserva e com descida lateral salva.
- Retomada da ordem de pedra assim que a picareta existe, sem bloquear pela fabricação opcional de outras ferramentas.
- Pré-validação da galeria 2×2 e busca de aproximações alternativas ao redor de bancadas protegidas.
- Saída de troncos ou bancadas isolados para continuar deslocamento e fabricação.
- Aproximação física de drops e limite efetivo de tentativas bloqueadas de recuperar a bancada, mantendo a memória de propriedade.

## Ambiente e limites

A execução local utilizou Java 17 e Gradle 8.7. O download direto pelo Java falhou inicialmente; a distribuição oficial foi obtida pela conexão disponível. A sondagem opcional de sockets Unix do Loom 1.6.12 lançava IOException quando indisponível neste ambiente. Somente o cache local de testes recebeu um adaptador para tratar esse caso como recurso não suportado. Nenhum Loom modificado, cache ou dependência adicional integra este ZIP.

Uma tentativa de repetição falhou antes de iniciar o Minecraft por corrupção de cache temporário do Gradle (EOFException / Buffer underflow). Após limpar os caches gerados, a repetição completa passou nos 70 testes; essa falha de preparação não foi contabilizada como rodada de GameTests.

Os testes de comandos e prioridades foram executados separadamente; a tarefa Gradle test indicava NO-SOURCE. Não houve execução remota do GitHub Actions nesta entrega. Não houve teste visual no cliente/FCL nem sessão longa de sobrevivência. Duas rodadas aprovadas não garantem ausência de falhas em todos os mundos.

## Reproduzir

Use Java 17 e execute na raiz dos fontes:

```sh
bash gradlew clean build compileTestmodJava runGametest --no-daemon
```

O workflow .github/workflows/build-fabric.yml também executa os testes independentes de comandos/prioridades e publica os relatórios. Para repetir GameTests em mundo novo, pare o servidor e remova apenas o mundo de testes gerado em run-gametest/world antes da próxima rodada.

## Testes manuais recomendados

1. Coletar árvore alta, entrar em WAIT e verificar descida, remoção dos apoios e coleta física sem duplicação.
2. Começar sem itens e pedir pedra: coletar madeira, fabricar picareta e retomar a quantidade solicitada.
3. Fabricar automaticamente espada, picareta e machado de pedra, inclusive quando falta um pedregulho e existe bancada pendente.
4. Repetir com bancada protegida no caminho, terreno irregular e NPC sobre um tronco isolado.
5. Salvar/reabrir durante a descida e conferir a retomada da limpeza.
