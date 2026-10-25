# Licenças do AI Player e de componentes incorporados

## Código próprio do AI Player

O código próprio do AI Player conserva sua declaração original de direitos
reservados (`All-Rights-Reserved`), exceto pelos direitos expressamente
concedidos abaixo para uso da biblioteca LGPL. Essa declaração não se aplica
a código de terceiros distribuído sob suas próprias licenças.

## Biblioteca interna derivada de Baritone

O código derivado de Baritone no módulo `embedded-baritone/`, pacote
`dev.aiplayer.baritone`, é licenciado sob a **GNU Lesser General Public
License, versão 3 ou posterior (LGPL-3.0-or-later)**. Os cabeçalhos de cada
arquivo, avisos de autoria e termos da LGPL permanecem aplicáveis às versões
modificadas. Consulte `third-party/Baritone/NOTICE.md`, `LGPL-3.txt` e
`GPL-3.txt`. Cópias desses documentos acompanham os binários distribuídos.

A inclusão direta dessas classes no JAR principal não altera sua licença.
O módulo-fonte e um binário separado da biblioteca continuam disponíveis,
junto do código e dos scripts necessários para recompilar a combinação.

Independentemente de qualquer aviso de direitos reservados do AI Player,
os destinatários podem modificar o componente LGPL, realizar engenharia
reversa da combinação para depurar essas modificações e recompilar ou
recombinar o AI Player com uma versão modificada da biblioteca. É concedida
permissão para usar o código da aplicação e os scripts de compilação
fornecidos para produzir e executar essa combinação modificada. Nenhum
termo do AI Player restringe os direitos concedidos pela LGPL sobre o código
por ela abrangido.

Essa permissão de recombinação não altera a licença do código próprio do
AI Player nem reduz as permissões da LGPL sobre a biblioteca. Os fontes
modificáveis da biblioteca e os arquivos necessários à recompilação da
combinação são disponibilizados no mesmo repositório. As instruções estão
em `docs/INTEGRACAO_BARITONE.md`.

## Outros componentes

FallingTree conserva sua licença e seus avisos em `third-party/FallingTree/`.
Minecraft, Fabric e demais dependências conservam seus próprios direitos e
licenças. Estes avisos não representam uma licença sobre o código do Minecraft.
