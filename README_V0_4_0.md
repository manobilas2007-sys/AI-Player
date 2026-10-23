# AI Player v0.4.0-dev — revisão de torres e mineração

Revisão de 21/10/2026 sobre `AI_Player_v0.4.0_fontes_corrigidos.zip` anexado pelo usuário. Minecraft Java 1.20.1, Fabric Loader 0.15.11, Fabric API 0.92.2+1.20.1, Java 17. Dependências preservadas.

## Correções desta revisão

- A ordem de esperar não bloqueia mais a recuperação pendente de apoios. O NPC termina a descida e busca os drops antes de voltar a esperar; combate e emergências continuam com prioridade.
- A seleção de pedra distingue alcance do braço de alinhamento para abrir uma galeria. O NPC se reposiciona quando está na diagonal errada, em vez de selecionar e recusar repetidamente a mesma pedra.
- Os oito testes de escavação já existentes foram registrados no Fabric; antes estavam no código, mas não eram executados.
- Testes que precisam caminhar ou cair usam física real. Testes de início de escavação aguardam o NPC pousar. Os cenários antigos receberam arenas isoladas para impedir interferência entre NPCs, drops e buscas de recursos de testes diferentes.
- Novas regressões exigem recuperar no inventário todos os materiais de uma torre de seis blocos e escavar através de diorito até a pedra seguinte.

## Capacidades preservadas

Madeira, ferramentas, inventário, comandos em português com sinônimos/erros de digitação, personalidade baiana, sobrevivência, combate, bancada física, apoios temporários e retomada de objetivos. Mineração de pedra exposta, escadas apoiadas e galerias 2×2 permanecem disponíveis. Diorito, granito e andesito naturais podem ser removidos para abrir passagem, mas não contam como pedregulho coletado.

Exemplos: `pegue 16 pedras`, `minera 8 pedregulhos`, `pegue 8 madeira`, `cancele`. Fabricação e equipamento automático precisam estar habilitados para preparar a picareta e progredir autonomamente. A coleta exige drops reais e espaço no inventário.

## Validação

**64/64 GameTests, 38 testes de comandos e 110 verificações de prioridades aprovados.** Consulte [o relatório de validação](docs/VALIDACAO_V0_4_0.md) e o XML real incluído. Não houve teste visual no Fold Craft Launcher nem execução remota no GitHub nesta revisão.

## Compilar pelo GitHub

Extraia o ZIP inteiro na raiz do repositório, incluindo `.github`, `gradle`, `src`, `build.gradle` e `settings.gradle`. Em **Actions → Build AI Player Fabric 1.20.1 → Run workflow**, deixe `run_gametests` habilitado. O workflow compila, executa os testes e disponibiliza o JAR e os relatórios em Artifacts.

Em um ambiente com Java 17:

```sh
bash gradlew clean build compileTestmodJava --no-daemon
bash gradlew runGametest --no-daemon
```

Para repetir a suíte localmente, use um mundo de GameTest novo; mantenha seus mundos de jogo separados de `run-gametest/world`.

## Limitações

Os testes são cenários controlados de servidor. Terrenos irregulares, mods adicionais, desempenho e animações no Android/FCL ainda precisam de teste no jogo. A busca é limitada a chunks carregados e a escavação a trechos curtos. Proteções observadas de blocos do jogador não identificam automaticamente toda construção antiga. Não foi adicionada mineração completa de minérios, iluminação, fornalhas ou exploração profunda.

As notas anteriores estão em `docs/HISTORICO_REVISOES_V0_4_0.md`; seus números descrevem versões anteriores.
