# AI Player v0.5.1-dev — Baritone adaptado ao NPC

Minecraft Java **1.20.1**, Fabric, Java 17 e Gradle Wrapper 8.7.

O planejador A*, objetivos e custos adaptados do Baritone 1.10.5 são compilados a partir dos fontes em `embedded-baritone/`. Suas classes são incluídas diretamente no JAR principal do AI Player, sem depender do carregamento de outro JAR pelo Fabric. **Não é necessário instalar Baritone separadamente.** O NPC usa o mundo do servidor e seu inventário real; não controla o jogador do cliente.

A versão 0.5.1-dev altera esse empacotamento após o relato de `NoClassDefFoundError: dev/aiplayer/baritone/Goal` no FCL. A biblioteca continua com fontes e build separados para modificação e recompilação. A execução no FCL ainda precisa ser conferida; os resultados de compilação, empacotamento e GameTests pertencem ao workflow da revisão correspondente.

## Comandos novos

- `/aiplayer construir abrigo` ou **construa um abrigo**: constrói um abrigo de madeira 5×5, com piso, paredes, porta aberta e teto. Usa 80 tábuas reais; pode converter troncos e obter madeira quando a fabricação automática está habilitada.
- `/aiplayer plantar` ou **plante trigo**: prepara até nove solos naturais próximos de água, planta e colhe apenas trigo maduro. Entregue sementes de trigo; a enxada pode ser fornecida ou fabricada com materiais reais.
- `/aiplayer explorar` ou **explore a área**: visita oito pontos próximos seguros em terreno carregado e lembra recursos visíveis.
- `/aiplayer cancelar` ou **cancele o projeto**: cancela o trabalho atual.
- `/aiplayer status`: mostra atividade e progresso.

As ordens anteriores de madeira, pedra e comida permanecem disponíveis. Combate, alimentação, inventário aberto, troca de machado e recuperação de apoios continuam sob o mesmo coordenador. Construções protegidas e blocos de outros proprietários são preservados. Projetos e progresso são salvos no mundo; rotas são recalculadas e não atravessam dimensões.

## Instalação e desenvolvimento

Use o JAR principal de `build/libs/` com Fabric API e FallingTree 4.3.4, como nas versões anteriores. A biblioteca interna do Baritone já está dentro dele. Edite os arquivos Java diretamente neste repositório; o ZIP r7 permanece preservado.

```sh
./gradlew clean :embedded-baritone:build build compileTestmodJava runGametest --no-daemon
python3 tools/check_embedded_baritone.py
```

O GitHub Actions publica o mod, FallingTree, relatórios, o binário separado da biblioteca, JARs de fontes e instruções de recompilação da biblioteca LGPL. Esse binário separado permite inspecionar e recompilar o componente; ele não é um mod adicional e não vai na pasta `mods`. O JAR principal já contém suas classes. Os detalhes da adaptação e sua matriz de capacidades estão em [docs/INTEGRACAO_BARITONE.md](docs/INTEGRACAO_BARITONE.md); os avisos estão em [LICENSE.md](LICENSE.md) e `third-party/Baritone/`.
