# AI Player 0.3.2-dev — correções após testes da 0.3.1

Alvo: Minecraft Java 1.20.1, Fabric, Java 17. Pacote de fontes para compilar pelo GitHub Actions. Não contém JAR.

## Alterações

- Folhas: quebra gradual da primeira folha bloqueando a visão do tronco, inclusive lateralmente. Abre espaço acima do corpo até a altura necessária ao salto. Mantém o alvo da árvore enquanto consegue avançar.
- Drops: tenta quebrar a folha que sustenta a madeira e as folhas que bloqueiam o alcance. Após tentativa limitada, ignora temporariamente aquele drop em vez de selecioná-lo de novo sem parar. O limite geral de duração é verificado antes da busca de drops.
- Caminhos: procura posições em que pode ficar de pé ao lado do alvo; exige caminho completo e rejeita rotas que descem mais de dois blocos abaixo do menor nível entre o NPC e o terreno da tarefa. Isso reduz desvios para cavernas; não é um planejador completo de terrenos arbitrários.
- Braço: estado de trabalho sincronizado com o cliente e movimento explícito do braço e manga no modelo Steve existente. Aplica-se à mineração e fabricação.
- Bancada: mantém a mesma bancada durante receitas consecutivas possíveis, incluindo preparo de tábuas/gravetos entre ferramentas. Só inicia recuperação quando a sequência termina ou deixa de ser possível.
- Reabrir mundo: preserva a posição da bancada independentemente da receita salva. Ao carregar, recupera a bancada própria antes de replanejar a fabricação; receita inválida não apaga mais a referência. Uma flag antiga de fuga não bloqueia essa recuperação quando não há inimigo.
- Autonomia: verifica necessidades com prazo de revisão, sem exigir um tick múltiplo exato. NPC saudável sem kit básico prioriza madeira, mesmo quando a opção de sobrevivência estiver desligada e fabricação automática ligada. Considera troncos e tábuas disponíveis e mantém uma pequena reserva depois do kit.
- Esperar continua impedindo novas tarefas automáticas. Fabricação automática precisa estar ligada; coleta de drops precisa estar ligada para recolher os materiais.

## Validação desta versão

- 13 testes do interpretador de comandos executados: passaram.
- Parse de sintaxe Java 17 dos 21 arquivos Java: passou. Não equivale a compilação com resolução de APIs.
- Adicionados três GameTests: bancada preservada mesmo com receita salva inválida; autonomia em tick fora do múltiplo antigo e respeito ao modo Esperar; bancada reutilizada entre picareta/machado/espada e recolhida ao final.
- GameTests e teste visual no Minecraft NÃO executados nesta sessão. Relatórios antigos em docs são históricos.

## Compilar no GitHub

O ZIP tem os arquivos do projeto diretamente na raiz, incluindo .github/workflows/build-fabric.yml. Extraia o ZIP e envie os arquivos para a raiz do repositório, mantendo a pasta .github. Não envie somente o ZIP como substituto dos arquivos.

Execute Actions > Build AI Player Fabric 1.20.1 > Run workflow. O workflow produz build/libs/ai-player-0.3.2-dev.jar.

O workflow também pode executar os GameTests marcando a opção run_gametests. Os testes novos precisam dessa execução; a validação de sintaxe local não comprova que eles passaram.

## Teste sugerido no FCL

1. NPC saudável, Seguir/Proteger, fabricação e coleta ligadas, mochila vazia: verificar início de coleta sem ordem.
2. Pinheiro com folhas baixas: observar abertura de folhas e corte dos troncos restantes.
3. Madeira sobre folhas: observar queda do item ou desistência temporária sem travar toda a tarefa.
4. Árvore perto de caverna: verificar aproximação pela superfície; uma rota reprovada pode resultar em escolha de outra árvore.
5. Dar tábuas, gravetos e uma bancada: observar ferramentas consecutivas com a mesma bancada.
6. Fechar o mundo durante a fabricação: ao voltar, observar recuperação da bancada própria.
7. Conferir movimento do braço na mineração com e sem armadura.

## Limites

Navegação e alcance em árvores muito largas, mudanças de terreno e drops altos fora do alcance ainda exigem teste no jogo. Não foi implementada busca automática de pedra/minérios, mineração por veios ou fornalha. A recuperação só reconhece bancadas cuja posição estava salva; não assume propriedade de bancadas antigas já abandonadas sem registro.

SmartBrainLib e GeckoLib não foram adicionados como dependências nesta versão. As correções usam os controladores e o modelo existentes. A presença desses mods no jogo não os integra automaticamente ao NPC; não foi realizado teste de integração específico com eles.
