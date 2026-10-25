# AI Player v0.6.0-dev — diálogos, abrigo e agricultura

Minecraft Java **1.20.1**, Fabric, Java 17 e Gradle Wrapper 8.7.

Converse com seu NPC pelo chat normal. O interpretador local aceita sinônimos, acentos e pequenos erros de escrita; não precisa de chave de API nem serviço de IA externo.

## Exemplos de diálogo

- **“Construa uma casa”**: o NPC apresenta os modelos disponíveis. Nesta versão existe o abrigo de madeira detalhado. Após escolher, ele confirma o local e a madeira antes de começar.
- **“Faça um abrigo de pinheiro”**: propõe a obra com uma única espécie de madeira, prepara o terreno natural e busca os materiais que faltarem.
- **“Crie uma plantação”**: propõe uma plantação irrigada com borda, canteiros e água. Usa um balde reutilizado, planta as sementes disponíveis e volta a seguir o dono ao terminar.
- **“Cuide da plantação”**: após sua confirmação, cuida dos canteiros existentes em um raio horizontal de 100 blocos do local da ordem, incluindo terra que você já arou. Colhe plantas maduras e replanta com sementes reais.
- **“Plante trigo”**: pergunta se você quer criar uma plantação ou cuidar de uma existente.
- **“Ache comida”**: prioriza a caça de animais adequados, preservando animais nomeados, presos por laço ou marcados como persistentes.
- **“Me siga”**, **“pare a tarefa”** e **“o que você está fazendo?”**: controlam e consultam o trabalho pelo diálogo.

Responda **“sim”** ou **“pode começar”** para autorizar a proposta; **“não”** descarta a proposta. A confirmação fica vinculada ao dono, ao NPC, à dimensão e ao local original da ordem e expira. O NPC só anuncia modelos realmente disponíveis.

## Trabalho no mundo

O abrigo possui piso de 5×7, madeira consistente, janelas de vidro, porta, telhado com escadas e lajes, bancada e fornalha. Preparação do terreno remove vegetação e neve fisicamente e permite cortes e fundações naturais limitados; construções protegidas, inventários e blocos de outros NPCs são preservados. Terreno com lava, água, grandes desníveis ou sem apoio exige outro local seguro.

Todos os blocos e ingredientes são reais. A busca de materiais preserva o solo reservado à própria obra, e a água exige uma bacia com apoio e laterais antes da colocação. O planejador do projeto pode procurar madeira da espécie selecionada, pedra, areia, ferro e combustível em terreno carregado e acessível, fabricar componentes e usar uma fornalha vanilla. O ferro do balde e o vidro precisam ser obtidos e processados; nenhuma tarefa cria materiais ou sementes gratuitamente. Sem uma rota ou recurso acessível, o status explica a necessidade pendente.

A exploração passou a fazer parte da observação e das caminhadas curtas da autonomia. As tarefas novas não precisam dos antigos comandos de construir, plantar ou explorar. As ordens anteriores de madeira, pedra e comida continuam disponíveis no chat. Projetos antigos de 0.5.x continuam legíveis para que obras já salvas não sejam descartadas.

## Instalação e desenvolvimento

Substitua o JAR antigo do AI Player pelo JAR principal de `build/libs/`, mantendo Fabric API e FallingTree 4.3.4. As classes do núcleo adaptado do Baritone estão diretamente dentro do AI Player, preservando a correção de carregamento da versão 0.5.1. O binário separado de `embedded-baritone/build/libs/` serve para inspeção e recompilação e não vai na pasta `mods`.

```sh
./gradlew clean :embedded-baritone:build build compileTestmodJava runGametest --no-daemon
python3 tools/check_embedded_baritone.py
```

O workflow compila os arquivos do repositório, verifica o JAR distribuído, testa navegação/combate em servidores Fabric 0.20.0 e 0.15.11 e executa os GameTests. Consulte o resultado da revisão no Actions; este texto não declara uma execução no Android/Fold Craft Launcher.

O ZIP r7 original permanece preservado. Proveniência e licenças estão em [LICENSE.md](LICENSE.md), `third-party/Baritone/` e [docs/INTEGRACAO_BARITONE.md](docs/INTEGRACAO_BARITONE.md).
