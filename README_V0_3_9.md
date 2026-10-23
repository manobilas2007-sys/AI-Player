# AI Player v0.3.9-dev — falas e personalidade

Minecraft Java 1.20.1 · Fabric · Java 17. Base: v0.3.8.

## O que mudou
Falas de chat com um jeito baiano, tranquilo, prestativo e bem-humorado. Expressões como “meu rei”, “oxe”, “bora” e “viu?” aparecem em frases curtas, sem alterar coleta, prioridades, combate ou navegação.

As mensagens são disparadas por acontecimentos do próprio NPC: mochila cheia durante coleta, rota recusada, recuperação de terreno, bancada bloqueada, retirada de combate, alimentação, preparação de materiais e busca autônoma de recursos. As respostas a ordens de madeira/comida, conclusão e cancelamento também receberam o novo jeito de falar, mantendo quantidades e progresso.

Exemplos:
- “Ô meu rei, a mochila tá sem espaço pra essa coleta. Dá uma aliviada nela?”
- “Esse caminho não tá dando passagem, viu? Vou tentar outra rota.”
- “Calma aí, que tô procurando um jeito de sair daqui.”
- “Ô meu rei, o negócio apertou. Vou recuar um pouco!”

## Controle das falas
- `/aiplayer falas desligar`: desliga os comentários automáticos do seu NPC mais próximo, até 48 blocos.
- `/aiplayer falas ligar`: volta a ativá-los.
- As confirmações de ordens manuais continuam funcionando mesmo com os comentários desligados.
- Preferência, intervalos e variação das frases são salvos no NPC.

## Frequência e privacidade no jogo
As falas automáticas vão somente ao dono, quando está a até 48 blocos e o inventário do NPC não está aberto. Há intervalo global de 160 ticks entre mensagens automáticas e de 1.200 ticks para repetir o mesmo motivo (cerca de 8 e 60 segundos a 20 TPS). Estados observados precisam durar 40 ticks e geram no máximo um comentário por episódio. Eventos pontuais, como começar a comer ou confirmar uma rota bloqueada, também respeitam os intervalos globais. Falas suprimidas não ficam acumuladas numa fila para aparecer fora de contexto.

## Escopo
Esta personalidade aparece no texto do chat: não há voz gravada nem síntese de voz. São falas contextuais locais, sem API, LLM, conexão externa ou dependência nova. O sistema não muda decisões para combinar com as falas e não utiliza o gerador aleatório da IA, preservando sua sequência de decisões.

Os diagnósticos são os que o sistema consegue confirmar. “Não achei árvore acessível” não significa que não existe árvore no mundo; uma busca pode falhar por alcance ou rota.

## Compilar e testar
Use `.github/workflows/build-fabric.yml` desta versão. O ZIP esperado pelo workflow é `AI_Player_Java_1.20.1_v0.3.9_dev_fontes.zip`. Enviar só o ZIP não atualiza um workflow antigo: substitua também o workflow na raiz do repositório, ou extraia todos os fontes na raiz.

`bash gradlew build` gera o JAR. `bash gradlew runGametest` executa os testes. Entrega apenas em ZIP de fontes.

No FCL, teste mochila cheia durante uma ordem, rota bloqueada, alimentação e os comandos para ligar/desligar. Confira também a leitura das mensagens com sua escala de chat. Não houve teste visual em cliente nesta entrega.
