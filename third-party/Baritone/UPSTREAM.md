# Proveniência do Baritone 1.10.5

## Arquivos recebidos

Os dois arquivos enviados pelo usuário, `baritone-api-fabric-1.10.5.jar` e
`baritone-api-fabric-1.10.5.zip`, contêm os mesmos bytes. O arquivo chamado
ZIP é uma cópia do JAR, contendo classes compiladas e recursos; não contém
o projeto-fonte Java.

- Tamanho: **4.807.732 bytes**.
- SHA-256: `2ec4cb8f94d0655864090d38b7009aa014c294aa3c09e20069842be0054acc07`.
- `fabric.mod.json`: Baritone `1.10.5`, Minecraft `>=1.20 <=1.20.1`.
- Release oficial: https://github.com/cabaletta/baritone/releases/tag/v1.10.5
- Artefato oficial: https://github.com/cabaletta/baritone/releases/download/v1.10.5/baritone-api-fabric-1.10.5.jar

O digest e o tamanho publicados pelo GitHub para o artefato oficial coincidem
com os arquivos recebidos. A release declara suporte a Minecraft 1.20 e
1.20.1. A integração do AI Player permanece em Minecraft **1.20.1** e Java 17.

## Fonte de referência

- Repositório: `cabaletta/baritone`.
- Tag: `v1.10.5`.
- Commit fixado: `8c55ad0721d42a01a359834edc16aaa9b5bc587d`.
- Licença: LGPL-3.0-or-later, conforme os cabeçalhos dos fontes.

O módulo `embedded-baritone/` identifica os arquivos e conceitos upstream
utilizados em seus cabeçalhos e documentação. Os arquivos foram adaptados
para um núcleo Java independente do jogador cliente. A integração não usa
descompilação do JAR obfuscado como substituta dos fontes upstream.

## Recursos que não são incorporados ao runtime do NPC

O JAR original contém mixins e processos associados ao jogador cliente, além
de um JAR aninhado `nether-pathfinder-1.6.jar`. Este inclui código nativo e uma
cópia sombreada de XZ. Esses componentes não são necessários ao núcleo Java
de planejamento do NPC e não são copiados para a integração.

Fastutil não está sombreado no JAR recebido; o Baritone usa a versão
disponibilizada pelo Minecraft. O módulo interno adaptado não exige instalar
outra cópia de Fastutil ou do Baritone.

A imagem upstream `LICENSE-Part-2.jpg` é preservada sem alterações.

## Distinção entre o original e a adaptação

`embedded-baritone/` é a biblioteca modificada do AI Player, não uma versão
oficial do Baritone. Os executores de ações continuam pertencendo ao NPC e
mantêm os controles de propriedade, inventário, ferramentas, regras do mundo,
produção física de drops e cancelamento de ordens. A matriz em
`docs/INTEGRACAO_BARITONE.md` delimita as capacidades efetivamente utilizadas.
