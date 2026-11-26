# AGENTS.md — instruções para agentes de código

Vale para qualquer agente (Claude Code, Antigravity, Copilot, Codex…). Leia
inteiro antes de mexer no código.

## O projeto

JotMD: leitor e editor de Markdown nativo para Android, clone de UI/UX do
Typora. 100% Jetpack Compose, sem WebView. Alvo: F-Droid. Repo público:
https://github.com/walterfr/JotMD. Idioma do projeto: português (código em
inglês, comentários, docs e commits em pt-BR).

Ordem de leitura:

1. **Este arquivo** — estado atual, regras, armadilhas.
2. `AI_CONTEXT.md` — decisões de arquitetura e roadmap original. As
   checkboxes do roadmap ali estão desatualizadas; o estado real está abaixo.
3. `DESIGN.md` — tokens visuais e comportamento medidos no Typora real.
4. `docs/typora-ref.md` — documento de referência e corpus de teste.

## Decisões travadas (não re-litigar sem pedido explícito do dono)

- **Motor "Escolha C":** `LazyColumn` de blocos. O bloco focado vira
  `BasicTextField` com o markdown cru; o resto é renderizado. Nunca WebView.
- **A fonte da verdade é a string Markdown**, nunca o AST.
- **Invariante inegociável:** `serialize(parse(texto)) == texto`, byte a byte.
  `RoundtripTest` guarda isso. Commit que quebra = arquivo do usuário
  corrompido em silêncio.
- **Armazenamento só por SAF** (`OpenDocument`, `OpenDocumentTree`,
  permissões persistidas). Nunca `MANAGE_EXTERNAL_STORAGE` (regra do F-Droid).
- **Foco por bloco, não por span:** o bloco focado expõe a sintaxe inteira.
  Diferença deliberada do Typora, ver `DESIGN.md` "Modelo de foco".
- **Busca:** FTS4 no SQLite nativo (`SQLiteOpenHelper`), não Room.
  Diverge do `AI_CONTEXT.md`, de propósito: uma tabela não justifica a
  dependência.
