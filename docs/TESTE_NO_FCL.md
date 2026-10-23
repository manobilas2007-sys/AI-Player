# Roteiro de validação no Android

Use primeiro um mundo Criativo de teste com apenas Fabric API e AI Player. Passe para Survival nos testes de combate. Esta lista é um roteiro a executar, não resultados já obtidos.

1. **Aparência**: invoque o NPC pelo ovo e confirme skin Steve, corpo humanoide, cabeça olhando e membros andando. Não deve haver arma extra presa ao modelo.
2. **Domesticação**: use um graveto. O nome passa a mostrar o dono. Interaja de novo para abrir o menu.
3. **Mochila**: arraste 12 diamantes para o primeiro slot e 37 ferros para o último. Feche e reabra. Retire ambos. As quantidades devem permanecer exatas.
4. **Equipamento**: coloque espada de madeira e espada de diamante na mochila. Feche o menu e espere um segundo. A de diamante deve estar na mão; a de madeira fica guardada. Teste também um conjunto de ferro/diamante. Reabra para conferir os slots.
5. **Escolha manual**: desative Auto, retire a arma da mão e coloque a de madeira manualmente. Feche e confira que não troca sozinha.
6. **Seguir**: afaste-se mais de 15 blocos. O NPC deve acompanhar e parar com espaço. Perto de você, pode passear. Ande por uma rota com parede e degrau de um bloco.
7. **Esperar**: dê a ordem, feche o menu e afaste-se. Sem combate/perigo, ele deve ficar na posição. O modo permite autodefesa.
8. **Proteger**: à noite, em Survival, coloque um zumbi perto. Observe o combate, a animação de golpe e a vida/durabilidade no menu. Mobs devem poder atacar o NPC. A retirada começa em vida crítica; ainda não há comida automática.
9. **Vir aqui**: aproxime-se, abra o menu e use o botão. Ele deve aparecer em uma posição livre próxima, sem atravessar para dentro de blocos.
10. **Salvar/carregar**: deixe itens, equipamento desgastado e modo Esperar; saia completamente do mundo e entre de novo. Confira dono, mochila, equipamento e modo.

Se algo falhar, envie a versão do Fabric/Fabric API, outros mods ativos, `latest.log` ou o relatório de crash e, para problemas de movimento/render, um vídeo curto mostrando a situação. Isso permite corrigir a próxima versão sem adivinhar a causa.