- **Inspeção com `morluto/rea` e `storytold/craft-anything` liberada:** usar o
  `rea` (MCP/CLI) e o `craft-anything` (`craft-anything-cli v0.6.1`, `--deep`,
  MCP registrado no Antigravity) para inspecionar o Typora instalado
  (`C:\Program Files\Typora`: executável, fuses, addons `.node`,
  `resources\locales\`, CSS/temas, HTML, JS/Electron, `app.asar`,
  `node_modules.asar`, `lib.asar`, `appsrc/`, `page-dist/`) e APKs Android de
  referência, extraindo regras de comportamento, tokens visuais e fluxos de
  UI/UX para reimplementar do zero em Kotlin/Compose no JotMD.
- **App Windows em Compose Desktop (`:desktop`):** o app desktop Windows
  usa Kotlin/JVM + Compose Multiplatform Desktop compartilhando `:core-md` e
  os fontes comuns de `:editor` (`editor/src/main/kotlin`), isolando apenas
  `ImagePreview` por plataforma (`editor/src/androidMain` no Android e
  `desktop/src/main` no Windows).

## Stack

Kotlin 2.1.21, AGP 8.7.3, Gradle 8.10.2, JDK 17, compileSdk/targetSdk 35,
minSdk 26, Compose BOM 2025.06.01, `org.jetbrains:markdown` 0.7.3. Versões em
`gradle/libs.versions.toml`.

```bash
./gradlew test                 # testes JVM de todos os módulos
./gradlew :app:installDebug    # instala no aparelho conectado via adb
./gradlew test assembleDebug   # o que tem que passar antes de commit
```

## Módulos

| Módulo | Conteúdo |
|---|---|
| `:core-md` | Kotlin puro, zero Android. `Block`/`DocState`, `MdParser` (`parse`), `MdSerializer` (`serialize`), `FrontMatter`, `Wikilinks`, `Tags` (`tagSpansIn`), `DocIndex`, `Scan`, `Alerts` (`alertOf`), `DocOutline` (`outlineOf`, `isTocMarker`), `DocStats` (`docStatsOf`, `TYPORA_WPM`), `DocRefs` (`docRefsOf`, links por referência e notas de rodapé `[^id]`), `MathRender` (`extractMathBlock`, `inlineMathSpansIn`, `renderLatexToUnicode`), `HtmlToMarkdown` (`htmlToMarkdown`), `DocumentImport` (importação pura HTML/DOCX/EPUB com proteção XXE e limite ZIP), `DraftStore` (`writeTextAtomic`, `DraftRepository` para recuperação de rascunhos), `DocSearch` (`findInDocument`, `replaceAllInText`, `replaceRangeInText`, `isValidSearchRegex`), `HtmlExport` (`exportToHtml`), `PdfExport` (`exportToPdf` com `[TOC]`, LaTeX ASCII fallback, referências e notas de rodapé). |
| `:editor` | Compose. `EditorState` (estado, modo foco/máquina, busca visual/regex, paste HTML/Markdown e tabela), `EditorPreferences` (`EditorPrefs`, `AppLanguage`), `JotStrings` (i18n EN/PT-BR via `LocalJotStrings`), `BlockEditor` (foco preservado ao alternar `</>` com `key(block.id)`, gutter + `VisualTransformation` sintática no código-fonte e teclas/atalho `Ctrl+T`), `BlockRenderer`/`BlockParts`/`InlineText` (renderização, GitHub Alerts, `==highlight==`, `<u>`, widget `[TOC]`, LaTeX, notas de rodapé, links por referência, realce de código, destaques de busca e controles de tabela), `Toolbar` (`⊞`), `ImagePreview`, `MdTheme` (5 presets via `MdTokens.themePreset`/`isDark`, tokens de sidebar Typora, escala de fonte e largura de coluna configuráveis), coluna centralizada (`600..1200.dp`). Operações puras em `editor/ops/`: `BlockOps`, `UndoStack`, `TaskOps`, `TableOps`, `SourceGutter`, `SyntaxHighlight`, `SmartEnter`, `SmartBackspace`, `SmartTab`, `SmartPair`, `SmartDelete`. |
| `:app` | Shell Android. `MainActivity` (gaveta estilo Typora com abas `Arquivos` — CRUD SAF `+ Nota`/`+ Pasta`/renomear/excluir — e `Índices`, menu overflow `⋮` mantendo chips seguros de foco `</>`/`Foco`/`Tabela`/`Colar+`, modal de Preferências em 3 seções, popover clicável de Contagem de Palavras em 4 linhas, recuperação de rascunhos e importação HTML/DOCX/EPUB via SAF, multi-abas, 5 temas, busca na pasta e na nota com `Aa`/`[ab]`/`.*`/destaque/substituição unitária, export HTML/PDF via SAF, links/notas de rodapé), `ExternalIntents` (abertura externa `.md`/`VIEW`/`EDIT`/`SEND` + preferências), `TabManager` (com `draftId`), `FileSession` (SAF + autosave), `FileBrowser` (árvore e CRUD via `DocumentsContract`), `SearchIndex` (FTS4). |
| `:desktop` | Shell Windows (Compose Desktop JVM). `Main` / `DesktopWorkspace` (CLI/associação `.md` no `.msi`/`.exe`, escrita atômica `writeTextAtomic`, preferências persistidas em `preferences.properties` + `DraftRepository`, i18n EN/PT-BR, modal de Preferências em 3 seções, modal de Contagem de Palavras em 4 linhas, importação HTML/DOCX/EPUB, abas, CRUD seguro na árvore `.md` com `DirectoryTreeWatcher`, Quick Open `Ctrl+P`, sidebar estilizada Typora com aba `Índices`, rodapé `30.dp` com `</>`/`Foco`/`Máquina`, `Tabela`/`Ctrl+T`, `Colar+`/`Ctrl+Shift+V` com PNG em `typora-user-images/`, busca na pasta e na nota com `Aa`/`[ab]`/`.*`/destaque/`Substituir`, 5 temas, export HTML/PDF, wikilinks/tags/notas de rodapé) + `ImagePreview.desktop` compartilhando `:core-md` e `editor/src/main/kotlin`. |

## Estado (2026-11-20)

Tag `v0.1.0` publicada. Feito e verificado em tablet:

- F0–F3: parser/serializer com roundtrip, renderização estilo Typora, edição
  por bloco, Enter divide, Backspace no início funde, ↑/↓ entre blocos,
  undo/redo com coalescência (~500 ms).
- F4: tabelas GFM alinhadas, checkboxes clicáveis, preview de imagens SAF,
  cercas de código com linguagem.
- Autosave: debounce de 1 s + flush no `ON_STOP`; título do arquivo vem do
  `DISPLAY_NAME`.
- F5: pasta raiz com navegação; busca full-text FTS4 (tokenizer `unicode61`,
  acentos dobrados, "acao" acha "ação"; reindexação incremental por
  `COLUMN_LAST_MODIFIED`; trecho com termo em negrito); `[[wikilinks]]`
  renderizados sem colchetes e clicáveis (abrem a nota; ausente mostra
  "Nota não encontrada"); `#tags` clicáveis (abrem a busca pela tag).
- Enter inteligente (`ops/SmartEnter.kt`): continua lista, numerada, tarefa
  e citação; item vazio sai da estrutura. Igual ao que o Typora grava.

Implementado em código e testado em JVM neste ciclo:

- YAML front matter (`BlockType.FRONT_MATTER`), `SmartBackspace`, `SmartTab`,
  `SmartPair`, `SmartDelete`, `TabManager` (Android `:app`), app desktop
  Windows (`:desktop`), tema escuro `night` (`MdTheme.kt`), modo código-fonte
  (`isSourceMode`), GitHub Alerts (`Alerts.kt`), `==highlight==`, busca e
  substituição na nota (`DocSearch.kt`), exportação HTML (`HtmlExport.kt`),
  exportação PDF 1.4 nativa (`PdfExport.kt` em `:core-md`, plugado via SAF
  em `:app` e diálogo em `:desktop`), otimização de split local e `contentType`
  em `LazyColumn` para documentos de 5k+ linhas, metadados/receita F-Droid
  (`dependenciesInfo` desligado em `app/build.gradle.kts`, Fastlane `pt-BR`/
  `en-US` em `fastlane/metadata/android/`, `fdroid/io.github.walterfr.jotmd.yml`),
  e bloco de paridade Typora pós-investigação `craft-anything` (`DocOutline`/`[TOC]`,
  `DocStats` `382 WPM`/CJK, `<u>`, `HtmlExport` com TOC e âncoras, 5 temas
  `GitHub`/`Newsprint`/`Pixyll`/`Whitey`/`Night`, Focus/Typewriter Mode, coluna
  `860dp`/`800dp`, `SourceGutter`, `TableOps`, padronização `"Sem Título"`,
  `Aa`/`[ab]`/substituição unitária, gaveta/sidebar `"Índices"` + Contagem de
  Palavras, rodapé `30dp`, `DirectoryTreeWatcher` e Quick Open `Ctrl+P` no Desktop),
  tag `v0.2.0` + workflow `.github/workflows/release.yml` e 2º bloco de paridade
  (`DocSearch` regex `.*` + destaques visuais no editor, `SyntaxHighlight` em
  cercas e código-fonte com `VisualTransformation`, `DocRefs` para links por
  referência e notas de rodapé `[^id]`, `MathRender` `$...$`/`$$...$$`,
  `HtmlToMarkdown` + colagem rica/`Colar+`/imagem PNG em `typora-user-images/`,
  inserção de tabela `3x4`/`Ctrl+T`/`Tabela`/`⊞` + movimentação de colunas,
  TOC/math/referências/notas no `PdfExport` e CRUD de notas/pastas no `:app` SAF
  e `:desktop`).

Verificado em tablet Android (`TB361FU - 16`, Android 16 via `adb`):

- Renderização e fluxo visual de `typora-ref.md`, barra multi-abas (`+`, troca,
  fechamento), barra `Buscar`/`Subst. tudo` (`1 substituído(s)`), tema escuro/claro,
  abertura SAF de arquivo em `/sdcard/Documents/jotmd-ws/`, `FRONT_MATTER`,
  GitHub Alerts (`[!NOTE]`, `[!WARNING]`), `==highlight==`, tabela GFM,
  toggle de checkbox salvo em disco e `SmartEnter` pelo Enter virtual do
  Microsoft SwiftKey gravado em disco.
- 3 bugs reais pegos no tablet e corrigidos com testes de regressão:
  (1) contraste do título/status bar no tema claro/escuro (`Surface` +
  `onBackground` + `SystemBarStyle` reativo ao `isDark` no `MainActivity.kt`,
  re-verificado em screenshot); (2) BOM UTF-8 (`\uFEFF`) antes de `---` no
  `MdParser.kt` + `RoundtripTest.kt` (re-verificado na tela); (3) `Backspace`/
  `Enter` do IME virtual chegando por `InputConnection` -> `EditorState.edit()`
  com filtro contra eco stale de `batchEdit` do SwiftKey (`EditorState.kt` +
  `EditorStateTest.kt`; re-validado no aparelho em 2026-11-22 com toques reais
  nas teclas `Enter`/`Backspace` do Microsoft SwiftKey: `SmartEnter` e
  `SmartBackspace` em cascata persistiram limpos em disco sem eco stale, além de
  validação completa da `v0.3.0`: CRUD SAF, TOC, LaTeX, realce sintático 1:1,
  busca regex `.*`, modos `</>`/`Foco`, tema `Night` e tabela `3x4`).

Concluído para submissão ao F-Droid (`v0.3.2`, `versionCode 5`):

- Ícone adaptativo vetorial de alto contraste (folha grafite `#1F2328` sobre
  `#EEF2F6` com `ic_launcher_monochrome` e temas API 31+ `values-v31` /
  `values-night-v31` para visibilidade na splash screen clara e escura),
  mipmaps PNG legados (`mipmap-*`), banner `featureGraphic.png` (`pt-BR` e
  `en-US`), ícone `512x512`, screenshots tablet `tenInchScreenshots/` no
  Fastlane e ícone Windows (`desktop/src/main/resources/jotmd-icon.png` e
  `jotmd.ico` multi-resolução na janela Compose + `nativeDistributions.windows`);
  módulo `:desktop` isolado via `-PandroidOnly=true` / `rm: [desktop]` no
  `settings.gradle.kts`, `distributionSha256Sum` no `gradle-wrapper.properties`
  e receita `fdroid/io.github.walterfr.jotmd.yml` (`AutoUpdateMode: Version v%v`,
  `Donate`, ordem do `fdroidserver`).

Concluído em `v0.4.0` (`versionCode 6`):

- **Preferências persistidas & i18n EN/PT-BR:** `EditorPrefs` (`5` temas, fonte `12..24 sp`, largura `600..1200 dp`, autopareamento, indentação `2`/`4`, idioma EN/PT-BR via `JotStrings`/`LocalJotStrings`, restaurar sessão) com modal em 3 seções no Android (`SharedPreferences`) e Desktop (`preferences.properties`).
- **Importação & recuperação:** `DocumentImport` puro em Kotlin para HTML/DOCX/EPUB (XXE + limite ZIP) e `DraftStore` com escrita atômica (`writeTextAtomic`) e recuperação de rascunhos (`DraftRepository`).
- **Abertura externa & paridade UI Typora:** `ExternalIntents` (`VIEW`/`EDIT`/`SEND` em `.md`/texto), associação `.md` no Desktop CLI e MSI/EXE (excluída em `AppImage`), preservação do foco/cursor ao alternar `</>` (`key(block.id)`), estilos de sidebar/overflow `⋮`/modal de Contagem de Palavras em 4 linhas e CI Node 24 (`release.yml`).

Concluído em `v0.4.1` (`versionCode 7`):

- Correção de `LaunchedEffect(incomingEvent)` em `MainActivity.kt` consumindo o intent apenas no bloco `finally` após `withContext(Dispatchers.IO)`, adicionando `IncomingIntentEvent`/`consumeIncomingEvent` em `ExternalIntents.kt`.
- `.imePadding()` em `ModalDrawerSheet` e `keyboardController?.hide()` ao abrir a gaveta lateral no Android para manter o rodapé de Contagem de Palavras visível acima do teclado virtual, além de `phoneScreenshots/` em retrato (`800x1280`).

Concluído em `v0.4.2` (`versionCode 8`):

- Inicialização em nota em branco limpa no Android e no Desktop (removido carregamento/empacotamento automático de `typora-ref.md` no runtime).
- `android:configChanges` em `MainActivity` no `AndroidManifest.xml` para preservar abas, texto, cursor e scroll na rotação do aparelho, além da persistência e restauração das abas SAF e aba ativa via `SharedPreferences` (`AndroidSessionTabs`).

Pendente:

- Abrir Merge Request no `fdroiddata` copiando `fdroid/io.github.walterfr.jotmd.yml`
  para `metadata/io.github.walterfr.jotmd.yml`.

## Armadilhas já pagas (não repetir)

1. **Pontuação vira token no `jetbrains/markdown`.** `(`, `)`, `*` soltos são
   tokens de marcador. Descartar por tipo apaga texto do usuário. Decidir pela
   posição na árvore. Ver `DESIGN.md`.
2. **Token e elemento com o mesmo nome** (`MarkdownTokenTypes.BLOCK_QUOTE` ≠
   `MarkdownElementTypes.BLOCK_QUOTE`). Trocar compila e vaza marcador.
3. **IDs de bloco:** uma única `BlockIdSource` por documento carregado, dona
   do `EditorState`. Chamar `parse()` com fonte nova no meio da edição colide
   IDs e quebra o `LazyColumn(key = id)`.
4. **Commit atrasado do IME.** O teclado (SwiftKey) entrega o texto em
   composição ao campo antigo DEPOIS que um toque já trocou o foco. Por isso
   `EditorState.edit(value, from)` descarta valores de bloco não focado.
   Sem isso, tocar noutro bloco sobrescreve ele inteiro.
5. **Toques em toolbar/checkbox/link:** usar `pointerInput(Unit)` +
   `detectTapGestures` + `rememberUpdatedState`. `clickable` ou
   `pointerInput(lambda)` roubam o foco do `BasicTextField` ou reiniciam o
   detector a cada recomposição.
6. **Enter no fim do bloco não pode reparsear** um `after` vazio (o parser
   não cria bloco para texto vazio e o foco pularia). Exceto cerca de código
   aberta, que precisa cascatear. Ver comentário em `splitAtCursor`.
7. **Wikilinks/tags na renderização** são "specials" por posição no source
   (`Ctx` em `InlineText.kt`), viram URIs internas `jotmd:wiki:` /
   `jotmd:tag:` e são interceptados por `LocalUriHandler` no `MainActivity`.
8. **Front matter só na linha 1 do arquivo.** `parse(text, ids, frontMatter)`:
   o default `true` vale só para o documento inteiro. Quem parseia um trecho
   (`reparse` com `fromIndex > 0`, `mergeWithPrevious` fora do topo,
   `splitAt`) passa `false`, senão um `---` no meio vira metadado.
9. **Scripts Python via heredoc no Git Bash do Windows** trocaram `"\\n"` por
   newline real ao editar `.kt`. Para editar código, use a ferramenta de
   edição do agente, não `python - <<EOF`.
10. **Corpus de teste em `docs/*.md` é `-text`** no `.gitattributes`: bytes
   exatos, sem normalização de fim de linha.
11. **IME Android (SwiftKey) faz `deleteSurroundingText` + eco de `batchEdit`:**
    teclados virtuais disparam `Backspace` via `InputConnection -> onValueChange`
    (`EditorState.edit`) em vez de `onPreviewKeyEvent`. Além disso, quando `edit`
    reescreve `editValue` (como `SmartBackspace`/`SmartPair`), o `batchEdit` do
    IME pode reenviar o texto cru anterior ~4 ms depois antes da recomposição
    atualizar o `BasicTextField`, exigindo o filtro `isStaleImeEcho` em `EditorState.kt`.
12. **BOM UTF-8 (`\uFEFF`) na linha 1:** arquivos criados no Windows podem começar
    com `\uFEFF` antes de `---`; `MdParser.parse` preserva o `\uFEFF` em
    `DocState.leading` para não quebrar `FRONT_MATTER` nem o roundtrip byte a byte.
13. **`LaunchedEffect(key)` nunca deve limpar a própria chave antes de suspender:**
    se `LaunchedEffect(incomingEvent)` chama `onConsumeIntent()` antes de
    `withContext(Dispatchers.IO)`, a mudança de chave cancela a corrotina no meio
    da leitura e descarta o intent. Envelopar com `IncomingIntentEvent` e consumir
    no `finally` após o tratamento (`MainActivity.kt`).

## Regras de trabalho

- Mudança de comportamento = teste JVM primeiro (`core-md/src/test`,
  `editor/src/test`). Bug corrigido deixa teste de regressão.
- Diff mínimo; reusar o que existe; sem dependência nova para o que poucas
  linhas resolvem.
- Commits: Conventional Commits em pt-BR (`feat(editor): …`, `fix(app): …`).
  Um commit por fase/feature, `test` + `assembleDebug` passando.
- **O dono também commita em paralelo.** Sempre `git fetch` antes de
  `git push`; se divergiu, `git pull --rebase`, rebuild, testes, push.
- Comportamento novo "como o Typora" deve ser observado ou inspecionado via
  `rea` ou `craft-anything` (`--deep`) no Typora antes e registrado no
  `DESIGN.md`.
- **Execução em paralelo com múltiplos subagentes:** tarefas ou features
  independentes devem ser sempre paralelizadas usando múltiplos subagentes
  (`invoke_subagent`) com conjuntos disjuntos de arquivos, consolidando o
  build/testes na thread principal.
- **Releases automáticas (GitHub Actions) & Pride Versioning:** versionamento
  segue o [**Pride Versioning**](https://pridever.org/) (`PROUD.DEFAULT.SHAME`).
  Tags `v*` disparam `.github/workflows/release.yml` (`.apk` Android,
  `.msi`/`.exe`/`.zip` portátil Windows e `SHA256SUMS.txt`). `app/build.gradle.kts`
  só ativa `signingConfig` quando `KEYSTORE_PATH`/`USE_DEBUG_SIGNING` existem no
  ambiente, mantendo o build do F-Droid intacto.

## Teste em aparelho (tablet Android via adb wireless)

- O aparelho é compartilhado com outros apps. Antes de qualquer toque:
  `adb shell dumpsys activity activities | grep topResumedActivity` e
  confirmar que o JotMD está na frente. Nunca tocar em outro app.
- Não contornar tela bloqueada: pedir ao dono para desbloquear.
- Durante o teste: `adb shell settings put system screen_off_timeout 1800000`;
  ao fim, restaurar `60000`.
- Arquivos de teste só em `/sdcard/Documents/jotmd-ws/` (criados pelo agente).
  No seletor SAF, navegar só para o armazenamento local.
- Ler o resultado pelo arquivo gravado (`adb exec-out cat …`), não só pela
  tela. Teclas via `adb shell input keyevent` são de hardware; testar também
  o Enter do teclado virtual.
- Limpeza ao fim: `adb shell rm -r /sdcard/Documents/jotmd-ws`,
  `adb shell pm clear io.github.walterfr.jotmd`.
